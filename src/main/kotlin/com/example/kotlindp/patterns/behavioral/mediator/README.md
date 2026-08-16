# Mediator

**Intent** — replace direct many-to-many references between components with a hub they all talk to,
turning an N² web of dependencies into N.

The symptom that calls for it: five components each holding references to the other four, and any
change to one rippling through all of them. The mediator holds the *interaction rules*; components
know only the mediator.

## Mediator vs Observer

They look similar and solve different problems.

- **Observer** — one-to-many broadcast. The subject doesn't know or care who listens.
- **Mediator** — many-to-many coordination. The hub knows every participant and encodes the *rules*
  between them.

**If your mediator has no rules and only forwards, you wanted an event bus (Observer).**

## Kotlin idiom

Rather than one growing `when`, register handlers per event type with `reified`:

```kotlin
inline fun <reified E : Any> on(noinline handler: (E) -> Unit)

mediator.on<UserRegistered> { sendWelcomeEmail(it.email) }
mediator.on<OrderPlaced>    { reserveInventory(it.orderId) }
```

This keeps the mediator open to extension instead of accreting branches. (`noinline` is needed
because the handler is stored, not called inline.) For a closed event set, a sealed hierarchy plus
`when` is the alternative.

## The failure mode

**The mediator concentrates coupling rather than removing it.** A `FormMediator` is fine; a
`SystemMediator` with 40 participants is a god object that everything depends on and nothing can be
tested without.

Keep one mediator per cohesive interaction group, and prefer the handler-registration form so it
stays open to extension.

## Production use case

UI form coordination (this folder's example: field changes enable/disable submit and update status,
with no field knowing about the button); workflow/saga orchestration across services; chat rooms;
resource arbitration; Spring's `ApplicationEventPublisher` once you add rules on top.
