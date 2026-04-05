@file:JvmName("KotlinInterop")

package uk.org.lidalia.kotlinfromgroovy

import groovy.lang.GString
import groovy.lang.MissingMethodException
import org.codehaus.groovy.runtime.InvokerHelper
import org.codehaus.groovy.runtime.wrappers.GroovyObjectWrapper
import org.codehaus.groovy.runtime.wrappers.PojoWrapper
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import kotlin.reflect.KCallable
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.kotlinFunction

fun callMethodWithNamedArgs(
  target: Any,
  methodName: String,
  namedArgs: LinkedHashMap<String, Any?>,
  positionalArgs: Array<Any?>,
  namedFirst: Boolean = false,
): Any? {
  ensureKotlinAwareMetaClass(target.javaClass)

  // When there are named args, try Kotlin reflection first to preserve
  // namedFirst ordering (which is lost if we go through Groovy dispatch).
  if (namedArgs.isNotEmpty()) {
    // When namedFirst=true, the source-level ordering of named vs positional
    // args matters, and only Kotlin reflection preserves it. Try Kotlin first.
    // When namedFirst=false, Groovy dispatch works and correctly handles
    // methods that take Map as first arg (Groovy convention).
    if (namedFirst) {
      try {
        return resolveKotlinMethodCall(target, methodName, namedArgs, positionalArgs, namedFirst)
      } catch (_: MissingMethodException) {
        // Named arg keys didn't match any parameter names — likely a literal
        // map value, not actual named args. Fall through to Groovy dispatch.
      }
    }
    val groovyArgs = if (positionalArgs.isEmpty()) {
      arrayOf<Any?>(namedArgs)
    } else {
      arrayOf<Any?>(namedArgs, *positionalArgs)
    }
    try {
      return InvokerHelper.invokeMethod(target, methodName, groovyArgs)
    } catch (_: MissingMethodException) {
      // Fall back to Kotlin named-arg resolution
    }
  }

  // Positional-only: use pickMethod to avoid Groovy coercing missing args to null
  val argTypes = positionalArgs.map { it?.javaClass ?: Any::class.java }.toTypedArray()
  val metaMethod = InvokerHelper.getMetaClass(target).pickMethod(methodName, argTypes)
  if (metaMethod != null && metaMethod.nativeParameterTypes.size == positionalArgs.size) {
    return metaMethod.invoke(target, positionalArgs)
  }

  // If single positional arg is a Map, it may be named args that the AST
  // transform couldn't detect (e.g. in Spock expect blocks)
  if (positionalArgs.size == 1 && positionalArgs[0] is Map<*, *>) {
    @Suppress("UNCHECKED_CAST")
    val mapArg = positionalArgs[0] as LinkedHashMap<String, Any?>
    try {
      return resolveKotlinMethodCall(target, methodName, mapArg, arrayOf())
    } catch (_: IllegalArgumentException) {
      // Map didn't match as named args; fall through
    } catch (_: MissingMethodException) {
      // fall through
    }
  }

  return resolveKotlinMethodCall(target, methodName, namedArgs, positionalArgs, namedFirst)
}

fun callExtensionMethod(
  declaringClasses: Array<Class<*>>,
  methodName: String,
  receiver: Any?,
  namedArgs: LinkedHashMap<String, Any?>,
  positionalArgs: Array<Any?>,
  namedFirst: Boolean = false,
): Any? {
  // Groovy's safe-navigation (?.) still calls the method with null receiver
  if (receiver == null) return null
  ensureKotlinAwareMetaClass(receiver.javaClass)

  // Instance members take priority over extensions (Kotlin semantics)
  try {
    if (namedArgs.isEmpty()) {
      val argTypes = positionalArgs.map { it?.javaClass ?: Any::class.java }.toTypedArray()
      val metaMethod = InvokerHelper.getMetaClass(receiver).pickMethod(methodName, argTypes)
      if (metaMethod != null && metaMethod.nativeParameterTypes.size == positionalArgs.size) {
        return metaMethod.invoke(receiver, positionalArgs)
      }
    }
    return resolveKotlinMethodCall(receiver, methodName, namedArgs, positionalArgs, namedFirst)
  } catch (_: MissingMethodException) {
    // Not an instance method — try extension
  }

  return resolveExtensionOnClasses(
    declaringClasses,
    receiver,
    methodName,
    namedArgs,
    positionalArgs,
    namedFirst,
  )
}

