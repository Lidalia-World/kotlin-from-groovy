@file:JvmName("KotlinDestructuringExtensions")

package uk.org.lidalia.kotlinfromgroovy

fun getAt(self: Any, index: Int): Any? {
  val componentNumber = index + 1
  val methodName = "component$componentNumber"
  val method = self.javaClass.methods.find { it.name == methodName && it.parameterCount == 0 }
    ?: throw IllegalArgumentException(
      "Destructuring declaration initializer of type ${self.javaClass.simpleName} must have a '$methodName()' function",
    )
  return method.invoke(self)
}
