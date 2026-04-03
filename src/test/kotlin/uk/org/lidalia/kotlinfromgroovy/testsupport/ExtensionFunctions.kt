package uk.org.lidalia.kotlinfromgroovy.testsupport

fun String.greetWith(greeting: String): String = "$greeting, $this!"

fun String.greetWithDefault(greeting: String = "Hello"): String = "$greeting, $this!"

fun String.formatWith(prefix: String, suffix: String = "."): String = "$prefix$this$suffix"

// Extension on a data class
fun DataClass.describe(): String = "DataClass($argument1, $argument2, $argument3)"

// Extension on a data class with complex argument
fun DataClass.withLabel(label: String, separator: String = ": "): String =
  "$label$separator$argument1"

// Extension on an interface
fun List<*>.secondOrNull(): Any? = if (size >= 2) get(1) else null

// Extension on a supertype (Collection) — should work on List, Set, etc.
fun Collection<*>.describeSize(label: String = "items"): String = "$size $label"

// Extension with nullable argument
fun String.wrapWith(prefix: String?, suffix: String? = null): String =
  "${prefix ?: ""}$this${suffix ?: ""}"

// Extension on Map
fun <K, V> Map<K, V>.describeEntries(): String = entries.joinToString { "${it.key}=${it.value}" }
