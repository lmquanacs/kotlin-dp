# Kotlin DSLs (type-safe builders)

A Kotlin DSL is five language features working together. Knowing which does what is the difference
between *designing* a DSL and copying one.

| Feature | Job |
|---|---|
| Function literal with receiver — `T.() -> Unit` | makes `this` the builder inside `{ }` |
| Trailing lambda syntax | lets `html { }` look like a language construct |
| Extension functions | adds DSL verbs to types you don't own |
| `infix` | removes dots and parens: `"age" gt 18` |
| `operator` (`invoke`, `unaryPlus`) | lets bare values mean something: `+"text"` |
| `@DslMarker` | stops nested scopes leaking into each other |

## The core mechanism

```kotlin
fun config(block: Config.() -> Unit): Config = Config().apply(block)
```

`apply` is literally this signature — `fun <T> T.apply(block: T.() -> Unit): T`. Every builder you
write is a variation of it.

## `@DslMarker` is not optional

Without it, inside `body { p { } }` **both** the `p` receiver and the `body` receiver are in implicit
scope, so misplaced calls compile happily and build the wrong tree. With it, only the innermost
receiver of a given marker is implicitly accessible; reaching out requires explicit `this@body`.

## Scope control beats validation

A DSL's real advantage over a config file is that the *compiler* enforces the schema. Push it
further and make ordering rules **structural**: put `retry` only on `HttpScope`, and it simply cannot
be called outside `http { }`. The invalid program can't be written — strictly stronger than
validating it afterwards.

## `infix` — when it reads well

Must be a member or extension, exactly one parameter, no `vararg`, no default.

It reads beautifully when the operation is genuinely binary and the name is a preposition or verb
(`shouldBe`, `to`, `until`, `downTo`), and badly otherwise. Note all infix calls share one precedence
level, lower than arithmetic — mixed expressions need parentheses.

## Rules that keep a DSL usable

1. Always `@DslMarker`.
2. Validate in `build()`, with messages naming the offending element.
3. Return an immutable product; never hand back the mutable builder.
4. Prefer named functions to clever operators. `+"text"` is idiomatic in HTML DSLs; inventing
   `unaryMinus` for "remove" is not.

## Where they're worth it

Gradle build scripts, Ktor routing, Exposed SQL, kotest/MockK assertions, HTML/JSON generation, test
fixtures, config that benefits from compile-time checking.

## Where they're not

A DSL costs ~3× a plain builder and is harder to debug — stack traces point into lambdas and IDE
navigation through receivers is worse than through method calls. It pays off when the DSL is written
**many times by many people**. Used once internally, it's a liability.
