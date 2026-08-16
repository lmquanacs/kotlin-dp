# Scope functions

Five functions differing along exactly two axes. Learn the table and the confusion disappears.

| Function | Receiver | Returns | Extension? |
|---|---|---|---|
| `let` | `it` | lambda result | yes |
| `run` | `this` | lambda result | yes |
| `with` | `this` | lambda result | no (takes an argument) |
| `apply` | `this` | **the receiver** | yes |
| `also` | `it` | **the receiver** | yes |

Two questions pick the right one:

1. Object back, or the block's result? → `apply`/`also` vs `let`/`run`/`with`
2. `this` or `it`? → configuring the object vs using it as an argument

## Idiomatic use of each

- **`let`** — null-safe transformation. `?.let { }` runs only when non-null and `it` is smart-cast.
  Also gives an intermediate value a scoped name without leaking a `val`.
- **`apply`** — configure and return the receiver. The builder idiom; every DSL here ends with
  `.apply(block)`. If the block *computes* something instead, you wanted `run`.
- **`also`** — side effect, then return unchanged. The `it` receiver signals "this is a side effect,
  not configuration" — right for logging and validation mid-chain.
- **`run`** — compute a value *from* an object. The non-extension `run { }` also scopes a block of
  statements into one expression (useful in a `when` branch).
- **`with`** — like `run`, receiver as argument. Best when making several calls on one object named
  once at the top.

## Four traps

**1. Nested scope functions shadow `it`.**
```kotlin
outer?.let { o -> inner?.let { i -> ... } }   // name them
outer?.let { inner?.let { ... } }             // which `it`?
```
Rule: name the parameter the moment you nest.

**2. `?.let { } ?: fallback` is not an `if`.** The fallback runs when *the block returns null*, not
only when the receiver is null. If the block can legitimately return null, the fallback fires
silently. A plain `if (a != null)` is clearer and correct.

**3. `apply` on a mutable object hides mutation.** Returning the receiver makes it easy to write
chains that look functional but mutate shared state. Prefer `copy()` on immutable data; keep `apply`
for building an object you own.

**4. Four or five chained scope functions is write-only code.** Two is usually the limit before a
named `val` communicates better. These are a readability tool; past a point they stop paying.

## Related

- **`takeIf` / `takeUnless`** — return the receiver if a predicate holds, else `null`. Turns a
  condition into something `?:` and `?.let` can chain with: `raw.takeIf { it.isNotEmpty() } ?: "unnamed"`.
- **`use`** — same shape, but it's about correctness: `Closeable.use { }` is Kotlin's
  try-with-resources. Never hand-write that `finally`.
