package uk.org.lidalia.kotlinfromgroovy

import groovy.lang.DelegatingMetaClass
import groovy.lang.GroovySystem
import groovy.lang.MetaClass
import groovy.lang.MetaClassRegistry
import groovy.lang.MissingMethodException

class KotlinAwareMetaClass(delegate: MetaClass) : DelegatingMetaClass(delegate) {

  override fun invokeMethod(
    target: Any,
    name: String,
    args: Array<Any?>,
  ): Any? {
    // For Kotlin classes, check for exact method match before delegating to Groovy.
    // Groovy silently coerces missing args to null instead of throwing
    // MissingMethodException, which bypasses default parameter support.
    if (target.javaClass.isAnnotationPresent(Metadata::class.java)) {
      val argTypes = args.map { it?.javaClass ?: Any::class.java }.toTypedArray()
      val metaMethod = delegate.pickMethod(name, argTypes)
      if (metaMethod == null || metaMethod.nativeParameterTypes.size != args.size) {
        return fallbackToKotlinReflect(
          target,
          name,
          args,
          MissingMethodException(name, target.javaClass, args),
        )
      }
    }
    return try {
      super.invokeMethod(target, name, args)
    } catch (e: MissingMethodException) {
      fallbackToKotlinReflect(target, name, args, e)
    }
  }

  override fun invokeMethod(
    target: Any,
    name: String,
    args: Any?,
  ): Any? = try {
    super.invokeMethod(target, name, args)
  } catch (e: MissingMethodException) {
    @Suppress("UNCHECKED_CAST")
    val argsArray: Array<Any?> = when (args) {
      null -> emptyArray()
      is Array<*> -> args as Array<Any?>
      else -> arrayOf(args)
    }
    fallbackToKotlinReflect(target, name, argsArray, e)
  }

  private fun fallbackToKotlinReflect(
    target: Any,
    name: String,
    args: Array<Any?>,
    original: MissingMethodException,
  ): Any? {
    // Groovy puts named args as a LinkedHashMap in the first position,
    // with any positional args after it.
    if (args.isNotEmpty() && args[0] is LinkedHashMap<*, *>) {
      @Suppress("UNCHECKED_CAST")
      val namedArgs = args[0] as LinkedHashMap<String, Any?>
      val positionalArgs = args.drop(1).toTypedArray()
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
    // Try as positional args with Kotlin default parameter support
    try {
      return resolveKotlinMethodCall(target, name, linkedMapOf(), args)
    } catch (_: MissingMethodException) {
      // Method not found — try extension functions
    }
    try {
      return resolveKotlinExtensionCall(target, name, linkedMapOf(), args)
    } catch (_: MissingMethodException) {
      throw original
    }
  }
}

internal fun ensureKotlinAwareMetaClass(clazz: Class<*>) {
  installGlobalMetaClassHandler()
  val registry = GroovySystem.getMetaClassRegistry()
  val current = registry.getMetaClass(clazz)
  if (current !is KotlinAwareMetaClass) {
    val wrapped = KotlinAwareMetaClass(current)
    wrapped.initialize()
    registry.setMetaClass(clazz, wrapped)
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
        return if (metaClass is KotlinAwareMetaClass) {
          metaClass
        } else {
          val wrapped = KotlinAwareMetaClass(metaClass)
          wrapped.initialize()
          wrapped
        }
      }
    },
  )
}
