package com.example.kotlindp.patterns.behavioral.observer

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.properties.Delegates

/**
 * # Observer
 *
 * Notify a set of dependents automatically when a subject changes.
 *
 * Kotlin gives you three levels of this, and picking the right one matters more than the
 * implementation details:
 * 1. `Delegates.observable` — property-level, zero infrastructure;
 * 2. a hand-rolled subject — full control, and the place to understand the pitfalls;
 * 3. `StateFlow`/`SharedFlow` — the production answer for anything asynchronous or multi-consumer.
 */

// ---------------------------------------------------------------------------------------------
// 1. Property-level observation — built into the language.
// ---------------------------------------------------------------------------------------------

/**
 * `Delegates.observable` fires *after* the assignment; `Delegates.vetoable` fires *before* and can
 * reject it by returning false. Between them they cover a surprising amount of what people build
 * whole listener frameworks for.
 */
class Thermostat(initial: Int) {
    val log = mutableListOf<String>()

    var temperature: Int by Delegates.observable(initial) { prop, old, new ->
        log += "${prop.name}: $old -> $new"
    }

    /** Rejects invalid values outright — the setter simply does not take effect. */
    var targetTemperature: Int by Delegates.vetoable(20) { _, _, new -> new in 5..30 }
}

// ---------------------------------------------------------------------------------------------
// 2. The classic subject/observer, with the pitfalls made explicit.
// ---------------------------------------------------------------------------------------------

fun interface Listener<E> {
    fun onEvent(event: E)
}

/**
 * A correct-enough synchronous subject. Three deliberate decisions:
 *
 * - **[CopyOnWriteArrayList]** — an observer that unsubscribes *during* notification would cause
 *   `ConcurrentModificationException` with an `ArrayList`. This is the single most common observer
 *   bug and it only shows up under load.
 * - **[subscribe] returns an unsubscribe handle** rather than requiring callers to keep the listener
 *   reference for a `removeListener` call. Subjects hold strong references to observers, so a
 *   forgotten unsubscribe is a memory leak; returning a closable handle makes cleanup natural.
 * - **Exceptions are isolated** — one failing observer must not prevent the others from being
 *   notified, nor propagate into the code that published the event.
 */
class EventBus<E> {
    private val listeners = CopyOnWriteArrayList<Listener<E>>()
    val failures = mutableListOf<Throwable>()

    fun subscribe(listener: Listener<E>): Subscription {
        listeners += listener
        return Subscription { listeners.remove(listener) }
    }

    fun publish(event: E) {
        listeners.forEach { listener ->
            try {
                listener.onEvent(event)
            } catch (e: Exception) {
                failures += e
            }
        }
    }

    fun subscriberCount(): Int = listeners.size

    fun interface Subscription {
        fun cancel()
    }
}

// ---------------------------------------------------------------------------------------------
// 3. Flows — the production answer.
// ---------------------------------------------------------------------------------------------

data class Price(val symbol: String, val cents: Long)

/**
 * `StateFlow` is an observable *value*: it always has a current value, new collectors immediately
 * receive it, and it conflates — a slow collector sees the latest value, not every intermediate one.
 * That is what you want for state (a price, a connection status, a form).
 *
 * `SharedFlow` is an observable *stream of events*: no initial value, and (with the right buffer)
 * no conflation, so collectors see each event. That is what you want for things that happened
 * (a trade executed, a button pressed) where dropping one is wrong.
 *
 * Getting this choice backwards is the usual Flow mistake: modelling events as `StateFlow` silently
 * drops them, and modelling state as `SharedFlow` leaves late subscribers with nothing to show.
 *
 * Both expose a read-only view (`asStateFlow`/`asSharedFlow`) so only the subject can emit.
 */
class PriceTicker {
    private val _current = MutableStateFlow(Price("ACME", 0))
    val current: StateFlow<Price> = _current.asStateFlow()

    private val _trades = MutableSharedFlow<Price>(replay = 0, extraBufferCapacity = 64)
    val trades: SharedFlow<Price> = _trades.asSharedFlow()

    fun update(price: Price) {
        _current.value = price
        // tryEmit never suspends; with a buffer configured it returns false only when full.
        _trades.tryEmit(price)
    }
}

/**
 * ## Choosing
 *
 * | Need | Use |
 * |---|---|
 * | React to one property changing | `Delegates.observable` |
 * | Synchronous in-process fan-out, full control | hand-rolled subject |
 * | Current value + updates, asynchronous | `StateFlow` |
 * | Stream of events, asynchronous | `SharedFlow` |
 * | Decoupled modules in a Spring app | `ApplicationEventPublisher` |
 *
 * Spring's own `@EventListener` + `ApplicationEventPublisher` is Observer with the wiring done for
 * you, and `@TransactionalEventListener` adds something hand-rolled buses get wrong: fire *after*
 * the transaction commits, so observers never see state that gets rolled back.
 *
 * ## The three failure modes
 *
 * 1. **Leaks** — subjects hold strong references to observers. Always return an unsubscribe handle.
 * 2. **Reentrancy** — an observer that mutates the subject re-enters `publish`. Copy-on-write helps;
 *    a queue is the real fix.
 * 3. **Untraceable flow** — enough indirection and no one can answer "what happens when I publish
 *    this?". Events are for *decoupling*, not for hiding a call you could have made directly.
 */
