package com.example.kotlindp.patterns.kotlinidioms.dsl

/**
 * # Type-safe builders (Kotlin DSLs)
 *
 * A Kotlin DSL is built from five language features working together. Understanding which feature
 * does what is the difference between designing a DSL and copying one.
 *
 * | Feature | Job |
 * |---|---|
 * | Function literal with receiver (`T.() -> Unit`) | makes `this` the builder inside `{ }` |
 * | Trailing lambda syntax | lets `html { }` look like a language construct |
 * | Extension functions | adds DSL verbs to types you do not own |
 * | `infix` | removes dots and parentheses: `"age" gt 18` |
 * | `operator` (`invoke`, `unaryPlus`) | lets bare values mean something: `+"text"` |
 * | `@DslMarker` | stops nested scopes leaking into each other |
 */

// ---------------------------------------------------------------------------------------------
// 1. The core mechanism, in isolation.
// ---------------------------------------------------------------------------------------------

/**
 * `T.() -> Unit` is a *function literal with receiver*: the lambda's `this` is the receiver, so the
 * body can call the receiver's members with no qualifier.
 *
 * `apply` is literally this signature — `fun <T> T.apply(block: T.() -> Unit): T`. Every DSL builder
 * you write is a variation of it.
 */
class Config {
    var host: String = "localhost"
    var port: Int = 8080
    val flags = mutableListOf<String>()
}

fun config(block: Config.() -> Unit): Config = Config().apply(block)

// ---------------------------------------------------------------------------------------------
// 2. A complete nested DSL: HTML.
// ---------------------------------------------------------------------------------------------

/**
 * `@DslMarker` is the single most important annotation in a nested DSL.
 *
 * Without it, inside `body { p { … } }` both the `p` receiver *and* the `body` receiver are in
 * implicit scope, so writing `p { p { } }`-style mistakes compiles happily and produces the wrong
 * tree. With it, only the innermost receiver of a given marker is accessible implicitly; reaching
 * out requires the explicit `this@body`.
 */
@DslMarker
annotation class HtmlDsl

@HtmlDsl
sealed class Node {
    abstract fun render(indent: String = ""): String
}

class TextNode(private val text: String) : Node() {
    override fun render(indent: String) = "$indent$text\n"
}

@HtmlDsl
open class Tag(private val name: String) : Node() {
    private val children = mutableListOf<Node>()
    private val attributes = mutableMapOf<String, String>()

    /**
     * `operator fun String.unaryPlus` is what makes bare `+"Hello"` legal inside a tag. It is a
     * neat trick, but use it sparingly — an operator whose meaning is not obvious from its symbol
     * costs the reader more than it saves the writer.
     */
    operator fun String.unaryPlus() {
        children += TextNode(this)
    }

    /** Attribute assignment via indexed access: `this["class"] = "btn"`. */
    operator fun set(key: String, value: String) {
        attributes[key] = value
    }

    protected fun <T : Tag> child(tag: T, block: T.() -> Unit): T = tag.apply(block).also { children += it }

    fun p(block: Tag.() -> Unit) = child(Tag("p"), block)
    fun div(block: Tag.() -> Unit) = child(Tag("div"), block)
    fun span(block: Tag.() -> Unit) = child(Tag("span"), block)

    override fun render(indent: String): String {
        val attrs = attributes.entries.joinToString("") { " ${it.key}=\"${it.value}\"" }
        return buildString {
            append("$indent<$name$attrs>\n")
            children.forEach { append(it.render("$indent  ")) }
            append("$indent</$name>\n")
        }
    }
}

class Html : Tag("html") {
    fun body(block: Tag.() -> Unit) = child(Tag("body"), block)
    fun head(block: Tag.() -> Unit) = child(Tag("head"), block)
}

fun html(block: Html.() -> Unit): Html = Html().apply(block)

// ---------------------------------------------------------------------------------------------
// 3. `infix` — DSLs that read as sentences.
// ---------------------------------------------------------------------------------------------

/**
 * An `infix` function must be a member or extension, take exactly one parameter, and that parameter
 * must not be `vararg` or have a default.
 *
 * The judgement call: infix reads beautifully when the operation is genuinely binary and the name is
 * a preposition or verb (`shouldBe`, `to`, `until`, `downTo`). It reads badly otherwise. And infix
 * calls all have the same precedence, lower than arithmetic — so mixed expressions need parentheses.
 */
data class Assertion(val passed: Boolean, val message: String)

class AssertionScope {
    val results = mutableListOf<Assertion>()

    infix fun Any?.shouldBe(expected: Any?) {
        results += Assertion(this == expected, "expected $expected but was $this")
    }

    infix fun String?.shouldContain(part: String) {
        results += Assertion(this?.contains(part) == true, "expected '$this' to contain '$part'")
    }

    infix fun Int.shouldBeGreaterThan(other: Int) {
        results += Assertion(this > other, "expected $this > $other")
    }
}

fun verify(block: AssertionScope.() -> Unit): List<Assertion> =
    AssertionScope().apply(block).results.filterNot { it.passed }

// ---------------------------------------------------------------------------------------------
// 4. Scope control: making illegal DSL states unrepresentable.
// ---------------------------------------------------------------------------------------------

/**
 * A DSL's real advantage over a config file is that the *compiler* enforces the schema. You can go
 * further and make ordering rules structural: here `retry` is only available inside `http { }`,
 * because it is a member of [HttpScope] and nothing else.
 *
 * That is a stronger guarantee than validation — the invalid program cannot be written.
 */
@DslMarker
annotation class ClientDsl

@ClientDsl
class ClientBuilder {
    private var baseUrl: String? = null
    private var http: HttpScope? = null

    fun url(value: String) {
        baseUrl = value
    }

    fun http(block: HttpScope.() -> Unit) {
        http = HttpScope().apply(block)
    }

    internal fun build(): ClientSpec = ClientSpec(
        baseUrl = requireNotNull(baseUrl) { "url is required" },
        retries = http?.retries ?: 0,
        timeoutMs = http?.timeoutMs ?: 30_000,
    )
}

@ClientDsl
class HttpScope {
    var timeoutMs: Long = 30_000
    var retries: Int = 0
        private set

    fun retry(times: Int) {
        require(times in 0..10) { "retries must be 0..10, was $times" }
        retries = times
    }
}

data class ClientSpec(val baseUrl: String, val retries: Int, val timeoutMs: Long)

fun client(block: ClientBuilder.() -> Unit): ClientSpec = ClientBuilder().apply(block).build()

/**
 * ## Where DSLs are worth it
 *
 * Gradle build scripts, Ktor routing, Exposed SQL, kotest/MockK assertions, HTML/JSON generation,
 * test fixtures, and configuration that benefits from compile-time checking.
 *
 * ## Where they are not
 *
 * A DSL costs roughly 3× the code of a plain builder and is harder to debug — stack traces point
 * into lambdas, and IDE navigation through receivers is worse than through method calls. It pays off
 * when the DSL is written *many times* by *many people*. Used once internally, it is a liability.
 *
 * ## Rules that keep a DSL usable
 *
 * 1. Always `@DslMarker`.
 * 2. Validate in `build()`, with messages naming the offending element.
 * 3. Return an immutable product; never hand back the mutable builder.
 * 4. Prefer named functions to clever operators. `+"text"` is idiomatic in HTML DSLs; inventing
 *    `unaryMinus` for "remove" is not.
 */
