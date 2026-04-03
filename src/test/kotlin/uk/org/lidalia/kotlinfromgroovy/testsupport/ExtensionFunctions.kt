package uk.org.lidalia.kotlinfromgroovy.testsupport

fun String.greetWith(greeting: String): String = "$greeting, $this!"

fun String.greetWithDefault(greeting: String = "Hello"): String = "$greeting, $this!"

fun String.formatWith(prefix: String, suffix: String = "."): String = "$prefix$this$suffix"
