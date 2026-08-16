package com.example.kotlindp.patterns

import com.example.kotlindp.patterns.production.cacheaside.CacheAside
import com.example.kotlindp.patterns.production.cacheaside.CachedRepository
import com.example.kotlindp.patterns.production.circuitbreaker.BreakerConfig
import com.example.kotlindp.patterns.production.circuitbreaker.BreakerState
import com.example.kotlindp.patterns.production.circuitbreaker.CircuitBreaker
import com.example.kotlindp.patterns.production.circuitbreaker.CircuitOpenException
import com.example.kotlindp.patterns.production.circuitbreaker.executeOrElse
import com.example.kotlindp.patterns.production.objectpool.ObjectPool
import com.example.kotlindp.patterns.production.objectpool.PoolExhaustedException
import com.example.kotlindp.patterns.production.objectpool.PooledConnection
import com.example.kotlindp.patterns.production.objectpool.use
import com.example.kotlindp.patterns.production.retry.HttpStatusException
import com.example.kotlindp.patterns.production.retry.RetryOutcome
import com.example.kotlindp.patterns.production.retry.RetryPolicy
import com.example.kotlindp.patterns.production.retry.defaultRetryable
import com.example.kotlindp.patterns.production.retry.retrySuspending
import com.example.kotlindp.patterns.production.retry.withRetry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/** Records sleeps instead of performing them, so the retry suite runs in milliseconds. */
private class FakeClock {
    val sleeps = mutableListOf<Long>()
    var nowMs = 0L
    fun sleep(ms: Long) {
        sleeps += ms
        nowMs += ms
    }
}

/** A manually-advanced clock, so breaker cooldowns are tested without waiting for them. */
private class TestClock(var nowMs: Long = 0) {
    fun now() = nowMs
    fun advance(ms: Long) {
        nowMs += ms
    }
}

class ProductionPatternsTest {

    @Nested
    inner class RetryTest {

        @Test
        fun `succeeds on the first attempt without sleeping`() {
            val clock = FakeClock()
            val result = withRetry(sleep = clock::sleep, now = { clock.nowMs }) { "ok" }

            assertEquals(RetryOutcome.Success("ok", 1), result)
            assertTrue(clock.sleeps.isEmpty())
        }

        @Test
        fun `retries until it succeeds and reports the attempt count`() {
            val clock = FakeClock()
            val result = withRetry(sleep = clock::sleep, now = { clock.nowMs }) { attempt ->
                if (attempt < 3) throw java.io.IOException("transient") else "ok"
            }

            assertEquals(RetryOutcome.Success("ok", 3), result)
            assertEquals(2, clock.sleeps.size)
        }

        @Test
        fun `gives up after maxAttempts and keeps the last error`() {
            val clock = FakeClock()
            val result = withRetry(
                policy = RetryPolicy(maxAttempts = 3),
                sleep = clock::sleep,
                now = { clock.nowMs },
            ) { throw java.io.IOException("always down") }

            assertTrue(result is RetryOutcome.Exhausted)
            result as RetryOutcome.Exhausted
            assertEquals(3, result.attempts)
            assertEquals("always down", result.lastError.message)
        }

        @Test
        fun `a non-retryable failure short-circuits immediately`() {
            val attempts = AtomicInteger()
            val result = withRetry(isRetryable = defaultRetryable, sleep = {}) {
                attempts.incrementAndGet()
                throw IllegalArgumentException("bad request")
            }

            assertTrue(result is RetryOutcome.NotRetryable)
            assertEquals(1, attempts.get())
        }

        @Test
        fun `the default classifier retries 429 and 5xx but not 4xx`() {
            assertTrue(defaultRetryable(HttpStatusException(429, "slow down")))
            assertTrue(defaultRetryable(HttpStatusException(503, "unavailable")))
            assertFalse(defaultRetryable(HttpStatusException(404, "not found")))
            assertFalse(defaultRetryable(HttpStatusException(400, "bad request")))
            assertTrue(defaultRetryable(java.io.IOException("reset")))
        }

        @Test
        fun `backoff grows exponentially and is capped`() {
            val policy = RetryPolicy(
                initialDelayMs = 100,
                multiplier = 2.0,
                maxDelayMs = 1_000,
                jitterFactor = 0.0,
            )

            assertEquals(100, policy.delayFor(1))
            assertEquals(200, policy.delayFor(2))
            assertEquals(400, policy.delayFor(3))
            assertEquals(1_000, policy.delayFor(10)) // capped
        }

        @Test
        fun `jitter spreads delays around the exponential value`() {
            val policy = RetryPolicy(initialDelayMs = 1_000, jitterFactor = 0.5, maxDelayMs = 10_000)
            val random = Random(seed = 42)

            val delays = (1..50).map { policy.delayFor(1, random) }

            assertTrue(delays.all { it in 500..1_500 }, "delays outside the jitter window: $delays")
            assertTrue(delays.distinct().size > 1, "jitter produced identical delays")
        }

        @Test
        fun `the elapsed budget stops retrying before maxAttempts`() {
            val clock = FakeClock()
            val result = withRetry(
                policy = RetryPolicy(maxAttempts = 20, initialDelayMs = 1_000, maxElapsedMs = 2_500),
                sleep = clock::sleep,
                now = { clock.nowMs },
            ) { throw java.io.IOException("down") }

            assertTrue(result is RetryOutcome.Exhausted)
            assertTrue(clock.nowMs <= 2_500, "budget exceeded: ${clock.nowMs}ms")
        }

        @Test
        fun `policy rejects nonsensical configuration`() {
            assertThrows(IllegalArgumentException::class.java) { RetryPolicy(maxAttempts = 0) }
            assertThrows(IllegalArgumentException::class.java) { RetryPolicy(jitterFactor = 2.0) }
            assertThrows(IllegalArgumentException::class.java) { RetryPolicy(multiplier = 0.5) }
        }

        @Test
        fun `the suspending variant retries without blocking a thread`() = runBlocking {
            val policy = RetryPolicy(maxAttempts = 4, initialDelayMs = 1, maxDelayMs = 2)
            val result = retrySuspending(policy) { attempt ->
                if (attempt < 3) throw java.io.IOException("transient") else "ok"
            }

            assertEquals(RetryOutcome.Success("ok", 3), result)
        }

        @Test
        fun `the suspending variant never retries cancellation`() = runBlocking {
            val attempts = AtomicInteger()

            assertThrows(kotlinx.coroutines.CancellationException::class.java) {
                runBlocking {
                    retrySuspending(RetryPolicy(maxAttempts = 5, initialDelayMs = 1)) {
                        attempts.incrementAndGet()
                        throw kotlinx.coroutines.CancellationException("cancelled")
                    }
                }
            }
            assertEquals(1, attempts.get())
        }
    }

