package uk.org.lidalia.kotlinfromgroovy.testsupport

fun joinAll(vararg items: String): String = items.joinToString(", ")

fun transform(input: String, block: (String) -> String): String = block(input)

data class Counter(val value: Int) {
  operator fun plus(other: Counter): Counter = Counter(value + other.value)

  operator fun get(index: Int): Int = value + index

  infix fun add(other: Counter): Counter = Counter(value + other.value)
}

infix fun String.concatWith(other: String): String = "$this$other"
