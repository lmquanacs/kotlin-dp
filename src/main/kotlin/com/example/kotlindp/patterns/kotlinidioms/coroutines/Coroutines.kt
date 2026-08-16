package com.example.kotlindp.patterns.kotlinidioms.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * # Coroutine patterns
 *
 * The concurrency patterns that recur in production Kotlin. The organising idea behind all of them
 * is **structured concurrency**: every coroutine has a parent scope, the scope does not complete
 * until its children do, and cancelling the scope cancels the children.
 *
 * That single rule is what makes leaks and orphaned work rare in Kotlin compared with raw threads
 * or unmanaged futures.
 */

// ---------------------------------------------------------------------------------------------
// 1. Parallel decomposition.
// ---------------------------------------------------------------------------------------------

data class Profile(val user: String, val orders: Int, val creditScore: Int)

/**
 * `coroutineScope` waits for every child and — crucially — **cancels the siblings if one fails**,
 * then rethrows. Two sequential `await`s would leave the second call running after the first failed.
 *
 * Note this is a suspend function that starts no scope of its own: it inherits the caller's, which
 * is what makes cancellation propagate all the way from the request boundary.
 */
suspend fun loadProfile(
    user: String,
    fetchOrders: suspend (String) -> Int,
    fetchScore: suspend (String) -> Int,
): Profile = coroutineScope {
    val orders = async { fetchOrders(user) }
    val score = async { fetchScore(user) }
    Profile(user, orders.await(), score.await())
}

/**
 * `supervisorScope` is the opposite policy: one child failing does **not** cancel its siblings.
 *
 * Use it for independent work where partial results are useful — a dashboard where one failing
 * widget should not blank the page. Use `coroutineScope` when the results are only meaningful
 * together.
 *
 * Choosing between them is a *product* decision, not a technical one.
 */
suspend fun <T> fetchAllTolerantly(
    keys: List<String>,
    fetch: suspend (String) -> T,
): Map<String, Result<T>> = supervisorScope {
    keys.associateWith { key ->
        async { runCatching { fetch(key) } }
    }.mapValues { (_, deferred) -> deferred.await() }
}

/**
 * Bounded parallelism. Launching 10 000 coroutines against a service with a 20-connection pool is a
 * self-inflicted outage; [Semaphore] caps in-flight work while keeping the code shape unchanged.
 */
suspend fun <T, R> mapConcurrently(
    items: List<T>,
    concurrency: Int = 8,
    transform: suspend (T) -> R,
): List<R> = coroutineScope {
    val permits = Semaphore(concurrency)
    items.map { item ->
        async { permits.withPermit { transform(item) } }
    }.awaitAll()
}

// ---------------------------------------------------------------------------------------------
// 2. Timeouts and cancellation.
// ---------------------------------------------------------------------------------------------

/**
 * `withTimeout` throws `TimeoutCancellationException`; `withTimeoutOrNull` returns `null`. Prefer
 * the latter when a timeout is an expected outcome rather than an error.
 *
 * A timeout on *every* external call is not optional in production — without one, a hung dependency
 * exhausts your thread pool and takes the service down with it.
 */
suspend fun <T> callWithTimeout(timeoutMs: Long, block: suspend () -> T): T =
    withTimeout(timeoutMs) { block() }

suspend fun <T> callOrNull(timeoutMs: Long, block: suspend () -> T): T? =
    withTimeoutOrNull(timeoutMs) { block() }

/**
 * **Cancellation is cooperative.** A coroutine is only cancellable at a suspension point; a tight
 * CPU loop with no `suspend` call will run to completion regardless.
 *
 * `ensureActive()` (or `yield()`) inserts the check. Without it, "cancel the job" silently does
 * nothing — one of the most common coroutine misunderstandings.
 */
suspend fun computeCancellable(iterations: Int, work: (Int) -> Unit) = coroutineScope {
    repeat(iterations) { i ->
        ensureActive() // throws CancellationException if the scope was cancelled
        work(i)
    }
}

/**
 * **Never swallow `CancellationException`.** `catch (e: Exception)` catches it, which breaks
 * structured concurrency: the coroutine keeps going after its scope was cancelled, and the parent
 * waits forever.
 *
 * Rethrow it explicitly — this is the correct shape for any broad catch inside a coroutine.
 */
suspend fun <T> safely(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

// ---------------------------------------------------------------------------------------------
// 3. Flow — asynchronous streams.
// ---------------------------------------------------------------------------------------------

/**
 * A `Flow` is cold: nothing runs until it is collected, and each collector triggers its own
 * execution. That is the difference from `SharedFlow`/`StateFlow` (hot), and the reason a `Flow`
 * returned from a repository does no work until someone consumes it.
 */
fun pagesOf(pageSize: Int, fetch: suspend (Int) -> List<String>): Flow<String> = flow {
    var page = 0
    while (true) {
        val batch = fetch(page)
        if (batch.isEmpty()) break
        batch.forEach { emit(it) }
        if (batch.size < pageSize) break
        page++
    }
}

/**
 * `buffer` decouples producer and consumer so a slow collector does not stall the producer;
 * `retryWhen` re-subscribes on failure with a delay; `catch` handles upstream errors only (not the
 * collector's own), which is exactly the separation you want.
 */
fun <T> Flow<T>.resilient(maxRetries: Long, delayMs: Long, onError: (Throwable) -> Unit): Flow<T> =
    this.retryWhen { cause, attempt ->
        if (cause is CancellationException) return@retryWhen false // never retry cancellation
        val retry = attempt < maxRetries
        if (retry) delay(delayMs * (attempt + 1)) else onError(cause)
        retry
    }.catch { onError(it) }.buffer()

fun exampleTransform(source: Flow<Int>): Flow<String> =
    source.map { "value-$it" }.buffer()

// ---------------------------------------------------------------------------------------------
// 4. Racing, and the "first result wins" pattern.
// ---------------------------------------------------------------------------------------------

/**
 * `select` takes whichever branch completes first. The important part is the `finally`: the losing
 * coroutines must be cancelled, or a "fastest wins" helper quietly leaks work on every call.
 */
suspend fun <T> firstOf(vararg blocks: suspend () -> T): T = coroutineScope {
    val deferreds: List<Deferred<T>> = blocks.map { block -> async { block() } }
    try {
        select { deferreds.forEach { d -> d.onAwait { it } } }
    } finally {
        deferreds.forEach { it.cancel() }
    }
}

/**
 * ## Dispatchers, in one paragraph
 *
 * `Dispatchers.Default` is CPU-bound work, sized to the core count. `Dispatchers.IO` is blocking I/O,
 * with a much larger pool. Blocking a `Default` thread starves computation everywhere in the
 * process — so any JDBC call, file read, or `Thread.sleep` belongs on `IO`, wrapped in
 * `withContext(Dispatchers.IO)`.
 *
 * ## Spring Boot 2.5 note
 *
 * Spring MVC is blocking. A `suspend` controller function requires WebFlux; on MVC, launch a scope
 * per request and block for its result, or move to WebFlux if the workload is genuinely I/O-bound.
 * Do not use `GlobalScope` — work launched there outlives the request, ignores cancellation, and is
 * the coroutine equivalent of a thread leak.
 *
 * ## The five rules
 *
 * 1. Never `GlobalScope`. Use a scope tied to a lifecycle.
 * 2. Timeout every external call.
 * 3. Never swallow `CancellationException`.
 * 4. Bound concurrency against limited resources.
 * 5. `coroutineScope` when results are needed together; `supervisorScope` when they are independent.
 */
@Suppress("unused")
private fun scopeDocs(scope: CoroutineScope) = scope
