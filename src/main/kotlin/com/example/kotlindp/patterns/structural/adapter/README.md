# Adapter

**Intent** — make an existing class usable through an interface it wasn't written for.

## Kotlin idiom

Three forms, cheapest first:

**1. Extension function** — zero-cost. Compiles to a static method, allocates nothing, no wrapper
class. Use when adaptation means "add convenience methods."

```kotlin
fun LegacyPaymentSdk.chargeDollars(account: String, dollars: Double): Boolean = ...
```

Limitation: extensions are resolved *statically*. They are not virtual and cannot satisfy an
interface contract. Need polymorphism → use a wrapper class.

**2. `by` delegation** — when the target interface is wide but mostly matches. Forwards every member
automatically; override only what changes. In Java this is one hand-written forwarding method per
interface member, which silently rots when someone adds a method to the interface.

```kotlin
class CachingRepositoryAdapter<T>(d: Repository<T>) : Repository<T> by d {
    override fun findById(id: String) = cache.getOrPut(id) { d.findById(id) }
}
```

**3. Object adapter** — plain composition. The workhorse. Prefer it to class adapters (inheritance);
Kotlin classes are `final` by default anyway, which nudges you the right way.

## When to use

Wrapping a third-party SDK, a legacy service, or any API whose vocabulary doesn't match your domain.

## Production use case

The one in this folder: an SDK that speaks `Double` dollars, numeric ISO currency codes, and integer
status codes, adapted to a domain that speaks `Money(cents, "USD")` and a typed result. The point is
that `840` and float arithmetic stop at the adapter boundary instead of spreading through the
codebase.

## Trade-offs

An adapter per third-party call is real overhead — worth it at integration boundaries you expect to
outlive the vendor, wasteful around a stable library you'd never swap.

Keep adapters *thin*: translation only. The moment one starts making decisions it's become a Facade
or a service, and the seam it was protecting is gone.
