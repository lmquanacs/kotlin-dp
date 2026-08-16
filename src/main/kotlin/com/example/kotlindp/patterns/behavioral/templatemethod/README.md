# Template Method

**Intent** — define the skeleton of an algorithm in one place; let subclasses fill in specific steps
without changing its structure.

This is the most inheritance-bound GoF pattern, and Kotlin offers a better composition-based
alternative in most cases.

## Three forms, in order of preference

**1. Higher-order function** — steps as parameters, defaults for optional hooks.

```kotlin
fun <T> runImport(
    source: String,
    parse: (String) -> T,
    write: (List<T>) -> Int,
    validate: (T) -> Boolean = { true },   // ← this replaces "optional hook"
): ImportReport
```

No base class, steps testable alone, and the default is visible **in the signature** rather than in a
base class you have to go read.

**2. `inline fun` with a lambda** — for "setup, run caller's block, always teardown". This is how
`use`, `runCatching`, and `transaction { }` are written. `inline` means no allocation and non-local
`return` works inside the block.

**3. Inheritance** — worth it when several subclasses share substantial state and many steps.

## If you use the inheritance form, two rules

- **The template method must not be `open`.** Kotlin gets this right by default: methods are final
  unless marked `open`, so a subclass can't override the skeleton and defeat the point. (In Java you
  must remember `final`; here you must remember `open` — the safer default.)
- **Separate required steps from hooks.** `abstract` = required. `open` with a default body =
  optional. Keeping those distinct is what stops the base class becoming a burden.

## The classic complaint

Template Method inverts control — the base class calls you. Fine until it grows a dozen hooks, at
which point nobody can predict execution order and subclasses couple to the parent's internals.
**If a hook needs to know *when* it's called, the abstraction has already failed.** Switch to
composition.

## In the wild

Spring is full of it: `JdbcTemplate`, `RestTemplate`, `TransactionTemplate`. Note these take a
*callback* rather than requiring a subclass — Spring itself moved to form #1.
