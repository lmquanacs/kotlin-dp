package com.example.kotlindp.patterns.structural.composite

/**
 * # Composite
 *
 * Treat individual objects and compositions of objects uniformly, by giving both the same
 * interface. Whenever you have a tree and want "the whole behaves like a part", this is it.
 *
 * Kotlin's contribution is the **sealed hierarchy**: leaf and branch become sealed subtypes, `when`
 * over them is exhaustive, and adding a third node kind produces compile errors at every traversal
 * instead of a silently-skipped branch at runtime.
 */

// ---------------------------------------------------------------------------------------------
// A filesystem tree — the textbook composite, still the clearest one.
// ---------------------------------------------------------------------------------------------

sealed interface FsNode {
    val name: String

    /** The uniform operation: leaves compute it directly, branches recurse. */
    fun sizeBytes(): Long
}

data class FsFile(override val name: String, val bytes: Long) : FsNode {
    override fun sizeBytes(): Long = bytes
}

data class FsDirectory(
    override val name: String,
    val children: List<FsNode> = emptyList(),
) : FsNode {
    override fun sizeBytes(): Long = children.sumOf { it.sizeBytes() }

    operator fun plus(child: FsNode): FsDirectory = copy(children = children + child)
}

/**
 * Traversal as a [Sequence] — lazy, so `firstOrNull { … }` on a huge tree stops as soon as it finds
 * a match instead of materialising every node.
 *
 * `yieldAll` inside `sequence { }` makes recursive traversal read almost like the tree's shape.
 */
fun FsNode.walk(): Sequence<FsNode> = sequence {
    yield(this@walk)
    if (this@walk is FsDirectory) {
        children.forEach { yieldAll(it.walk()) }
    }
}

fun FsNode.render(indent: String = ""): String = when (this) {
    // Exhaustive `when` over a sealed interface — no `else` branch, and adding a node type breaks
    // this at compile time. That is the whole safety argument for sealed composites.
    is FsFile -> "$indent$name (${bytes}b)\n"
    is FsDirectory -> "$indent$name/\n" + children.joinToString("") { it.render("$indent  ") }
}

// ---------------------------------------------------------------------------------------------
// A composite that is genuinely useful in production: composable validation rules.
// ---------------------------------------------------------------------------------------------

data class Violation(val field: String, val message: String)

/**
 * A single rule and a group of rules are the same type, so callers never care which they hold.
 */
sealed interface Rule<T> {
    fun validate(value: T): List<Violation>
}

class Predicate<T>(
    private val field: String,
    private val message: String,
    private val test: (T) -> Boolean,
) : Rule<T> {
    override fun validate(value: T): List<Violation> =
        if (test(value)) emptyList() else listOf(Violation(field, message))
}

/** The composite: runs every child and concatenates the results. */
class AllOf<T>(private val rules: List<Rule<T>>) : Rule<T> {
    override fun validate(value: T): List<Violation> = rules.flatMap { it.validate(value) }
}

/** A different composition strategy over the same children — passes if *any* child passes. */
class AnyOf<T>(private val rules: List<Rule<T>>, private val field: String) : Rule<T> {
    override fun validate(value: T): List<Violation> {
        val results = rules.map { it.validate(value) }
        return if (results.any { it.isEmpty() }) emptyList()
        else listOf(Violation(field, "no alternative matched"))
    }
}

/**
 * Combining rules with `operator fun plus` lets callers write `emailRule + lengthRule`, which is
 * usually more readable than `AllOf(listOf(emailRule, lengthRule))`.
 */
operator fun <T> Rule<T>.plus(other: Rule<T>): Rule<T> = AllOf(listOf(this, other))

fun <T> rules(vararg rules: Rule<T>): Rule<T> = AllOf(rules.toList())

fun <T> rule(field: String, message: String, test: (T) -> Boolean): Rule<T> =
    Predicate(field, message, test)

/**
 * ## Notes
 *
 * **Depth.** Recursive `sizeBytes()` will blow the stack on a pathologically deep tree. Real
 * filesystems are shallow; user-supplied JSON is not. If input depth is untrusted, either cap it
 * during parsing or write the traversal with an explicit stack.
 *
 * **`data class` + recursion.** Generated `equals`/`hashCode`/`toString` recurse over the whole
 * subtree. That is convenient in tests and a performance trap on large trees — and an infinite loop
 * if the graph ever contains a cycle. Composite assumes a *tree*; add a parent pointer and you get
 * a cycle and a `StackOverflowError` from `toString()`.
 *
 * **Kotlin vs GoF.** GoF puts `add`/`remove` on the common interface so leaves and branches are
 * fully interchangeable, forcing leaves to throw on `add`. Sealed types let you keep child
 * management on [FsDirectory] only and recover the branch with a safe `is` check — strictly better.
 */
