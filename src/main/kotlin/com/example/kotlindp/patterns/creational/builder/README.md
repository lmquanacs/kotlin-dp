# Builder

**Intent** — build a complex object step by step, separating construction from representation.

This is the pattern Kotlin changed the most. Java needs Builder because it lacks default and named
arguments; with 8 optional fields you write either 2^8 constructors or a builder. Kotlin has both,
so **most Java builders should not be ported** — they become a data class with defaults.

## What survives

**1. Nothing — use named + default arguments.** The common case.

```kotlin
HttpRequest(url = "https://api.example.com", timeout = Duration.ofSeconds(5))
```

**2. Type-safe DSL builder.** For *nested* structures, which named arguments can't express well.
Built from function literals with receiver (`PipelineBuilder.() -> Unit`) plus `apply`:

```kotlin
pipeline("release") {
    env("CI", "true")
    stage("build")  { command = "./gradlew build" }
    stage("deploy") { command = "./deploy.sh"; retries = 3 }
}
```

**3. Classic fluent builder.** Only when Java callers must use the API — named arguments are
invisible from Java.

**4. `copy()`.** Java builders are often used to make a tweaked copy; `copy()` does that natively.

## The two rules that matter

- **Always `@DslMarker`.** Without it, inside `stage { }` both the inner and outer receivers are in
  scope, so a typo silently configures the wrong object. With it, that's a compile error.
- **Validate in `build()`, and hand back an immutable product.** `require`/`requireNotNull` there is
  the one thing a builder gives you that named arguments don't — cross-field validation and a good
  error message. Use `toList()`/`toMap()` so the mutable builder state can't leak into the product.

## Production use case

Configuration trees (routing, pipelines, retry policies), test fixtures, HTML/SQL/JSON generation,
Gradle's own build scripts.

## Trade-offs

A DSL builder is roughly 3× the code of a data class. Justify it with nesting or validation, not
with taste. `copy()` is *shallow* — keep fields immutable and it never bites.