    @Nested
    inner class CircuitBreakerTest {

        private fun failing(): Nothing = throw java.io.IOException("dependency down")

        @Test
        fun `starts closed and stays closed while calls succeed`() {
            val breaker = CircuitBreaker()

            repeat(20) { assertEquals("ok", breaker.execute { "ok" }) }
            assertEquals(BreakerState.Closed(0), breaker.currentState())
        }

        @Test
        fun `opens after the failure threshold`() {
            val breaker = CircuitBreaker(BreakerConfig(failureThreshold = 3))

            repeat(3) {
                assertThrows(java.io.IOException::class.java) { breaker.execute { failing() } }
            }

            assertTrue(breaker.currentState() is BreakerState.Open)
        }

        @Test
        fun `an open circuit fails fast without touching the dependency`() {
            val calls = AtomicInteger()
            val breaker = CircuitBreaker(BreakerConfig(failureThreshold = 2))

            repeat(2) {
                assertThrows(java.io.IOException::class.java) {
                    breaker.execute { calls.incrementAndGet(); failing() }
                }
            }

            assertThrows(CircuitOpenException::class.java) {
                breaker.execute { "never runs".also { calls.incrementAndGet() } }
            }
            assertEquals(2, calls.get())
        }

        @Test
        fun `a success resets the consecutive failure count`() {
            val breaker = CircuitBreaker(BreakerConfig(failureThreshold = 3))

            assertThrows(java.io.IOException::class.java) { breaker.execute { failing() } }
            assertThrows(java.io.IOException::class.java) { breaker.execute { failing() } }
            breaker.execute { "recovered" }

            assertEquals(BreakerState.Closed(0), breaker.currentState())
        }

        @Test
        fun `it half-opens after the cooldown and closes on enough probe successes`() {
            val clock = TestClock()
            val breaker = CircuitBreaker(
                BreakerConfig(failureThreshold = 1, cooldownMs = 1_000, successThreshold = 2),
                now = clock::now,
            )

            assertThrows(java.io.IOException::class.java) { breaker.execute { failing() } }
            assertTrue(breaker.currentState() is BreakerState.Open)

            clock.advance(1_000)
            breaker.execute { "probe 1" }
            assertTrue(breaker.currentState() is BreakerState.HalfOpen)

            breaker.execute { "probe 2" }
            assertEquals(BreakerState.Closed(0), breaker.currentState())
        }

        @Test
        fun `a failed probe sends it straight back to open`() {
            val clock = TestClock()
            val breaker = CircuitBreaker(
                BreakerConfig(failureThreshold = 1, cooldownMs = 500, successThreshold = 2),
                now = clock::now,
            )

            assertThrows(java.io.IOException::class.java) { breaker.execute { failing() } }
            clock.advance(500)

            assertThrows(java.io.IOException::class.java) { breaker.execute { failing() } }
            assertTrue(breaker.currentState() is BreakerState.Open)
        }

        @Test
        fun `isFailure decides what counts against the breaker`() {
            val breaker = CircuitBreaker(BreakerConfig(failureThreshold = 2))
            val notFound = { throw HttpStatusException(404, "missing") }

            // 404s are valid answers, not dependency failures
            repeat(5) {
                assertThrows(HttpStatusException::class.java) {
                    breaker.execute(isFailure = { it is HttpStatusException && it.status >= 500 }) { notFound() }
                }
            }

            assertEquals(BreakerState.Closed(0), breaker.currentState())
        }

        @Test
        fun `a fallback turns fail-fast into a degraded response`() {
            val breaker = CircuitBreaker(BreakerConfig(failureThreshold = 1))

            // first call fails and opens the breaker — the fallback answers
            assertEquals("cached", breaker.executeOrElse({ "cached" }) { failing() })
            // now the circuit is open, so the fallback answers without touching the dependency
            assertEquals("cached", breaker.executeOrElse({ "cached" }) { "live" })
            assertTrue(breaker.currentState() is BreakerState.Open)
        }

        @Test
        fun `concurrent failures are counted atomically`() {
            val breaker = CircuitBreaker(BreakerConfig(failureThreshold = 50))
            val pool = Executors.newFixedThreadPool(8)
            val latch = CountDownLatch(1)

            repeat(100) {
                pool.submit {
                    latch.await()
                    runCatching { breaker.execute { failing() } }
                }
            }
            latch.countDown()
            pool.shutdown()
            pool.awaitTermination(5, TimeUnit.SECONDS)

            assertTrue(breaker.currentState() is BreakerState.Open)
        }
    }

