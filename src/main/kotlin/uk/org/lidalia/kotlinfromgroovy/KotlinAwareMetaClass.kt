package uk.org.lidalia.kotlinfromgroovy

import groovy.lang.DelegatingMetaClass
import groovy.lang.GroovyObject
import groovy.lang.GroovySystem
import groovy.lang.MetaClass
import groovy.lang.MetaClassRegistry
import groovy.lang.MissingMethodException

class KotlinAwareMetaClass(delegate: MetaClass) : DelegatingMetaClass(delegate) {

  override fun invokeMethod(
    target: Any,
    name: String,
    args: Array<Any?>?,
  ): Any? {
    val safeArgs = args ?: emptyArray()
    // For Kotlin classes, check for exact method match before delegating to Groovy.
    // Groovy silently coerces missing args to null instead of throwing
    // MissingMethodException, which bypasses default parameter support.
    if (target.javaClass.isAnnotationPresent(Metadata::class.java)) {
      val argTypes = safeArgs.map { it?.javaClass ?: Any::class.java }.toTypedArray()
      val metaMethod = delegate.pickMethod(name, argTypes)
      if (metaMethod == null || metaMethod.nativeParameterTypes.size != safeArgs.size) {
        return fallbackToKotlinReflect(
          target,
          name,
          safeArgs,
          MissingMethodException(name, target.javaClass, safeArgs),
        )
      }
    }
    return try {
      super.invokeMethod(target, name, safeArgs)
    } catch (e: MissingMethodException) {
      // Only fall back for the method we're trying to call, not for
      // MissingMethodExceptions thrown inside the method's execution
      // (e.g. private superclass methods called within the method body).
      if (e.method == name) {
        fallbackToKotlinReflect(target, name, safeArgs, e)
      } else {
        throw e
      }
    }
  }

  override fun invokeMethod(
    target: Any,
    name: String,
    args: Any?,
  ): Any? = when (args) {
    null -> invokeMethod(target, name, emptyArray<Any?>())

    else -> try {
      super.invokeMethod(target, name, args)
    } catch (e: MissingMethodException) {
      if (e.method == name) {
        @Suppress("UNCHECKED_CAST")
        val argsArray: Array<Any?> = when (args) {
          is Array<*> -> args as Array<Any?>
          else -> arrayOf(args)
        }
        fallbackToKotlinReflect(target, name, argsArray, e)
      } else {
        throw e
      }
    }
  }

  private fun fallbackToKotlinReflect(
    target: Any,
    name: String,
    args: Array<Any?>,
    original: MissingMethodException,
  ): Any? {
    // Groovy puts named args as a LinkedHashMap in the first position,
    // with any positional args after it.
    val namedArgs: LinkedHashMap<String, Any?>
    val positionalArgs: Array<Any?>
    if (args.isNotEmpty() && args[0] is LinkedHashMap<*, *>) {
      @Suppress("UNCHECKED_CAST")
      namedArgs = args[0] as LinkedHashMap<String, Any?>
      positionalArgs = args.drop(1).toTypedArray()
    } else {
      namedArgs = linkedMapOf()
      positionalArgs = args
    }
    return resolveKotlinCall(target, name, namedArgs, positionalArgs, original)
  }
}

private fun resolveKotlinCall(
  target: Any,
  name: String,
  namedArgs: LinkedHashMap<String, Any?>,
  positionalArgs: Array<Any?>,
  original: MissingMethodException,
): Any? {
  try {
    return resolveKotlinMethodCall(target, name, namedArgs, positionalArgs)
  } catch (_: MissingMethodException) {
    // Method not found — try extension functions
  }
  try {
    return resolveKotlinExtensionCall(target, name, namedArgs, positionalArgs)
  } catch (_: MissingMethodException) {
    throw original
  }
}

// Groovy's indy Selector (invokedynamic dispatch) only recognizes
// MetaClassImpl, ClosureMetaClass, and ExpandoMetaClass by exact class
// match. DelegatingMetaClass (our KotlinAwareMetaClass's parent) is not
// recognized, which disables the normal method selection path. This
// causes private superclass method calls to fail because the fallback
// 3-arg invokeMethod path loses sender class information needed for
// private method resolution. Groovy classes (GroovyObject implementors)
// are the only classes affected, since they use dynamic dispatch for
// private methods. Java and Kotlin classes use direct invocation.
private fun shouldWrapMetaClass(theClass: Class<*>): Boolean =
  !GroovyObject::class.java.isAssignableFrom(theClass)

internal fun ensureKotlinAwareMetaClass(clazz: Class<*>) {
  installGlobalMetaClassHandler()
  if (!shouldWrapMetaClass(clazz)) return
  val registry = GroovySystem.getMetaClassRegistry()
  val current = registry.getMetaClass(clazz)
  if (current !is KotlinAwareMetaClass) {
    registry.setMetaClass(clazz, KotlinAwareMetaClass(current).apply { initialize() })
  }
}

private var globalHandlerInstalled = false

@Synchronized
internal fun installGlobalMetaClassHandler() {
  if (globalHandlerInstalled) return
  globalHandlerInstalled = true
  val registry = GroovySystem.getMetaClassRegistry()
  val original = registry.metaClassCreationHandler
  registry.setMetaClassCreationHandle(
    object : MetaClassRegistry.MetaClassCreationHandle() {
      override fun createNormalMetaClass(theClass: Class<*>, body: MetaClassRegistry): MetaClass {
        val metaClass = original.create(theClass, body)
        return if (metaClass !is KotlinAwareMetaClass && shouldWrapMetaClass(theClass)) {
          KotlinAwareMetaClass(metaClass).apply { initialize() }
        } else {
          metaClass
        }
      }
    },
  )
}
