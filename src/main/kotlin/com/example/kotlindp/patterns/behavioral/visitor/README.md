# Visitor

**Intent** — add new operations to an object structure without modifying the classes in it.

## The honest summary

Visitor exists in Java to work around a missing language feature. You can't pattern-match on type,
so the double-dispatch dance (`accept(visitor)` → `visitor.visitX(this)`) recovers the concrete type.
**Kotlin has sealed classes and exhaustive `when`, which is the same capability with none of the
ceremony.**

```kotlin
fun Expr.evaluate(scope: Map<String, Double>): Double = when (this) {
    is Expr.Num      -> value
    is Expr.Add      -> left.evaluate(scope) + right.evaluate(scope)
    is Expr.Variable -> scope[name] ?: error("unbound '$name'")
}
```

Adding an operation = adding a function. Nothing in the hierarchy changes — exactly what Visitor
promises, with no interface, no `accept`, no double dispatch. And you get the same safety guarantee:
add a node type and *every* `when` stops compiling until it's handled.

## When the classic form still wins

Exactly one case: **the set of operations must be extensible by code you don't control** — a plugin
API, or a library whose users add traversals over a hierarchy you own. A `when` can't be extended by
a third party without editing your function; a visitor interface can be implemented from outside.

Even then, Kotlin lets you skip the `accept` method on every node — one `when` does the dispatch, so
nodes stay free of visitor plumbing.

## The expression problem, stated plainly

You get easy new *types* or easy new *operations*, not both:

| Approach | New operation | New type |
|---|---|---|
| sealed + `when` | trivial (one function) | breaks every `when` — **compiler shows you where** |
| Visitor | trivial (one class) | breaks every visitor, including third-party ones you can't fix |
| Methods on nodes | edit every node class | trivial |

For a hierarchy **you own**, sealed + `when` wins on every axis: less code, exhaustiveness checking,
smart casts, and the compiler points at every site needing an update.

## Production use case

ASTs and query builders; document/report trees; rule engines; serialisation over a closed hierarchy;
and the everyday case — a sealed domain model with several interpretations (`evaluate`, `format`,
`simplify`, `variables` in this folder).
