package com.example.kotlindp.patterns.kotlinidioms.functional

/**
 * # Functional idioms
 *
 * Higher-order functions, composition, currying, partial application, and the collection pipeline.
 *
 * These are not "advanced Kotlin" — they are the everyday shape of production code once you stop
 * writing loops. The parts worth being deliberate about are *where the boundaries are*: pure
 * transformation in the middle, effects at the edges.
 */

// ---------------------------------------------------------------------------------------------
// 1. Composition.
// ---------------------------------------------------------------------------------------------

/**
 * `f then g` applies `f` first — the reading order people expect. The mathematical `compose`
 * (`g ∘ f`, applying `g` last) is also provided; pick one convention per codebase and stick to it,
 * because mixing them is a reliable source of reversed pipelines.
 */
infix fun <A, B, C> ((A) -> B).then(next: (B) -> C): (A) -> C = { a -> next(this(a)) }

infix fun <A, B, C> ((B) -> C).compose(before: (A) -> B): (A) -> C = { a -> this(before(a)) }

val trim: (String) -> String = String::trim
val lower: (String) -> String = String::lowercase
val slugify: (String) -> String = trim then lower then { it.replace(Regex("\\s+"), "-") }

// ---------------------------------------------------------------------------------------------
// 2. Currying and partial application.
// ---------------------------------------------------------------------------------------------

/**
 * **Partial application** — fix some arguments now, supply the rest later. In Kotlin this is usually
 * just a lambda that closes over the fixed values, which is clearer than a `partial()` helper.
 *
 * **Currying** — turn an n-argument function into a chain of 1-argument functions.
 *
 * Honest assessment: currying is rarely the right tool in Kotlin, because default arguments and
 * named arguments already solve the "configure some parameters" problem more readably. Know it,
 * reach for it seldom.
 */
fun <A, B, C> ((A, B) -> C).curried(): (A) -> (B) -> C = { a -> { b -> this(a, b) } }

fun <A, B, C> ((A) -> (B) -> C).uncurried(): (A, B) -> C = { a, b -> this(a)(b) }

fun <A, B, C> ((A, B) -> C).partial(a: A): (B) -> C = { b -> this(a, b) }

val log: (String, String) -> String = { level, message -> "[$level] $message" }
val warn: (String) -> String = log.partial("WARN")

// ---------------------------------------------------------------------------------------------
// 3. Memoisation — a higher-order function that adds caching.
// ---------------------------------------------------------------------------------------------

/**
 * Only valid for **pure** functions. Memoising something with side effects, or something whose
 * result depends on time or external state, produces stale answers that are very hard to debug.
 *
 * Note the deliberate limitation: this cache is unbounded. Production memoisation needs an eviction
 * policy (see `production/cacheaside`) or it becomes a memory leak on unbounded input.
 */
fun <A, R> ((A) -> R).memoized(): (A) -> R {
    val cache = mutableMapOf<A, R>()
    return { a -> cache.getOrPut(a) { this(a) } }
}

/** Recursive memoisation needs the function to call the *memoised* version, not itself. */
class Fibonacci {
    var calls = 0
        private set

    private val memo = mutableMapOf<Int, Long>()

    fun compute(n: Int): Long = memo.getOrPut(n) {
        calls++
        if (n <= 1) n.toLong() else compute(n - 1) + compute(n - 2)
    }
}

// ---------------------------------------------------------------------------------------------
// 4. The collection pipeline.
// ---------------------------------------------------------------------------------------------

data class Sale(val region: String, val product: String, val cents: Long, val units: Int)

/**
 * A pipeline says *what* you want; a loop says *how*. The stdlib operators worth knowing by name,
 * because they replace a hand-written loop that is easy to get subtly wrong:
 *
 * - `groupBy` / `associateBy` / `associateWith` — build maps without a mutable accumulator
 * - `partition` — split by predicate in one pass, returning both halves
 * - `fold` / `runningFold` — accumulate with an explicit seed
 * - `sumOf` / `maxByOrNull` / `minByOrNull` — aggregate without an intermediate list
 * - `windowed` / `chunked` / `zipWithNext` — adjacent-element work, no index arithmetic
 * - `flatMap` / `mapNotNull` — flatten and filter-map in one step
 * - `distinctBy` / `sortedWith(compareBy(...).thenByDescending(...))` — multi-key ordering
 */
fun revenueByRegion(sales: List<Sale>): Map<String, Long> =
    sales.groupBy { it.region }.mapValues { (_, rows) -> rows.sumOf { it.cents } }

fun topProductPerRegion(sales: List<Sale>): Map<String, String?> =
    sales.groupBy { it.region }
        .mapValues { (_, rows) -> rows.maxByOrNull { it.cents }?.product }

fun splitBySize(sales: List<Sale>, thresholdCents: Long): Pair<List<Sale>, List<Sale>> =
    sales.partition { it.cents >= thresholdCents }

/** `fold` makes the accumulator and the seed explicit, which is why it beats a `var` outside a loop. */
fun runningTotals(sales: List<Sale>): List<Long> =
    sales.runningFold(0L) { acc, sale -> acc + sale.cents }.drop(1)

/** `zipWithNext` for period-over-period deltas — no index arithmetic, no off-by-one. */
fun deltas(values: List<Long>): List<Long> = values.zipWithNext { a, b -> b - a }

/** Multi-key sorting reads as the requirement does. */
fun ranked(sales: List<Sale>): List<Sale> =
    sales.sortedWith(compareByDescending<Sale> { it.cents }.thenBy { it.product })

// ---------------------------------------------------------------------------------------------
// 5. Functional core, imperative shell.
// ---------------------------------------------------------------------------------------------

/**
 * The architectural point behind all of the above.
 *
 * **Core**: pure functions over immutable data. No I/O, no clock, no randomness. Trivially testable
 * — no mocks, no setup, no ordering between tests.
 *
 * **Shell**: a thin layer that reads the world, calls the core, and writes the result back.
 *
 * The mistake to avoid is scattering effects through the transformation. `pricingRules` below is
 * pure and testable with a one-line assertion; the repository call and the audit write stay in
 * [applyPricing], which is the only part needing a stub.
 */
data class LineItem(val sku: String, val unitCents: Long, val quantity: Int)

/** Pure core: same input, same output, always. */
fun priceLines(items: List<LineItem>, discountPercent: Int): Long {
    require(discountPercent in 0..100) { "discount must be 0..100" }
    val subtotal = items.sumOf { it.unitCents * it.quantity }
    return subtotal - subtotal * discountPercent / 100
}

/** Imperative shell: effects in, pure call, effects out. */
class PricingService(
    private val loadItems: (String) -> List<LineItem>,
    private val discountFor: (String) -> Int,
    private val audit: (String) -> Unit,
) {
    fun applyPricing(cartId: String): Long {
        val items = loadItems(cartId)
        val total = priceLines(items, discountFor(cartId))
        audit("priced $cartId -> $total")
        return total
    }
}

/**
 * ## Cautions
 *
 * - **Eager by default.** Every chained operator on a `List` allocates a new list. For long chains
 *   over large data use `asSequence()`; for short chains over small data, don't (see
 *   `behavioral/iterator`).
 * - **Readability has a limit.** A 12-operator chain is as hard to read as a 30-line loop. Break it
 *   with named intermediate `val`s — they cost nothing and name the steps.
 * - **Stack depth.** Kotlin has no general tail-call optimisation; `tailrec` works only for directly
 *   self-recursive functions. Deep non-tail recursion overflows.
 */
tailrec fun gcd(a: Long, b: Long): Long = if (b == 0L) a else gcd(b, a % b)