fun constructWithNamedArgs(
  clazz: Class<*>,
  namedArgs: LinkedHashMap<String, Any?>,
  positionalArgs: Array<Any?>,
  namedFirst: Boolean = false,
): Any {
  ensureKotlinAwareMetaClass(clazz)

  // Try Groovy's default constructor resolution first
  if (namedArgs.isEmpty()) {
    try {
      return groovyConstruct(clazz, positionalArgs)
    } catch (_: Exception) {
      // Fall back to Kotlin reflection
    }
  }

  val kClass = clazz.kotlin
  val constructor = kClass.primaryConstructor
  if (constructor == null) {
    // Java class or class without primary constructor — use Groovy dispatch
    return groovyConstruct(clazz, buildGroovyArgs(namedArgs, positionalArgs))
  }

  try {
    val paramMap = resolveArgs(
      constructor.parameters,
      namedArgs,
      positionalArgs,
      namedFirst,
      validateNullability = isKotlinClass(clazz),
    )

    constructor.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    return callByUnwrapping(constructor, paramMap) as Any
  } catch (e: UnknownNamedParameterException) {
    // Named args didn't match constructor params — possibly a literal map
    // value misidentified as named args by the AST transform.
    // Try Groovy dispatch with the map as a positional argument;
    // if that also fails, re-throw the original error.
    try {
      return groovyConstruct(clazz, buildGroovyArgs(namedArgs, positionalArgs))
    } catch (_: Exception) {
      throw e
    }
  }
}

internal fun resolveKotlinMethodCall(
  target: Any,
  methodName: String,
  namedArgs: LinkedHashMap<String, Any?>,
  positionalArgs: Array<Any?>,
  namedFirst: Boolean = false,
): Any? {
  val kClass = target::class
  val functions = try {
    kClass.memberFunctions.filter { it.name == methodName }
  } catch (_: Exception) {
    // kotlin-reflect can fail on some Java classes (e.g. LinkedHashSet.clone visibility)
    emptyList()
  }

  if (functions.isEmpty()) {
    throw MissingMethodException(
      methodName,
      target.javaClass,
      arrayOf<Any?>(namedArgs, *positionalArgs),
    )
  }

  val errors = mutableListOf<IllegalArgumentException>()

  for (function in functions) {
    try {
      val valueParams = function.parameters.filter { it.kind == KParameter.Kind.VALUE }
      val instanceParam = function.parameters.first { it.kind == KParameter.Kind.INSTANCE }

      val paramMap = resolveArgs(
        valueParams,
        namedArgs,
        positionalArgs,
        namedFirst,
        validateNullability = isKotlinClass(target.javaClass),
      )
      paramMap[instanceParam] = target

      function.isAccessible = true
      return callByUnwrapping(function, paramMap)
    } catch (e: IllegalArgumentException) {
      errors += e
    }
  }

  // If all errors are "unknown parameter name" AND there are positional args,
  // the named args likely represent a literal map value rather than actual named
  // arguments. Signal this as a missing method so callers can fall back to Groovy
  // dispatch. Without positional args, it's genuinely a wrong named arg error.
  if (positionalArgs.isNotEmpty() && errors.all { it is UnknownNamedParameterException }) {
    throw MissingMethodException(
      methodName,
      target.javaClass,
      arrayOf<Any?>(namedArgs, *positionalArgs),
    )
  }
  throw errors.first()
}

