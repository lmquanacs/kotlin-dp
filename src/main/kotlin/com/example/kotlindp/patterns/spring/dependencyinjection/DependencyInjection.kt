package com.example.kotlindp.patterns.spring.dependencyinjection

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

/**
 * # Dependency Injection as Strategy + Abstract Factory
 *
 * The Spring container **is** a factory. Most hand-written factories in a Spring codebase are
 * re-implementing something the container already does, usually worse.
 *
 * This folder shows the four wiring shapes that replace hand-rolled pattern code, and the one rule
 * that matters most in Kotlin: **use constructor injection**.
 */

// ---------------------------------------------------------------------------------------------
// 1. Constructor injection — the only form to use in Kotlin.
// ---------------------------------------------------------------------------------------------

/**
 * Why constructor injection is non-negotiable in Kotlin:
 *
 * - Dependencies become `val` — immutable, non-null, no `lateinit`, no `?`.
 * - The type system enforces that a constructed object is fully initialised. Field injection
 *   (`@Autowired lateinit var`) postpones that to runtime and throws
 *   `UninitializedPropertyAccessException` when the wiring is wrong.
 * - The class is testable with `new`, with no Spring context and no reflection-based injection.
 * - A constructor with eight parameters is *visibly* doing too much. Field injection hides that.
 *
 * Since Spring 4.3 a single constructor needs no `@Autowired` at all — which is why the Kotlin
 * spelling is just a primary constructor.
 */
interface FraudCheck {
    val name: String
    fun suspicious(amountCents: Long, country: String): Boolean
}

@Component
class AmountFraudCheck : FraudCheck {
    override val name = "amount"
    override fun suspicious(amountCents: Long, country: String) = amountCents > 1_000_000
}

@Component
class CountryFraudCheck : FraudCheck {
    override val name = "country"
    override fun suspicious(amountCents: Long, country: String) = country in setOf("XX", "ZZ")
}

@Component
class VelocityFraudCheck : FraudCheck {
    override val name = "velocity"
    private val seen = mutableMapOf<String, Int>()

    override fun suspicious(amountCents: Long, country: String): Boolean =
        seen.merge(country, 1, Int::plus)!! > 100
}

// ---------------------------------------------------------------------------------------------
// 2. Injecting List<T> — Strategy and Composite, with zero registry code.
// ---------------------------------------------------------------------------------------------

/**
 * Spring injects **every** bean implementing [FraudCheck]. Adding a fourth check means adding a
 * `@Component` — this class does not change, and neither does any configuration.
 *
 * That is the Strategy pattern's registry and the Composite pattern's child list, both supplied by
 * the container. Compare with `behavioral/strategy`, where the same thing is hand-written.
 *
 * Ordering is *not* guaranteed by declaration; use `@Order`/`Ordered` when it matters, and be
 * explicit about it — relying on incidental ordering is a bug waiting for a refactor.
 */
@Service
class FraudService(private val checks: List<FraudCheck>) {

    fun evaluate(amountCents: Long, country: String): List<String> =
        checks.filter { it.suspicious(amountCents, country) }.map { it.name }

    fun availableChecks(): Set<String> = checks.map { it.name }.toSet()
}

/**
 * Injecting `Map<String, T>` keys each bean by its **bean name**, giving a lookup table for free.
 * This is Factory Method driven by configuration, with no `when` block to maintain.
 */
@Service
class FraudCheckRegistry(private val checksByBeanName: Map<String, FraudCheck>) {

    fun beanNames(): Set<String> = checksByBeanName.keys

    fun byName(name: String): FraudCheck? = checksByBeanName.values.firstOrNull { it.name == name }
}

// ---------------------------------------------------------------------------------------------
// 3. Choosing between several candidates: @Primary and @Qualifier.
// ---------------------------------------------------------------------------------------------

interface NotificationSender {
    fun send(to: String, body: String): String
}

/**
 * `@Primary` marks the default when several candidates match. It is the right tool when there *is*
 * an obvious default; it is the wrong tool for "I got a NoUniqueBeanDefinitionException and this
 * made it go away".
 */
@Component
@Primary
class EmailSender : NotificationSender {
    override fun send(to: String, body: String) = "email->$to: $body"
}

@Component("smsSender")
class SmsSender : NotificationSender {
    override fun send(to: String, body: String) = "sms->$to: $body"
}

/**
 * `@Qualifier` selects a specific bean. Note the Kotlin syntax: on a constructor parameter the
 * annotation goes directly on the parameter, and no `@Autowired` is needed.
 *
 * Prefer a *custom qualifier annotation* over a string in production code — a typo in `"smsSender"`
 * is a startup failure, whereas a typo in an annotation name is a compile error.
 */
@Service
class AlertService(
    private val default: NotificationSender,
    @Qualifier("smsSender") private val urgent: NotificationSender,
) {
    fun notifyUser(to: String, body: String): String = default.send(to, body)
    fun alertUser(to: String, body: String): String = urgent.send(to, body)
}

// ---------------------------------------------------------------------------------------------
// 4. Optional and lazy dependencies: ObjectProvider.
// ---------------------------------------------------------------------------------------------

interface MetricsExporter {
    fun export(name: String, value: Long)
}

/**
 * [ObjectProvider] is the clean way to express "this dependency may not exist" or "resolve it
 * later". It replaces three worse habits: a nullable `@Autowired(required = false)` field,
 * `@Lazy` used to paper over a circular dependency, and a `try/catch` around `getBean`.
 *
 * Combined with a Null Object default (`behavioral/nullobject`), the call sites stay free of null
 * checks entirely.
 */
@Service
class InstrumentedFraudService(
    private val fraudService: FraudService,
    private val exporterProvider: ObjectProvider<MetricsExporter>,
) {
    fun evaluate(amountCents: Long, country: String): List<String> {
        val flags = fraudService.evaluate(amountCents, country)
        // No-op when no exporter bean is defined — no null check, no branch.
        exporterProvider.ifAvailable { it.export("fraud.flags", flags.size.toLong()) }
        return flags
    }
}

/**
 * ## The rule that saves the most debugging time
 *
 * **Never inject the `ApplicationContext` to call `getBean()`.** That is the Service Locator
 * anti-pattern: it hides dependencies from the constructor, defeats compile-time checking, and
 * turns a startup failure into a runtime one. If you find yourself reaching for it, you want
 * `List<T>`, `Map<String, T>`, or `ObjectProvider<T>`.
 *
 * ## Circular dependencies
 *
 * Constructor injection makes a cycle a **startup failure** rather than a subtle runtime bug. That
 * is a feature. Spring Boot 2.6+ disallows cycles by default; on 2.5 they still resolve with
 * `@Lazy`, but the right fix is almost always to extract the shared behaviour into a third bean.
 */
