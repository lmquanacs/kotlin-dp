package com.example.kotlindp.patterns.behavioral.state

/**
 * # State
 *
 * Let an object change its behaviour when its internal state changes — so it appears to change
 * class. In practice: replace a tangle of boolean flags and `if` chains with an explicit state
 * machine.
 *
 * This is the pattern Kotlin's **sealed classes** improve most. Illegal states become
 * unrepresentable, transitions become an exhaustive `when`, and each state carries exactly the data
 * that state needs — no more nullable fields that are "only set when status == SHIPPED".
 */

// ---------------------------------------------------------------------------------------------
// The states. Note each carries its own data.
// ---------------------------------------------------------------------------------------------

/**
 * Compare with the flag-based alternative:
 *
 * ```kotlin
 * class Order(var paid: Boolean, var shipped: Boolean,
 *             var trackingCode: String?, var cancelReason: String?)
 * ```
 *
 * That version can represent `shipped = true, paid = false`, and every reader has to know which
 * nullable fields are meaningful in which combination. The sealed version cannot express those
 * states at all — the illegal combinations have no constructor.
 */
sealed class OrderState {
    object Draft : OrderState()
    data class AwaitingPayment(val amountCents: Long) : OrderState()
    data class Paid(val paymentRef: String) : OrderState()
    data class Shipped(val paymentRef: String, val trackingCode: String) : OrderState()
    data class Cancelled(val reason: String) : OrderState()

    /** Terminal states accept no further events; encoding that here keeps callers honest. */
    val isTerminal: Boolean get() = this is Shipped || this is Cancelled
}

sealed class OrderEvent {
    data class Submit(val amountCents: Long) : OrderEvent()
    data class Pay(val paymentRef: String) : OrderEvent()
    data class Ship(val trackingCode: String) : OrderEvent()
    data class Cancel(val reason: String) : OrderEvent()
}

// ---------------------------------------------------------------------------------------------
// The transition function: a pure function of (state, event) -> state.
// ---------------------------------------------------------------------------------------------

sealed class TransitionResult {
    data class Moved(val to: OrderState) : TransitionResult()
    data class Rejected(val from: OrderState, val event: OrderEvent, val why: String) : TransitionResult()
}

/**
 * Keeping the transition **pure** (no mutation, no I/O) is what makes a state machine testable:
 * every rule is one assertion, with no object to construct and no clock or database to stub.
 *
 * The nested `when` is exhaustive over states; within each state only the legal events are listed
 * and everything else falls to a single rejection. Add a state and the compiler flags this function.
 */
fun transition(state: OrderState, event: OrderEvent): TransitionResult {
    fun reject(why: String) = TransitionResult.Rejected(state, event, why)

    return when (state) {
        is OrderState.Draft -> when (event) {
            is OrderEvent.Submit -> TransitionResult.Moved(OrderState.AwaitingPayment(event.amountCents))
            is OrderEvent.Cancel -> TransitionResult.Moved(OrderState.Cancelled(event.reason))
            else -> reject("a draft order must be submitted first")
        }

        is OrderState.AwaitingPayment -> when (event) {
            is OrderEvent.Pay -> TransitionResult.Moved(OrderState.Paid(event.paymentRef))
            is OrderEvent.Cancel -> TransitionResult.Moved(OrderState.Cancelled(event.reason))
            else -> reject("awaiting payment")
        }

        is OrderState.Paid -> when (event) {
            // `state.paymentRef` is smart-cast here — the data travels with the state, so there is
            // no nullable field to check and no chance of reading it in the wrong state.
            is OrderEvent.Ship -> TransitionResult.Moved(OrderState.Shipped(state.paymentRef, event.trackingCode))
            is OrderEvent.Cancel -> TransitionResult.Moved(OrderState.Cancelled(event.reason))
            else -> reject("already paid")
        }

        is OrderState.Shipped -> reject("shipped orders are final")
        is OrderState.Cancelled -> reject("cancelled orders are final")
    }
}

/**
 * A thin mutable shell around the pure function. Only this class has state; all the *rules* stay
 * pure and independently testable.
 */
class Order(initial: OrderState = OrderState.Draft) {
    var state: OrderState = initial
        private set

    private val history = mutableListOf(initial)

    /** @return true if the event was accepted. */
    fun handle(event: OrderEvent): Boolean = when (val result = transition(state, event)) {
        is TransitionResult.Moved -> {
            state = result.to
            history += result.to
            true
        }

        is TransitionResult.Rejected -> false
    }

    fun history(): List<OrderState> = history.toList()
}

// ---------------------------------------------------------------------------------------------
// The GoF form — state as an object with behaviour.
// ---------------------------------------------------------------------------------------------

/**
 * The classic spelling puts the behaviour *on* the state object and lets each state return the next.
 * Use it when states carry substantial behaviour, not just data. Use the sealed + `when` form when
 * the interesting part is the transition table — which is most of the time, because a table you can
 * read top to bottom beats behaviour scattered across a dozen classes.
 */
interface Connection {
    fun open(): Connection
    fun close(): Connection
    val name: String
}

object Closed : Connection {
    override val name = "closed"
    override fun open(): Connection = Open
    override fun close(): Connection = this
}

object Open : Connection {
    override val name = "open"
    override fun open(): Connection = this
    override fun close(): Connection = Closed
}

/**
 * ## Notes
 *
 * **Persistence.** A sealed state maps to a discriminator column plus per-state fields. Write the
 * mapping explicitly; don't let Jackson polymorphic deserialisation infer it from class names, or a
 * rename becomes a production data incident.
 *
 * **Concurrency.** [Order] is not thread-safe. Because [transition] is pure, making it safe is easy:
 * hold the state in an `AtomicReference` and `updateAndGet`, or confine it to one coroutine/actor.
 *
 * **Where this pays off.** Order lifecycles, payment flows, job/task status, connection and
 * circuit-breaker states, upload and approval workflows — anywhere you currently have three booleans
 * whose valid combinations live only in someone's head.
 */
