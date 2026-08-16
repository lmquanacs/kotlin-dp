package com.example.kotlindp.patterns.kotlinidioms.extensions

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * # Extension functions and properties
 *
 * Add members to a type you do not own, without inheritance and without a wrapper.
 *
 * The one fact that explains all the behaviour below: **an extension is compiled to a static
 * function taking the receiver as its first parameter.** It is not added to the class, and it is
 * not virtual. Everything that surprises people about extensions follows from that.
 */

// ---------------------------------------------------------------------------------------------
// 1. Basics.
// ---------------------------------------------------------------------------------------------

/** Extension function on a type from another library. */
fun String.toSlug(): String = trim()
    .lowercase()
    .replace(Regex("[^a-z0-9]+"), "-")
    .trim('-')

/**
 * Extension *property*. It can have a getter but **no backing field** — there is nowhere to put
 * one, since the class is not being modified. So extension properties must be computed.
 */
val String.isBlankOrNumeric: Boolean
    get() = isBlank() || all { it.isDigit() }

/**
 * Extensions on generic types make domain vocabulary available across the stdlib.
 *
 * `Iterable<T>` rather than `List<T>` widens applicability at no cost.
 */
fun <T> Iterable<T>.second(): T = drop(1).firstOrNull() ?: throw NoSuchElementException("no second element")

fun <T> List<T>.replaceAt(index: Int, value: T): List<T> =
    mapIndexed { i, existing -> if (i == index) value else existing }

// ---------------------------------------------------------------------------------------------
// 2. Nullable receivers — a genuinely distinctive capability.
// ---------------------------------------------------------------------------------------------

/**
 * The receiver type may be nullable, so the extension can be called on `null` **without** `?.`.
 * That is how `orEmpty()` and `isNullOrBlank()` work in the stdlib, and it is impossible with a
 * regular method.
 */
fun String?.orPlaceholder(placeholder: String = "—"): String =
    if (this.isNullOrBlank()) placeholder else this

fun <T> Collection<T>?.sizeOrZero(): Int = this?.size ?: 0

// ---------------------------------------------------------------------------------------------
// 3. Domain vocabulary — where extensions pay off most.
// ---------------------------------------------------------------------------------------------

data class Money(val cents: Long, val currency: String = "USD")

/** Extensions turn primitives into domain values at the call site: `20.dollars`, `99.cents`. */
val Int.dollars: Money get() = Money(this * 100L)
val Int.cents: Money get() = Money(this.toLong())

operator fun Money.plus(other: Money): Money {
    require(currency == other.currency) { "cannot add $currency to ${other.currency}" }
    return copy(cents = cents + other.cents)
}

operator fun Money.times(quantity: Int): Money = copy(cents = cents * quantity)

val LocalDate.isWeekend: Boolean
    get() = dayOfWeek.value >= 6

infix fun LocalDate.daysUntil(other: LocalDate): Long = ChronoUnit.DAYS.between(this, other)

// ---------------------------------------------------------------------------------------------
// 4. The three things that surprise people.
// ---------------------------------------------------------------------------------------------

open class Shape
class Circle : Shape()

/**
 * **(a) Extensions are dispatched statically.**
 *
 * ```kotlin
 * fun Shape.describe() = "shape"
 * fun Circle.describe() = "circle"
 * val s: Shape = Circle()
 * s.describe()   // "shape" — resolved from the *declared* type, not the runtime type
 * ```
 *
 * If you need polymorphism, you need a real (open) member. This is the single biggest gotcha: an
 * extension that looks like an override is not one.
 */
fun Shape.describe(): String = "shape"
fun Circle.describe(): String = "circle"

/**
 * **(b) A member always wins over an extension with the same signature.**
 *
 * So adding an extension named like an existing method silently does nothing, and — worse — a
 * library adding a member in a later version can silently change your call's behaviour. Name
 * extensions distinctly.
 */
class Repository {
    fun find(id: String): String = "member:$id"
}

/** Never called: [Repository.find] shadows it. The compiler warns — that warning is the lesson. */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun Repository.find(id: String): String = "extension:$id"

/**
 * **(c) Extensions cannot access `private` members**, because they are just static functions
 * outside the class. They see only the public/internal API — which is a feature: an extension can
 * never break an invariant it cannot reach.
 */

// ---------------------------------------------------------------------------------------------
// 5. Member extensions and the receiver-scoping trick.
// ---------------------------------------------------------------------------------------------

/**
 * An extension declared *inside* a class has two receivers: the dispatch receiver (the class) and
 * the extension receiver. It is only in scope within that class — the mechanism that lets a DSL
 * expose verbs only inside its own block, and the reason `"a" shouldBe "b"` is available in a test
 * DSL and nowhere else.
 */
class HtmlEscaper(private val escapeQuotes: Boolean) {

    /** Only usable inside [HtmlEscaper] — deliberate scoping, not a limitation. */
    fun String.escaped(): String {
        var out = replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        if (escapeQuotes) out = out.replace("\"", "&quot;")
        return out
    }

    fun escapeAll(values: List<String>): List<String> = values.map { it.escaped() }
}

/**
 * ## Guidelines
 *
 * **Do**
 * - Add domain vocabulary to stdlib and third-party types (`20.dollars`, `date.isWeekend`).
 * - Write null-safe helpers with nullable receivers.
 * - Keep conversion functions (`toX()`) as extensions, so the source type stays unaware of the
 *   target — this is how you keep a domain model free of DTO knowledge.
 *
 * **Do not**
 * - Reach for extensions when the behaviour belongs to the class you own — put it in the class.
 * - Expect polymorphism.
 * - Declare hundreds of extensions on `Any` or `String` at package level; they pollute completion
 *   for the whole project. Scope them to a package that must be imported.
 */
