# Typed errors: sealed results, Either, Result

## The design question

**Which failures belong in the type system, and which belong in exceptions?**

The rule that holds up in production:

- **Expected failures are values** — validation errors, "not found", a declined payment. They're part
  of the function's contract and callers must handle them.
- **Unexpected failures are exceptions** — broken socket, a bug, OOM. Callers can't meaningfully
  handle them; let them propagate to a boundary that logs and gives up.

Encoding *expected* failures as exceptions is the mistake: the signature lies, the compiler can't
help, and handling scatters across `catch` blocks far from the cause.

## Prefer a domain-specific sealed hierarchy

More useful than a generic `Either<String, T>` because it **names** the failures:

```kotlin
sealed class PaymentError {
    data class InsufficientFunds(val shortfallCents: Long) : PaymentError()
    data class Declined(val code: String, val retryable: Boolean) : PaymentError()
    object RateLimited : PaymentError()
}
```

Callers `when` over them exhaustively, and adding a failure mode flags every call site. Compare
`fun charge(): String  // throws PaymentException` — nothing in the signature says a decline is
possible, and the retryable/non-retryable distinction is lost.

## Either, for combinators

`out L, out R` makes it covariant, which is what lets `right()`/`left()` have clean types via
`Nothing`. The useful operations: `map`, `flatMap`, `mapLeft`, `fold`, `getOrElse`.

**Fail-fast vs accumulate is a real design decision**, and one exceptions can't express at all
because the first `throw` ends the story:

```kotlin
// fail-fast: flatMap short-circuits, later validations never run
validateEmail(x).flatMap(::validateAge).flatMap(::validateCountry)

// accumulate: run everything, collect all errors — right for form validation
```

## `runCatching` — two sharp edges

1. **It catches `Throwable`**, including `CancellationException`. Swallowing that inside a coroutine
   **breaks structured concurrency** — the coroutine keeps running after its scope was cancelled.
   Rethrow it explicitly, or don't use `runCatching` in coroutine code.
2. **`kotlin.Result` is untyped in the failure** — it carries a `Throwable`, so you're back to
   inspecting exception types. Fine at an integration boundary; poor for domain errors.

## Choosing

| Situation | Use |
|---|---|
| Domain failures with distinct handling | sealed result hierarchy |
| Generic pipeline needing map/flatMap | `Either` |
| Wrapping a throwing library call | `runCatching` |
| Genuinely exceptional / unrecoverable | exceptions |
| Absence, no explanation needed | `T?` |

Don't convert everything to `Either`. `T?` with `?:` is simpler when the only information is "nothing
there", and exceptions remain right for programmer errors.

## In Spring

Keep typed results inside the domain and translate at the edge: a controller maps
`InsufficientFunds` → 402, `RateLimited` → 429. Don't let `Either` leak into HTTP responses, and
don't let `HttpStatus` leak into the domain.
