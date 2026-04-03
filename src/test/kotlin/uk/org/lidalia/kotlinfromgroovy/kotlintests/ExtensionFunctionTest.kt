package uk.org.lidalia.kotlinfromgroovy.kotlintests

import org.junit.jupiter.api.Test
import uk.org.lidalia.kotlinfromgroovy.testsupport.DataClass
import uk.org.lidalia.kotlinfromgroovy.testsupport.describe
import uk.org.lidalia.kotlinfromgroovy.testsupport.describeEntries
import uk.org.lidalia.kotlinfromgroovy.testsupport.describeSize
import uk.org.lidalia.kotlinfromgroovy.testsupport.formatWith
import uk.org.lidalia.kotlinfromgroovy.testsupport.greetWith
import uk.org.lidalia.kotlinfromgroovy.testsupport.greetWithDefault
import uk.org.lidalia.kotlinfromgroovy.testsupport.secondOrNull
import uk.org.lidalia.kotlinfromgroovy.testsupport.withLabel
import uk.org.lidalia.kotlinfromgroovy.testsupport.wrapWith

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

  @Test
  fun `can call an extension function on a data class`() {
    val dc = DataClass("hello", 42, true)
    assert(dc.describe() == "DataClass(hello, 42, true)")
  }

  @Test
  fun `can call an extension function on a data class with arguments`() {
    val dc = DataClass("hello", 42, true)
    assert(dc.withLabel("Value") == "Value: hello")
  }

  @Test
  fun `can call an extension function on a data class with named arguments`() {
    val dc = DataClass("hello", 42, true)
    assert(dc.withLabel(label = "X", separator = " -> ") == "X -> hello")
  }

  @Test
  fun `can call an extension function on a data class with default argument`() {
    val dc = DataClass("hello", 42, true)
    assert(dc.withLabel("Tag") == "Tag: hello")
  }

  @Test
  fun `can call an extension function on a List via interface`() {
    assert(listOf(10, 20, 30).secondOrNull() == 20)
  }

  @Test
  fun `can call an extension function on an empty list via interface`() {
    assert(emptyList<Any>().secondOrNull() == null)
  }

  @Test
  fun `can call an extension function on a Collection supertype with a List`() {
    assert(listOf(1, 2, 3).describeSize() == "3 items")
  }

  @Test
  fun `can call an extension function on a Collection supertype with a Set`() {
    assert(setOf(1, 2).describeSize() == "2 items")
  }

  @Test
  fun `can call an extension function on a Collection with named argument`() {
    assert(listOf(1, 2).describeSize(label = "elements") == "2 elements")
  }

  @Test
  fun `can call an extension function with nullable argument`() {
    assert("hello".wrapWith("<<", ">>") == "<<hello>>")
  }

  @Test
  fun `can call an extension function with null argument`() {
    assert("hello".wrapWith(null) == "hello")
  }

  @Test
  fun `can call an extension function on a Map`() {
    assert(mapOf("a" to 1, "b" to 2).describeEntries() == "a=1, b=2")
  }
}
