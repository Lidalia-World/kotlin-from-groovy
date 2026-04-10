package uk.org.lidalia.kotlinfromgroovy.kotlintests

import org.junit.jupiter.api.Test
import uk.org.lidalia.kotlinfromgroovy.ReifiedBridgeGenerator
import uk.org.lidalia.kotlinfromgroovy.testsupport.TypeConverter

class ReifiedBridgeGeneratorTest {

  private val inlineReifiedFunctionsKt =
    Class.forName("uk.org.lidalia.kotlinfromgroovy.testsupport.InlineReifiedFunctionsKt")

  @Test
  fun `generates bridge for typeName that returns class simple name`() {
    val result = ReifiedBridgeGenerator.callReifiedStatic(
      inlineReifiedFunctionsKt,
      "typeName",
      arrayOf(String::class.java),
      arrayOf(),
    )
    assert(result == "String") { "Expected 'String' but got '$result'" }
  }

  @Test
  fun `generates bridge for isInstanceOf that checks instance type`() {
    val trueResult = ReifiedBridgeGenerator.callReifiedStatic(
      inlineReifiedFunctionsKt,
      "isInstanceOf",
      arrayOf(String::class.java),
      arrayOf("hello"),
    )
    assert(trueResult == true) { "Expected true but got '$trueResult'" }

    val falseResult = ReifiedBridgeGenerator.callReifiedStatic(
      inlineReifiedFunctionsKt,
      "isInstanceOf",
      arrayOf(Int::class.javaObjectType),
      arrayOf("hello"),
    )
    assert(falseResult == false) { "Expected false but got '$falseResult'" }
  }

  @Test
  fun `generates bridge for instance method convert with marker 1 and 4`() {
    val converter = TypeConverter()
    val result = ReifiedBridgeGenerator.callReifiedInstance(
      converter,
      "convert",
      arrayOf(Int::class.javaObjectType),
      arrayOf("42"),
    )
    assert(result == 42) { "Expected 42 but got '$result'" }
  }
}
