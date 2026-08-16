# Singleton

**Intent** — exactly one instance of a type, with a global access point.

## Kotlin idiom

`object Foo { ... }` *is* the pattern. The compiler emits a static `INSTANCE` field initialised in
the static initialiser, so the JVM's class-initialisation lock gives you thread-safe lazy creation
with no double-checked locking, no `volatile`, no `synchronized`.

When the instance needs constructor arguments (`object` can't take them), use a private constructor
plus `by lazy` in the companion — `lazy` defaults to `SYNCHRONIZED` mode, which is double-checked
locking in one word.

## When to use

- Stateless helpers and constant holders (`object AppInfo`).
- Process-wide registries that genuinely have no sensible second instance.
- Sealed-hierarchy members with no data — `object Loading : UiState` costs one allocation total.

## Production use case

Configuration constants, a JSON `ObjectMapper` shared across a service, an enum-like registry of
supported currencies.

## Trade-offs — read before using

An `object` is **global mutable state**. It cannot be swapped for a fake, it survives across tests
in the same JVM, and it is the most common source of order-dependent test failures. If your
singleton grows a `reset()` method for tests, that is the design telling you it should be a bean.

In a Spring codebase, prefer `@Component`: it is a singleton scoped to the application context,
constructor-injectable, replaceable in a test slice, and destroyed with the context. Reach for
`object` only for immutable data.

Mutable fields inside a singleton must be thread-safe on their own — `AtomicLong`,
`ConcurrentHashMap`, not `var`.
