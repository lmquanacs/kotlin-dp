# Memento

**Intent** — capture an object's internal state so it can be restored later, **without exposing that
state**.

The encapsulation clause is the whole point and the part usually botched. If the snapshot is just the
object's public fields, you haven't applied Memento — you've leaked the internals, and anyone can now
construct an invalid state.

## Kotlin idiom

**If your state is already an immutable data class, every value is its own memento.** No snapshot
type, no copying logic, no risk of a shallow copy sharing mutable structure:

```kotlin
data class FormState(val fields: Map<String, String>, val step: Int)

fun apply(transform: (FormState) -> FormState) {
    past += state
    state = transform(state)
    future.clear()          // new action invalidates the redo branch
}
```

This is the version for new code. The full pattern matters when you're wrapping a genuinely mutable
object you don't control.

For that case, the encapsulation comes from a `private constructor` plus `internal` fields: the
caretaker can hold the memento and hand it back, and can do nothing else with it. Expose only a
`label` so a UI can render the undo stack without reading state.

## Memento vs Command

Both give you undo, by opposite means:

| | stores | restore | memory |
|---|---|---|---|
| Memento | **state** | trivial | proportional to state size |
| Command | **intent** | each command must reverse itself — often the hard part | tiny |

Real systems mix them: commands for the log, periodic mementos as checkpoints so replay doesn't
start from zero. That's exactly how database recovery and event-sourced systems work.

## Production use case

Editor undo/redo; multi-step form wizards with back navigation; transaction rollback in memory;
optimistic UI (snapshot, apply, restore on server rejection); saga compensation checkpoints.

## Trade-offs

Full snapshots of large state are expensive. Mitigate by bounding the history, snapshotting every N
operations, or using persistent/structurally-shared data structures so unchanged parts aren't copied.
