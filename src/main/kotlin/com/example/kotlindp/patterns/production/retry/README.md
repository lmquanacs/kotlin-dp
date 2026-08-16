# Retry with exponential backoff and jitter

The most-written and most-often-wrong resilience pattern.

**A naive retry loop makes outages worse.** Every client retries at the same moment and the
recovering service is knocked over again by the synchronised wave. That's a *retry storm*, and jitter
is what prevents it.

## Four decisions make a policy correct

1. **Which failures are retryable.** Retrying a 400 is pointless; retrying a non-idempotent write is
   dangerous.
2. **How long to wait.** Exponential, so load falls as failures persist.
3. **Jitter.** Randomised, so clients de-synchronise.
4. **A cap** — on both per-attempt delay and *total elapsed* time.

```kotlin
fun delayFor(attempt: Int): Long {
    val exponential = initialDelayMs * multiplier.pow(attempt - 1)
    val capped = min(exponential, maxDelayMs)
    val jitter = capped * jitterFactor
    return (capped - jitter + random.nextDouble() * jitter * 2).toLong()
}
```

Without jitter, N clients that failed together retry together *forever*. With it they spread out
within a round or two.

## Idempotency is the caller's responsibility

**Retrying `POST /charge` can double-charge a customer.** Retry reads freely; retry writes only with
an idempotency key.

## Classify failures explicitly

```kotlin
when (error) {
    is HttpStatusException -> error.status == 429 || error.status in 500..599
    is IOException -> true                      // transient by nature
    is IllegalArgumentException -> false        // bad request will be bad next time too
    else -> false
}
```

The default of "retry everything" retries validation errors forever; "retry nothing" makes the
pattern pointless. Decide.

## Two implementation details

- **Inject `sleep` and `now`.** A retry helper that can only be tested in real time will not be
  tested.
- **In the suspending version, never retry `CancellationException`** — rethrow it, or a cancelled
  scope keeps working. And `delay` doesn't block a thread, so waiting is free.

## Retry budget — the part most implementations miss

Per-call limits don't bound *system-wide* amplification. If 100 clients each retry 3×, a struggling
service sees 4× normal load exactly when it can least handle it.

Mature systems add a **retry budget**: a token bucket permitting retries only while they stay below
~10% of total requests. Combine with a circuit breaker, which stops retrying altogether once a
dependency is clearly down.

## Spring

`spring-retry` gives `@Retryable`/`@Recover` with the same semantics via AOP. Remember the proxy
caveat — self-invocation bypasses it.
