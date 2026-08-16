package com.example.kotlindp.patterns.structural.adapter

/**
 * # Adapter
 *
 * Make an existing class usable through an interface it was not written for. The classic use is
 * wrapping a third-party or legacy API so the rest of your code depends on *your* abstraction.
 *
 * Kotlin adds a lightweight option Java lacks: **extension functions**. When you only need to add
 * methods (not implement an interface), an extension adapts the type with no wrapper object, no
 * allocation, and no delegation boilerplate.
 */

// ---------------------------------------------------------------------------------------------
// The interface our application wants to depend on.
// ---------------------------------------------------------------------------------------------

data class Money(val amountCents: Long, val currency: String)

data class PaymentResult(val success: Boolean, val reference: String, val error: String? = null)

interface PaymentGateway {
    fun charge(customerId: String, amount: Money): PaymentResult
}

// ---------------------------------------------------------------------------------------------
// The adaptee: a third-party SDK we do not control. Note the mismatched vocabulary —
// floats for money, a status int, a different notion of identity.
// ---------------------------------------------------------------------------------------------

class LegacyPaymentSdk {
    /** @return status code: 0 = ok, 1 = declined, 2 = error */
    fun doPayment(account: String, dollars: Double, currencyCode: Int): Int = when {
        dollars <= 0.0 -> 2
        currencyCode !in setOf(USD, EUR) -> 2
        account.startsWith("blocked") -> 1
        else -> 0
    }

    fun lastTransactionId(): String = "legacy-tx-001"

    companion object {
        const val USD = 840
        const val EUR = 978
    }
}

// ---------------------------------------------------------------------------------------------
// Object adapter — composition. This is the form to use; prefer it over inheritance.
// ---------------------------------------------------------------------------------------------

/**
 * The adapter absorbs every impedance mismatch in one place:
 * - cents (`Long`) → dollars (`Double`), so no float arithmetic leaks into our domain
 * - ISO currency letters → the SDK's numeric codes
 * - status integers → a typed [PaymentResult]
 *
 * This is where the value is. Without the adapter, `840` and `dollars` would spread through the
 * codebase and every call site would have to remember the status-code table.
 */
class LegacyPaymentGatewayAdapter(private val sdk: LegacyPaymentSdk) : PaymentGateway {

    override fun charge(customerId: String, amount: Money): PaymentResult {
        val dollars = amount.amountCents / 100.0
        val code = currencyCode(amount.currency)

        return when (val status = sdk.doPayment(customerId, dollars, code)) {
            0 -> PaymentResult(success = true, reference = sdk.lastTransactionId())
            1 -> PaymentResult(false, "", "declined")
            else -> PaymentResult(false, "", "sdk error status=$status")
        }
    }

    private fun currencyCode(currency: String): Int = when (currency) {
        "USD" -> LegacyPaymentSdk.USD
        "EUR" -> LegacyPaymentSdk.EUR
        else -> throw IllegalArgumentException("Unsupported currency: $currency")
    }
}

// ---------------------------------------------------------------------------------------------
// Adapter via `by` delegation — when the target interface is wide but mostly matches.
// ---------------------------------------------------------------------------------------------

interface Repository<T> {
    fun findById(id: String): T?
    fun findAll(): List<T>
    fun count(): Int
}

class InMemoryRepository<T>(private val items: Map<String, T>) : Repository<T> {
    override fun findById(id: String): T? = items[id]
    override fun findAll(): List<T> = items.values.toList()
    override fun count(): Int = items.size
}

/**
 * `by delegate` forwards *every* member of [Repository] automatically; you override only what you
 * want to change. In Java this would be one hand-written forwarding method per interface member —
 * and the moment someone adds a method to the interface, the hand-written version silently rots.
 *
 * Here we add caching to `findById` and let the other three pass straight through.
 */
class CachingRepositoryAdapter<T>(
    private val delegate: Repository<T>,
) : Repository<T> by delegate {

    private val cache = mutableMapOf<String, T?>()

    override fun findById(id: String): T? = cache.getOrPut(id) { delegate.findById(id) }

    fun cacheSize(): Int = cache.size
}

// ---------------------------------------------------------------------------------------------
// Extension functions — the zero-cost adapter.
// ---------------------------------------------------------------------------------------------

/**
 * When adaptation means "add convenience methods" rather than "conform to an interface", an
 * extension is the lightest possible adapter: it compiles to a static function, allocates nothing,
 * and needs no wrapper class.
 *
 * The limitation is that extensions are resolved *statically* — they are not virtual and cannot
 * satisfy an interface contract. The moment you need polymorphism, go back to a wrapper class.
 */
fun LegacyPaymentSdk.chargeDollars(account: String, dollars: Double): Boolean =
    doPayment(account, dollars, LegacyPaymentSdk.USD) == 0

/** Adapting a foreign type to a domain type is a very common extension shape. */
fun Map<String, Any?>.toMoney(): Money = Money(
    amountCents = (this["amount_cents"] as? Number)?.toLong()
        ?: throw IllegalArgumentException("missing amount_cents"),
    currency = this["currency"] as? String ?: "USD",
)
