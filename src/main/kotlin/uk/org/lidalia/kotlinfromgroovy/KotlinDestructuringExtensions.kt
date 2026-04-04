@file:JvmName("KotlinDestructuringExtensions")

package uk.org.lidalia.kotlinfromgroovy

// Install the global KotlinAwareMetaClass handler eagerly when this
// extension module is loaded, so that Kotlin default parameter support
// works for method calls without requiring AST transformation.
private val metaClassHandlerInstalled = run { installGlobalMetaClassHandler() }

fun getAt(self: Any, index: Int): Any? {
  val componentNumber = index + 1
  val methodName = "component$componentNumber"
  val method = self.javaClass.methods.find { it.name == methodName && it.parameterCount == 0 }
    ?: throw IllegalArgumentException(
      "Destructuring declaration initializer of type ${self.javaClass.simpleName} must have a '$methodName()' function",
    )
  return method.invoke(self)
}
