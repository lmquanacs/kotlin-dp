package com.example.kotlindp.patterns.behavioral.iterator

/**
 * # Iterator
 *
 * Traverse a collection without exposing its internal representation.
 *
 * Kotlin implements this at the language level: implement `operator fun iterator()` and your type
 * works with `for`, destructuring, and the whole stdlib. More importantly, **`Sequence` and
 * `sequence { }` make lazy iterators trivial** — which is where the pattern actually earns its keep
 * in production, on data too large or too slow to materialise.
 */

// ---------------------------------------------------------------------------------------------
// 1. Making a custom type iterable.
// ---------------------------------------------------------------------------------------------

/**
 * A ring buffer: the internal array and the wrap-around indexing are exactly the representation an
 * iterator is meant to hide.
 *
 * Implementing [Iterable] gives you `for`, `map`, `filter`, `joinToString` and the rest for free.
 * Note the `size`/`index` capture: this iterator is *fail-fast* only by accident. Real production
 * iterators over mutable structures should track a modification count and throw
 * `ConcurrentModificationException`, as the JDK collections do — silently iterating a structure that
 * changed underneath you is far worse than failing.
 */
class RingBuffer<T>(private val capacity: Int) : Iterable<T> {
    private val items = arrayOfNulls<Any?>(capacity)
    private var head = 0
    var size: Int = 0
        private set

    fun add(item: T) {
        val tail = (head + size) % capacity
        items[tail] = item
        if (size == capacity) head = (head + 1) % capacity else size++
    }

    @Suppress("UNCHECKED_CAST")
    operator fun get(index: Int): T {
        require(index in 0 until size) { "index $index out of bounds (size=$size)" }
        return items[(head + index) % capacity] as T
    }

    /** `operator` is what wires this into the `for` loop and the stdlib. */
    override fun iterator(): Iterator<T> = object : Iterator<T> {
        private var cursor = 0
        override fun hasNext(): Boolean = cursor < size
        override fun next(): T {
            if (!hasNext()) throw NoSuchElementException()
            return get(cursor++)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// 2. `sequence { }` — the iterator you should usually write.
// ---------------------------------------------------------------------------------------------

/**
 * `sequence { }` builds a lazy iterator from ordinary-looking imperative code. `yield` suspends
 * until the consumer asks for the next element, so this is an infinite sequence that costs nothing
 * until taken from.
 */
fun fibonacci(): Sequence<Long> = sequence {
    var a = 0L
    var b = 1L
    while (true) {
        yield(a)
        val next = a + b
        a = b
        b = next
    }
}

/**
 * The production case: **paginated API traversal**.
 *
 * The caller writes `pages.take(50)` and never sees a page boundary; only the pages actually
 * consumed are fetched. Doing this eagerly would mean fetching every page before returning the
 * first item — the difference between a 200ms response and a 40s one.
 */
fun <T> paginated(pageSize: Int, fetch: (offset: Int, limit: Int) -> List<T>): Sequence<T> = sequence {
    var offset = 0
    while (true) {
        val page = fetch(offset, pageSize)
        if (page.isEmpty()) break
        yieldAll(page)
        if (page.size < pageSize) break // short page means last page
        offset += pageSize
    }
}

/** Recursive traversal reads naturally with `yieldAll`. */
fun <T> Iterable<Iterable<T>>.flattenLazily(): Sequence<T> = sequence {
    forEach { yieldAll(it) }
}

// ---------------------------------------------------------------------------------------------
// 3. Sequence vs Iterable — the performance question people get wrong.
// ---------------------------------------------------------------------------------------------

/**
 * `Iterable` operations are **eager**: each step allocates a full intermediate list.
 * `Sequence` operations are **lazy**: elements flow through the whole chain one at a time.
 *
 * ```kotlin
 * list.map { … }.filter { … }.first()   // maps all N, filters all N, takes 1
 * list.asSequence().map { … }.filter { … }.first()  // maps and filters until the first match
 * ```
 *
 * The catch: laziness has per-element overhead. For a small list with one or two operations, eager
 * is *faster* — `asSequence()` on a 10-element list is a pessimisation. Sequences win on long
 * chains, large or infinite data, and early termination (`first`, `any`, `take`).
 *
 * [firstMatchCost] demonstrates the difference by counting how many elements each approach touches.
 */
fun firstMatchCost(data: List<Int>, predicate: (Int) -> Boolean): Pair<Int, Int> {
    var eagerTouches = 0
    data.map { eagerTouches++; it * 2 }.firstOrNull { predicate(it) }

    var lazyTouches = 0
    data.asSequence().map { lazyTouches++; it * 2 }.firstOrNull { predicate(it) }

    return eagerTouches to lazyTouches
}

/**
 * ## Notes
 *
 * **A `Sequence` from `sequence { }` is single-pass by default** — iterating it twice throws
 * `IllegalStateException`. If a caller might traverse more than once, hand back a `List` or use
 * `constrainOnce()`/`Sequence { … }` deliberately.
 *
 * **Resource lifetime.** A lazy sequence over a file or `ResultSet` keeps the handle open until
 * consumption finishes. Wrap it in `use { }` and consume inside the block, or you leak descriptors
 * — a very common bug when returning `Sequence` from a repository method.
 *
 * **Coroutines.** For iteration that needs suspending work per element (an HTTP call per page), use
 * `Flow` instead of `Sequence`: `sequence { }` cannot call suspend functions.
 */
