# Cache-Aside (lazy loading)

The application checks the cache; on a miss it loads from the source and populates the cache. The
cache doesn't know about the database, and the database doesn't know about the cache.

The most common caching pattern in production. **The hard parts are not the lookup** — they're
eviction, invalidation, and the thundering herd.

## Four decisions, each fixing a specific failure

**Bounded size.** An unbounded cache is a memory leak with a friendly name.

**TTL.** Without expiry, stale data lives forever. With it, staleness has a *known bound* — which is
the property you actually promise the rest of the system.

**Single-flight.** On a miss for a hot key, N concurrent requests all hit the database at once — the
*cache stampede*. A per-key lock collapses them into one load. This is the single most valuable line
in the implementation:

```kotlin
val lock = loadLocks.computeIfAbsent(key) { ReentrantLock() }
return lock.withLock {
    readFresh(key)?.let { return@withLock it.value }   // double-check
    loader(key).also { if (it != null) put(key, it) }
}
```

**Negative caching is opt-in.** Caching "not found" prevents repeated lookups for missing keys (a
cheap DoS vector), but a newly-created entity then stays invisible for a full TTL. Make it a
conscious choice.

## Invalidate, don't update

The write path *must* invalidate, or readers serve stale data for a full TTL after every update.

And invalidate rather than writing the new value in: writing looks better and is racier — two
concurrent writers can leave the cache holding the loser's value forever, while invalidation
converges on whatever the next read finds. Order matters too: **write first, then invalidate.**
Invalidating first leaves a window where a concurrent read repopulates with the old value.

## The other caching patterns, one line each

- **Read-through** — the cache loads on a miss. Same effect, loader lives in the cache.
- **Write-through** — cache and database together. Consistent, slower writes.
- **Write-behind** — cache now, database asynchronously. Fast, loses data on a crash.
- **Refresh-ahead** — refresh hot entries before expiry, so no request pays the load cost.

## What actually goes wrong

1. **Unbounded growth.** Always cap.
2. **Stampede.** Single-flight + **jittered TTLs**, so entries loaded together don't all expire
   together.
3. **Stale after write.** Invalidate on the write path.
4. **Distributed inconsistency.** Per-instance caches diverge across replicas. Either accept a
   bounded staleness window (usually fine) or use Redis and accept the network hop and a new
   dependency.
5. **Caching the wrong thing.** A cache in front of a fast query with a 5% hit rate is pure overhead.
   Measure the hit rate — that's what `CacheStats` is for — and delete caches that don't earn their
   place.

## In practice

Use Caffeine (`@Cacheable` with a Caffeine backend in Spring): proper eviction, refresh, async
loading, metrics. This class exists to make the failure modes visible.
