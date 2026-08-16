# Circuit Breaker

**Intent** — stop calling a dependency that is clearly failing, so you fail fast instead of piling
threads onto a service that can't answer.

This is the pattern that prevents **cascading failure**. Without it, one slow dependency consumes
every thread in the calling service, which then stops answering *its* callers, and the outage spreads
upstream. Retry alone makes this worse; the breaker bounds it.

## It's a State machine

```
  CLOSED ──failure threshold──► OPEN ──after cooldown──► HALF_OPEN
     ▲                                                       │
     └──────────── enough probe successes ───────────────────┘
                   (any probe failure → OPEN, full cooldown)
```

Implemented exactly as `behavioral/state` describes: sealed states, transitions as an exhaustive
`when`.

## Kotlin implementation notes

**State in an `AtomicReference`, advanced with `updateAndGet`.** A breaker is called from every
request thread at once, and a non-atomic counter under-counts failures precisely when it matters
most. This gives lock-free atomic transitions.

**Limit half-open probes.** Half-open is a *test*, not a resumption — admit one or two calls and keep
everyone else failing fast.

## Tuning

- **failureThreshold** — too low and transient blips open the circuit; too high and it opens after the
  damage is done. Consecutive-failure counting is simple; a **rolling failure rate** over a window is
  better for high-traffic services, because 5 failures out of 10 000 requests is not an outage.
- **cooldown** — long enough for real recovery, short enough not to extend the outage. Seconds, not
  minutes.
- **`isFailure`** — the most commonly misconfigured knob. **Timeouts and 5xx count; 404 and 400 do
  not.** Counting a 404 opens the breaker for a perfectly healthy service.

## Always pair it with a fallback

Failing fast is only an improvement if the caller has something to say. Cached data, a default, or a
degraded response all beat an exception.

## Where the state lives

This breaker is per-instance. With N replicas you get N independent breakers — usually *fine* (each
instance measures what it experiences) and occasionally not. A shared breaker needs shared state and
then has its own availability problem. Prefer per-instance.

## In practice

**Use Resilience4j, not this class.** It adds rolling windows, metrics, bulkheads and rate limiters,
and is well tested. This implementation exists to make the state machine legible — because tuning a
library you don't understand is how breakers end up permanently open.
