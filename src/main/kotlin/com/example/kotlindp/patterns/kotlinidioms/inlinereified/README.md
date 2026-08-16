# inline, reified, value classes

## `inline`

Copies the function body — and its lambdas — into the call site. This removes a `Function`
allocation per call and a virtual dispatch.

**But that's the least important benefit.** The JIT often handles allocation anyway. The two things
`inline` gives you that nothing else can:

1. **Non-local `return`** — `return` inside the lambda returns from the *enclosing* function. This is
   why `forEach { return x }` works.
2. **`reified` type parameters.**

**Guidance: inline functions that take lambdas.** Inlining a large function with no lambda just
bloats bytecode and can be *slower* by hurting the instruction cache.

| Modifier | Meaning |
|---|---|
| `inline` | copy body + lambdas to call site |
| `crossinline` | still inlined, but non-local return forbidden — the lambda runs from another context |
| `noinline` | opt this lambda out, so it can be stored in a variable or passed on |

## `reified`

Generics are erased on the JVM; a normal function can't ask what `T` is. Because an inline body is
copied to the call site — where the type argument *is* known — `reified` lets it use `T` as a real
type. This is why Kotlin APIs read `decode<Foo>(json)` where Java reads `readValue(json, Foo.class)`.

**Limits:**
- Requires `inline`, so it can't be used on a virtual/abstract/open member.
- `T` must be concrete at the call site — you can't forward a non-reified `T` into it.
- It doesn't defeat erasure: `reified List<String>` still sees `List<*>`; only the outermost type is
  materialised.

## `@JvmInline value class`

Wraps one value and is compiled away — at runtime a `UserId` *is* a `String`. You get compile-time
distinctness for free, and `init` blocks work so validation happens once at construction.

This kills the transposed-argument bug:

```kotlin
fun transfer(from: String, to: String, amount: Long)  // easy to swap
fun grant(user: UserId, order: OrderId)               // compile error if swapped
```

**When it boxes** (allocation comes back):
- as a generic type argument — `List<UserId>` boxes every element;
- as a nullable — `UserId?` must box, a primitive slot has no null;
- stored in a field typed as a supertype or interface.

So the guarantee is "no allocation in the common path", not "never allocated". Worth using for the
type safety alone; treat performance as a bonus.

One JVM wrinkle: two value classes over the same underlying type can't both appear in otherwise
identical overloads — after erasure the JVM sees the same method. Kotlin mangles names to cope, which
is visible from Java.

## Summary

| Feature | Use when | Cost of misuse |
|---|---|---|
| `inline` | takes a lambda; need non-local return or `reified` | bytecode bloat |
| `crossinline` | inlined lambda called from another context | — |
| `noinline` | the lambda must be stored | loses inlining for it |
| `reified` | need the type at runtime | forces `inline`, no virtual dispatch |
| `value class` | domain wrapper over one primitive | boxes in generics/nullable |
