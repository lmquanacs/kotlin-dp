package com.example.kotlindp.patterns.behavioral.visitor

/**
 * # Visitor
 *
 * Add new operations to an object structure without modifying the classes in it.
 *
 * Visitor exists in Java to work around a missing language feature: you cannot pattern-match on
 * type, so the double-dispatch dance (`accept(visitor)` calling `visitor.visitX(this)`) is used to
 * recover the concrete type. **Kotlin has sealed classes and exhaustive `when`, which is the same
 * capability with none of the ceremony.**
 *
 * Both forms are here, because the classic form still wins in one specific case.
 */

// ---------------------------------------------------------------------------------------------
// The object structure: an expression tree.
// ---------------------------------------------------------------------------------------------

sealed class Expr {
    data class Num(val value: Double) : Expr()
    data class Add(val left: Expr, val right: Expr) : Expr()
    data class Mul(val left: Expr, val right: Expr) : Expr()
    data class Neg(val operand: Expr) : Expr()
    data class Variable(val name: String) : Expr()
}

// ---------------------------------------------------------------------------------------------
// 1. The Kotlin way — exhaustive `when`, no visitor at all.
// ---------------------------------------------------------------------------------------------

/**
 * Each operation is a plain (extension) function. Adding an operation means adding a function;
 * nothing in the hierarchy changes — which is exactly what Visitor promises, achieved without an
 * interface, an `accept` method, or double dispatch.
 *
 * The exhaustiveness guarantee is the same as Visitor's: add a node type and *every* `when` here
 * stops compiling until it is handled. That is the property that makes both approaches safe.
 */
fun Expr.evaluate(scope: Map<String, Double> = emptyMap()): Double = when (this) {
    is Expr.Num -> value
    is Expr.Add -> left.evaluate(scope) + right.evaluate(scope)
    is Expr.Mul -> left.evaluate(scope) * right.evaluate(scope)
    is Expr.Neg -> -operand.evaluate(scope)
    is Expr.Variable -> scope[name] ?: throw IllegalArgumentException("unbound variable '$name'")
}

/** A second operation. Note the first one did not have to change, and neither did [Expr]. */
fun Expr.format(): String = when (this) {
    is Expr.Num -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
    is Expr.Add -> "(${left.format()} + ${right.format()})"
    is Expr.Mul -> "${left.format()} * ${right.format()}"
    is Expr.Neg -> "-${operand.format()}"
    is Expr.Variable -> name
}

/** A third: constant folding, an operation that returns a transformed tree. */
fun Expr.simplify(): Expr = when (this) {
    is Expr.Num, is Expr.Variable -> this
    is Expr.Neg -> when (val inner = operand.simplify()) {
        is Expr.Num -> Expr.Num(-inner.value)
        else -> Expr.Neg(inner)
    }

    is Expr.Add -> {
        val l = left.simplify()
        val r = right.simplify()
        when {
            l is Expr.Num && r is Expr.Num -> Expr.Num(l.value + r.value)
            l is Expr.Num && l.value == 0.0 -> r
            r is Expr.Num && r.value == 0.0 -> l
            else -> Expr.Add(l, r)
        }
    }

    is Expr.Mul -> {
        val l = left.simplify()
        val r = right.simplify()
        when {
            l is Expr.Num && r is Expr.Num -> Expr.Num(l.value * r.value)
            l is Expr.Num && l.value == 0.0 -> Expr.Num(0.0)
            r is Expr.Num && r.value == 0.0 -> Expr.Num(0.0)
            l is Expr.Num && l.value == 1.0 -> r
            r is Expr.Num && r.value == 1.0 -> l
            else -> Expr.Mul(l, r)
        }
    }
}

/** Variable collection — an operation that accumulates rather than computes. */
fun Expr.variables(): Set<String> = when (this) {
    is Expr.Num -> emptySet()
    is Expr.Variable -> setOf(name)
    is Expr.Neg -> operand.variables()
    is Expr.Add -> left.variables() + right.variables()
    is Expr.Mul -> left.variables() + right.variables()
}

// ---------------------------------------------------------------------------------------------
// 2. The classic Visitor — still useful in one case.
// ---------------------------------------------------------------------------------------------

/**
 * Use this form when the *set of operations* must be extensible by code you do not control — a
 * plugin API, or a library whose users add their own traversals over a hierarchy you own.
 *
 * A `when` cannot be extended by a third party without them editing your function; a visitor
 * interface can be implemented from outside. That is the entire remaining advantage.
 */
interface ExprVisitor<R> {
    fun visitNum(node: Expr.Num): R
    fun visitAdd(node: Expr.Add): R
    fun visitMul(node: Expr.Mul): R
    fun visitNeg(node: Expr.Neg): R
    fun visitVariable(node: Expr.Variable): R
}

/**
 * The dispatch. In Java this must be an `accept` method on each node (double dispatch); in Kotlin
 * one `when` does the job, so nodes stay free of visitor plumbing.
 */
fun <R> Expr.accept(visitor: ExprVisitor<R>): R = when (this) {
    is Expr.Num -> visitor.visitNum(this)
    is Expr.Add -> visitor.visitAdd(this)
    is Expr.Mul -> visitor.visitMul(this)
    is Expr.Neg -> visitor.visitNeg(this)
    is Expr.Variable -> visitor.visitVariable(this)
}

class DepthVisitor : ExprVisitor<Int> {
    override fun visitNum(node: Expr.Num) = 1
    override fun visitVariable(node: Expr.Variable) = 1
    override fun visitNeg(node: Expr.Neg) = 1 + node.operand.accept(this)
    override fun visitAdd(node: Expr.Add) = 1 + maxOf(node.left.accept(this), node.right.accept(this))
    override fun visitMul(node: Expr.Mul) = 1 + maxOf(node.left.accept(this), node.right.accept(this))
}

/**
 * ## The expression problem, stated plainly
 *
 * You can have easy new *types* or easy new *operations*, not both:
 *
 * - **`when` over a sealed hierarchy** — adding an operation is trivial (one function); adding a
 *   node type breaks every `when`, but the compiler shows you exactly where.
 * - **Visitor** — adding an operation is trivial (one visitor class); adding a node type breaks
 *   every visitor implementation, including third-party ones you cannot fix.
 * - **Methods on the nodes themselves** — adding a node type is trivial; adding an operation means
 *   editing every node class.
 *
 * For a hierarchy *you own*, sealed + `when` wins on every axis: less code, exhaustiveness checking,
 * smart casts, and the compiler points at every site that needs updating.
 *
 * ## Where this shows up
 *
 * ASTs and query builders, document/report trees, rule engines, serialisation over a closed
 * hierarchy, and — the everyday case — anywhere a sealed domain model needs several interpretations.
 */
