# Observer

**Intent** — notify a set of dependents automatically when a subject changes.

## Three levels in Kotlin

**1. `Delegates.observable` / `Delegates.vetoable`** — property-level, zero infrastructure.
`observable` fires *after* assignment; `vetoable` fires *before* and can reject it by returning
false. Between them they cover a lot of what people build whole listener frameworks for.

**2. Hand-rolled subject** — full control; the place to learn the pitfalls.

**3. `StateFlow` / `SharedFlow`** — the production answer for anything asynchronous or
multi-consumer.

## StateFlow vs SharedFlow — get this right

| | `StateFlow` | `SharedFlow` |
|---|---|---|
| models | a **value** (state) | a **stream of events** |
| initial value | required, always present | none |
| late subscriber | immediately gets current value | gets nothing (unless `replay`) |
| conflation | yes — slow collector sees latest only | no (with buffer) |

Getting it backwards is the usual Flow mistake: modelling events as `StateFlow` **silently drops
them**; modelling state as `SharedFlow` leaves late subscribers with nothing to show.

Expose read-only views (`asStateFlow()`, `asSharedFlow()`) so only the subject can emit.

## If you hand-roll it, three decisions are mandatory

1. **`CopyOnWriteArrayList`, not `ArrayList`.** An observer that unsubscribes *during* notification
   throws `ConcurrentModificationException`. Most common observer bug; only shows up under load.
2. **`subscribe` returns an unsubscribe handle.** Subjects hold strong references to observers, so a
   forgotten unsubscribe is a memory leak. A returned handle makes cleanup natural.
3. **Isolate exceptions.** One failing observer must not stop the others being notified, nor
   propagate back into the publisher.

## Spring

`ApplicationEventPublisher` + `@EventListener` is Observer with the wiring done. And
`@TransactionalEventListener` handles something hand-rolled buses get wrong: fire *after* commit, so
observers never see state that gets rolled back.

## Trade-offs

**Untraceable control flow.** With enough indirection nobody can answer "what happens when I publish
this?". Events are for *decoupling*, not for hiding a call you could have made directly.

**Reentrancy.** An observer that mutates the subject re-enters `publish`. Copy-on-write helps; a
queue is the real fix.
