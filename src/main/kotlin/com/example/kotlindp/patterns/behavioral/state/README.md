# State

**Intent** — an object changes behaviour when its internal state changes, so it appears to change
class. In practice: replace a tangle of boolean flags and `if` chains with an explicit state machine.

## Kotlin idiom — this is the big one

Sealed classes make **illegal states unrepresentable**. Compare:

```kotlin
// flags: can represent shipped=true, paid=false. Which nullable fields are valid when?
class Order(var paid: Boolean, var shipped: Boolean,
            var trackingCode: String?, var cancelReason: String?)

// sealed: the illegal combinations have no constructor
sealed class OrderState {
    object Draft : OrderState()
    data class Paid(val paymentRef: String) : OrderState()
    data class Shipped(val paymentRef: String, val trackingCode: String) : OrderState()
}
```

Each state carries exactly the data that state needs — no more nullable fields that are "only set
when status == SHIPPED", and no chance of reading one in the wrong state. Inside a `when` branch the
state is smart-cast, so `state.paymentRef` just works.

## Keep the transition pure

`transition(state, event) -> result` as a **pure function** — no mutation, no I/O — is what makes a
state machine testable. Every rule is one assertion, with no object to construct and no clock or
database to stub. Wrap it in a thin mutable shell if you need one.

The nested `when` is exhaustive over states, so adding a state makes the compiler point at the
transition function.

## GoF form vs sealed form

The classic spelling puts behaviour *on* the state object, each state returning the next
(`Open.close() -> Closed`). Use it when states carry substantial behaviour. Use sealed + `when` when
the interesting part is the transition *table* — which is most of the time, because a table you read
top to bottom beats behaviour scattered across a dozen classes.

## Production use case

Order lifecycles, payment flows, job/task status, connection state, circuit breakers, upload and
approval workflows — anywhere you currently have three booleans whose valid combinations live only
in someone's head.

## Trade-offs

**Persistence.** A sealed state maps to a discriminator column plus per-state fields. Write that
mapping explicitly; don't let Jackson polymorphic deserialisation infer it from class names, or a
class rename becomes a production data incident.

**Concurrency.** The mutable shell isn't thread-safe. Because the transition is pure, the fix is
cheap: hold state in an `AtomicReference` and `updateAndGet`, or confine it to one coroutine.
