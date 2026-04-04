package uk.org.lidalia.kotlinfromgroovy.testsupport

class ClassWithDefaultedArgumentsToMethods {

  val calls = mutableListOf<Call>()

  fun functionWithOneDefaultedArgument(argument1: String = "argument1") {
    calls += Call(
      functionName = "functionWithOneDefaultedArgument",
      arguments = linkedMapOf(
        "argument1" to argument1,
      ),
    )
  }

  fun functionWithTwoArgumentFirstDefaulted(argument1: String = "argument1", argument2: String) {
    calls += Call(
      functionName = "functionWithTwoArgumentFirstDefaulted",
      arguments = linkedMapOf(
        "argument1" to argument1,
        "argument2" to argument2,
      ),
    )
  }

  fun functionWithTwoArgumentsSecondDefaulted(argument1: String, argument2: String = "argument2") {
    calls += Call(
      functionName = "functionWithTwoArgumentsSecondDefaulted",
      arguments = linkedMapOf(
        "argument1" to argument1,
        "argument2" to argument2,
      ),
    )
  }

  fun functionWithTwoArgumentsBothDefaulted(
    argument1: String = "argument1",
    argument2: String = "argument2",
  ) {
    calls += Call(
      functionName = "functionWithTwoArgumentsBothDefaulted",
      arguments = linkedMapOf(
        "argument1" to argument1,
        "argument2" to argument2,
      ),
    )
  }

  fun functionWithOneNullableArgumentDefaultedToNotNull(argument1: String? = "argument1") {
    calls += Call(
      functionName = "functionWithOneNullableArgumentDefaultedToNotNull",
      arguments = linkedMapOf(
        "argument1" to argument1,
      ),
    )
  }

  fun functionWithOneNullableArgumentDefaultedToNull(argument1: String? = null) {
    calls += Call(
      functionName = "functionWithOneNullableArgumentDefaultedToNull",
      arguments = linkedMapOf(
        "argument1" to argument1,
      ),
    )
  }

  fun functionWithDefaultsAndTrailingNullable(
    argument1: String = "argument1",
    argument2: String = "argument2",
    argument3: List<String>?,
  ) {
    calls += Call(
      functionName = "functionWithDefaultsAndTrailingNullable",
      arguments = linkedMapOf(
        "argument1" to argument1,
        "argument2" to argument2,
        "argument3" to argument3,
      ),
    )
  }
}