internal fun resolveKotlinExtensionCall(
  target: Any,
  methodName: String,
  namedArgs: LinkedHashMap<String, Any?>,
  positionalArgs: Array<Any?>,
  namedFirst: Boolean = false,
): Any? {
  val candidates = KotlinExtensionFunctionResolver.findExtensionFunctions(
    methodName,
    target.javaClass,
  )

  if (candidates.isEmpty()) {
    throw MissingMethodException(
      methodName,
      target.javaClass,
      arrayOf<Any?>(namedArgs, *positionalArgs),
    )
  }

  val errors = mutableListOf<IllegalArgumentException>()

  for (candidate in candidates) {
    try {
      val function = candidate.function
      val receiverParam = function.parameters
        .first { it.kind == KParameter.Kind.EXTENSION_RECEIVER }
      val valueParams = function.parameters
        .filter { it.kind == KParameter.Kind.VALUE }

      val paramMap = resolveArgs(
        valueParams,
        namedArgs,
        positionalArgs,
        namedFirst,
        validateNullability = true,
      )
      paramMap[receiverParam] = target

      function.isAccessible = true
      return callByUnwrapping(function, paramMap)
    } catch (e: IllegalArgumentException) {
      errors += e
    }
  }

  if (errors.all { it is UnknownNamedParameterException }) {
    throw MissingMethodException(
      methodName,
      target.javaClass,
      arrayOf<Any?>(namedArgs, *positionalArgs),
    )
  }
  throw errors.first()
}

private fun resolveExtensionOnClasses(
  declaringClasses: Array<Class<*>>,
  target: Any,
  methodName: String,
  namedArgs: LinkedHashMap<String, Any?>,
  positionalArgs: Array<Any?>,
  namedFirst: Boolean,
): Any? {
  val errors = mutableListOf<IllegalArgumentException>()

  declaringClasses.forEach { declaringClass ->
    findExtensionFunctionsOnClass(
      declaringClass,
      methodName,
      target.javaClass,
    ).forEach { function ->
      try {
        val receiverParam = function.parameters
          .first { it.kind == KParameter.Kind.EXTENSION_RECEIVER }
        val valueParams = function.parameters
          .filter { it.kind == KParameter.Kind.VALUE }

        val paramMap =
          resolveArgs(
            valueParams,
            namedArgs,
            positionalArgs,
            namedFirst,
            validateNullability = true,
          )
        paramMap[receiverParam] = target

        function.isAccessible = true
        return callByUnwrapping(function, paramMap)
      } catch (e: IllegalArgumentException) {
        errors += e
      }
    }
  }

  if (errors.isEmpty() || errors.all { it is UnknownNamedParameterException }) {
    throw MissingMethodException(
      methodName,
      target.javaClass,
      arrayOf<Any?>(namedArgs, *positionalArgs),
    )
  }
  throw errors.first()
}

private fun findExtensionFunctionsOnClass(
  declaringClass: Class<*>,
  methodName: String,
  receiverType: Class<*>,
): List<KFunction<*>> = declaringClass.declaredMethods.asSequence()
  .filter { Modifier.isPublic(it.modifiers) }
  .filter { Modifier.isStatic(it.modifiers) }
  .filter { !it.isSynthetic }
  .filter { it.name == methodName }
  .mapNotNull { method ->
    try {
      val kFunction = method.kotlinFunction ?: return@mapNotNull null
      val receiverParam = kFunction.parameters
        .find { it.kind == KParameter.Kind.EXTENSION_RECEIVER }
        ?: return@mapNotNull null
      val extReceiverType = receiverParam.type.jvmErasure.java
      if (extReceiverType.isAssignableFrom(receiverType)) kFunction else null
    } catch (_: Throwable) {
      null
    }
  }
  .toList()

internal class UnknownNamedParameterException(name: String) :
  IllegalArgumentException("Cannot find a parameter with this name: $name")

private fun callByUnwrapping(callable: KCallable<*>, paramMap: Map<KParameter, Any?>): Any? = try {
  callable.callBy(paramMap)
} catch (e: InvocationTargetException) {
  throw e.cause ?: e
}

