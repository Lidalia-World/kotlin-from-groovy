package uk.org.lidalia.kotlinfromgroovy

import java.io.File
import java.net.URI
import java.net.URL
import java.net.URLClassLoader
import java.util.jar.JarFile
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.jvmErasure
import kotlin.reflect.jvm.kotlinFunction

internal object KotlinExtensionFunctionResolver {

  internal data class ExtensionFunction(
    val function: KFunction<*>,
    val receiverType: Class<*>,
  )

  private val extensionsByName: Map<String, List<ExtensionFunction>> by lazy {
    scanClasspath()
  }

  fun findExtensionFunctions(methodName: String, receiverType: Class<*>): List<ExtensionFunction> =
    extensionsByName[methodName]
      ?.filter { it.receiverType.isAssignableFrom(receiverType) }
      ?: emptyList()

  private fun scanClasspath(): Map<String, List<ExtensionFunction>> {
    val result = mutableMapOf<String, MutableList<ExtensionFunction>>()
    val classLoader = Thread.currentThread().contextClassLoader ?: return result

    val urls = collectClasspathUrls(classLoader)

    for (url in urls) {
      try {
        val file = File(URI(url.toString().replace(" ", "%20")))
        if (file.isDirectory) {
          scanDirectory(file, file, classLoader, result)
        } else if (file.name.endsWith(".jar")) {
          scanJar(file, classLoader, result)
        }
      } catch (_: Exception) {
        // Skip entries we can't process
      }
    }

    return result
  }

  private fun collectClasspathUrls(classLoader: ClassLoader): List<URL> {
    val urls = mutableListOf<URL>()
    var current: ClassLoader? = classLoader
    while (current != null) {
      if (current is URLClassLoader) {
        urls.addAll(current.urLs)
      }
      current = current.parent
    }
    if (urls.isEmpty()) {
      val cp = System.getProperty("java.class.path") ?: return urls
      for (entry in cp.split(File.pathSeparator)) {
        urls.add(File(entry).toURI().toURL())
      }
    }
    return urls
  }

  private fun scanDirectory(
    root: File,
    dir: File,
    classLoader: ClassLoader,
    result: MutableMap<String, MutableList<ExtensionFunction>>,
  ) {
    for (file in dir.listFiles() ?: return) {
      if (file.isDirectory) {
        scanDirectory(root, file, classLoader, result)
      } else if (file.name.endsWith("Kt.class") && '$' !in file.name) {
        val className = file.relativeTo(root).path
          .removeSuffix(".class")
          .replace(File.separatorChar, '.')
        checkClass(className, classLoader, result)
      }
    }
  }

  private fun scanJar(
    jarFile: File,
    classLoader: ClassLoader,
    result: MutableMap<String, MutableList<ExtensionFunction>>,
  ) {
    JarFile(jarFile).use { jar ->
      for (entry in jar.entries()) {
        val name = entry.name
        if (name.endsWith("Kt.class") && '$' !in name) {
          val className = name
            .removeSuffix(".class")
            .replace('/', '.')
          checkClass(className, classLoader, result)
        }
      }
    }
  }

  @Suppress("SwallowedException")
  private fun checkClass(
    className: String,
    classLoader: ClassLoader,
    result: MutableMap<String, MutableList<ExtensionFunction>>,
  ) {
    try {
      val clazz = classLoader.loadClass(className)
      val metadata = clazz.getAnnotation(Metadata::class.java) ?: return
      if (metadata.kind != 2) return

      for (method in clazz.declaredMethods) {
        try {
          val modifiers = method.modifiers
          if (!java.lang.reflect.Modifier.isPublic(modifiers)) continue
          if (!java.lang.reflect.Modifier.isStatic(modifiers)) continue
          if (method.isSynthetic) continue

          val kFunction = method.kotlinFunction ?: continue

          val receiverParam = kFunction.parameters
            .find { it.kind == KParameter.Kind.EXTENSION_RECEIVER }
            ?: continue
          val receiverType = receiverParam.type.jvmErasure.java

          result.getOrPut(method.name) { mutableListOf() }
            .add(ExtensionFunction(kFunction, receiverType))
        } catch (_: Throwable) {
          // Skip methods we can't inspect (e.g. types kotlin-reflect can't handle)
        }
      }
    } catch (_: Exception) {
      // Skip classes we can't load or inspect
    }
  }
}
