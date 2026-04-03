# Test gap analysis and hardening plan

## Context

The global `MetaClassCreationHandle` wraps every MetaClass (Java, Groovy, and Kotlin)
with `KotlinAwareMetaClass`. This means every `MissingMethodException` in the entire
runtime falls through to kotlin-reflect before re-throwing. We need confidence that
this doesn't break normal Groovy behaviour, and that the features we do support work
beyond the narrow types currently tested.

## Priority 1 — Can we break normal Groovy? [DONE]

### 1.1 Standard Groovy features under the global MetaClass [DONE]

Added `GroovyInteropSmokeSpec` covering GString, closures, collect/findAll/inject,
with/tap, type coercion, spread operator, Expando, safe navigation, elvis, regex,
ranges, multiple assignment, closure coercion to interface, and Java standard library
method calls.

**Bug found:** Property-style access on Java classes (`list.size` vs `list.size()`) is
broken. The AST transform rewrites `list.getSize()` (which Groovy generates from
`list.size` at INSTRUCTION_SELECTION phase) to `callMethodWithNamedArgs`, but
`pickMethod("getSize", [])` doesn't match ArrayList's `size()` method. Marked as
`@PendingFeature`.

### 1.2 Error quality for non-Kotlin classes [DONE]

Added tests for calling non-existent methods on String, List, and Map.
All throw `MissingMethodException` as expected.

## Priority 2 — Extension functions beyond String [DONE]

### 2.1 Complex receiver types [DONE]

Added extension functions and tests for:
- Data class receivers (`DataClass.describe()`, `DataClass.withLabel()`)
- Interface receivers (`List<*>.secondOrNull()`)
- Supertype receivers (`Collection<*>.describeSize()` — works on List and Set)
- Map receivers (`Map<K, V>.describeEntries()`)

**Bug found:** kotlin-reflect crashes with `IllegalStateException: Cannot infer
visibility for inherited open fun clone()` when introspecting `LinkedHashSet` (and
potentially other Java classes). Fixed by catching `Exception` in
`resolveKotlinMethodCall` when calling `memberFunctions`.

### 2.2 Extension functions with complex argument types [DONE]

Added tests for nullable arguments (`String.wrapWith(prefix: String?, suffix: String? = null)`),
named arguments on extension functions with complex receivers, and default parameters
on data class extension functions.

## Priority 3 — Null safety at the boundary [DONE]

### 3.1 Null passed to non-nullable parameters [DONE]

Implemented null validation in `resolveArgs`. Passing `null` for a non-nullable Kotlin
parameter now throws `IllegalArgumentException("Null passed for non-null parameter 'x'")`
instead of an opaque `NullPointerException` from `callBy`.

**Key finding:** The null check must only apply to Kotlin classes (detected via
`@kotlin.Metadata`), not Java classes. kotlin-reflect reports all Java parameters as
non-nullable, so validating them would break calls to Java methods that accept null
(e.g., Spock's `SpecificationContext.setThrownException(null)`).

## Priority 4 — Known limitations [DONE]

### 4.1 Varargs [NOT SUPPORTED]

`resolveArgs` does not collect trailing positional arguments into a vararg array.
Documented with `@PendingFeature` test.

### 4.2 Closures as Kotlin function types [NOT TESTED]

Did not add a test — this requires a Groovy closure to be passed to a Kotlin
function expecting a `(T) -> R` type. The conversion is non-trivial and would be
a feature, not a bug fix.

### 4.3 Operator overloading [MOSTLY WORKS]

- `plus` operator (`+`): works in both method-call and operator syntax
- `get` operator (`[]`): works via method name, fails via subscript syntax
  (Groovy routes subscript through `getAt` not `get`). Marked `@PendingFeature`.

## Outstanding issues

1. **Property-style access on Java classes** — `list.size` fails, `list.size()` works.
   The AST transform rewrites the getter call but Groovy's property-to-getter mapping
   is lost.

2. **Subscript operator** — `counter[5]` fails because Groovy dispatches to `getAt`
   not `get`. Would need the extension module or KotlinAwareMetaClass to bridge this.

3. **Varargs** — Would need `resolveArgs` to detect `KParameter.isVararg` and collect
   trailing positional args into an array.

4. **Closure-to-lambda conversion** — Would need type-aware conversion in `resolveArgs`
   or `callMethodWithNamedArgs` to wrap `Closure` in a Kotlin function adapter.
