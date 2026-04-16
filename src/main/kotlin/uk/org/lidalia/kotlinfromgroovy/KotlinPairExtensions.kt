@file:JvmName("KotlinPairExtensions")

package uk.org.lidalia.kotlinfromgroovy

fun <A, B> to(self: A, that: B): Pair<A, B> = self to that
