---
name: kotlin-expert
description: Expert Kotlin guidance calibrated to this repo's pinned toolchain — Kotlin 1.5.30, JVM target 11, Spring Boot 2.5.15, coroutines 1.5.2, Gradle 7.6.4. Use for any Kotlin work here — writing or reviewing .kt code, picking an idiom or design pattern, coroutines/Flow, sealed types and typed errors, generics and variance, DSLs, Spring wiring, or build and toolchain errors. Load it before reaching for a modern Kotlin feature (data object, enum entries, ..<, context receivers, T & Any, buildList, typeOf, Duration): several of those do not compile on 1.5.30, and this skill carries the verified list plus the substitute that does.
---

# Kotlin Expert — kotlin-dp

This repo is pinned to **Kotlin 1.5.30**, released 2021. Most Kotlin you have seen
since is newer than what compiles here. The single largest failure mode when
working in this repo is writing correct modern Kotlin that this compiler rejects.

Two things follow from that, and they order everything below: **check the version
boundary before writing code**, and **route into the existing catalogue instead of
re-deriving it** — this repo already documents its own language surface, and
re-explaining it burns context the task needs.

## The pinned toolchain

Every version below is resolved from the actual compile classpath, not from the
declaration.

| | | |
|---|---|---|
| Kotlin compiler | 1.5.30 | `build.gradle.kts:5-6` |
| kotlin-stdlib / -reflect | 1.5.30 | uniform — the Kotlin plugin constraint wins over the Boot BOM |
| kotlinx-coroutines-core | 1.5.2 | BOM-managed, declared without a version |
| Spring Boot | 2.5.15 | `build.gradle.kts:3` |
| jackson-module-kotlin | 2.12.7 | BOM-managed |
| JVM target | 11 | `build.gradle.kts:14,33` — no Java 12+ APIs |
| Gradle | 7.6.4 (wrapper) | **pinned on purpose — see Build** |
| Compiler args | `-Xjsr305=strict` only | `build.gradle.kts:32` |

Available: Spring web / AOP / validation, Jackson, kotlin-reflect, coroutines-core.
**Not on the classpath**: kotlinx-serialization, Arrow, MockK, Mockito-Kotlin,
Kotest, AssertJ, `kotlinx-coroutines-test` (so no `runTest` — use `runBlocking`).
Tests are JUnit 5 with `org.junit.jupiter.api.Assertions` and lambda test doubles.

## Rule 1 — check the boundary before you write

Verified by compiling each construct against this exact toolchain. Full matrix and
the reasoning: `references/version-boundaries.md`.

**Does not compile — parse or resolution error:**

| Feature | Since | Write this instead |
|---|---|---|
| `data object A : Z` | 1.9 | `object A : Z` (accept the default `toString`) |
| `MyEnum.entries` | 1.9 | `MyEnum.values()` |
| `0..<n` | 1.7.20 | `0 until n` |
| `context(Foo) fun …` | 1.6.20 (exp.) | receiver parameter, or a member extension |
| `fun <T> f(t: T): T & Any` | 1.7 | return `T` and `requireNotNull` at the boundary |

**Compiles only behind an opt-in that this build has not enabled** — see Rule 3:
`buildList` / `buildMap`, `typeOf<T>()`, everything in `kotlin.time` (`Duration`,
`5.seconds`). Note `kotlin.time` in 1.5.30 is the old shape — `Duration.seconds(5)`,
not `5.seconds`. Prefer `java.time.Duration`, which the repo already uses.

**Available and idiomatic here** — don't avoid these out of caution: `sealed
interface`, `@JvmInline value class`, `Result<T>` as an ordinary return type,
`firstNotNullOfOrNull`, `Char.digitToInt`, `fun interface`, trailing commas,
`Delegates.*`, `StateFlow` / `SharedFlow`, `select` (no opt-in needed on
coroutines 1.5.2).

## Rule 2 — `when` exhaustiveness is silent here

The sharpest trap in this repo. On 1.5.30 a non-exhaustive `when` **statement**
over a sealed type compiles with **no error and no warning** — verified. The
compiler only enforces exhaustiveness when `when` is used as an **expression**.
Kotlin 1.6 added the warning; 1.7 made it an error. Neither has happened here.

That undercuts the main argument for sealed hierarchies — "add a subtype and the
compiler shows you every site". It only holds if the `when` produces a value.

```kotlin
// Silently incomplete on 1.5.30. Add a State and nothing tells you.
fun handle(s: State) { when (s) { is Idle -> start(); is Running -> stop() } }

// Enforced: the assignment forces exhaustiveness.
fun handle(s: State) {
    val ignored: Unit = when (s) { is Idle -> start(); is Running -> stop() }
}

