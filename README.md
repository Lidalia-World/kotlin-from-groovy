# kotlin-from-groovy

A library that lets Groovy code call Kotlin idiomatically — named arguments, default parameter
values, data class `copy`, and destructuring all work as you'd expect.

This is particularly useful when you have Kotlin production code and Groovy
(typically [Spock](https://spockframework.org)) tests.

## Setup

Add `kotlin-from-groovy` as a dependency of your test (or production) Groovy code. It needs to be
on the compile classpath so the AST transformation runs during Groovy compilation.

### Gradle (Kotlin DSL)

```kotlin
dependencies {
  testImplementation("uk.org.lidalia:kotlin-from-groovy:$kotlinFromGroovyVersion")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
  testImplementation "uk.org.lidalia:kotlin-from-groovy:$kotlinFromGroovyVersion"
}
```

You also need Groovy compilation to see Kotlin classes. If your test source set
has both Kotlin and Groovy, make sure the Groovy compiler's classpath includes
compiled Kotlin classes:

```kotlin
tasks.named<GroovyCompile>("compileTestGroovy") {
  classpath += files(tasks.compileTestKotlin.get().destinationDirectory)
}
```

## Features

### Named arguments

Call any Kotlin method using Groovy's named argument syntax:

```kotlin
// Kotlin
class MyService {
  fun createUser(name: String, age: Int, active: Boolean) { ... }
}
```

```groovy
// Groovy
service.createUser(name: 'Alice', age: 30, active: true)
```

### Default parameter values

Omit arguments that have defaults in Kotlin — for both methods and constructors:

```kotlin
// Kotlin
class Config(
  val host: String = "localhost",
  val port: Int = 8080,
)
```

```groovy
// Groovy
def config = new Config()                          // uses both defaults
def config2 = new Config("example.com")            // positional, port defaults
def config3 = new Config(port: 9090)               // named, host defaults
```

This also works for method calls:

```kotlin
// Kotlin
fun fetch(url: String, timeout: Int = 5000, retries: Int = 3) { ... }
```

```groovy
// Groovy
fetch("https://example.com")                       // timeout and retries default
fetch("https://example.com", retries: 1)           // just override retries
```

### Data class `copy`

Call `copy` on Kotlin data classes using named arguments to override specific fields:

```kotlin
// Kotlin
data class User(val name: String, val age: Int, val active: Boolean)
```

```groovy
// Groovy
def user = new User('Alice', 30, true)
def updated = user.copy(age: 31)                   // User(name=Alice, age=31, active=true)
def renamed = user.copy(name: 'Bob', active: false) // User(name=Bob, age=30, active=false)
```

Positional arguments also work:

```groovy
def copied = user.copy()                            // identical copy
def withNewName = user.copy('Bob')                  // first param overridden positionally
```

### Destructuring

Destructure any Kotlin data class or class with `componentN()` functions using
Groovy's multi-assignment syntax:

```kotlin
// Kotlin
data class Point(val x: Int, val y: Int)
```

```groovy
// Groovy
def (x, y) = new Point(10, 20)
assert x == 10
assert y == 20
```

Partial destructuring works too — you don't need to destructure all components:

```groovy
def (first, second) = new Triple('a', 'b', 'c')   // third is ignored
```

Typed destructuring is supported, but note that Groovy will coerce types rather than
throwing an error on mismatch:

```groovy
def (String x, String y) = new Point(10, 20)
// x == '10', y == '20' — Groovy coerces Integer to String via toString()
```

## How it works

The library has two mechanisms:

1. **A Groovy AST transformation** that rewires method calls, constructor calls, and `copy`
   calls at compile time to go through a Kotlin-aware resolver. This is registered automatically
   via `META-INF/services` — no annotation needed in your code.

2. **A Groovy extension module** that adds `getAt(int)` to all objects, enabling Groovy's
   multi-assignment syntax (`def (a, b) = ...`) to call Kotlin `componentN()` functions.

Both are picked up automatically when the library is on the classpath.

## Requirements

- Kotlin 2.0+
- Groovy 4.0+
- JVM 17+
