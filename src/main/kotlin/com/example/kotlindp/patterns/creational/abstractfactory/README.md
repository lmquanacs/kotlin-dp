# Abstract Factory

**Intent** — create *families* of related objects without naming concrete classes, and make it
impossible to mix members of different families.

Factory Method picks one product. Abstract Factory picks a matched set. The value isn't the
indirection, it's the constraint: one factory produces both the connection and the dialect, so a
Postgres connection can never be paired with MySQL paging syntax.

## Kotlin idiom

Stateless factories are `object`s, so a whole family costs zero allocations. Products stay
`private`, and the vendor selector is a `when` over an enum.

For small families (one or two roles), a data class of function references is lighter than an
interface hierarchy and gives the same "members travel together" guarantee:

```kotlin
data class Persistence(
    val connection: (host: String, db: String) -> Connection,
    val paginate: (String, Int, Int) -> String,
)
```

## When to use

Several products must be consistent with each other, and the whole set varies along one axis
(vendor, platform, protocol version, tenant).

## Production use case

Database vendor portability; cloud provider abstraction (S3 vs GCS blob store + signer + lifecycle
policy); test doubles — one `InMemoryFactory` replaces an entire family in a suite.

## Trade-offs

Adding a **product role** means changing the factory interface and every implementation. Adding a
**family** is cheap. So this pattern is right when families change often and roles don't — and
badly wrong when it's the other way around.

In Spring, don't hand-roll it: declare `PersistenceFactory` beans behind `@ConditionalOnProperty`
and inject the interface. The container is already an abstract factory.