    @Nested
    inner class ObjectPoolTest {

        private fun pool(maxSize: Int = 2, timeoutMs: Long = 200): ObjectPool<PooledConnection> {
            val ids = AtomicInteger()
            return ObjectPool(maxSize, timeoutMs) { PooledConnection(ids.incrementAndGet()) }
        }

        @Test
        fun `objects are created lazily up to the cap`() {
            val pool = pool(maxSize = 3)
            assertEquals(0, pool.stats().created)

            val a = pool.borrow()
            assertEquals(1, pool.stats().created)
            assertEquals(1, pool.stats().inUse)

            pool.release(a)
            assertEquals(0, pool.stats().inUse)
            assertEquals(1, pool.stats().idle)
        }

        @Test
        fun `a released object is reused rather than recreated`() {
            val pool = pool()

            val first = pool.borrow()
            pool.release(first)
            val second = pool.borrow()

            assertEquals(first.id, second.id)
            assertEquals(1, pool.stats().created)
        }

        @Test
        fun `borrowing past the cap fails after the timeout`() {
            val pool = pool(maxSize = 1, timeoutMs = 50)
            pool.borrow()

            val error = assertThrows(PoolExhaustedException::class.java) { pool.borrow() }
            assertTrue(error.message!!.contains("50ms"))
        }

        @Test
        fun `the scoped use function releases even when the block throws`() {
            val pool = pool(maxSize = 1)

            assertThrows(IllegalStateException::class.java) {
                pool.use { error("caller blew up") }
            }

            assertEquals(0, pool.stats().inUse)
            assertEquals(1, pool.stats().idle)
        }

        @Test
        fun `use returns the block result and the object stays usable`() {
            val pool = pool()

            val result = pool.use { it.query("SELECT 1") }
            assertTrue(result.startsWith("conn-"))

            // the same object comes back, carrying its state — the hazard the README warns about
            pool.use { assertEquals(1, it.uses) }
        }

        @Test
        fun `an invalid object is discarded on release, freeing its slot`() {
            val pool = pool(maxSize = 1)

            val connection = pool.borrow()
            connection.close()
            pool.release(connection)

            assertEquals(0, pool.stats().created)

            val replacement = pool.borrow()
            assertTrue(replacement.isValid())
        }

        @Test
        fun `concurrent borrowers never exceed the cap`() {
            val pool = pool(maxSize = 4, timeoutMs = 2_000)
            val peak = AtomicInteger()
            val inUse = AtomicInteger()
            val executor = Executors.newFixedThreadPool(16)

            repeat(64) {
                executor.submit {
                    pool.use {
                        val current = inUse.incrementAndGet()
                        peak.updateAndGet { p -> maxOf(p, current) }
                        Thread.sleep(2)
                        inUse.decrementAndGet()
                    }
                }
            }
            executor.shutdown()
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS))

