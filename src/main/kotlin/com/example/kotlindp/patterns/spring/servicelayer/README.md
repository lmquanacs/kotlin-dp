# Service layer — Facade + Repository + typed errors at the edge

The layered arrangement most Spring applications use, with the three decisions that stop it
degenerating into anaemic pass-through classes.

**1. The service is a Facade** (`structural/facade`) — one method per use case, owning orchestration
and compensating actions.

**2. The domain returns sealed results** (`kotlinidioms/result`), not exceptions, for *expected*
failures.

**3. DTOs are separate from domain types**, translated at the edge in both directions.

## Sealed failures → HTTP status

```kotlin
sealed class OrderFailure {
    data class UnknownProduct(val sku: String) : OrderFailure()
    data class InsufficientStock(val sku: String, val available: Int, val requested: Int) : OrderFailure()
    data class InvalidQuantity(val requested: Int) : OrderFailure()
}

private fun OrderFailure.toHttp(): Pair<HttpStatus, ErrorResponse> = when (this) {
    is OrderFailure.UnknownProduct    -> NOT_FOUND to ...
    is OrderFailure.InsufficientStock -> CONFLICT to ...
    is OrderFailure.InvalidQuantity   -> BAD_REQUEST to ...
}
```

The exhaustive `when` means a new failure mode **cannot silently become a 500**. And status codes are
chosen per failure — collapsing them all to 400 throws away the information the sealed hierarchy
exists to preserve.

## Why DTOs, really

Serialise the domain type directly and the wire format *is* the domain model — you can no longer
rename a field, add an internal-only property, or change a representation without breaking clients.
The DTO is a seam that lets the two evolve apart.

Keep mappers as **extension functions**, so the domain type stays unaware a DTO exists.

## Repository as a port

Declaring it as an interface in the domain's language is what makes the service unit-testable: a test
supplies a map-backed implementation — no database, no Spring context, no mocking framework.

With Spring Data, the interface is all you write; the implementation is a dynamic proxy
(`structural/proxy`).

## `@RestControllerAdvice`

Chain of Responsibility for *unexpected* exceptions — the ones the sealed result deliberately doesn't
model. Expected failures are values handled by the controller's `when`; genuinely exceptional ones
land here as a 500 with a **safe** message. Never let a raw exception message reach a client.

Scope it with `basePackages` so it can't change behaviour elsewhere in the app.

## Layer rules

| Layer | May depend on | Must not know about |
|---|---|---|
| Controller | service, DTOs | repositories, SQL |
| Service | repositories, domain | HTTP, `HttpStatus`, servlets |
| Repository | domain | services, DTOs |
| Domain | nothing | all of the above |

The most valuable row is the middle one: **no `HttpStatus` in a service.** The moment a service
returns HTTP concepts it can only be called from a controller — and the scheduled job that needs the
same use case has to duplicate it.

## The anaemic-layer smell

If a service method is a one-line delegation to a repository, the layer is pure ceremony. Either it
owns real behaviour — validation, orchestration, transactions, compensation — or it shouldn't exist.
