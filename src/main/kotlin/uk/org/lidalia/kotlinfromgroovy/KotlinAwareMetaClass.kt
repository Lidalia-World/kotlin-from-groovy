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
  ): Any? = try {
    super.invokeMethod(target, name, args)
  } catch (e: MissingMethodException) {
    fallbackToKotlinReflect(target, name, args, e)
  }

  override fun invokeMethod(
    target: Any,
    name: String,
    args: Any?,
  ): Any? = try {
    super.invokeMethod(target, name, args)
  } catch (e: MissingMethodException) {
    @Suppress("UNCHECKED_CAST")
    val argsArray = when (args) {
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
    // If single arg is a LinkedHashMap, try as Kotlin named args
    if (args.size == 1 && args[0] is LinkedHashMap<*, *>) {
      @Suppress("UNCHECKED_CAST")
      val namedArgs = args[0] as LinkedHashMap<String, Any?>
      try {
        return resolveKotlinMethodCall(target, name, namedArgs, arrayOf())
      } catch (_: MissingMethodException) {
        // Method not found — try extension functions
      } catch (_: Exception) {
        throw original
      }
      try {
        return resolveKotlinExtensionCall(target, name, namedArgs, arrayOf())
      } catch (_: Exception) {
        throw original
      }
    }
    // Try as positional args with Kotlin default parameter support
    try {
      return resolveKotlinMethodCall(target, name, linkedMapOf(), args)
    } catch (_: MissingMethodException) {
      // Method not found — try extension functions
    } catch (_: Exception) {
      throw original
    }
    try {
      return resolveKotlinExtensionCall(target, name, linkedMapOf(), args)
    } catch (_: Exception) {
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
private fun installGlobalMetaClassHandler() {
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