            assertTrue(peak.get() <= 4, "peak in-use was ${peak.get()}")
            assertEquals(0, pool.stats().inUse)
        }

        @Test
        fun `drain closes every idle object`() {
            val pool = pool(maxSize = 2)
            val a = pool.borrow()
            val b = pool.borrow()
            pool.release(a)
            pool.release(b)

            pool.drain()

            assertEquals(0, pool.stats().created)
            assertEquals(0, pool.stats().idle)
        }
    }

    @Nested
    inner class CacheAsideTest {

        @Test
        fun `a miss loads and a hit does not`() {
            val loads = AtomicInteger()
            val cache = CacheAside<String, String>(loader = { loads.incrementAndGet(); "value-$it" })

            assertEquals("value-a", cache.get("a"))
            assertEquals("value-a", cache.get("a"))

            assertEquals(1, loads.get())
            assertEquals(1, cache.stats().hits)
            assertEquals(1, cache.stats().misses)
            assertEquals(0.5, cache.stats().hitRate)
        }

        @Test
        fun `entries expire after the ttl`() {
            val clock = AtomicLong(0)
            val loads = AtomicInteger()
            val cache = CacheAside<String, String>(
                ttlMs = 1_000,
                now = clock::get,
                loader = { loads.incrementAndGet(); "value" },
            )

            cache.get("a")
            clock.set(999)
            cache.get("a")
            assertEquals(1, loads.get())

            clock.set(1_000)
            cache.get("a")
            assertEquals(2, loads.get())
        }

        @Test
        fun `a null load is not cached unless negative caching is enabled`() {
            val loads = AtomicInteger()
            val cache = CacheAside<String, String>(loader = { loads.incrementAndGet(); null })

            assertNull(cache.get("missing"))
            assertNull(cache.get("missing"))
            assertEquals(2, loads.get())

            val negativeLoads = AtomicInteger()
            val negative = CacheAside<String, String>(
                cacheNegative = true,
                loader = { negativeLoads.incrementAndGet(); null },
            )
            negative.get("missing")
            negative.get("missing")
            assertEquals(1, negativeLoads.get())
        }

        @Test
        fun `the cache stays bounded`() {
            val cache = CacheAside<Int, String>(maxSize = 10, loader = { "v$it" })

            (1..50).forEach { cache.get(it) }

            assertTrue(cache.size() <= 10, "cache grew to ${cache.size()}")
            assertTrue(cache.stats().evictions > 0)
        }

        @Test
        fun `single-flight collapses a stampede into one load`() {
            val loads = AtomicInteger()
            val cache = CacheAside<String, String>(loader = {
                loads.incrementAndGet()
                Thread.sleep(50)
                "value"
            })

            val executor = Executors.newFixedThreadPool(16)
            val latch = CountDownLatch(1)
            repeat(16) { executor.submit { latch.await(); cache.get("hot-key") } }

            latch.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS))

            assertEquals(1, loads.get(), "the cache stampeded: ${loads.get()} concurrent loads")
        }

        @Test
        fun `invalidate forces the next read to reload`() {
            val loads = AtomicInteger()
            val cache = CacheAside<String, String>(loader = { "v${loads.incrementAndGet()}" })

            assertEquals("v1", cache.get("a"))
            cache.invalidate("a")
            assertEquals("v2", cache.get("a"))
        }

        @Test
        fun `a write invalidates so readers never serve stale data`() {
            val backing = mutableMapOf("a" to "old")
            val cache = CacheAside<String, String>(loader = { backing[it] })
            val repository = CachedRepository(cache) { key, value -> backing[key] = value }

            assertEquals("old", repository.find("a"))
            repository.save("a", "new")
            assertEquals("new", repository.find("a"))
        }

        @Test
        fun `invalidateAll clears everything`() {
            val cache = CacheAside<String, String>(loader = { "v" })
            cache.get("a")
            cache.get("b")
            assertEquals(2, cache.size())

            cache.invalidateAll()
            assertEquals(0, cache.size())
        }

        @Test
        fun `configuration is validated`() {
            assertThrows(IllegalArgumentException::class.java) {
                CacheAside<String, String>(maxSize = 0) { "v" }
            }
            assertThrows(IllegalArgumentException::class.java) {
                CacheAside<String, String>(ttlMs = 0) { "v" }
            }
        }
    }
}
