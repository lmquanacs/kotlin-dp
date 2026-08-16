# Command

**Intent** — turn a request into an object so it can be stored, queued, logged, parameterised, and
undone.

The give-away that you need it: you want to do something *with* an action rather than just perform
it — retry it, schedule it, audit it, replay it, reverse it.

## Kotlin idiom

**A lambda is already a command.** `() -> Unit` plus a deque is a working task queue, and that's the
right choice when you don't need undo.

What a lambda *can't* give you is **undo** and **serialisability**, which is why the interface form
still earns its keep.

## Three rules that are easy to get wrong

1. **Undo must capture enough state.** `DeleteText` has to remember the text it removed. This is the
   discipline the pattern imposes — undo is only possible if `execute` records enough.
2. **Macro undo runs in reverse order.** `commands.asReversed().forEach { it.undo() }`.
3. **Executing a new command clears the redo stack.** Otherwise redo replays against a history that
   no longer exists.

## Serialisable commands

For a queue that survives a restart, the command must be **data, not a closure** — a lambda can't be
persisted. Model it as a sealed data class and interpret it on the far side:

```kotlin
sealed class DocCommand {
    data class Insert(val at: Int, val text: String) : DocCommand()
    data class Delete(val at: Int, val length: Int) : DocCommand()
}
fun DocCommand.applyTo(doc: TextDocument) = when (this) { ... }
```

This is exactly how event sourcing, CQRS command buses, and write-ahead logs work: the intent is
stored, the effect is derived.

## Production use case

Undo/redo; job queues with retry; audit logs of *intent* rather than outcome; the transactional
outbox pattern; batch/macro operations.

## Trade-offs

A class per action. Don't convert every method call into a command — reach for it only when you
genuinely need to *keep* the action around.

A command queue that stops on the first exception leaves the system in an unknown state. Decide
explicitly: collect failures and continue, or stop and make the partial state recoverable.
