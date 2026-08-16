package com.example.kotlindp.patterns.production.circuitbreaker

import java.util.concurrent.atomic.AtomicReference

/**
 * # Circuit Breaker
 *
 * Stop calling a dependency that is clearly failing, so you fail fast instead of piling threads onto
 * a service that cannot answer.
 *
 * This is the pattern that prevents *cascading failure*. Without it, one slow dependency consumes
 * every thread in the calling service, which then stops answering its own callers, and the outage
 * spreads upstream. Retry alone makes this worse; the breaker is what bounds it.
 *
 * It is also a textbook State pattern — see `behavioral/state` — with three states:
 *
 * ```
 *   CLOSED ──failure threshold──► OPEN ──after cooldown──► HALF_OPEN
 *      ▲                                                      │
 *      └──────────── enough probe successes ──────────────────┘
 *                    (any probe failure → OPEN)
 * ```
 */

sealed class BreakerState {
    /** Normal operation; failures are being counted. */
    data class Closed(val consecutiveFailures: Int) : BreakerState()

    /** Failing fast. No calls reach the dependency until [openedAtMs] + cooldown. */
    data class Open(val openedAtMs: Long) : BreakerState()

    /** Probing. A limited number of calls are let through to test recovery. */
    data class HalfOpen(val successes: Int, val inFlight: Int) : BreakerState()
}

class CircuitOpenException(message: String) : RuntimeException(message)

data class BreakerConfig(
    val failureThreshold: Int = 5,
    val cooldownMs: Long = 10_000,
    /** Consecutive probe successes needed to close again. */
    val successThreshold: Int = 2,
    /** Concurrent probes allowed while half-open. Keep small — this is a test, not a resumption. */
    val halfOpenProbes: Int = 1,
)

/**
 * State is held in an [AtomicReference] and advanced with `updateAndGet`, so transitions are atomic
 * without a lock. This matters: a breaker is called from every request thread simultaneously, and a
 * non-atomic counter under-counts failures precisely when it matters most.
 */
class CircuitBreaker(
    private val config: BreakerConfig = BreakerConfig(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val state = AtomicReference<BreakerState>(BreakerState.Closed(0))

    fun currentState(): BreakerState = state.get()

    /**
     * @param isFailure decides what counts against the breaker. A 404 is a valid answer, not a
     *        dependency failure — counting it opens the breaker for a healthy service.
     * @throws CircuitOpenException immediately when the circuit is open.
     */
    fun <T> execute(isFailure: (Throwable) -> Boolean = { true }, block: () -> T): T {
        when (val current = transitionIfCooldownElapsed()) {
            is BreakerState.Open ->
                throw CircuitOpenException("circuit open since ${current.openedAtMs}")

            is BreakerState.HalfOpen ->
                // Admit only a limited number of probes; everyone else keeps failing fast.
                if (current.inFlight >= config.halfOpenProbes) {
                    throw CircuitOpenException("circuit half-open, probe limit reached")
                } else {
                    state.set(current.copy(inFlight = current.inFlight + 1))
                }

            is BreakerState.Closed -> Unit
        }

        return try {
            block().also { onSuccess() }
        } catch (e: Throwable) {
            if (isFailure(e)) onFailure() else onSuccess()
            throw e
        }
    }

    private fun transitionIfCooldownElapsed(): BreakerState = state.updateAndGet { current ->
        if (current is BreakerState.Open && now() - current.openedAtMs >= config.cooldownMs) {
            BreakerState.HalfOpen(successes = 0, inFlight = 0)
        } else {
            current
        }
    }

    private fun onSuccess() {
        state.updateAndGet { current ->
            when (current) {
                is BreakerState.Closed -> BreakerState.Closed(0)
                is BreakerState.HalfOpen -> {
                    val successes = current.successes + 1
                    if (successes >= config.successThreshold) BreakerState.Closed(0)
                    else BreakerState.HalfOpen(successes, (current.inFlight - 1).coerceAtLeast(0))
                }

                is BreakerState.Open -> current
            }
        }
    }

    private fun onFailure() {
        state.updateAndGet { current ->
            when (current) {
                is BreakerState.Closed -> {
                    val failures = current.consecutiveFailures + 1
                    if (failures >= config.failureThreshold) BreakerState.Open(now())
                    else BreakerState.Closed(failures)
                }

                // A failed probe means "still broken" — straight back to open, full cooldown.
                is BreakerState.HalfOpen -> BreakerState.Open(now())
                is BreakerState.Open -> current
            }
        }
    }

    fun reset() = state.set(BreakerState.Closed(0))
}

/**
 * A breaker is far more useful with a fallback: failing fast is only an improvement if the caller
 * has something to say. Cached data, a default, or a degraded response all beat an exception.
 */
fun <T> CircuitBreaker.executeOrElse(fallback: (Throwable) -> T, block: () -> T): T =
    try {
        execute(block = block)
    } catch (e: Throwable) {
        fallback(e)
    }

/**
 * ## Tuning, briefly
 *
 * - **failureThreshold** — too low and transient blips open the circuit; too high and it opens after
 *   the damage is done. Consecutive-failure counting (used here) is simple; a rolling *failure rate*
 *   over a time window is better for high-traffic services, because 5 failures out of 10 000 requests
 *   is not an outage.
 * - **cooldown** — long enough for the dependency to actually recover, short enough not to extend
 *   the outage. Seconds, not minutes.
 * - **isFailure** — the most commonly misconfigured knob. Timeouts and 5xx count; 404 and 400 do not.
 *
 * ## Where the state must live
 *
 * This breaker is per-instance. With N replicas you get N independent breakers, which is usually
 * *fine* (each instance measures what it experiences) and occasionally not — a shared breaker needs
 * shared state and then has its own availability problem. Prefer per-instance.
 *
 * ## In practice
 *
 * Use Resilience4j rather than this class; it adds rolling windows, metrics, bulkheads and rate
 * limiters, and is well tested. This implementation is here to make the state machine legible —
 * because tuning a library you do not understand is how breakers end up permanently open.
 */