// Preferred: expression body, real return type.
fun label(s: State): String = when (s) { is Idle -> "idle"; is Running -> "running" }
```

Design side-effecting sealed dispatch to return a value — even `Unit`. When
reviewing, treat a bare `when (x) { … }` statement over a sealed type or enum as a
finding, not a style preference.

## Rule 3 — opt-in needs a compiler flag that isn't set

On 1.5.30 the `@OptIn` annotation *itself* is gated. Writing
`@OptIn(ExperimentalStdlibApi::class)` compiles but warns:

```
This class can only be used with the compiler argument '-Xopt-in=kotlin.RequiresOptIn'
```

Verified fix — the flag is `-Xopt-in` on 1.5.30, **not** the `-opt-in` spelling
from 1.6+:

```kotlin
freeCompilerArgs = listOf("-Xjsr305=strict", "-Xopt-in=kotlin.RequiresOptIn")
```

Don't add this casually. It's a build-wide change for the sake of `buildList` or
`typeOf`, both of which have a plain substitute (`mutableListOf` + `toList`,
a `reified` type parameter). Propose it only if the user wants experimental stdlib
APIs across the project, and say what it unlocks.

## Don't re-derive the language map

This repo documents its own Kotlin surface. Read the map, load the one folder you
need, and stop — do not open the catalogue broadly.

- **[docs/KOTLIN_FEATURES.md](../../../docs/KOTLIN_FEATURES.md)** — every language
  feature used here, why it matters, and the folder that demonstrates it. Grep this
  first when the question is "how does this repo do X".
- **[src/main/kotlin/com/example/kotlindp/patterns/README.md](../../../src/main/kotlin/com/example/kotlindp/patterns/README.md)**
  — 40+ patterns, each with a one-line Kotlin verdict.
- **`references/catalogue-map.md`** — the same catalogue indexed by *symptom*
  ("I need to retry a flaky call", "two components need to coordinate") rather than
  by pattern name. Use it when you know the problem but not the pattern's name.

Every pattern folder is `Pattern.kt` + `README.md` (intent, Kotlin idiom, production
use case, trade-offs). One folder is typically 200–400 lines — read the `README.md`
first and the `.kt` only if you need the exact syntax.

## Verdicts that override the textbook

This catalogue takes positions. Follow them; they are what makes the code here
consistent, and several exist to stop you writing a pattern Kotlin already has.

- **Builder** — mostly obsolete. Named + default arguments win. Builders survive
  only for nested DSLs and staged validation.
- **Visitor** — sealed + `when` replaces it, unless third parties add operations.
- **Null Object** — `?.` wins, unless the absent case has a *name* worth reading.
- **Strategy** — a function type is already the interface. `typealias` it.
- **Singleton** — `object` is the pattern; anything stateful should be a Spring bean.
- **Flyweight / Object Pool** — measure first. `enum` and `object` are already flyweights.
- **Decorator / Adapter** — `by` delegation, not hand-written forwarding. Decorator
  order is semantics, not style.
- **In Spring, the framework already is the pattern**: `List<T>` injection is a
  Strategy registry, `@ConditionalOnProperty` is an Abstract Factory, `@EventListener`
  is Observer, AOP is Decorator. Don't hand-roll a worse one.

## House style

Match the surrounding file. Distinctive habits, all consistent across the repo:

- KDoc on every public declaration, written as *design rationale* — why this shape,
  what the trade-off is — not a restatement of the signature.
- `// ---- N. Section name ----` banner comments separating numbered sections.
- Expression bodies (`= when (…)`, `= coroutineScope { … }`) over block bodies.
- Explicit imports, one per symbol. No wildcards, no unused imports.
- Trailing commas in multi-line parameter lists.
- Sealed hierarchies with `data class` / `object` subtypes for typed errors; expected
  failures are **values**, unexpected failures are **exceptions**.
- 4-space indent, ~110-col lines.
- Tests: backtick names, `@Nested inner class` grouping, lambda test doubles rather
  than a mocking framework, injected `sleep`/`now` so timing tests run instantly,
  `runBlocking` for suspend functions.
- Classes are `final` by default; `kotlin("plugin.spring")` opens `@Component` types
  so CGLIB can proxy them. Don't add `open` by hand to Spring beans.

## Coroutines here

Coroutines 1.5.2 with `spring-boot-starter-web` — a **blocking servlet stack**. A
`suspend` controller method needs `runBlocking` at the boundary or a WebFlux
dependency this project does not have. Structured concurrency rules the repo holds
to (`patterns/kotlinidioms/coroutines/`):

- `coroutineScope` when results are only meaningful together; `supervisorScope` when
  partial results are useful. That's a product decision, not a technical one.
- `withTimeout` on every external call — no exceptions.
- `runCatching` **catches `CancellationException`**. Rethrow it, or use a sealed
  result instead.
- `select`: cancel the losing branches in `finally`.
- `StateFlow` for state, `SharedFlow` for events. Backwards, and you drop data.

## Build and test

```bash
./gradlew build          # compile + test + package
./gradlew test
./gradlew bootRun        # :8080
./gradlew compileKotlin  # fastest check that code compiles
```

**Always `./gradlew`, never a system `gradle`.** The wrapper is pinned to 7.6.4
because the Boot 2.5 and Kotlin 1.5.30 plugins do not load on Gradle 8+; on Gradle 9
the Boot plugin fails with `Configuration.getUploadTaskName()` not found. Every build
prints a "Deprecated Gradle features were used" warning — expected, harmless, not a
thing to fix.

## Review checklist

1. Does it compile on 1.5.30? Check Rule 1 before anything else.
2. Any `when` **statement** over a sealed type or enum? Rule 2 — it is silently
   unchecked. Make it an expression.
3. `!!` anywhere? Prefer `requireNotNull(x) { "message" }`.
4. `runCatching` around suspending code without rethrowing `CancellationException`?
5. External call without `withTimeout`?
6. Hand-written forwarding methods where `by` delegation would do?
7. A Builder, Visitor, or Null Object that named arguments, `when`, or `?.` replaces?
8. Expected failures thrown as exceptions instead of returned as sealed values?
9. New dependency — is it actually on the classpath, or assumed?
10. KDoc that restates the signature instead of explaining the design choice?
