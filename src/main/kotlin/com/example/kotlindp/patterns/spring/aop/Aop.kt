package com.example.kotlindp.patterns.spring.aop

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * # AOP: Decorator and Proxy, applied by the framework
 *
 * Spring AOP generates a proxy around a bean and routes calls through your advice. That is exactly
 * the Decorator pattern (`structural/decorator`) and the Proxy pattern (`structural/proxy`) —
 * except the wrapper is generated at runtime, so one aspect decorates a hundred beans.
 *
 * `@Transactional`, `@Cacheable`, `@Retryable`, `@Async` and `@PreAuthorize` are all built this way.
 *
 * The trade: you get cross-cutting behaviour with no wrapper classes, and you lose the ability to
 * see, from a call site, that anything is happening at all.
 */

// ---------------------------------------------------------------------------------------------
// A custom annotation — a pointcut you can read.
// ---------------------------------------------------------------------------------------------

/**
 * Prefer annotation-driven pointcuts to package-expression ones.
 *
 * `@Around("execution(* com.example..service.*.*(..))")` silently stops matching when someone
 * renames a package. An annotation is explicit at the point of use, refactor-safe, and greppable.
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Timed(val name: String = "")

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AuditLogged(val action: String)

// ---------------------------------------------------------------------------------------------
// The aspects.
// ---------------------------------------------------------------------------------------------

/**
 * A timing aspect — the decorator from `structural/decorator`, applied declaratively.
 *
 * The `finally` block is the important detail, exactly as in the hand-written version: without it,
 * failed calls are never timed, and your latency metrics quietly exclude the slowest cases.
 */
@Aspect
@Component
@Order(1)
class TimingAspect {

    private val totals = ConcurrentHashMap<String, AtomicLong>()
    private val counts = ConcurrentHashMap<String, AtomicLong>()

    @Around("@annotation(timed)")
    fun time(joinPoint: ProceedingJoinPoint, timed: Timed): Any? {
        val name = timed.name.ifEmpty { joinPoint.signature.name }
        val start = System.nanoTime()
        try {
            return joinPoint.proceed()
        } finally {
            val elapsed = System.nanoTime() - start
            totals.computeIfAbsent(name) { AtomicLong() }.addAndGet(elapsed)
            counts.computeIfAbsent(name) { AtomicLong() }.incrementAndGet()
        }
    }

    fun callCount(name: String): Long = counts[name]?.get() ?: 0
    fun recorded(): Set<String> = counts.keys.toSet()
    fun reset() {
        totals.clear(); counts.clear()
    }
}

/**
 * An auditing aspect. Note it records both outcomes: an audit trail that only logs successes is
 * worse than none, because it looks complete.
 */
@Aspect
@Component
@Order(2)
class AuditAspect {

    val entries = java.util.concurrent.CopyOnWriteArrayList<String>()

    @Around("@annotation(audited)")
    fun audit(joinPoint: ProceedingJoinPoint, audited: AuditLogged): Any? =
        try {
            joinPoint.proceed().also { entries += "ok:${audited.action}" }
        } catch (e: Throwable) {
            entries += "fail:${audited.action}:${e.javaClass.simpleName}"
            throw e
        }
}

// ---------------------------------------------------------------------------------------------
// A bean that gets decorated.
// ---------------------------------------------------------------------------------------------

/**
 * Nothing here knows it is being timed or audited — which is the point, and the danger.
 *
 * **The Kotlin-specific trap:** Spring AOP uses CGLIB for classes, which *subclasses* the bean. A
 * Kotlin class is `final` by default, so CGLIB cannot proxy it and the advice silently never runs.
 *
 * This project's `kotlin("plugin.spring")` (kotlin-allopen) opens classes annotated with
 * `@Component`/`@Service`/`@Configuration` and friends automatically. Without that plugin,
 * `@Transactional` on a Kotlin service is a no-op — the single most common Kotlin + Spring bug.
 */
@Service
class ReportService {

    @Timed(name = "report.generate")
    @AuditLogged(action = "generate-report")
    fun generate(rows: Int): String {
        require(rows >= 0) { "rows must be >= 0" }
        return "report with $rows rows"
    }

    @Timed
    fun quickSummary(): String = summaryHelper()

    /**
     * **Self-invocation bypasses the proxy.** This method is annotated, but calling it from
     * [quickSummary] via an implicit `this` goes straight to the target object — no proxy, no
     * advice, no `@Transactional`.
     *
     * The fix is to move the method to another bean, not to inject the bean into itself.
     */
    @Timed(name = "never.recorded")
    fun summaryHelper(): String = "summary"
}

/**
 * ## What Spring AOP can and cannot intercept
 *
 * | Call | Intercepted? |
 * |---|---|
 * | External call to a public method on a proxied bean | yes |
 * | Self-invocation (`this.other()`) | **no** |
 * | `private` / `final` method | **no** |
 * | Call on an object created with `new` | **no** — it is not a bean |
 * | Kotlin class without allopen | **no** — CGLIB cannot subclass a `final` class |
 *
 * Every row of that table is a bug someone has spent a day on.
 *
 * ## When to use AOP, and when to write a decorator by hand
 *
 * **AOP** for genuinely cross-cutting infrastructure applied uniformly to many beans: timing,
 * auditing, tracing, transactions, caching, security.
 *
 * **A hand-written decorator** (`structural/decorator`) for domain behaviour, or when only two or
 * three classes are involved. Explicit wrapping is visible in the constructor and in stack traces,
 * and it does not depend on proxy semantics that a `private` modifier can silently break.
 *
 * ## Cost
 *
 * Stack traces grow `$$EnhancerBySpringCGLIB$$` frames; debugging steps into generated code;
 * behaviour changes depending on how a method is called. Use it deliberately, and prefer
 * annotation-based pointcuts so the behaviour is at least visible at the declaration.
 */
