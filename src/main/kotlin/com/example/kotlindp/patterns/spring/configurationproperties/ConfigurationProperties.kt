package com.example.kotlindp.patterns.spring.configurationproperties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import java.time.Duration
import javax.validation.Valid
import javax.validation.constraints.Max
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank

/**
 * # Typed configuration (Builder + Value Object, done by the framework)
 *
 * `@ConfigurationProperties` binds a slice of configuration to a typed object. In Kotlin, combined
 * with `@ConstructorBinding`, it produces an **immutable data class with defaults and validation** —
 * which is exactly what the Builder pattern is usually used to construct by hand.
 *
 * The single biggest benefit is not typing: it is **fail-fast**. A misconfigured value stops the
 * application at startup with a message naming the property, instead of throwing at 3am the first
 * time that code path runs.
 */

/**
 * `@ConstructorBinding` (Boot 2.2+) is what makes the properties immutable — without it Spring
 * needs setters, so every field becomes a `var` and the object is mutable for the process lifetime.
 *
 * Note how much this replaces:
 * - `@Value("\${...}")` on individual fields — untyped, unvalidated, scattered;
 * - a hand-written builder with defaults;
 * - manual `require(...)` calls in an `init` block.
 *
 * Relaxed binding means `connect-timeout`, `connectTimeout`, `CONNECT_TIMEOUT` and
 * `PAYMENTS_CONNECTTIMEOUT` all bind to the same property, so the same class works with YAML,
 * properties files, and environment variables.
 */
@ConstructorBinding
@ConfigurationProperties(prefix = "payments")
@Validated
data class PaymentProperties(

    @field:NotBlank(message = "payments.base-url is required")
    val baseUrl: String = "https://payments.example.com",

    /**
     * `Duration` binds from `5s`, `500ms`, `PT5S`. Using a real type rather than `Long` removes a
     * whole class of unit-confusion bugs — the ones where someone passes seconds to a millisecond
     * parameter.
     */
    val connectTimeout: Duration = Duration.ofSeconds(5),

    val readTimeout: Duration = Duration.ofSeconds(30),

    @field:Min(0)
    @field:Max(10)
    val maxRetries: Int = 3,

    /** Nested types bind from nested keys — `payments.pool.max-size`. */
    @field:Valid
    val pool: PoolProperties = PoolProperties(),

    /** Collections and maps bind from lists and nested keys. */
    val enabledCurrencies: Set<String> = setOf("USD", "EUR"),

    val featureFlags: Map<String, Boolean> = emptyMap(),
) {
    /**
     * Cross-field validation that annotations cannot express belongs in `init`. It runs during
     * binding, so a bad combination is still a startup failure.
     */
    init {
        require(readTimeout >= connectTimeout) {
            "payments.read-timeout ($readTimeout) must be >= payments.connect-timeout ($connectTimeout)"
        }
    }

    /** Derived values belong here too — computed once, at startup, from validated inputs. */
    val totalTimeout: Duration get() = connectTimeout + readTimeout

    fun currencySupported(code: String): Boolean = code in enabledCurrencies

    fun flagEnabled(name: String): Boolean = featureFlags[name] ?: false
}

@ConstructorBinding
data class PoolProperties(
    @field:Min(1) @field:Max(100) val maxSize: Int = 10,
    val idleTimeout: Duration = Duration.ofMinutes(1),
)

/**
 * On Boot 2.5, `@ConstructorBinding` classes must be registered — either with
 * `@EnableConfigurationProperties` as here, or with `@ConfigurationPropertiesScan` on the
 * application class. A plain `@Component` will *not* work with constructor binding.
 */
@Configuration
@EnableConfigurationProperties(PaymentProperties::class)
class PaymentPropertiesConfig

/**
 * Consumers inject the typed object, not `Environment` and not `@Value` strings.
 *
 * The payoff at the call site: `properties.maxRetries` is an `Int` that has already been validated
 * to be in 0..10. Nothing downstream needs to re-check it.
 */
@Service
class PaymentClient(private val properties: PaymentProperties) {

    fun describe(): String = buildString {
        append(properties.baseUrl)
        append(" timeout=").append(properties.totalTimeout)
        append(" retries=").append(properties.maxRetries)
        append(" pool=").append(properties.pool.maxSize)
    }

    fun canCharge(currency: String): Boolean = properties.currencySupported(currency)
}

/**
 * ## Guidelines
 *
 * - **One properties class per cohesive concern**, named after its prefix. A single
 *   `AppProperties` with 60 fields is a god object.
 * - **Always give defaults** that work in development, so a fresh checkout starts.
 * - **Use real types**: `Duration`, `DataSize`, `URI`, enums, `Set<T>`. Not `String`.
 * - **`@Validated` plus JSR-380 annotations** for field rules; `init` for cross-field rules.
 * - Prefer this over `@Value`, which is untyped, unvalidated, and scatters configuration knowledge
 *   across the codebase.
 *
 * ## In tests
 *
 * Because the class is a plain data class, unit tests just construct it:
 * `PaymentProperties(maxRetries = 1)`. No context, no `@TestPropertySource`. That is the same
 * advantage constructor injection gives, applied to configuration.
 */
