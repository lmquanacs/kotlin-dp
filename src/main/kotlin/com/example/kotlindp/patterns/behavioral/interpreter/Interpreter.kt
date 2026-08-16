package com.example.kotlindp.patterns.behavioral.interpreter

/**
 * # Interpreter
 *
 * Represent a small language's grammar as a class hierarchy, and evaluate sentences by walking it.
 *
 * The under-appreciated point: **you have almost certainly already built one.** A feature-flag rule
 * engine, a search filter, an access-control policy, a pricing rule — each is a little language,
 * and the difference between "a mess of nested ifs" and "an interpreter" is whether the grammar is
 * explicit.
 *
 * Kotlin's contribution: the AST is a sealed hierarchy, evaluation is an exhaustive `when`, and a
 * DSL can serve as the "parser" so no string parsing is needed at all.
 */

// ---------------------------------------------------------------------------------------------
// The grammar, as a sealed hierarchy.
// ---------------------------------------------------------------------------------------------

/** Context = the variable bindings a sentence is evaluated against. */
typealias Context = Map<String, Any?>

sealed class Predicate {
    // --- terminal expressions ---
    data class Equals(val field: String, val value: Any?) : Predicate()
    data class GreaterThan(val field: String, val value: Double) : Predicate()
    data class Contains(val field: String, val substring: String) : Predicate()
    data class OneOf(val field: String, val values: Set<Any?>) : Predicate()
    object Always : Predicate()

    // --- non-terminal expressions: they compose other predicates ---
    data class And(val terms: List<Predicate>) : Predicate()
    data class Or(val terms: List<Predicate>) : Predicate()
    data class Not(val term: Predicate) : Predicate()
}

// ---------------------------------------------------------------------------------------------
// The interpreter.
// ---------------------------------------------------------------------------------------------

/**
 * One function, exhaustive over the grammar. Adding a rule type is a compile error here and nowhere
 * else — which is the property that makes a hand-written rule engine maintainable.
 *
 * `all`/`any` short-circuit, so `And` stops at the first false term just as you would want.
 */
fun Predicate.evaluate(context: Context): Boolean = when (this) {
    is Predicate.Always -> true
    is Predicate.Equals -> context[field] == value
    is Predicate.GreaterThan -> (context[field] as? Number)?.toDouble()?.let { it > value } ?: false
    is Predicate.Contains -> (context[field] as? String)?.contains(substring) ?: false
    is Predicate.OneOf -> context[field] in values
    is Predicate.And -> terms.all { it.evaluate(context) }
    is Predicate.Or -> terms.any { it.evaluate(context) }
    is Predicate.Not -> !term.evaluate(context)
}

/**
 * A second interpretation of the same tree — the payoff for making the grammar explicit.
 *
 * This one renders SQL. Real engines add a third that renders an explanation ("matched because
 * plan = pro"), which is impossible if the rules are buried in nested `if`s.
 */
fun Predicate.toSql(): String = when (this) {
    is Predicate.Always -> "1=1"
    is Predicate.Equals -> "$field = ${literal(value)}"
    is Predicate.GreaterThan -> "$field > $value"
    is Predicate.Contains -> "$field LIKE '%$substring%'"
    is Predicate.OneOf -> "$field IN (${values.joinToString(", ") { literal(it) }})"
    is Predicate.And -> terms.joinToString(" AND ", "(", ")") { it.toSql() }
    is Predicate.Or -> terms.joinToString(" OR ", "(", ")") { it.toSql() }
    is Predicate.Not -> "NOT (${term.toSql()})"
}

private fun literal(value: Any?): String = when (value) {
    null -> "NULL"
    is Number, is Boolean -> value.toString()
    // Illustrative only. Real code must use bound parameters, never string interpolation — a
    // rule engine that builds SQL by concatenation is an SQL-injection vector.
    else -> "'${value.toString().replace("'", "''")}'"
}

// ---------------------------------------------------------------------------------------------
// The "parser" — a DSL instead of string parsing.
// ---------------------------------------------------------------------------------------------

/**
 * The classic Interpreter needs a parser to turn text into a tree. When the rules are authored by
 * developers, a Kotlin DSL replaces the parser entirely: the compiler *is* the parser, so typos are
 * compile errors and refactoring works.
 *
 * (When rules are authored at runtime by users, you do need real parsing — and then a `String` →
 * [Predicate] parser is the missing piece, with the tree above unchanged.)
 */
@DslMarker
annotation class RuleDsl

@RuleDsl
class PredicateBuilder {
    private val terms = mutableListOf<Predicate>()

    infix fun String.eq(value: Any?) {
        terms += Predicate.Equals(this, value)
    }

    infix fun String.gt(value: Number) {
        terms += Predicate.GreaterThan(this, value.toDouble())
    }

    infix fun String.contains(substring: String) {
        terms += Predicate.Contains(this, substring)
    }

    infix fun String.oneOf(values: Collection<Any?>) {
        terms += Predicate.OneOf(this, values.toSet())
    }

    fun any(block: PredicateBuilder.() -> Unit) {
        terms += Predicate.Or(PredicateBuilder().apply(block).terms())
    }

    fun not(block: PredicateBuilder.() -> Unit) {
        terms += Predicate.Not(Predicate.And(PredicateBuilder().apply(block).terms()))
    }

    internal fun terms(): List<Predicate> = terms.toList()

    internal fun build(): Predicate = when (terms.size) {
        0 -> Predicate.Always
        1 -> terms.single()
        else -> Predicate.And(terms.toList())
    }
}

fun rule(block: PredicateBuilder.() -> Unit): Predicate = PredicateBuilder().apply(block).build()

/**
 * ## Example
 *
 * ```kotlin
 * val eligible = rule {
 *     "country" oneOf setOf("US", "CA")
 *     "age" gt 18
 *     any {
 *         "plan" eq "pro"
 *         "credits" gt 100
 *     }
 * }
 * eligible.evaluate(mapOf("country" to "US", "age" to 30, "plan" to "pro"))  // true
 * eligible.toSql()
 * ```
 *
 * ## When to use
 *
 * The grammar is small and *stable*, and you need several interpretations of it (evaluate, render,
 * explain, optimise, persist).
 *
 * ## When not to
 *
 * Interpreter scales badly with grammar size — one class per rule, and every interpretation grows a
 * branch. Past roughly a dozen node types, use a real parser generator (ANTLR) or an embeddable
 * engine (CEL, JEXL, MVEL) instead of hand-rolling.
 *
 * Two things to get right before shipping one: **bound the recursion depth** if rules come from
 * users (a deeply nested tree is a stack-overflow DoS), and **never build SQL by interpolation** —
 * emit parameter placeholders and a bound-argument list.
 */
