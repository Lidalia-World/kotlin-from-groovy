package uk.org.lidalia.kotlinfromgroovy.kotlintests

import org.junit.jupiter.api.Test
import uk.org.lidalia.kotlinfromgroovy.ReifiedBridgeGenerator

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
}
