package com.example.kotlindp.patterns.production.cacheaside

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * # Cache-Aside (lazy loading)
 *
 * The application checks the cache; on a miss it loads from the source and populates the cache. The
 * cache does not know about the database, and the database does not know about the cache.
 *
 * This is the most common caching pattern in production, and the three things that make it hard are
 * not the lookup: **eviction, invalidation, and the thundering herd.**
 */

data class CacheStats(val hits: Long, val misses: Long, val evictions: Long, val loads: Long) {
    val hitRate: Double get() = if (hits + misses == 0L) 0.0 else hits.toDouble() / (hits + misses)
}

/**
 * A bounded, TTL-aware cache with single-flight loading.
 *
 * Deliberate choices, each fixing a specific production failure:
 *
 * - **Bounded size.** An unbounded cache is a memory leak with a friendly name. Eviction here is
 *   naive FIFO-ish; real caches use LRU/LFU/W-TinyLFU (Caffeine).
 * - **TTL.** Without expiry, stale data lives forever. With it, staleness has a known bound — which
 *   is the property you actually promise to the rest of the system.
 * - **Single-flight.** On a miss for a hot key, N concurrent requests would all hit the database
 *   simultaneously — the *thundering herd* / cache stampede. A per-key lock collapses them into one
 *   load, and it is the single most valuable line in this class.
 * - **Negative caching is opt-in.** Caching "not found" prevents repeated lookups for missing keys
 *   (a cheap DoS vector), but it also means a newly-created entity stays invisible for a TTL. Make
 *   that a conscious choice, not a default.
 */
class CacheAside<K : Any, V : Any>(
    private val maxSize: Int = 1_000,
    private val ttlMs: Long = 60_000,
    private val cacheNegative: Boolean = false,
    private val now: () -> Long = System::currentTimeMillis,
    private val loader: (K) -> V?,
) {
    private data class Entry<V>(val value: V?, val expiresAt: Long)

    private val entries = ConcurrentHashMap<K, Entry<V>>()
    private val insertionOrder = java.util.concurrent.ConcurrentLinkedQueue<K>()
    private val loadLocks = ConcurrentHashMap<K, ReentrantLock>()

    private val hits = AtomicLong()
    private val misses = AtomicLong()
    private val evictions = AtomicLong()
    private val loads = AtomicLong()

    init {
        require(maxSize > 0) { "maxSize must be > 0" }
        require(ttlMs > 0) { "ttlMs must be > 0" }
    }

    fun get(key: K): V? {
        readFresh(key)?.let { entry ->
            hits.incrementAndGet()
            return entry.value
        }

        misses.incrementAndGet()

        // Single-flight: only one thread per key performs the load.
        val lock = loadLocks.computeIfAbsent(key) { ReentrantLock() }
        return lock.withLock {
            // Double-check — another thread may have populated it while we waited for the lock.
            readFresh(key)?.let { return@withLock it.value }

            loads.incrementAndGet()
            val loaded = loader(key)

            if (loaded != null || cacheNegative) put(key, loaded)
            loaded
        }.also {
            loadLocks.remove(key)
        }
    }

    private fun readFresh(key: K): Entry<V>? {
        val entry = entries[key] ?: return null
        if (now() >= entry.expiresAt) {
            entries.remove(key)
            return null
        }
        return entry
    }

    private fun put(key: K, value: V?) {
        if (entries.put(key, Entry(value, now() + ttlMs)) == null) {
            insertionOrder += key
        }
        evictIfNeeded()
    }

    private fun evictIfNeeded() {
        while (entries.size > maxSize) {
            val oldest = insertionOrder.poll() ?: break
            if (entries.remove(oldest) != null) evictions.incrementAndGet()
        }
    }

    /**
     * Explicit invalidation on write. This is the half of cache-aside that gets forgotten: the write
     * path must invalidate, or readers serve stale data for a full TTL after every update.
     *
     * **Invalidate, do not update.** Writing the new value into the cache looks better and is
     * racier — two concurrent writers can leave the cache holding the loser's value forever, while
     * invalidation converges on whatever the next read finds in the database.
     */
    fun invalidate(key: K) {
        entries.remove(key)
    }

    fun invalidateAll() {
        entries.clear()
        insertionOrder.clear()
    }

    fun stats(): CacheStats = CacheStats(hits.get(), misses.get(), evictions.get(), loads.get())

    fun size(): Int = entries.size
}

/**
 * Write-through helper: perform the write, then invalidate. Order matters — invalidating *before*
 * the write leaves a window where a concurrent read repopulates the cache with the old value.
 */
class CachedRepository<K : Any, V : Any>(
    private val cache: CacheAside<K, V>,
    private val write: (K, V) -> Unit,
) {
    fun save(key: K, value: V) {
        write(key, value)
        cache.invalidate(key)
    }

    fun find(key: K): V? = cache.get(key)
}

/**
 * ## The other caching patterns, in one line each
 *
 * - **Read-through** — the cache itself loads on a miss. Same effect, the loader lives in the cache.
 * - **Write-through** — write to cache and database together. Consistent, slower writes.
 * - **Write-behind** — write to cache, flush to the database asynchronously. Fast, and it loses data
 *   on a crash.
 * - **Refresh-ahead** — refresh hot entries before they expire, so no request pays the load cost.
 *
 * ## What actually goes wrong
 *
 * 1. **Unbounded growth.** Always cap. Always.
 * 2. **Stampede.** Single-flight, as above. Add jittered TTLs so entries loaded together do not all
 *    expire together.
 * 3. **Stale after write.** Invalidate on the write path.
 * 4. **Distributed inconsistency.** Per-instance caches diverge across replicas. Either accept a
 *    bounded staleness window (usually fine) or use a shared cache (Redis) and accept the network
 *    hop and a new dependency.
 * 5. **Caching the wrong thing.** A cache in front of a fast query with a 5% hit rate is pure
 *    overhead. Measure the hit rate — that is what [CacheStats] is for — and delete caches that do
 *    not earn their place.
 *
 * ## In practice
 *
 * Use Caffeine (`@Cacheable` with a Caffeine backend in Spring). It has proper eviction, refresh,
 * async loading and metrics. This class exists to make the failure modes visible.
 */
