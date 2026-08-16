package com.example.kotlindp.patterns.production.objectpool

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * # Object Pool
 *
 * Reuse expensive-to-create objects instead of allocating new ones.
 *
 * **Start with the warning.** Object pooling was widely used in early Java and is now usually
 * counterproductive: modern JVM allocation is a pointer bump, and generational GC collects
 * short-lived objects almost for free. Pooling plain objects makes code slower *and* introduces
 * lifecycle bugs.
 *
 * Pooling is right for objects whose cost is **not allocation** but *acquisition* — a TCP handshake,
 * a TLS negotiation, an OS thread, a database session. That is why connection pools and thread pools
 * exist and generic object pools mostly do not.
 */

interface Poolable {
    /** Cheap liveness check. A pool that hands out dead connections is worse than no pool. */
    fun isValid(): Boolean
    fun close()
}

class PooledConnection(val id: Int) : Poolable {
    var closed = false
        private set

    var uses = 0
        private set

    fun query(sql: String): String {
        check(!closed) { "connection $id is closed" }
        uses++
        return "conn-$id: $sql"
    }

    override fun isValid(): Boolean = !closed
    override fun close() {
        closed = true
    }
}

class PoolExhaustedException(message: String) : RuntimeException(message)

/**
 * A bounded blocking pool.
 *
 * The design decisions that make it safe:
 *
 * - **Bounded.** [ArrayBlockingQueue] plus a `created` counter caps total objects. An unbounded pool
 *   is not a pool — under load it becomes an unbounded resource leak.
 * - **A borrow timeout.** Waiting forever for a permit turns resource exhaustion into a total hang
 *   with no diagnostic. Failing after a bounded wait produces a stack trace that names the pool.
 * - **Validate on return.** Broken objects are discarded rather than recycled, and the counter is
 *   decremented so a replacement can be created.
 * - **Lazy creation.** Objects are created on demand up to the cap, not all at startup.
 */
class ObjectPool<T : Poolable>(
    private val maxSize: Int,
    private val borrowTimeoutMs: Long = 5_000,
    private val factory: () -> T,
) {
    private val available = ArrayBlockingQueue<T>(maxSize)
    private val created = AtomicInteger(0)
    private val borrowed = AtomicInteger(0)

    init {
        require(maxSize > 0) { "maxSize must be > 0" }
    }

    fun borrow(): T {
        // Fast path: something idle and healthy.
        while (true) {
            val pooled = available.poll() ?: break
            if (pooled.isValid()) {
                borrowed.incrementAndGet()
                return pooled
            }
            created.decrementAndGet() // discard the dead one, freeing a slot
        }

        // Room to grow?
        while (true) {
            val current = created.get()
            if (current >= maxSize) break
            if (created.compareAndSet(current, current + 1)) {
                borrowed.incrementAndGet()
                return factory()
            }
        }

        // At capacity: wait for a return, but not forever.
        val waited = available.poll(borrowTimeoutMs, TimeUnit.MILLISECONDS)
            ?: throw PoolExhaustedException("no object available within ${borrowTimeoutMs}ms (max=$maxSize)")
        borrowed.incrementAndGet()
        return waited
    }

    fun release(item: T) {
        borrowed.decrementAndGet()
        if (!item.isValid()) {
            created.decrementAndGet()
            return
        }
        // offer, not put: if the queue is somehow full, drop rather than block a returning caller.
        if (!available.offer(item)) {
            created.decrementAndGet()
            item.close()
        }
    }

    fun stats(): PoolStats = PoolStats(
        created = created.get(),
        idle = available.size,
        inUse = borrowed.get(),
        max = maxSize,
    )

    fun drain() {
        while (true) {
            val item = available.poll() ?: break
            item.close()
            created.decrementAndGet()
        }
    }
}

data class PoolStats(val created: Int, val idle: Int, val inUse: Int, val max: Int)

/**
 * **The API that prevents the pattern's defining bug.**
 *
 * Every object pool ever written has leaked because someone returned early, or threw, and never
 * called `release`. Do not expose `borrow`/`release` as the primary API — expose a scoped function
 * with `try/finally`, so returning the object is not something a caller can forget.
 *
 * `inline` here means the lambda can `return` from the caller and the object is still released.
 */
inline fun <T : Poolable, R> ObjectPool<T>.use(block: (T) -> R): R {
    val item = borrow()
    return try {
        block(item)
    } finally {
        release(item)
    }
}

/**
 * ## When to pool
 *
 * - Database connections — **use HikariCP**, do not write this.
 * - Threads — `ExecutorService` / coroutine dispatchers.
 * - HTTP connections — the client's own pool (OkHttp, Apache).
 * - Large buffers where allocation genuinely shows up in a profile.
 *
 * ## When not to
 *
 * Anything cheap to construct. A pool adds synchronisation, a validity protocol, a leak risk, and
 * state that outlives a single use — for objects the JVM would allocate in nanoseconds.
 *
 * ## The subtle hazard
 *
 * A pooled object carries state between uses. A connection left inside an open transaction, a buffer
 * still holding the previous caller's bytes, a `ThreadLocal` set by whoever borrowed it last — these
 * are the bugs that make pooling expensive to get right. Reset on release, and validate on borrow.
 */
