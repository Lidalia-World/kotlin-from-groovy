package uk.org.lidalia.kotlinfromgroovy.kotlintests

import org.junit.jupiter.api.Test
import uk.org.lidalia.kotlinfromgroovy.testsupport.formatWith
import uk.org.lidalia.kotlinfromgroovy.testsupport.greetWith
import uk.org.lidalia.kotlinfromgroovy.testsupport.greetWithDefault

class ExtensionFunctionTest {

  @Test
  fun `can call an extension function`() {
    assert("World".greetWith("Hello") == "Hello, World!")
  }

  @Test
  fun `can call an extension function with default argument`() {
    assert("World".greetWithDefault() == "Hello, World!")
  }

  @Test
  fun `can call an extension function with explicit argument overriding default`() {
    assert("World".greetWithDefault("Hi") == "Hi, World!")
  }

  @Test
  fun `can call an extension function with named arguments`() {
    assert("World".greetWith(greeting = "Hey") == "Hey, World!")
  }

  @Test
  fun `can call an extension function with named argument overriding default`() {
    assert("content".formatWith(prefix = "<<", suffix = ">>") == "<<content>>")
  }

  @Test
  fun `can call an extension function with partial named args using default for rest`() {
    assert("content".formatWith(prefix = ">>") == ">>content.")
  }
}
