package com.example.kotlindp.patterns.spring.applicationevents

import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * # Observer via ApplicationEventPublisher
 *
 * Spring's event mechanism is the Observer pattern with the registry, dispatch, and (optionally)
 * async delivery supplied by the container. Compare `behavioral/observer`, where the same machinery
 * is hand-written — including the `CopyOnWriteArrayList` and the unsubscribe handle that are easy
 * to get wrong.
 *
 * Its real purpose is **decoupling a core use case from its side effects**: placing an order should
 * not require the order service to know about email, analytics, and inventory.
 */

// ---------------------------------------------------------------------------------------------
// Events — immutable data classes.
// ---------------------------------------------------------------------------------------------

/**
 * Since Spring 4.2 an event need not extend `ApplicationEvent`; any object works. Use a `data class`
 * so events are immutable — a listener that mutates an event is a bug that only shows up when
 * listener order changes.
 *
 * Include the data listeners need. An event carrying only an ID forces every listener to hit the
 * database, which converts one write into N reads.
 */
data class OrderPlaced(
    val orderId: String,
    val customerId: String,
    val totalCents: Long,
    val occurredAt: Instant = Instant.now(),
)

data class OrderCancelled(val orderId: String, val reason: String)

// ---------------------------------------------------------------------------------------------
// Publisher.
// ---------------------------------------------------------------------------------------------

/**
 * The publisher knows nothing about who listens. Adding a new side effect means adding a listener
 * bean — this class does not change.
 *
 * That is the benefit and also the cost: read this class and you *cannot tell* what happens when an
 * order is placed. Use events for genuinely independent side effects, not to hide a call you could
 * have made directly.
 */
@Service
class OrderPublishingService(private val events: ApplicationEventPublisher) {

    private val placed = CopyOnWriteArrayList<String>()

    fun place(orderId: String, customerId: String, totalCents: Long): String {
        placed += orderId
        // Publication is synchronous by default: listeners run on the caller's thread,
        // inside the caller's transaction, before this method returns.
        events.publishEvent(OrderPlaced(orderId, customerId, totalCents))
        return orderId
    }

    fun cancel(orderId: String, reason: String) {
        placed.remove(orderId)
        events.publishEvent(OrderCancelled(orderId, reason))
    }

    fun placedOrders(): List<String> = placed.toList()
}

// ---------------------------------------------------------------------------------------------
// Listeners.
// ---------------------------------------------------------------------------------------------

/**
 * `@EventListener` infers the event type from the parameter — no interface, no registration.
 *
 * `@Order` controls sequence among listeners of the same event. If you find yourself needing it,
 * ask whether the listeners are really independent; ordered listeners are a hidden workflow, and a
 * workflow belongs in a service (see `structural/facade`).
 */
@Component
@Order(1)
class InventoryListener {
    val reserved = CopyOnWriteArrayList<String>()

    @EventListener
    fun on(event: OrderPlaced) {
        reserved += event.orderId
    }

    @EventListener
    fun on(event: OrderCancelled) {
        reserved.remove(event.orderId)
    }
}

@Component
@Order(2)
class EmailListener {
    val sent = CopyOnWriteArrayList<String>()

    @EventListener
    fun on(event: OrderPlaced) {
        sent += "receipt for ${event.orderId} to ${event.customerId}"
    }
}

/**
 * `condition` is a SpEL filter evaluated before the listener runs — useful for keeping a
 * `if (...) return` out of the listener body, and self-documenting at the declaration.
 */
@Component
class HighValueOrderListener {
    val flagged = CopyOnWriteArrayList<String>()

    @EventListener(condition = "#event.totalCents > 100000")
    fun on(event: OrderPlaced) {
        flagged += event.orderId
    }
}

/**
 * A listener can handle several event types by declaring the supertype, or by listing classes.
 * Here a common audit trail records everything.
 */
@Component
class AuditListener {
    val entries = CopyOnWriteArrayList<String>()

    @EventListener
    fun onPlaced(event: OrderPlaced) {
        entries += "placed:${event.orderId}"
    }

    @EventListener
    fun onCancelled(event: OrderCancelled) {
        entries += "cancelled:${event.orderId}:${event.reason}"
    }
}

/**
 * ## The three things to know
 *
 * **1. Publication is synchronous by default.** Listeners run on the publisher's thread, inside its
 * transaction, before `publishEvent` returns. A slow listener slows the request; a throwing listener
 * **rolls back the publisher's transaction**. That surprises people who assume "event" means
 * "asynchronous".
 *
 * **2. `@Async` changes the failure semantics entirely.** Add `@Async` (with `@EnableAsync`) and the
 * listener runs on another thread — outside the transaction, with no rollback, and with exceptions
 * going nowhere unless you configure an `AsyncUncaughtExceptionHandler`. Fine for email; wrong for
 * anything that must not be lost.
 *
 * **3. `@TransactionalEventListener` is the one worth remembering.** It fires *after commit*
 * (`AFTER_COMMIT` by default), so a listener never acts on state that gets rolled back. This is the
 * classic bug in hand-rolled event buses: sending a confirmation email for an order whose
 * transaction then fails.
 *
 * ```kotlin
 * @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
 * fun on(event: OrderPlaced) { mailer.sendReceipt(event) }
 * ```
 *
 * Note it needs an active transaction — with none, the listener silently does not fire unless
 * `fallbackExecution = true`.
 *
 * ## When not to use events
 *
 * When the "listener" is really the next step of the use case. If the order cannot be considered
 * placed until inventory is reserved, that is a direct call in a transactional service, not an
 * event. Events express *"this happened, react if you care"*, not *"now do this"*.
 *
 * For cross-service delivery you need a real broker plus the transactional outbox pattern; Spring
 * events are in-process only and are lost on a crash.
 */
