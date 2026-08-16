# Coroutine patterns

The organising idea behind all of them is **structured concurrency**: every coroutine has a parent
scope, the scope doesn't complete until its children do, and cancelling the scope cancels the
children. That one rule is why leaks and orphaned work are rare in Kotlin compared with raw threads
or unmanaged futures.

## `coroutineScope` vs `supervisorScope`

| | on child failure |
|---|---|
| `coroutineScope` | cancels siblings, rethrows |
| `supervisorScope` | siblings continue |

Use `coroutineScope` when results are only meaningful together; `supervisorScope` for independent
work where partial results are useful — a dashboard where one failing widget shouldn't blank the
page. **This is a product decision, not a technical one.**

Note that two sequential `await`s are *not* equivalent to `coroutineScope`: they leave the second
call running after the first fails.

## Bounded parallelism

Launching 10 000 coroutines against a service with a 20-connection pool is a self-inflicted outage.

```kotlin
val permits = Semaphore(concurrency)
items.map { async { permits.withPermit { transform(it) } } }.awaitAll()
```

## Cancellation — the two things people get wrong

**1. Cancellation is cooperative.** A coroutine is only cancellable at a suspension point. A tight
CPU loop with no `suspend` call runs to completion regardless — "cancel the job" silently does
nothing. Insert `ensureActive()` or `yield()`.

**2. Never swallow `CancellationException`.** `catch (e: Exception)` catches it, which breaks
structured concurrency: the coroutine keeps going after its scope was cancelled and the parent waits
forever. Rethrow explicitly:

```kotlin
try { ... }
catch (e: CancellationException) { throw e }
catch (e: Exception) { Result.failure(e) }
```

## Timeouts

`withTimeout` throws; `withTimeoutOrNull` returns null — prefer the latter when a timeout is an
expected outcome rather than an error.

**A timeout on every external call is not optional.** Without one, a hung dependency exhausts your
thread pool and takes the service down.

## Flow

Cold: nothing runs until collected, and each collector triggers its own execution. That's the
difference from `SharedFlow`/`StateFlow` (hot), and why a `Flow` returned from a repository does no
work until consumed.

- `buffer` — decouples producer and consumer so a slow collector doesn't stall the producer
- `retryWhen` — re-subscribe on failure (never retry `CancellationException`)
- `catch` — handles **upstream** errors only, not the collector's own. That separation is deliberate.

## Racing

`select` takes whichever branch finishes first. The important part is the `finally` that cancels the
losers — without it, a "fastest wins" helper leaks work on every call.

## Dispatchers

`Dispatchers.Default` = CPU-bound, sized to core count. `Dispatchers.IO` = blocking I/O, much larger
pool. **Blocking a `Default` thread starves computation process-wide**, so any JDBC call, file read,
or `Thread.sleep` belongs in `withContext(Dispatchers.IO)`.

## Spring Boot 2.5 note

Spring MVC is blocking. A `suspend` controller function requires WebFlux; on MVC, launch a
per-request scope and block for the result, or move to WebFlux if the workload is genuinely I/O-bound.

**Never `GlobalScope`** — work launched there outlives the request, ignores cancellation, and is the
coroutine equivalent of a thread leak.

## The five rules

1. Never `GlobalScope`. Use a scope tied to a lifecycle.
2. Timeout every external call.
3. Never swallow `CancellationException`.
4. Bound concurrency against limited resources.
5. `coroutineScope` when results are needed together; `supervisorScope` when independent.