@Suppress("UNCHECKED_CAST")
private fun groovyConstruct(clazz: Class<*>, args: Array<Any?>): Any =
  InvokerHelper.invokeConstructorOf(clazz, args) as Any

private fun buildGroovyArgs(
  namedArgs: LinkedHashMap<String, Any?>,
  positionalArgs: Array<Any?>,
): Array<Any?> = when {
  namedArgs.isEmpty() -> positionalArgs
  positionalArgs.isEmpty() -> arrayOf<Any?>(namedArgs)
  else -> arrayOf<Any?>(namedArgs, *positionalArgs)
}

private fun coerceGroovyType(value: Any, param: KParameter): Any = when {
  value is GString && param.type.jvmErasure == String::class -> value.toString()
  else -> value
}

private fun unwrapGroovyWrapper(value: Any): Any? = when (value) {
  is PojoWrapper -> value.unwrap()
  is GroovyObjectWrapper -> value.unwrap()
  else -> value
}

private fun isKotlinClass(clazz: Class<*>): Boolean =
  clazz.isAnnotationPresent(Metadata::class.java)

private fun resolveArgs(
  params: List<KParameter>,
  namedArgs: LinkedHashMap<String, Any?>,
  positionalArgs: Array<Any?>,
  namedFirst: Boolean = false,
  validateNullability: Boolean = false,
): MutableMap<KParameter, Any?> {
  val paramMap = mutableMapOf<KParameter, Any?>()
  val assignedParams = mutableSetOf<KParameter>()

  validateNamedArgNames(params, namedArgs)

  if (namedFirst && positionalArgs.isNotEmpty() && namedArgs.isNotEmpty()) {
    assignNamedFirst(params, namedArgs, positionalArgs, paramMap, assignedParams)
  } else {
    assignPositionalFirst(params, namedArgs, positionalArgs, paramMap, assignedParams)
  }

  fillMissingRequiredParams(params, assignedParams, paramMap)
  validateAndCoerceArgs(paramMap, validateNullability)

  return paramMap
}

private fun validateNamedArgNames(
  params: List<KParameter>,
  namedArgs: LinkedHashMap<String, Any?>,
) {
  namedArgs.keys.forEach { name ->
    params.find { it.name == name }
      ?: throw UnknownNamedParameterException(name)
  }
}

private fun assignNamedFirst(
  params: List<KParameter>,
  namedArgs: LinkedHashMap<String, Any?>,
  positionalArgs: Array<Any?>,
  paramMap: MutableMap<KParameter, Any?>,
  assignedParams: MutableSet<KParameter>,
) {
  // Named args were written before positional args in source.
  // Assign named args by name first, then positional args fill slots
  // after the highest-indexed named parameter.
  namedArgs.forEach { (name, value) ->
    val param = params.find { it.name == name }!!
    paramMap[param] = value
    assignedParams += param
  }

  val highestNamedIndex = namedArgs.keys
    .map { name -> params.indexOfFirst { it.name == name } }
    .max()

  val slotsAfterNamed = params
    .filterIndexed { index, param -> index > highestNamedIndex && param !in assignedParams }

  if (positionalArgs.size > slotsAfterNamed.size) {
    throw IllegalArgumentException("Mixing named and positioned arguments is not allowed")
  }

  slotsAfterNamed.zip(positionalArgs.toList()).forEach { (param, value) ->
    paramMap[param] = value
    assignedParams += param
  }
}

private fun assignPositionalFirst(
  params: List<KParameter>,
  namedArgs: LinkedHashMap<String, Any?>,
  positionalArgs: Array<Any?>,
  paramMap: MutableMap<KParameter, Any?>,
  assignedParams: MutableSet<KParameter>,
) {
  assignPositionalArgs(params, positionalArgs, paramMap, assignedParams)

  namedArgs.forEach { (name, value) ->
    val param = params.find { it.name == name }!!
    if (param in assignedParams) {
      throw IllegalArgumentException("An argument is already passed for this parameter")
    }
    paramMap[param] = value
    assignedParams += param
  }
}

