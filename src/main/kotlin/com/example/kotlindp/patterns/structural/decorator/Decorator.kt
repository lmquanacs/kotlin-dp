package com.example.kotlindp.patterns.structural.decorator

/**
 * # Decorator
 *
 * Add behaviour to an object by wrapping it, without touching its class and without a subclass per
 * combination.
 *
 * This is the pattern Kotlin's `by` delegation was practically designed for. In Java a decorator
 * must hand-write a forwarding method for every interface member; in Kotlin `: Service by inner`
 * generates all of them, so the decorator's body contains *only the behaviour it adds*.
 */

data class Query(val sql: String, val params: List<Any?> = emptyList())

interface QueryExecutor {
    fun execute(query: Query): List<Map<String, Any?>>
    fun healthy(): Boolean
}

/** The component being decorated. */
class RealQueryExecutor : QueryExecutor {
    var invocations: Int = 0
        private set

    override fun execute(query: Query): List<Map<String, Any?>> {
        invocations++
        if (query.sql.contains("BOOM")) throw IllegalStateException("SQL error")
        return listOf(mapOf("sql" to query.sql, "params" to query.params.size))
    }

    override fun healthy(): Boolean = true
}

// ---------------------------------------------------------------------------------------------
// Decorators. Each one overrides exactly the member it cares about; `by` forwards the rest.
// ---------------------------------------------------------------------------------------------

/**
 * Note what is *not* here: no `override fun healthy()`. `by inner` supplies it. Add a fourth method
 * to [QueryExecutor] tomorrow and every decorator keeps compiling and keeps forwarding correctly.
 */
class LoggingExecutor(
    private val inner: QueryExecutor,
    private val sink: MutableList<String> = mutableListOf(),
) : QueryExecutor by inner {

    override fun execute(query: Query): List<Map<String, Any?>> {
        sink += "→ ${query.sql}"
        val result = runCatching { inner.execute(query) }
        sink += result.fold(
            onSuccess = { "← ${it.size} row(s)" },
            onFailure = { "✗ ${it.message}" },
        )
        return result.getOrThrow()
    }

    fun log(): List<String> = sink.toList()
}

/** Timing decorator. Cross-cutting concerns like this are the canonical decorator use. */
class TimingExecutor(
    private val inner: QueryExecutor,
    private val clock: () -> Long = System::nanoTime,
) : QueryExecutor by inner {

    var lastDurationNanos: Long = 0
        private set

    override fun execute(query: Query): List<Map<String, Any?>> {
        val start = clock()
        try {
            return inner.execute(query)
        } finally {
            // `finally` so a failed query is still timed — a detail that is easy to get wrong.
            lastDurationNanos = clock() - start
        }
    }
}

/**
 * Caching decorator. Illustrates a decorator that can *skip* the delegate entirely.
 *
 * This one is order-sensitive: put it inside [TimingExecutor] and you time cache hits (near zero);
 * put it outside and you time only real executions. Decorator order is semantics, not style.
 */
class CachingExecutor(private val inner: QueryExecutor) : QueryExecutor by inner {

    private val cache = mutableMapOf<Query, List<Map<String, Any?>>>()

    override fun execute(query: Query): List<Map<String, Any?>> =
        cache.getOrPut(query) { inner.execute(query) }

    fun cachedQueries(): Int = cache.size
}

/** Retry decorator — the same shape, with a loop. */
class RetryingExecutor(
    private val inner: QueryExecutor,
    private val attempts: Int = 3,
) : QueryExecutor by inner {

    init {
        require(attempts >= 1) { "attempts must be >= 1" }
    }

    override fun execute(query: Query): List<Map<String, Any?>> {
        var last: Throwable? = null
        repeat(attempts) {
            try {
                return inner.execute(query)
            } catch (e: IllegalStateException) {
                last = e
            }
        }
        throw IllegalStateException("failed after $attempts attempts", last)
    }
}

// ---------------------------------------------------------------------------------------------
// Composing decorators.
// ---------------------------------------------------------------------------------------------

/**
 * Stacks read **inside-out**: the last wrapper applied is the outermost, and runs first.
 *
 * ```kotlin
 * LoggingExecutor(TimingExecutor(CachingExecutor(RealQueryExecutor())))
 * // log → time → cache → real
 * ```
 *
 * A `fold` over a list of wrapper functions makes the order explicit and configurable, which is how
 * you would drive this from configuration.
 */
fun QueryExecutor.decorateWith(
    vararg wrappers: (QueryExecutor) -> QueryExecutor,
): QueryExecutor = wrappers.fold(this) { acc, wrap -> wrap(acc) }

/**
 * ## Kotlin alternatives worth knowing
 *
 * For a *single-method* interface, a higher-order function is a lighter decorator than a class:
 *
 * ```kotlin
 * typealias Handler = (Query) -> List<Map<String, Any?>>
 * fun Handler.logged(): Handler = { q -> println(q.sql); this(q) }
 * ```
 *
 * And in Spring, the framework decorates for you: `@Transactional`, `@Cacheable`, `@Retryable` and
 * `@Async` are all AOP proxies — decorators generated at runtime. Hand-write decorators for domain
 * behaviour; use the annotations for infrastructure.
 *
 * One caveat on `by` delegation: the delegate reference is captured at construction. If a decorated
 * method calls another method on `this`, the call goes to the *delegate's* implementation, not back
 * through the decorator. Delegation is not inheritance, and there is no `super` dispatch.
 */
