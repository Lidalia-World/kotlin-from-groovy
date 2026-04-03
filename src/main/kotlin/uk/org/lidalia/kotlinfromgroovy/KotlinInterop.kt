@file:JvmName("KotlinInterop")

package uk.org.lidalia.kotlinfromgroovy

import groovy.lang.MissingMethodException
import org.codehaus.groovy.runtime.InvokerHelper
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

  // First try Groovy's default behavior
  try {
    if (namedArgs.isEmpty()) {
      // Use pickMethod with param count check to avoid Groovy coercing
      // missing args to null (which violates Kotlin null safety)
      val argTypes = positionalArgs.map { it?.javaClass ?: Any::class.java }.toTypedArray()
      val metaMethod = InvokerHelper.getMetaClass(target).pickMethod(methodName, argTypes)
      if (metaMethod != null && metaMethod.nativeParameterTypes.size == positionalArgs.size) {
        return metaMethod.invoke(target, positionalArgs)
      }
    } else {
      val groovyArgs = if (positionalArgs.isEmpty()) {
        arrayOf<Any?>(namedArgs)
      } else {
        arrayOf<Any?>(namedArgs, *positionalArgs)
      }
      return InvokerHelper.invokeMethod(target, methodName, groovyArgs)
    }
  } catch (_: MissingMethodException) {
    // Fall back to Kotlin named-arg resolution
  }

  // If single positional arg is a Map, it may be named args that the AST
  // transform couldn't detect (e.g. in Spock expect blocks)
  if (namedArgs.isEmpty() && positionalArgs.size == 1 && positionalArgs[0] is Map<*, *>) {
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
    ?: error("Class ${clazz.simpleName} has no primary constructor")

  val paramMap = resolveArgs(
    constructor.parameters,
    namedArgs,
    positionalArgs,
    namedFirst,
  )

  constructor.isAccessible = true
  @Suppress("UNCHECKED_CAST")
  return constructor.callBy(paramMap) as Any
}

internal fun resolveKotlinMethodCall(
  target: Any,
  methodName: String,
  namedArgs: LinkedHashMap<String, Any?>,
  positionalArgs: Array<Any?>,
  namedFirst: Boolean = false,
): Any? {
  val kClass = target::class
  val functions = kClass.memberFunctions.filter { it.name == methodName }

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

      val paramMap = resolveArgs(valueParams, namedArgs, positionalArgs, namedFirst)
      paramMap[instanceParam] = target

      function.isAccessible = true
      return function.callBy(paramMap)
    } catch (e: IllegalArgumentException) {
      errors += e
    }
  }

  throw errors.first()
}

private fun resolveArgs(
  params: List<KParameter>,
  namedArgs: LinkedHashMap<String, Any?>,
  positionalArgs: Array<Any?>,
  namedFirst: Boolean = false,
): MutableMap<KParameter, Any?> {
  val paramMap = mutableMapOf<KParameter, Any?>()
  val assignedParams = mutableSetOf<KParameter>()

  // Validate named arg names
  for (name in namedArgs.keys) {
    val param = params.find { it.name == name }
      ?: throw IllegalArgumentException("Cannot find a parameter with this name: $name")
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

  // Validate argument types
  for ((param, value) in paramMap) {
    if (value != null && !param.type.jvmErasure.isInstance(value)) {
      val typeDesc = describeValueType(value)
      val expectedName = param.type.jvmErasure.simpleName
      throw IllegalArgumentException(
        "The $typeDesc does not conform to the expected type $expectedName",
      )
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
