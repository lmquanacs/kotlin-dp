# Prototype

**Intent** — create new objects by copying an existing instance rather than constructing from
scratch.

## Kotlin idiom

`data class` → `copy()`. That's the whole pattern, and it's strictly better than Java's version:
`clone()` bypasses constructors, `Cloneable` is a marker interface that doesn't declare `clone`, and
the default is a shallow copy. `copy()` calls the primary constructor, so `init` blocks and
validation still run, and it takes named arguments for the fields you want changed.

```kotlin
val doc = CONFIDENTIAL_TEMPLATE.copy(title = "Q3 Report", author = "quan")
```

## When to use

- Pre-configured templates cloned per use (report configs, request defaults, test fixtures).
- The expensive part of creation is *configuration*, not allocation — assemble a tuned object graph
  once at startup, copy it per request.
- Immutable update: "same thing, one field different."

## The trap

**`copy()` is shallow.** Mutable fields are shared with the original, and mutating through one is
visible through the other — the same defect `clone()` has. Keep data class fields immutable
(`List`, not `MutableList`) and the problem cannot occur. When you genuinely need mutable state,
write an explicit `deepCopy()` that rebuilds the mutable members.

Second, subtler point: `copy()` passes the *current* value of every field you don't name, so
defaults are **not** re-applied. A field explicitly set to `null` stays `null`.

## Production use case

Building a per-request config from a template; snapshotting state for undo (see Memento); test data
builders — `validUser.copy(email = "bad")` is the clearest way to express "valid except one thing."

## Trade-offs

For deep copies of large graphs, a serialisation round-trip (Jackson, kotlinx.serialization) is
simpler and less error-prone than hand-written `deepCopy()` chains — at a real CPU cost. Measure.
