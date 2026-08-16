# Factory Method

**Intent** — let a method decide which concrete class to instantiate, so callers depend on an
interface rather than a constructor.

## Kotlin idiom

Three spellings, in increasing order of ceremony:

1. **Companion function** — `NotificationFactory.create(Channel.EMAIL)`. Covers 90% of cases.
2. **`operator fun invoke` on the companion** — `Transport("https")` reads like a constructor but is
   a function call, so you can later add caching or return a shared instance without touching any
   call site. This is how the stdlib's pseudo-constructors work.
3. **Abstract creator** — the literal GoF form. Worth it only when the creator itself has behaviour
   that varies alongside the product.

Two details that carry a lot of weight:

- Make concrete products `internal`/`private`. Then the factory isn't a convention people are asked
  to follow, it's the only reachable door.
- Dispatch on an `enum` or sealed type, not a `String`. `when` over an enum used as an expression is
  checked for exhaustiveness, so adding a channel becomes a compile error at every site that must
  handle it — instead of a runtime `IllegalArgumentException` in production.

## When to use

The set of implementations is closed and known at compile time, and the choice is data-driven
(a config value, a column in a row, a request field).

## Production use case

Picking a notification channel from a user preference; building a payment gateway client from a
provider code; choosing a parser from a file extension.

## Trade-offs

If the choice is *static* — one implementation per environment — you don't need this pattern, you
need dependency injection. Spring's `@Bean`/`@Profile`/`@ConditionalOnProperty` already is a
factory, and a hand-written one on top just adds a layer.

Its natural limit is a single product type. Once you're creating *families* of objects that must
match each other, move to Abstract Factory.
