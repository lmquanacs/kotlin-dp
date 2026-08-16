package com.example.kotlindp.patterns.behavioral.strategy

/**
 * # Strategy
 *
 * Define a family of interchangeable algorithms and select one at runtime.
 *
 * In Java this needs an interface plus one class per algorithm. In Kotlin a **function type is
 * already a strategy interface**, so most strategies are just lambdas — no hierarchy, no allocation
 * when inlined, and testable without a mock.
 *
 * Rule of thumb: single-method strategy → function type. Strategy with state, a name, or several
 * operations → interface.
 */

data class Basket(val itemCount: Int, val subtotalCents: Long, val customerTier: String)

// ---------------------------------------------------------------------------------------------
// 1. Strategy as a function type — the default choice.
// ---------------------------------------------------------------------------------------------

/**
 * A `typealias` gives the function type a domain name, so signatures read as intent rather than as
 * plumbing. This costs nothing at runtime — it is erased to the underlying type.
 */
typealias DiscountStrategy = (Basket) -> Long

val noDiscount: DiscountStrategy = { 0 }

val tenPercentOff: DiscountStrategy = { basket -> basket.subtotalCents / 10 }

val bulkDiscount: DiscountStrategy = { basket ->
    if (basket.itemCount >= 10) basket.subtotalCents / 5 else 0
}

/** Strategies compose: this one is built from others rather than duplicating their logic. */
fun bestOf(vararg strategies: DiscountStrategy): DiscountStrategy = { basket ->
    strategies.maxOfOrNull { it(basket) } ?: 0
}

/** Injecting the strategy keeps [Checkout] closed to modification and open to new algorithms. */
class Checkout(private val discount: DiscountStrategy = noDiscount) {
    fun total(basket: Basket): Long = basket.subtotalCents - discount(basket)
}

// ---------------------------------------------------------------------------------------------
// 2. Strategy as an interface — when it needs identity or configuration.
// ---------------------------------------------------------------------------------------------

/**
 * Use this form when the strategy must be *named* (persisted, logged, chosen from config) or
 * carries state. A bare lambda cannot tell you which algorithm it is; this can.
 */
interface ShippingStrategy {
    val code: String
    fun costCents(weightGrams: Int, distanceKm: Int): Long
}

object StandardShipping : ShippingStrategy {
    override val code = "standard"
    override fun costCents(weightGrams: Int, distanceKm: Int) = 500L + weightGrams / 100
}

object ExpressShipping : ShippingStrategy {
    override val code = "express"
    override fun costCents(weightGrams: Int, distanceKm: Int) = 1500L + weightGrams / 50 + distanceKm * 2L
}

/** A *configured* strategy — the reason it is a class rather than an object. */
class FlatRateShipping(private val flatCents: Long) : ShippingStrategy {
    override val code = "flat"
    override fun costCents(weightGrams: Int, distanceKm: Int) = flatCents
}

/**
 * Registry lookup by code. In Spring you get this for free: inject `List<ShippingStrategy>` (or
 * `Map<String, ShippingStrategy>`) and the container supplies every implementation, so adding a
 * strategy means adding a `@Component` and touching nothing else.
 */
class ShippingCalculator(strategies: List<ShippingStrategy>) {
    private val byCode = strategies.associateBy { it.code }

    fun cost(code: String, weightGrams: Int, distanceKm: Int): Long =
        (byCode[code] ?: error("Unknown shipping strategy '$code'")).costCents(weightGrams, distanceKm)

    fun available(): Set<String> = byCode.keys
}

// ---------------------------------------------------------------------------------------------
// 3. Strategy as an enum — a closed set with behaviour attached.
// ---------------------------------------------------------------------------------------------

/**
 * Kotlin enums can declare abstract members and override them per constant. That yields an
 * exhaustive, serialisable, `when`-friendly strategy family in a dozen lines.
 *
 * Choose this when the set is genuinely closed and each member has no configuration. Choose the
 * interface form when third parties must add implementations — you cannot extend an enum.
 */
enum class TaxStrategy {
    NONE {
        override fun taxCents(amountCents: Long) = 0L
    },
    VAT_20 {
        override fun taxCents(amountCents: Long) = amountCents * 20 / 100
    },
    SALES_TAX_7 {
        override fun taxCents(amountCents: Long) = amountCents * 7 / 100
    };

    abstract fun taxCents(amountCents: Long): Long
}

/**
 * ## Why `when` is not always the alternative
 *
 * A `when` block *is* a fine strategy selector for a small closed set. It becomes a problem when
 * the same `when` gets copy-pasted across the codebase — then adding a case means finding every
 * copy. Extract to a strategy at the second occurrence, not the first.
 *
 * ## Testing
 *
 * The lambda form is the reason to prefer it: `Checkout { 100 }` needs no mocking framework, no
 * stub class, and no `verify`. If a strategy is hard to test, it is doing too much.
 */
