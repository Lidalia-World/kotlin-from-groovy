package uk.org.lidalia.kotlinfromgroovy.testsupport

inline fun <reified T> typeName(): String = T::class.java.simpleName

inline fun <reified T> isInstanceOf(value: Any): Boolean = value is T

class TypeConverter {
  inline fun <reified T : Any> convert(value: String): T {
    @Suppress("UNCHECKED_CAST")
    return when (T::class) {
      Int::class -> value.toInt() as T
      Long::class -> value.toLong() as T
      Boolean::class -> value.toBoolean() as T
      else -> value as T
    }
  }
}
