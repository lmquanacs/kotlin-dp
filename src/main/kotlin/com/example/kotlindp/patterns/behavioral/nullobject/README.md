# Null Object

**Intent** — provide a do-nothing implementation of an interface so callers never have to null-check.

## The honest framing

This pattern is a response to Java's null problem, and **Kotlin already solved that problem** with
nullable types, `?.`, and `?:`. So the useful part isn't the pattern — it's knowing when it still
earns its place.

Most of the time the answer is: use the language.

```kotlin
class OrderService(private val audit: AuditSink? = null) {
    fun place(id: String) { audit?.record("order.placed $id") }
}
```

Plus the stdlib's built-in null objects: `emptyList()`, `emptyMap()`, `emptySequence()`,
`orEmpty()`, `?: default`.

## When it still wins

**1. An optional collaborator called from many places** — metrics, audit, tracing. A no-op default
beats twenty `?.` calls:

```kotlin
object NoOpAuditSink : AuditSink { override fun record(event: String) = Unit }
class OrderService(private val audit: AuditSink = NoOpAuditSink)
```

`object` means one shared instance, zero allocation.

**2. The absent case has a *name* in the domain** — this is the variant genuinely worth having:

```kotlin
sealed class Principal {
    data class Authenticated(val id: String, val roles: Set<String>) : Principal()
    object Anonymous : Principal()          // a participant, not an absence
}
```

`Anonymous` answers the same questions a real user does, so callers branch only where the
distinction matters, and the exhaustive `when` guarantees they don't forget. Strictly more
informative than `User?`.

**3. A default swappable in tests** without a mocking framework.

## The rule that makes it safe

**A null object must be valid and silent — never throwing.** A null object that throws is *worse*
than a null, because it fails later and further from the cause.

## When not to use it

One or two call sites — `?.` is clearer. And when the caller genuinely must react to absence: a null
object that silently swallows a missing configuration turns a startup failure into a mystery at 3am.
**Absence that matters should be loud, not polite.**
