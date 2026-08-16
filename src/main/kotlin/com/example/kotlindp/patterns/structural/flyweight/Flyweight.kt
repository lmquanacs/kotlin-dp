package com.example.kotlindp.patterns.structural.flyweight

import java.util.concurrent.ConcurrentHashMap

/**
 * # Flyweight
 *
 * Share one instance across many logical objects to cut memory, by splitting state into:
 * - **intrinsic** state — shared, immutable, stored in the flyweight;
 * - **extrinsic** state — unique per use, passed in as parameters.
 *
 * This is the one GoF pattern whose justification is purely quantitative. Don't apply it because
 * it's elegant; apply it when a heap dump says you have millions of near-identical objects.
 */

// ---------------------------------------------------------------------------------------------
// The intrinsic state — shared and immutable.
// ---------------------------------------------------------------------------------------------

/**
 * Currency metadata: a handful of distinct values shared by potentially millions of amounts.
 *
 * Immutability is not optional. A shared flyweight with mutable state is a data race and a
 * spooky-action-at-a-distance bug affecting every holder at once.
 */
class CurrencyMeta private constructor(
    val code: String,
    val symbol: String,
    val minorUnits: Int,
) {
    /** The extrinsic state ([amountMinor]) arrives as a parameter rather than being stored. */
    fun format(amountMinor: Long): String {
        val divisor = generateSequence(1L) { it * 10 }.elementAt(minorUnits)
        val whole = amountMinor / divisor
        val frac = (amountMinor % divisor).toString().padStart(minorUnits, '0')
        return if (minorUnits == 0) "$symbol$whole" else "$symbol$whole.$frac"
    }

    /**
     * The flyweight factory. [ConcurrentHashMap.computeIfAbsent] gives atomic get-or-create, so
     * concurrent callers still receive the *same* instance — a plain `HashMap` here would both
     * corrupt the map and hand out duplicates, defeating the whole purpose.
     */
    companion object {
        private val pool = ConcurrentHashMap<String, CurrencyMeta>()

        fun of(code: String): CurrencyMeta = pool.computeIfAbsent(code) {
            when (it) {
                "USD" -> CurrencyMeta("USD", "$", 2)
                "EUR" -> CurrencyMeta("EUR", "€", 2)
                "JPY" -> CurrencyMeta("JPY", "¥", 0)
                else -> CurrencyMeta(it, it, 2)
            }
        }

        fun poolSize(): Int = pool.size
        fun clearPool() = pool.clear()
    }
}

/**
 * The lightweight object. Millions of these can exist; each holds a *reference* to shared metadata
 * rather than its own copy of the symbol and precision.
 *
 * `@JvmInline value class` would go a step further and erase the wrapper entirely at runtime — see
 * the `kotlinidioms/inlinereified` folder.
 */
data class Amount(val minorUnits: Long, val currencyCode: String) {
    val meta: CurrencyMeta get() = CurrencyMeta.of(currencyCode)
    fun formatted(): String = meta.format(minorUnits)
}

// ---------------------------------------------------------------------------------------------
// A generic interning pool.
// ---------------------------------------------------------------------------------------------

/**
 * Interning is flyweight applied to values: many equal instances collapse to one, so later equality
 * checks can short-circuit on reference identity and the heap holds one copy.
 *
 * The **leak** is the thing to understand: this pool is a strong reference. Interning unbounded
 * user-supplied strings is a textbook memory leak. Bound the pool, or use weak references
 * (`WeakHashMap`), or intern only values from a known-small domain — country codes, HTTP header
 * names, enum-like tokens.
 */
class InternPool<T : Any>(private val maxSize: Int = 1_024) {
    private val pool = ConcurrentHashMap<T, T>()

    fun intern(value: T): T {
        if (pool.size >= maxSize) return value // fail open: bounded, never unbounded growth
        return pool.putIfAbsent(value, value) ?: value
    }

    fun size(): Int = pool.size
}

/**
 * ## Kotlin has flyweights built in
 *
 * - **`object`** — one instance per declaration, already shared.
 * - **Boxed `Int` caching** — the JVM caches `Integer` for −128..127, so `Int?` values in that range
 *   are shared automatically. (`===` on boxed integers therefore behaves differently inside and
 *   outside that window; never compare boxed numbers by reference.)
 * - **`String` literals** — interned by the JVM at class load.
 * - **`enum`** — a fixed set of shared instances, which is exactly a flyweight pool with compile-time
 *   membership. For a *closed* set, an enum beats a hand-written pool every time.
 * - **`@JvmInline value class`** — no instance to share, because there is no instance.
 *
 * ## When to use
 *
 * Only after measuring. The pattern trades a hash lookup (and cache-miss risk) per access for
 * memory, and it forces immutability on the shared part. Millions of objects, few distinct values →
 * good. Thousands of objects → you have just made the code slower and harder to read.
 */
