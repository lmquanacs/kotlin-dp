# Iterator

**Intent** — traverse a collection without exposing its internal representation.

## Kotlin idiom

Kotlin implements this at the language level. Declare `operator fun iterator()` (or implement
`Iterable<T>`) and your type works with `for`, destructuring, and the entire stdlib —
`map`, `filter`, `joinToString`, everything.

But the part that actually earns its keep in production is **`sequence { }`**: a lazy iterator built
from ordinary imperative code, where `yield` suspends until the consumer asks for the next element.

```kotlin
fun <T> paginated(pageSize: Int, fetch: (Int, Int) -> List<T>): Sequence<T> = sequence {
    var offset = 0
    while (true) {
        val page = fetch(offset, pageSize)
        if (page.isEmpty()) break
        yieldAll(page)
        if (page.size < pageSize) break
        offset += pageSize
    }
}
```

The caller writes `pages.take(50)` and never sees a page boundary; only the pages actually consumed
are fetched. Eagerly, this is the difference between a 200 ms response and a 40 s one.

## Sequence vs Iterable — the bit people get wrong

`Iterable` operations are **eager** — each step allocates a full intermediate list.
`Sequence` operations are **lazy** — elements flow through the whole chain one at a time.

```kotlin
list.map { }.filter { }.first()               // maps all N, filters all N, takes 1
list.asSequence().map { }.filter { }.first()  // stops at the first match
```

**But laziness has per-element overhead.** For a small list with one or two operations, eager is
*faster* — `asSequence()` on a 10-element list is a pessimisation. Sequences win on long chains,
large or infinite data, and early termination (`first`, `any`, `take`).

## Three gotchas

1. **`sequence { }` is single-pass.** Iterating twice throws `IllegalStateException`. If callers
   might traverse more than once, return a `List`.
2. **Resource lifetime.** A lazy sequence over a file or `ResultSet` keeps the handle open until
   consumption finishes. Returning a `Sequence` from a repository method is a classic descriptor
   leak — wrap in `use { }` and consume inside.
3. **No suspend functions.** `sequence { }` can't call them. For an HTTP call per element, use
   `Flow`.

## Production use case

Paginated API/database traversal; log and CSV streaming; infinite generators (IDs, backoff delays);
custom domain collections (the `RingBuffer` here) where the internal wrap-around indexing is exactly
what should stay hidden.
