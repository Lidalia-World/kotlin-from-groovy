package uk.org.lidalia.kotlinfromgroovy.testsupport

class ClassThatThrowsInConstructor(
  val value: String = error("Constructor failed: ${"value not provided"}"),
)
