# Observer via ApplicationEventPublisher

Spring's event mechanism is Observer with the registry, dispatch, and optional async delivery
supplied by the container — including the `CopyOnWriteArrayList` and unsubscribe handling that
`behavioral/observer` shows are easy to get wrong by hand.

Its real purpose: **decouple a core use case from its side effects.** Placing an order shouldn't
require the order service to know about email, analytics, and inventory.

## Shape

```kotlin
@Service
class OrderService(private val events: ApplicationEventPublisher) {
    fun place(...): String {
        // ...
        events.publishEvent(OrderPlaced(orderId, customerId, totalCents))
    }
}

@Component
class EmailListener {
    @EventListener
    fun on(event: OrderPlaced) { ... }
}
```

Since Spring 4.2 events need not extend `ApplicationEvent` — any object works. Use a **`data class`**
so events are immutable; a listener that mutates an event is a bug that surfaces only when listener
order changes.

**Include the data listeners need.** An event carrying only an ID forces every listener to hit the
database, converting one write into N reads.

## The three things to know

**1. Publication is synchronous by default.** Listeners run on the publisher's thread, inside its
transaction, before `publishEvent` returns. A slow listener slows the request; a **throwing listener
rolls back the publisher's transaction**. This surprises people who assume "event" means
"asynchronous".

**2. `@Async` changes failure semantics entirely.** The listener runs on another thread — outside the
transaction, no rollback, and exceptions go nowhere unless you configure an
`AsyncUncaughtExceptionHandler`. Fine for email; wrong for anything that must not be lost.

**3. `@TransactionalEventListener` is the one worth remembering.**

```kotlin
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
fun on(event: OrderPlaced) { mailer.sendReceipt(event) }
```

Fires *after commit*, so a listener never acts on state that gets rolled back — the classic bug in
hand-rolled event buses (confirmation email for an order whose transaction then fails). Note it needs
an active transaction; with none it silently doesn't fire unless `fallbackExecution = true`.

## `condition` and `@Order`

`@EventListener(condition = "#event.totalCents > 100000")` is a SpEL filter evaluated before the
listener runs — keeps an `if (...) return` out of the body and self-documents at the declaration.

`@Order` sequences listeners for the same event. **If you need it, ask whether the listeners are
really independent** — ordered listeners are a hidden workflow, and a workflow belongs in a service.

## When not to use events

When the "listener" is really the next step of the use case. If an order isn't placed until inventory
is reserved, that's a direct call in a transactional service. Events mean *"this happened, react if
you care"*, not *"now do this"*.

The cost: read the publisher and you **cannot tell what happens** when an order is placed. Use events
for genuinely independent side effects, not to hide a call you could have made directly.

For cross-service delivery you need a real broker plus the transactional outbox pattern — Spring
events are in-process only and lost on a crash.
