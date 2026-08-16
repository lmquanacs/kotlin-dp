# Strategy

**Intent** — a family of interchangeable algorithms, selected at runtime.

## Kotlin idiom

**A function type is already a strategy interface.** In Java this pattern needs an interface plus one
class per algorithm; in Kotlin most strategies are lambdas — no hierarchy, no allocation when
inlined, testable without a mocking framework.

```kotlin
typealias DiscountStrategy = (Basket) -> Long
class Checkout(private val discount: DiscountStrategy = { 0 })
```

The `typealias` costs nothing at runtime and makes signatures read as intent instead of plumbing.

## Choosing the form

| Form | Use when |
|---|---|
| Function type | single method, no identity — **the default** |
| Interface | strategy must be named (persisted, logged, config-selected) or carries state |
| Enum with abstract member | closed set, no per-instance configuration |

A bare lambda can't tell you which algorithm it is. The moment you need to persist the choice or log
it, go to an interface with a `code`.

Enums can declare abstract members and override them per constant — an exhaustive, serialisable,
`when`-friendly strategy family in a dozen lines. But you can't extend an enum, so if third parties
must add implementations, use the interface.

## Spring integration

Inject `List<ShippingStrategy>` or `Map<String, ShippingStrategy>` and the container supplies every
implementation. Adding a strategy becomes "add a `@Component`" and nothing else changes.

## Production use case

Pricing/discount rules, shipping cost calculation, retry backoff policies, serialisation formats,
auth token validation per issuer.

## Trade-offs

**A `when` block is a fine strategy selector for a small closed set.** It only becomes a problem when
the same `when` is copy-pasted across the codebase, because then adding a case means finding every
copy. Extract to a strategy at the *second* occurrence, not the first.

Strategies should compose (`bestOf(tenPercentOff, bulkDiscount)`) rather than accrete flags.
