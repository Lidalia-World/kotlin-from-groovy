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

    urls.forEach { url ->
      try {
        val file = File(URI(url.toString().replace(" ", "%20")))
        when {
          file.isDirectory -> scanDirectory(file, file, classLoader, result)
          file.name.endsWith(".jar") -> scanJar(file, classLoader, result)
        }
      } catch (_: Exception) {
        // Skip entries we can't process
      }
    }

    return result
  }

  private fun collectClasspathUrls(classLoader: ClassLoader): List<URL> {
    val urls = generateSequence<ClassLoader>(classLoader) { it.parent }
      .filterIsInstance<URLClassLoader>()
      .flatMap { it.urLs.asSequence() }
      .toMutableList()
    if (urls.isEmpty()) {
      val cp = System.getProperty("java.class.path") ?: return urls
      cp.split(File.pathSeparator).mapTo(urls) { File(it).toURI().toURL() }
    }
    return urls
  }

  private fun scanDirectory(
    root: File,
    dir: File,
    classLoader: ClassLoader,
    result: MutableMap<String, MutableList<ExtensionFunction>>,
  ) {
    (dir.listFiles() ?: return).forEach { file ->
      when {
        file.isDirectory -> {
          scanDirectory(root, file, classLoader, result)
        }

        file.name.endsWith("Kt.class") && '$' !in file.name -> {
          val className = file.relativeTo(root).path
            .removeSuffix(".class")
            .replace(File.separatorChar, '.')
          checkClass(className, classLoader, result)
        }
      }
    }
  }

  private fun scanJar(
    jarFile: File,
    classLoader: ClassLoader,
    result: MutableMap<String, MutableList<ExtensionFunction>>,
  ) {
    JarFile(jarFile).use { jar ->
      jar.entries().asSequence()
        .map { it.name }
        .filter { it.endsWith("Kt.class") && '$' !in it }
        .forEach { name ->
          val className = name
            .removeSuffix(".class")
            .replace('/', '.')
          checkClass(className, classLoader, result)
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

      clazz.declaredMethods.asSequence()
        .filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }
        .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) }
        .filter { !it.isSynthetic }
        .mapNotNull { method ->
          try {
            val kFunction = method.kotlinFunction ?: return@mapNotNull null
            val receiverParam = kFunction.parameters
              .find { it.kind == KParameter.Kind.EXTENSION_RECEIVER }
              ?: return@mapNotNull null
            val receiverType = receiverParam.type.jvmErasure.java
            method.name to ExtensionFunction(kFunction, receiverType)
          } catch (_: Throwable) {
            null
          }
        }
        .forEach { (name, ext) ->
          result.getOrPut(name) { mutableListOf() }.add(ext)
        }
    } catch (_: Exception) {
      // Skip classes we can't load or inspect
    }
  }
}