private fun assignPositionalArgs(
  params: List<KParameter>,
  positionalArgs: Array<Any?>,
  paramMap: MutableMap<KParameter, Any?>,
  assignedParams: MutableSet<KParameter>,
) {
  val lastParam = params.lastOrNull()
  val varargIndex = if (lastParam?.isVararg == true) params.size - 1 else -1

  when {
    varargIndex >= 0 && positionalArgs.size > varargIndex -> {
      // Assign non-vararg positional args, then pack remainder into vararg
      positionalArgs.take(varargIndex).forEachIndexed { index, value ->
        paramMap[params[index]] = value
        assignedParams += params[index]
      }
      paramMap[lastParam!!] = toTypedVarargArray(lastParam, positionalArgs.drop(varargIndex))
      assignedParams += lastParam
    }

    positionalArgs.size <= params.size -> {
      positionalArgs.forEachIndexed { index, value ->
        paramMap[params[index]] = value
        assignedParams += params[index]
      }
    }

    else -> {
      throw IllegalArgumentException("Too many arguments")
    }
  }
}

private fun fillMissingRequiredParams(
  params: List<KParameter>,
  assignedParams: Set<KParameter>,
  paramMap: MutableMap<KParameter, Any?>,
) {
  // Nullable params without defaults are filled with null to match
  // Groovy's behavior of coercing omitted trailing args to null.
  // Vararg params are implicitly optional — default to empty array.
  params
    .filter { it !in assignedParams && !it.isOptional && !it.isVararg }
    .forEach { param ->
      if (param.type.isMarkedNullable) {
        paramMap[param] = null
      } else {
        throw IllegalArgumentException("No value passed for parameter '${param.name}'")
      }
    }
}

private fun validateAndCoerceArgs(
  paramMap: MutableMap<KParameter, Any?>,
  validateNullability: Boolean,
) {
  // Validate argument types and nullability.
  // When null is passed for a non-null param that has a default,
  // remove it from the map so callBy uses the Kotlin default value.
  // This matches Groovy's convention where null means "unspecified."
  val paramsToRemove = mutableListOf<KParameter>()
  paramMap.forEach { (param, value) ->
    val effective = value?.let { unwrapGroovyWrapper(it) }
    if (effective == null) {
      if (!param.type.isMarkedNullable && param.isOptional) {
        paramsToRemove += param
      } else if (validateNullability && !param.type.isMarkedNullable) {
        throw NullPointerException(
          "Null passed for non-null parameter '${param.name}'",
        )
      }
      if (value != null) {
        paramMap[param] = null
      }
    } else {
      val coerced = coerceGroovyType(effective, param)
      if (!param.type.jvmErasure.isInstance(coerced)) {
        val typeDesc = describeValueType(coerced)
        val expectedName = param.type.jvmErasure.simpleName
        throw IllegalArgumentException(
          "The $typeDesc does not conform to the expected type $expectedName",
        )
      }
      if (coerced !== value) {
        paramMap[param] = coerced
      }
    }
  }

  paramsToRemove.forEach { paramMap.remove(it) }
}

private fun toTypedVarargArray(param: KParameter, values: List<Any?>): Any {
  val elementType = param.type.arguments.first().type?.jvmErasure?.java ?: Any::class.java
  val typedArray = java.lang.reflect.Array.newInstance(elementType, values.size)
  values.forEachIndexed { i, v -> java.lang.reflect.Array.set(typedArray, i, v) }
  return typedArray
}

private fun describeValueType(value: Any): String = when (value) {
  is Int -> "integer literal"
  is Long -> "long literal"
  is Double -> "double literal"
  is Float -> "float literal"
  is Boolean -> "boolean literal"
  is Char -> "character literal"
  is String -> "string literal"
  else -> "value of type ${value::class.simpleName}"
}
