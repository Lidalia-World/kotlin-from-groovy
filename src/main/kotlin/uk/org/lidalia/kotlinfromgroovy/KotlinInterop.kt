@file:JvmName("KotlinInterop")

package uk.org.lidalia.kotlinfromgroovy

import groovy.lang.GString
import groovy.lang.MissingMethodException
import org.codehaus.groovy.runtime.InvokerHelper
import org.codehaus.groovy.runtime.wrappers.GroovyObjectWrapper
import org.codehaus.groovy.runtime.wrappers.PojoWrapper
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KCallable
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.jvmErasure

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
        try {
          return resolveKotlinExtensionCall(
            target,
            methodName,
            namedArgs,
            positionalArgs,
            namedFirst,
          )
        } catch (_: MissingMethodException) {
          // Named arg keys didn't match any parameter names — likely a literal
          // map value, not actual named args. Fall through to Groovy dispatch.
        }
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

  try {
    return resolveKotlinMethodCall(target, methodName, namedArgs, positionalArgs, namedFirst)
  } catch (_: MissingMethodException) {
    return resolveKotlinExtensionCall(target, methodName, namedArgs, positionalArgs, namedFirst)
  }
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
      @Suppress("UNCHECKED_CAST")
      return InvokerHelper.invokeConstructorOf(clazz, positionalArgs) as Any
    } catch (_: Exception) {
      // Fall back to Kotlin reflection
    }
  }

  val kClass = clazz.kotlin
  val constructor = kClass.primaryConstructor
  if (constructor == null) {
    // Java class or class without primary constructor — use Groovy dispatch
    val groovyArgs = buildGroovyArgs(namedArgs, positionalArgs)
    @Suppress("UNCHECKED_CAST")
    return InvokerHelper.invokeConstructorOf(clazz, groovyArgs) as Any
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
    val groovyArgs = buildGroovyArgs(namedArgs, positionalArgs)
    try {
      @Suppress("UNCHECKED_CAST")
      return InvokerHelper.invokeConstructorOf(clazz, groovyArgs) as Any
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

internal class UnknownNamedParameterException(name: String) :
  IllegalArgumentException("Cannot find a parameter with this name: $name")

private fun callByUnwrapping(callable: KCallable<*>, paramMap: Map<KParameter, Any?>): Any? = try {
  callable.callBy(paramMap)
} catch (e: InvocationTargetException) {
  throw e.cause ?: e
}

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

  // Validate named arg names
  for (name in namedArgs.keys) {
    val param = params.find { it.name == name }
      ?: throw UnknownNamedParameterException(name)
  }

  if (namedFirst && positionalArgs.isNotEmpty() && namedArgs.isNotEmpty()) {
    // Named args were written before positional args in source.
    // Assign named args by name first, then positional args fill slots
    // after the highest-indexed named parameter.
    for ((name, value) in namedArgs) {
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

    for ((param, value) in slotsAfterNamed.zip(positionalArgs.toList())) {
      paramMap[param] = value
      assignedParams += param
    }
  } else {
    // Positional args first (standard behavior)
    for ((index, value) in positionalArgs.withIndex()) {
      if (index >= params.size) {
        throw IllegalArgumentException("Too many arguments")
      }
      val param = params[index]
      paramMap[param] = value
      assignedParams += param
    }

    for ((name, value) in namedArgs) {
      val param = params.find { it.name == name }!!
      if (param in assignedParams) {
        throw IllegalArgumentException("An argument is already passed for this parameter")
      }
      paramMap[param] = value
      assignedParams += param
    }
  }

  // Check for missing required params
  for (param in params) {
    if (param !in assignedParams && !param.isOptional) {
      throw IllegalArgumentException("No value passed for parameter '${param.name}'")
    }
  }

  // Validate argument types and nullability
  for ((param, value) in paramMap) {
    if (value == null) {
      if (validateNullability && !param.type.isMarkedNullable) {
        throw NullPointerException(
          "Null passed for non-null parameter '${param.name}'",
        )
      }
    } else {
      val unwrapped = unwrapGroovyWrapper(value)
      if (unwrapped == null) {
        if (validateNullability && !param.type.isMarkedNullable) {
          throw NullPointerException(
            "Null passed for non-null parameter '${param.name}'",
          )
        }
        paramMap[param] = null
      } else {
        val coerced = coerceGroovyType(unwrapped, param)
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
  }

  return paramMap
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
