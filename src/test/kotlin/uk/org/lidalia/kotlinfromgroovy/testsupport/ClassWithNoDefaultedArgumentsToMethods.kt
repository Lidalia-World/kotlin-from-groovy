package uk.org.lidalia.kotlinfromgroovy.testsupport

class ClassWithNoDefaultedArgumentsToMethods {

  val calls = mutableListOf<Call>()

  fun functionWithMultipleArguments(
    argument1: String,
    argument2: Int,
    argument3: Boolean,
  ) {
    calls += Call(
      functionName = "functionWithMultipleArguments",
      arguments = linkedMapOf(
        "argument1" to argument1,
        "argument2" to argument2,
        "argument3" to argument3,
      ),
    )
  }

  fun functionWithMapArgument(argument1: Map<String, Any>) {
    calls += Call(
      functionName = "functionWithMapArgument",
      arguments = linkedMapOf(
        "argument1" to argument1,
      ),
    )
  }

  fun functionWithMapAndOtherArgument(argument1: Map<String, Any>, argument2: String) {
    calls += Call(
      functionName = "functionWithMapAndOtherArgument",
      arguments = linkedMapOf(
        "argument1" to argument1,
        "argument2" to argument2,
      ),
    )
  }

  fun functionWithOtherAndMapArgument(argument1: String, argument2: Map<String, Any>) {
    calls += Call(
      functionName = "functionWithOtherAndMapArgument",
      arguments = linkedMapOf(
        "argument1" to argument1,
        "argument2" to argument2,
      ),
    )
  }

  fun functionWithLinkedMapArgument(argument1: LinkedHashMap<String, Any>) {
    calls += Call(
      functionName = "functionWithLinkedMapArgument",
      arguments = linkedMapOf(
        "argument1" to argument1,
      ),
    )
  }

  fun functionWithVarargs(vararg options: String) {
    calls += Call(
      functionName = "functionWithVarargs",
      arguments = linkedMapOf(
        "options" to options.toList(),
      ),
    )
  }
}
