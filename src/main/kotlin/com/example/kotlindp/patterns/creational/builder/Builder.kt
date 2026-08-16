package com.example.kotlindp.patterns.creational.builder

import java.time.Duration

/**
 * # Builder
 *
 * Assemble a complex object step by step, keeping the construction process separate from the final
 * representation.
 *
 * This is the pattern Kotlin has changed the most. Java needs Builder because it has neither
 * default arguments nor named arguments — with 8 optional fields you either write 2^8 constructors
 * or a builder. Kotlin has both, so **most Java builders should simply not be ported**.
 *
 * What survives are the two cases named arguments cannot handle: nested/hierarchical structures,
 * and construction that requires validation or accumulation across steps.
 */

// ---------------------------------------------------------------------------------------------
// 1. The case where you do NOT need a builder.
// ---------------------------------------------------------------------------------------------

/**
 * Default + named arguments already give you everything a Java builder does, with less code, and
 * with the compiler enforcing that required fields are present.
 *
 * ```kotlin
 * HttpRequest(url = "https://api.example.com", method = "POST", timeout = Duration.ofSeconds(5))
 * ```
 *
 * Note `headers` is `Map<String, String>`, not `MutableMap` — the built object is immutable, which
 * is the property that actually makes construction safe to share across threads.
 */
data class HttpRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val timeout: Duration = Duration.ofSeconds(30),
    val followRedirects: Boolean = true,
)

// ---------------------------------------------------------------------------------------------
// 2. Type-safe DSL builder — the idiomatic Kotlin form, for nested structures.
// ---------------------------------------------------------------------------------------------

/**
 * `@DslMarker` is what makes a nested DSL safe.
 *
 * Without it, inside `server { host { … } }` the inner lambda's receiver *and* the outer one are
 * both in scope, so a typo silently configures the wrong object. With it, the compiler rejects any
 * reference to an outer receiver of the same marker — you would have to write `this@server.port`
 * explicitly. Every Kotlin DSL you build should have one of these.
 */
@DslMarker
annotation class PipelineDsl

data class Stage(val name: String, val command: String, val retries: Int)

data class Pipeline(
    val name: String,
    val stages: List<Stage>,
    val environment: Map<String, String>,
)

@PipelineDsl
class PipelineBuilder(private val name: String) {
    private val stages = mutableListOf<Stage>()
    private val environment = mutableMapOf<String, String>()

    /**
     * A nested block. The parameter is a *function literal with receiver*: inside the lambda,
     * `this` is a [StageBuilder], so callers write bare `command = "…"` with no qualifier.
     * That receiver trick is the entire mechanism behind Kotlin DSLs.
     */
    fun stage(name: String, configure: StageBuilder.() -> Unit) {
        stages += StageBuilder(name).apply(configure).build()
    }

    /** `infix` lets the DSL read as `"KEY" to "value"`-style prose. */
    infix fun String.envTo(value: String) {
        environment[this] = value
    }

    fun env(key: String, value: String) {
        environment[key] = value
    }

    internal fun build(): Pipeline {
        require(stages.isNotEmpty()) { "Pipeline '$name' must declare at least one stage" }
        // Defensive copies: the builder is mutable, the product must not be.
        return Pipeline(name, stages.toList(), environment.toMap())
    }
}

@PipelineDsl
class StageBuilder(private val name: String) {
    var command: String? = null
    var retries: Int = 0

    internal fun build(): Stage {
        val cmd = requireNotNull(command) { "Stage '$name' is missing a command" }
        require(retries >= 0) { "Stage '$name' has negative retries: $retries" }
        return Stage(name, cmd, retries)
    }
}

/**
 * Entry point. Top-level function, so usage reads as a literal:
 *
 * ```kotlin
 * val p = pipeline("release") {
 *     env("CI", "true")
 *     stage("build") { command = "./gradlew build" }
 *     stage("deploy") { command = "./deploy.sh"; retries = 3 }
 * }
 * ```
 */
fun pipeline(name: String, configure: PipelineBuilder.() -> Unit): Pipeline =
    PipelineBuilder(name).apply(configure).build()

// ---------------------------------------------------------------------------------------------
// 3. Classic fluent builder — when Java callers must use the API.
// ---------------------------------------------------------------------------------------------

/**
 * Kotlin's named arguments are invisible from Java, so a Kotlin library with a Java audience still
 * benefits from a chained builder. `@JvmStatic`/`@JvmOverloads` would round it out in a real library.
 */
class EmailBuilder {
    private var to: String? = null
    private var subject: String = "(no subject)"
    private var body: String = ""
    private val cc = mutableListOf<String>()

    fun to(address: String) = apply { this.to = address }
    fun subject(value: String) = apply { this.subject = value }
    fun body(value: String) = apply { this.body = value }
    fun cc(address: String) = apply { this.cc += address }

    fun build(): Email = Email(
        to = requireNotNull(to) { "Email requires a recipient" },
        subject = subject,
        body = body,
        cc = cc.toList(),
    )
}

data class Email(val to: String, val subject: String, val body: String, val cc: List<String>)

// ---------------------------------------------------------------------------------------------
// 4. `copy()` as a builder for modification.
// ---------------------------------------------------------------------------------------------

/**
 * Java builders are often used to make a tweaked copy of an existing object. In Kotlin `copy()`
 * does that natively and keeps the result immutable:
 *
 * ```kotlin
 * val retried = request.copy(timeout = Duration.ofSeconds(60))
 * ```
 *
 * Beware the one trap: `copy()` is a *shallow* copy. Mutable collections inside the object are
 * shared with the original. Keep data class fields immutable and this stops being a concern.
 */
fun HttpRequest.withHeader(name: String, value: String): HttpRequest =
    copy(headers = headers + (name to value))
