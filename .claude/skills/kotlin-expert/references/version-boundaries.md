# Kotlin 1.5.30 version boundaries

Every row was verified by compiling the construct against this repo's toolchain
(Kotlin 1.5.30, JVM target 11, Gradle 7.6.4) and reading the compiler output. This
is not recalled from the language changelog.

Re-run the check yourself with a throwaway file under `src/main/kotlin/` and
`./gradlew compileKotlin --offline`, then delete it.

---

## Hard failures — will not compile

| Construct | Landed in | Compiler says | Substitute |
|---|---|---|---|
| `data object A : Z` | 1.9 | `Modifier 'data' is not applicable to 'object'` | `object A : Z`; override `toString()` if the name matters |
| `MyEnum.entries` | 1.9 | `Unresolved reference: entries` | `MyEnum.values()` — allocates a fresh array per call, hoist it into a `companion object val` in hot paths |
| `0..<n` | 1.7.20 | `Expecting an element` (parse error) | `0 until n` |
| `context(Foo) fun bar()` | 1.6.20, experimental | `Expecting a top level declaration` | Extension receiver, a member extension inside the context class, or pass the context as a parameter |
| `fun <T> f(t: T): T & Any` | 1.7 | `Expecting a top level declaration` | Return `T`; enforce with `requireNotNull` at the boundary |

Parse-level failures (`..<`, context receivers, `T & Any`) cascade: one of them can
produce a dozen unrelated errors in the same file, and they can mask genuine errors
elsewhere. If a file emits a burst of `Expecting a top level declaration`, look for
a post-1.5 syntax construct first rather than debugging the reported lines.

## Gated behind an opt-in the build has not enabled

These resolve, but error out demanding `@OptIn`:

| Construct | Required opt-in |
|---|---|
| `buildList { }`, `buildMap { }`, `buildSet { }` | `kotlin.ExperimentalStdlibApi` |
| `typeOf<T>()` | `kotlin.ExperimentalStdlibApi` |
| `kotlin.time.Duration` and everything around it | `kotlin.time.ExperimentalTime` |

And then `@OptIn` itself warns, because `-Xopt-in=kotlin.RequiresOptIn` is not in
`freeCompilerArgs`:

```
w: This class can only be used with the compiler argument '-Xopt-in=kotlin.RequiresOptIn'
```

Two ways out:

1. **Substitute** (preferred — no build change):
   - `buildList { add(x) }` → `mutableListOf<T>().apply { add(x) }.toList()`
   - `typeOf<T>()` → an `inline fun <reified T>` parameter
   - `kotlin.time.Duration` → `java.time.Duration`, which this repo already uses
2. **Enable it** in `build.gradle.kts`, only if the user wants experimental stdlib
   APIs project-wide:
   ```kotlin
   freeCompilerArgs = listOf("-Xjsr305=strict", "-Xopt-in=kotlin.RequiresOptIn")
   ```
   Verified to silence the warning. The flag is spelled `-Xopt-in` on 1.5.30; the
   `-opt-in` form is 1.6+ and is rejected here.

`kotlin.time` on 1.5.30 also has the *old* API shape: `Duration.seconds(5)`, not
`5.seconds`. Even with the opt-in, code copied from a modern example won't compile.

## Available — use freely

Verified compiling with no opt-in and no warning:

- `sealed interface`, and sealed subtypes across files in the same package + module
- `@JvmInline value class`
- `Result<T>` as the return type of an ordinary (non-inline) function
- `firstNotNullOf` / `firstNotNullOfOrNull`
- `Char.digitToInt()`
- `fun interface` (SAM conversion)
- Trailing commas
- `select { }` on coroutines 1.5.2 — no `@OptIn` needed
- `StateFlow`, `SharedFlow`, `Flow`, `Semaphore`/`withPermit`, `ensureActive`

## The exhaustiveness gap

Verified: on 1.5.30 this compiles with **no error and no warning**.

```kotlin
sealed class Z { object A : Z(); object B : Z() }
fun z(v: Z) { when (v) { is Z.A -> Unit } }   // B is missing. Silence.
```

`when` is only checked for exhaustiveness when used as an **expression**. Kotlin 1.6
added `NON_EXHAUSTIVE_WHEN_STATEMENT` as a warning and 1.7 promoted it to an error —
this compiler predates both.

Practical consequence: the compiler will **not** find your call sites when you add a
sealed subtype. Grep for them by hand (`rg 'when *\(' --type kotlin`), or convert the
`when` into an expression so the check comes back.

## Also absent, for reference

Not tested individually — none of these exist in 1.5.30, and reaching for them is the
common way modern Kotlin fails here:

- K2 compiler (2.0) — this is the old frontend, so error messages are terser and
  smart-cast inference is weaker, especially across `?:` and lambda boundaries
- Explicit backing fields `field: T` (2.0, still experimental)
- `Enum.entries`, `data object` (1.9)
- Definitely non-null types, `Regex.matchAt`, `..<` (1.7)
- Stable `Duration`/`typeOf`/`buildList`, suspend conversions (1.6)
- `kotlinx-coroutines-test` `runTest` — not a dependency at all; use `runBlocking`
