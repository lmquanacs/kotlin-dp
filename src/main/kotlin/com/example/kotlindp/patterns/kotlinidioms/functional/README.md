# Functional idioms

Higher-order functions, composition, currying, and the collection pipeline. Not "advanced Kotlin" —
the everyday shape of production code once you stop writing loops.

## Composition

```kotlin
infix fun <A, B, C> ((A) -> B).then(next: (B) -> C): (A) -> C = { a -> next(this(a)) }
val slugify = trim then lower then { it.replace(Regex("\\s+"), "-") }
```

`then` applies left-first (reading order); mathematical `compose` applies right-first. **Pick one
convention per codebase** — mixing them is a reliable source of reversed pipelines.

## Currying and partial application

Both are here for completeness, with an honest assessment: **currying is rarely the right tool in
Kotlin.** Default and named arguments already solve "configure some parameters" more readably.
Partial application is usually just a lambda closing over the fixed values. Know them, reach for them
seldom.

## Memoisation

```kotlin
fun <A, R> ((A) -> R).memoized(): (A) -> R {
    val cache = mutableMapOf<A, R>()
    return { a -> cache.getOrPut(a) { this(a) } }
}
```

Only valid for **pure** functions — memoising something with side effects or time-dependent results
produces stale answers that are very hard to debug. And this cache is unbounded: production
memoisation needs an eviction policy (see `production/cacheaside`) or it's a memory leak.

## The collection pipeline

Worth knowing by name, because each replaces a loop that's easy to get subtly wrong:

| Operator | Replaces |
|---|---|
| `groupBy` / `associateBy` / `associateWith` | mutable map accumulator |
| `partition` | two filtered passes |
| `fold` / `runningFold` | `var` outside a loop |
| `sumOf` / `maxByOrNull` | aggregate + intermediate list |
| `windowed` / `chunked` / `zipWithNext` | index arithmetic, off-by-one |
| `flatMap` / `mapNotNull` | nested loop + null check |
| `sortedWith(compareBy(..).thenBy(..))` | hand-written comparator |

## Functional core, imperative shell

The architectural point behind all of it.

- **Core** — pure functions over immutable data. No I/O, no clock, no randomness. Testable with a
  one-line assertion: no mocks, no setup, no inter-test ordering.
- **Shell** — a thin layer that reads the world, calls the core, writes the result back.

The mistake is scattering effects *through* the transformation. Keep `priceLines(items, discount)`
pure and let the repository call and audit write live in the service that wraps it — then only the
shell needs a stub.

## Cautions

- **Eager by default.** Every chained operator on a `List` allocates a new list. Long chains over
  large data → `asSequence()`. Short chains over small data → don't.
- **Readability has a limit.** A 12-operator chain is as hard to read as a 30-line loop. Break it
  with named intermediate `val`s.
- **Stack depth.** Kotlin has no general TCO; `tailrec` works only for *directly* self-recursive
  functions. Deep non-tail recursion overflows.
