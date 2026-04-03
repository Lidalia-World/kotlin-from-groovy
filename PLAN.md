# Test gap analysis and hardening plan

## Context

The global `MetaClassCreationHandle` wraps every MetaClass (Java, Groovy, and Kotlin)
with `KotlinAwareMetaClass`. This means every `MissingMethodException` in the entire
runtime falls through to kotlin-reflect before re-throwing. We need confidence that
this doesn't break normal Groovy behaviour, and that the features we do support work
beyond the narrow types currently tested.

## Priority 1 — Can we break normal Groovy?

### 1.1 Standard Groovy features under the global MetaClass

The global wrapper intercepts all `MissingMethodException`s. We have zero tests that
normal Groovy idioms still work:

- GString interpolation (`"hello ${name}"`)
- Closures (`list.collect { it * 2 }`)
- Groovy built-in methods (`findAll`, `collect`, `inject`, `with`, `tap`)
- Property access via getters/setters
- Groovy builders / `Expando` / dynamic dispatch
- `as` type coercion
- Spread operator (`*.`)
- Method missing / property missing on Groovy classes
- Calling Java standard library methods (e.g., `'hello'.toUpperCase()`)

**Goal:** A `GroovyInteropSmokeSpec` that exercises common Groovy patterns on Java
and Groovy objects, confirming the MetaClass wrapper is transparent.

### 1.2 Error quality for non-Kotlin classes

When a method genuinely doesn't exist on a Java/Groovy object, the error should still
be a clear `MissingMethodException` — not a confusing kotlin-reflect error or a
swallowed/replaced exception.

**Goal:** Tests that call non-existent methods on Java objects (e.g., `'hello'.noSuchMethod()`)
and verify the original `MissingMethodException` is thrown with a clear message.

## Priority 2 — Extension functions beyond String

### 2.1 Complex receiver types

All extension function tests use `String` receivers. Untested:

- Extension functions on interfaces (receiver is an interface type)
- Extension functions on abstract classes
- Extension functions where the receiver is a supertype (inheritance)
- Extension functions on user-defined data classes / regular classes
- Extension functions on generic types (e.g., `List<T>.myFun()`)

**Goal:** Add test extension functions with non-String receivers and corresponding
Groovy + Kotlin mirror tests.

### 2.2 Extension functions with complex argument types

Current extension function tests only pass strings. Untested:

- Passing objects (data classes, collections) as arguments to extension functions
- Nullable arguments
- Multiple overloaded extension functions with different receiver types

**Goal:** Extension function tests with richer argument and receiver types.

## Priority 3 — Null safety at the boundary

### 3.1 Null passed to non-nullable parameters

`resolveArgs` validates types but skips null values entirely. Passing `null` for a
non-nullable Kotlin parameter gets through to `callBy`, which throws an opaque
`NullPointerException` instead of a clear error.

**Goal:** Either:
- (a) Validate nullability in `resolveArgs` and throw `IllegalArgumentException` with
  a message like `"Null passed for non-null parameter 'x'"`, or
- (b) Document this as a known limitation.

Add tests for `method(x: null)` where `x: String` — both for methods and constructors.

## Priority 4 — Known limitations to document or implement

### 4.1 Varargs

`resolveArgs` does not collect trailing positional arguments into a vararg array.
Calling a Kotlin `vararg` function from Groovy will fail.

**Decision needed:** Implement vararg support, or document as a limitation?

### 4.2 Closures as Kotlin function types

No conversion from `groovy.lang.Closure` to Kotlin `FunctionN` types. Passing a
Groovy closure where a Kotlin lambda is expected throws `IllegalArgumentException`.

**Decision needed:** Implement closure-to-lambda conversion, or document as a limitation?

### 4.3 Operator overloading

Kotlin operators (`plus`, `get`, `invoke`, etc.) probably work by accident because
Groovy dispatches by method name. But there are no tests, and Groovy's own operator
type coercions are bypassed in the kotlin-reflect fallback path.

**Goal:** Add tests for at least `plus` and `get` operators on Kotlin classes called
from Groovy with operator syntax.

## Order of work

1. **1.1 + 1.2** — Groovy smoke tests (highest risk, most likely to find real bugs)
2. **2.1 + 2.2** — Extension function coverage (likely to find bugs in receiver matching)
3. **3.1** — Null safety (small fix or document)
4. **4.1–4.3** — Decide and document/implement remaining gaps
