# Interpreter

**Intent** — represent a small language's grammar as a class hierarchy and evaluate sentences by
walking it.

## The under-appreciated point

**You have almost certainly already built one.** A feature-flag rule engine, a search filter, an
access-control policy, a pricing rule — each is a little language. The difference between "a mess of
nested ifs" and "an interpreter" is whether the grammar is *explicit*.

## Kotlin idiom

- **AST = sealed hierarchy**, split into terminal expressions (`Equals`, `GreaterThan`) and
  non-terminals that compose others (`And`, `Or`, `Not`).
- **Evaluation = exhaustive `when`.** Adding a rule type is a compile error at every interpretation
  and nowhere else. That's what makes a hand-written rule engine maintainable.
- **A DSL replaces the parser.** When rules are authored by developers, the Kotlin compiler *is* the
  parser — typos are compile errors and refactoring works:

```kotlin
val eligible = rule {
    "country" oneOf setOf("US", "CA")
    "age" gt 18
    any {
        "plan" eq "pro"
        "credits" gt 100
    }
}
```

(When rules are authored at runtime by *users*, you do need real parsing — but only the
`String` → AST step is missing; the tree and interpreters are unchanged.)

## The payoff: multiple interpretations

Making the grammar explicit means the same tree can be evaluated in memory, rendered to SQL,
rendered to an explanation ("matched because plan = pro"), optimised, or persisted. That last one —
explainability — is impossible once rules are buried in nested `if`s, and it's usually what someone
asks for six months in.

## Two things to get right before shipping one

1. **Bound the recursion depth** if rules come from users. A deeply nested tree is a stack-overflow
   DoS.
2. **Never build SQL by interpolation.** Emit parameter placeholders and a bound-argument list; a
   rule engine that concatenates strings is an injection vector.

## Trade-offs

Interpreter scales badly with grammar size — one class per rule, and every interpretation grows a
branch. Past roughly a dozen node types, use a parser generator (ANTLR) or an embeddable engine
(CEL, JEXL, MVEL) instead of hand-rolling.
