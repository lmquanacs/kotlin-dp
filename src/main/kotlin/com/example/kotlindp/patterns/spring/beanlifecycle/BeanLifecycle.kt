package com.example.kotlindp.patterns.spring.beanlifecycle

import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy

/**
 * # Bean lifecycle and scopes — Singleton, done properly
 *
 * A Spring bean is a Singleton **scoped to the application context**, which is strictly better than
 * an `object` (see `creational/singleton`): it can be constructor-injected, replaced in a test
 * slice, and destroyed with the context, so tests do not leak into each other.
 *
 * This folder covers the lifecycle hooks and the scopes, plus the two mistakes that cause most
 * lifecycle bugs.
 */

// ---------------------------------------------------------------------------------------------
// 1. Initialisation: constructor vs @PostConstruct.
// ---------------------------------------------------------------------------------------------

/**
 * **Where should initialisation go?**
 *
 * - **Constructor** — anything derivable from the injected dependencies. Preferred: the object is
 *   never observable in a half-built state.
 * - **`@PostConstruct`** — work that must happen *after* injection completes and that is not safe in
 *   a constructor: warming a cache, registering with a discovery service, validating configuration.
 *
 * The distinction matters for one specific reason: **calling an overridable method or a proxied
 * method from a constructor does not work.** The proxy does not exist yet, so `@Transactional`,
 * `@Cacheable` and `@Async` are all inert during construction. `@PostConstruct` runs after the
 * proxy is in place.
 */
@Component
class WarmingCache {

    private val entries = mutableMapOf<String, String>()

    // Note: `var x = false; private set` does not compile here. kotlin-allopen makes every member of
    // a @Component `open`, and Kotlin forbids a private setter on an open property. Private field
    // plus an accessor function is the idiomatic workaround in Spring beans.
    private var initialised = false
    private var destroyed = false

    fun isInitialised(): Boolean = initialised
    fun isDestroyed(): Boolean = destroyed

    @PostConstruct
    fun warmUp() {
        entries["default"] = "preloaded"
        initialised = true
    }

    /**
     * `@PreDestroy` runs on graceful shutdown. It does **not** run on `kill -9`, so it is for
     * tidiness (flushing buffers, deregistering) and never for correctness — anything that must
     * survive a crash needs to be durable before the process ends.
     */
    @PreDestroy
    fun flush() {
        entries.clear()
        destroyed = true
    }

    fun get(key: String): String? = entries[key]
    fun size(): Int = entries.size
}

/**
 * The interface-based equivalents (`InitializingBean`, [DisposableBean]) work but couple your class
 * to Spring. Prefer the annotations, or `@Bean(initMethod, destroyMethod)` for third-party types you
 * cannot annotate.
 */
@Component
class ConnectionRegistry : DisposableBean {
    private val open = mutableListOf<String>()
    private var closedCleanly = false

    fun open(name: String) {
        open += name
    }

    fun openCount(): Int = open.size
    fun isClosedCleanly(): Boolean = closedCleanly

    override fun destroy() {
        open.clear()
        closedCleanly = true
    }
}

// ---------------------------------------------------------------------------------------------
// 2. Scopes.
// ---------------------------------------------------------------------------------------------

/**
 * **Singleton (the default).** One instance per context. Must be **stateless or thread-safe** —
 * this is the number-one Spring bug: a `var` field on a `@Service` is shared by every concurrent
 * request.
 *
 * Here the state is deliberately an [AtomicInteger] to make that requirement explicit.
 */
@Component
class RequestMetrics {
    private val handled = AtomicInteger()

    fun record(): Int = handled.incrementAndGet()
    fun total(): Int = handled.get()
}

/**
 * **Prototype.** A new instance per injection point or `getBean` call.
 *
 * The trap: **a prototype injected into a singleton is created once**, at the singleton's
 * construction, and then never again — so it behaves as a singleton. If you need a fresh instance
 * per call, inject `ObjectProvider<T>` and call `getObject()`, or use a factory.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class ScratchBuffer {
    val id: String = java.util.UUID.randomUUID().toString()
    private val data = StringBuilder()

    fun append(text: String): ScratchBuffer = apply { data.append(text) }
    fun content(): String = data.toString()
}

/**
 * Correct use of a prototype from a singleton: a provider, resolved per call.
 */
@Component
class BufferFactory(private val buffers: org.springframework.beans.factory.ObjectProvider<ScratchBuffer>) {
    fun newBuffer(): ScratchBuffer = buffers.getObject()
}

// ---------------------------------------------------------------------------------------------
// 3. @Bean methods — the factory for types you do not own.
// ---------------------------------------------------------------------------------------------

/**
 * `@Bean` in a `@Configuration` class is the Factory Method pattern for third-party types you cannot
 * annotate — an HTTP client, an `ObjectMapper`, a thread pool.
 *
 * `initMethod`/`destroyMethod` give lifecycle hooks to classes that know nothing about Spring.
 *
 * Note `@Configuration` classes are themselves CGLIB-proxied so that calling one `@Bean` method from
 * another returns the *same* singleton instead of a second instance. That proxying is also why
 * `@Configuration` classes must not be `final` — allopen handles it here.
 */
@Configuration
class InfrastructureConfig {

    @Bean(destroyMethod = "shutdown")
    fun taskRunner(): TaskRunner = TaskRunner(poolSize = 4)

    /** Calling another @Bean method returns the shared singleton, not a new object. */
    @Bean
    fun taskReporter(): TaskReporter = TaskReporter(taskRunner())
}

/** A "third-party" type: no Spring annotations, lifecycle wired externally. */
class TaskRunner(val poolSize: Int) {
    var running = true
        private set

    fun submit(name: String): String = "ran:$name(pool=$poolSize)"

    fun shutdown() {
        running = false
    }
}

class TaskReporter(private val runner: TaskRunner) {
    fun report(): String = "reporter over pool of ${runner.poolSize}"
    fun sameRunner(other: TaskRunner): Boolean = runner === other
}

/**
 * ## Startup order
 *
 * Constructor injection determines the graph, so Spring initialises dependencies first. Do not use
 * `@DependsOn` to fix ordering unless the dependency is genuinely invisible (a bean that must exist
 * before another reads a file it wrote). `@DependsOn` is a comment the compiler cannot check.
 *
 * For work at startup, prefer `ApplicationRunner`/`CommandLineRunner` over `@PostConstruct`: they
 * run after the *whole* context is ready, so it is safe to call other beans.
 *
 * ## The two mistakes
 *
 * 1. **Mutable state on a singleton bean.** Shared by every request thread. Use immutable fields, or
 *    thread-safe types, or change the scope.
 * 2. **Prototype injected into a singleton.** Created once. Use `ObjectProvider`.
 */
