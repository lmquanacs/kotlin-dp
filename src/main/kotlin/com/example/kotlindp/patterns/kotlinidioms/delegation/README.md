# Delegation

Kotlin has **two** delegation features sharing one keyword. Confusing them is common.

1. **Class delegation** — `class A(b: B) : I by b`. The compiler generates a forwarding method for
   every member of `I`.
2. **Property delegation** — `val x by delegate`. Reads/writes route to the delegate's
   `getValue`/`setValue`.

## Class delegation

"Favour composition over inheritance" is easy advice and tedious to follow in Java, because
composition means one forwarding method per interface member. `by` removes that tax entirely — which
is why Kotlin can make classes `final` by default without pain.

```kotlin
class ValidatingEventStore(private val d: EventStore) : EventStore by d {
    override fun append(e: String) { if (e.isNotBlank()) d.append(e) }
}
```

**The trap: delegation is not inheritance.** There's no virtual dispatch back into the delegating
class. If a delegate method calls another method on itself, that call resolves to the *delegate's*
implementation — never to your override. Usually what you want (no fragile base class), but it
surprises people expecting template-method behaviour.

## Standard property delegates

| Delegate | Behaviour |
|---|---|
| `by lazy { }` | computed on first access, cached; thread-safe by default |
| `Delegates.observable` | callback **after** assignment |
| `Delegates.vetoable` | callback **before**; return false to reject |
| `Delegates.notNull()` | `lateinit` for primitives — throws if read before set |
| `by map` | map-backed property; typed façade over a schemaless payload |

`lazy` defaults to `SYNCHRONIZED`. Use `LazyThreadSafetyMode.NONE` only when access is provably
single-threaded — it's faster and offers no guarantee at all.

Map-backed properties throw `NoSuchElementException` on *access*, not construction — a convenience,
not a validation mechanism.

## Writing your own

Implement `ReadOnlyProperty<R, T>` or `ReadWriteProperty<R, T>`. Three shapes worth having in a
production codebase:

- **`Audited`** — records every write with the property name. An audit trail added by one word at the
  declaration site.
- **`Validated`** — fails at the *assignment*, so the invalid value never enters the object. Far
  easier to debug than discovering it three layers later.
- **`Derived`** — computes from `thisRef`. Always consistent, impossible to leave stale — the usual
  bug with a manually-maintained cached field.

## `provideDelegate`

`operator fun provideDelegate(thisRef, property)` runs logic *at property creation* — validating the
name, registering it, or picking a different delegate based on it. This is how Gradle's `by project`
and DI frameworks' `by inject()` work.

## Costs

Each delegated property allocates a delegate object and adds an indirection per access. Matters in
hot loops, nowhere else. `lazy` in particular is a real object with a volatile field, not free.
