package com.example.kotlindp.patterns.structural.facade

/**
 * # Facade
 *
 * Present one simple entry point over a complicated subsystem, so callers do not have to know the
 * order of operations, the compensating actions, or which of six collaborators to talk to.
 *
 * A Facade does not hide the subsystem — advanced callers can still reach the parts. It removes the
 * *obligation* to understand them for the common case.
 */

// ---------------------------------------------------------------------------------------------
// The subsystem: several collaborators, each correct in isolation, jointly hard to use.
// ---------------------------------------------------------------------------------------------

data class Order(val id: String, val sku: String, val quantity: Int, val customerId: String)

class InventoryService {
    private val stock = mutableMapOf("WIDGET" to 10, "GADGET" to 0)

    fun available(sku: String): Int = stock[sku] ?: 0
    fun reserve(sku: String, qty: Int): Boolean {
        val have = available(sku)
        if (have < qty) return false
        stock[sku] = have - qty
        return true
    }

    fun release(sku: String, qty: Int) {
        stock[sku] = available(sku) + qty
    }
}

class PricingService {
    fun priceCents(sku: String, qty: Int): Long = when (sku) {
        "WIDGET" -> 1_500L * qty
        "GADGET" -> 4_200L * qty
        else -> throw IllegalArgumentException("Unknown sku $sku")
    }
}

class PaymentService {
    val charges = mutableListOf<Pair<String, Long>>()
    var declineCustomer: String? = null

    fun charge(customerId: String, cents: Long): String {
        if (customerId == declineCustomer) throw IllegalStateException("card declined")
        charges += customerId to cents
        return "pay_${charges.size}"
    }
}

class ShippingService {
    fun schedule(orderId: String): String = "ship_$orderId"
}

class AuditLog {
    val entries = mutableListOf<String>()
    fun record(event: String) {
        entries += event
    }
}

// ---------------------------------------------------------------------------------------------
// The facade.
// ---------------------------------------------------------------------------------------------

sealed class CheckoutResult {
    data class Success(val paymentRef: String, val shipmentRef: String, val totalCents: Long) : CheckoutResult()
    data class Failure(val reason: String) : CheckoutResult()
}

/**
 * One method for the whole business operation.
 *
 * The value is not just fewer imports at the call site — it is that the **compensating action**
 * lives here. Reserving stock and then failing to charge must release the stock; that rule is easy
 * to forget when each caller orchestrates the subsystem itself, and impossible to forget when there
 * is exactly one orchestrator.
 *
 * Note the facade returns a sealed [CheckoutResult] rather than throwing: the subsystem's varied
 * exception types are translated into a closed set of outcomes the caller must handle.
 */
class CheckoutFacade(
    private val inventory: InventoryService,
    private val pricing: PricingService,
    private val payments: PaymentService,
    private val shipping: ShippingService,
    private val audit: AuditLog,
) {

    fun checkout(order: Order): CheckoutResult {
        audit.record("checkout.started ${order.id}")

        if (!inventory.reserve(order.sku, order.quantity)) {
            audit.record("checkout.out_of_stock ${order.id}")
            return CheckoutResult.Failure("out of stock: ${order.sku}")
        }

        val total = pricing.priceCents(order.sku, order.quantity)

        val paymentRef = try {
            payments.charge(order.customerId, total)
        } catch (e: IllegalStateException) {
            // The compensating action. This is the reason the facade exists.
            inventory.release(order.sku, order.quantity)
            audit.record("checkout.payment_failed ${order.id}")
            return CheckoutResult.Failure("payment failed: ${e.message}")
        }

        val shipmentRef = shipping.schedule(order.id)
        audit.record("checkout.completed ${order.id}")
        return CheckoutResult.Success(paymentRef, shipmentRef, total)
    }
}

/**
 * ## Kotlin notes
 *
 * A facade over *stateless* collaborators can be a file of top-level functions instead of a class —
 * Kotlin has no "everything must live in a class" rule, so there is no reason to invent a
 * `CheckoutUtils` holder.
 *
 * In Spring, this is your `@Service` layer, and it should be. Constructor-inject the collaborators;
 * put `@Transactional` on the facade method so the compensating actions above are replaced by a
 * rollback where the resources are transactional.
 *
 * ### The failure mode to watch
 *
 * Facades accrete. A facade with 40 methods and 12 injected collaborators is a god object wearing a
 * pattern's name. Keep one facade per *use case family*, and split when the injected set stops
 * looking cohesive.
 */
