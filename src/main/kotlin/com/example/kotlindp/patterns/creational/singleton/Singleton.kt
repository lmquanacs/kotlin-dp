package com.example.kotlindp.patterns.creational.singleton

import java.util.concurrent.atomic.AtomicLong

/**
 * # Singleton
 *
 * Guarantee exactly one instance of a type and give the program a single access point to it.
 *
 * In Java this needs a private constructor, a static field, and double-checked locking with a
 * `volatile` field to be correct. Kotlin makes it a language feature: `object` declarations are
 * compiled to a class with a static `INSTANCE` field initialised in the static initialiser, so
 * the JVM class-loading contract gives you thread-safe lazy initialisation for free.
 */

/**
 * The simplest form. Initialised the first time the class is touched, thread-safely, by the JVM.
 *
 * Note the caveat that applies to *every* singleton: this is global mutable state. It survives
 * across tests, cannot be swapped for a fake, and is the single most common cause of flaky
 * test suites. Prefer a Spring bean (see [MetricsRegistry] below) unless the state genuinely is
 * process-global.
 */
object AppInfo {
    const val NAME: String = "kotlin-dp"
    const val VERSION: String = "0.0.1-SNAPSHOT"

    fun describe(): String = "$NAME v$VERSION"
}

/**
 * A singleton holding mutable state. Because it is shared by every thread in the process, all
 * mutable fields must themselves be thread-safe — here an [AtomicLong]. A plain `var counter: Long`
 * would silently lose increments under concurrency.
 */
object RequestCounter {
    private val count = AtomicLong(0)

    fun increment(): Long = count.incrementAndGet()

    fun value(): Long = count.get()

    /** Test hook. If your singleton needs one of these, that is a strong hint it should be a bean. */
    fun reset() = count.set(0)
}

/**
 * `object` cannot take constructor parameters. When the single instance needs configuration, use a
 * private constructor plus a companion holding the lazily-built instance.
 *
 * [lazy] defaults to [LazyThreadSafetyMode.SYNCHRONIZED]: the initialiser runs at most once even if
 * several threads race on first access. That is the same guarantee as double-checked locking, in
 * one word.
 */
class ConnectionPool private constructor(val size: Int) {

    fun borrow(): String = "connection-from-pool-of-$size"

    companion object {
        private val instance: ConnectionPool by lazy { ConnectionPool(size = 10) }

        fun get(): ConnectionPool = instance
    }
}

/**
 * The production-grade alternative: don't write a singleton, declare one.
 *
 * A Spring `@Component`/`@Bean` is a singleton *scoped to the application context*, not to the
 * classloader. It can be constructor-injected, replaced with a stub in a test slice, and is
 * destroyed with the context — so tests do not leak into each other. In a Spring Boot codebase
 * this should be your default and `object` the exception.
 *
 * ```kotlin
 * @Component
 * class MetricsRegistry {
 *     private val counters = ConcurrentHashMap<String, AtomicLong>()
 *     fun increment(name: String): Long =
 *         counters.computeIfAbsent(name) { AtomicLong() }.incrementAndGet()
 * }
 * ```
 */
class MetricsRegistry {
    private val counters = java.util.concurrent.ConcurrentHashMap<String, AtomicLong>()

    fun increment(name: String): Long =
        counters.computeIfAbsent(name) { AtomicLong() }.incrementAndGet()

    fun snapshot(): Map<String, Long> = counters.mapValues { it.value.get() }
}
