package com.example.kotlindp.patterns.production.retry

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * # Retry with exponential backoff and jitter
 *
 * The most-written and most-often-wrong resilience pattern. A naive retry loop makes outages worse:
 * every client retries at the same moment, and the recovering service is knocked over again by the
 * synchronised wave. That is a *retry storm*, and jitter is what prevents it.
 *
 * Four decisions make a retry policy correct:
 * 1. **Which failures are retryable** — retrying a 400 is pointless and retrying a non-idempotent
 *    write is dangerous.
 * 2. **How long to wait** — exponential, so load falls as failures persist.
 * 3. **Jitter** — randomised, so clients de-synchronise.
 * 4. **A cap** — on both delay and total elapsed time, so a caller cannot wait forever.
 */

data class RetryPolicy(
    val maxAttempts: Int = 3,
    val initialDelayMs: Long = 100,
    val maxDelayMs: Long = 5_000,
    val multiplier: Double = 2.0,
    /** Fraction of the computed delay that is randomised. 0.0 = none, 1.0 = full jitter. */
    val jitterFactor: Double = 0.5,
    /** Total budget across all attempts. A retry that outlives its caller's deadline is waste. */
    val maxElapsedMs: Long = 30_000,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
        require(initialDelayMs > 0) { "initialDelayMs must be > 0" }
        require(multiplier >= 1.0) { "multiplier must be >= 1.0" }
        require(jitterFactor in 0.0..1.0) { "jitterFactor must be 0.0..1.0" }
    }

    /**
     * Exponential backoff with **full jitter** (the AWS Architecture Blog formulation): pick a delay
     * uniformly from a window around the exponential value rather than using it directly.
     *
     * Without jitter, N clients that failed together retry together forever. With it they spread out
     * within one or two rounds.
     */
    fun delayFor(attempt: Int, random: Random = Random.Default): Long {
        val exponential = initialDelayMs * multiplier.pow(attempt - 1)
        val capped = min(exponential, maxDelayMs.toDouble())
        val jitterRange = capped * jitterFactor
        val low = capped - jitterRange
        return (low + random.nextDouble() * (jitterRange * 2)).toLong().coerceIn(0, maxDelayMs)
    }
}

sealed class RetryOutcome<out T> {
    data class Success<T>(val value: T, val attempts: Int) : RetryOutcome<T>()
    data class Exhausted(val attempts: Int, val lastError: Throwable) : RetryOutcome<Nothing>()
    data class NotRetryable(val error: Throwable) : RetryOutcome<Nothing>()
}

/**
 * A blocking retry. `sleep` is injected so tests run instantly instead of actually waiting —
 * a retry helper that can only be tested in real time will not be tested.
 *
 * **Idempotency is the caller's responsibility.** Retrying `POST /charge` can double-charge a
 * customer. Retry reads freely; retry writes only with an idempotency key.
 */
fun <T> withRetry(
    policy: RetryPolicy = RetryPolicy(),
    isRetryable: (Throwable) -> Boolean = { true },
    random: Random = Random.Default,
    sleep: (Long) -> Unit = { Thread.sleep(it) },
    now: () -> Long = System::currentTimeMillis,
    block: (attempt: Int) -> T,
): RetryOutcome<T> {
    val start = now()
    var lastError: Throwable? = null

    for (attempt in 1..policy.maxAttempts) {
        try {
            return RetryOutcome.Success(block(attempt), attempt)
        } catch (e: Throwable) {
            if (!isRetryable(e)) return RetryOutcome.NotRetryable(e)
            lastError = e

            if (attempt == policy.maxAttempts) break

            val delay = policy.delayFor(attempt, random)
            // Respect the overall budget: never start a wait that would exceed it.
            if (now() - start + delay > policy.maxElapsedMs) break
            sleep(delay)
        }
    }

    return RetryOutcome.Exhausted(policy.maxAttempts, lastError ?: IllegalStateException("no attempt ran"))
}

/**
 * The suspending version. Two differences that matter:
 * - `delay` does not block a thread, so retries cost nothing while waiting;
 * - `CancellationException` must never be retried, or a cancelled scope keeps working.
 */
suspend fun <T> retrySuspending(
    policy: RetryPolicy = RetryPolicy(),
    isRetryable: (Throwable) -> Boolean = { true },
    random: Random = Random.Default,
    block: suspend (attempt: Int) -> T,
): RetryOutcome<T> {
    var lastError: Throwable? = null

    for (attempt in 1..policy.maxAttempts) {
        try {
            return RetryOutcome.Success(block(attempt), attempt)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!isRetryable(e)) return RetryOutcome.NotRetryable(e)
            lastError = e
            if (attempt < policy.maxAttempts) {
                kotlinx.coroutines.delay(policy.delayFor(attempt, random))
            }
        }
    }

    return RetryOutcome.Exhausted(policy.maxAttempts, lastError ?: IllegalStateException("no attempt ran"))
}

/**
 * A realistic retryability classifier. The shape matters more than the specific types: **decide
 * explicitly**, because the default of "retry everything" retries validation errors forever and the
 * default of "retry nothing" makes the pattern pointless.
 */
class HttpStatusException(val status: Int, message: String) : RuntimeException(message)

val defaultRetryable: (Throwable) -> Boolean = { error ->
    when (error) {
        is HttpStatusException -> error.status == 429 || error.status in 500..599
        is java.io.IOException -> true // connection reset, timeout — transient by nature
        is IllegalArgumentException -> false // a bad request will be bad next time too
        else -> false
    }
}

/**
 * ## Retry budget
 *
 * A subtlety worth knowing: per-call retry limits do not bound *system-wide* amplification. If every
 * one of 100 clients retries 3×, a struggling service sees 4× its normal load exactly when it can
 * least handle it.
 *
 * Mature systems add a **retry budget** — a token bucket that permits retries only while they stay
 * below, say, 10% of total requests. Combine with a circuit breaker (`production/circuitbreaker`),
 * which stops retrying altogether once a dependency is clearly down.
 *
 * ## Spring
 *
 * `spring-retry` gives you `@Retryable`/`@Recover` with the same semantics via AOP. Remember the
 * proxy caveat: self-invocation bypasses it (see `structural/proxy`).
 */
