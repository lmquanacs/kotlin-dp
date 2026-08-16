# Kotlin feature map

Every Kotlin language feature used in this repository, what it's for, and where to see it working.
Paths are relative to `src/main/kotlin/com/example/kotlindp/patterns/`.

---

## Types and null safety

| Feature | Why it matters | See |
|---|---|---|
| Nullable types `T?` | Absence is in the type system, not a convention | `behavioral/nullobject` |
| Safe call `?.`, Elvis `?:` | Replaces most Null Object usage | `kotlinidioms/scopefunctions` |
| `!!` | Almost always a design smell — prefer `requireNotNull` with a message | — |
| Smart casts | After `is`/`!= null` the compiler narrows the type automatically | `behavioral/state` |
| `lateinit` | `var` initialised after construction; **not** for primitives | `kotlinidioms/delegation` |
| `Delegates.notNull()` | The primitive equivalent of `lateinit` | `kotlinidioms/delegation` |
| `Nothing` | The bottom type; makes `Either<Nothing, R>` assignable anywhere | `kotlinidioms/result` |
| `-Xjsr305=strict` | Treats Spring's `@NonNull`/`@Nullable` as real Kotlin types | `build.gradle.kts` |

## Classes and objects

| Feature | Why it matters | See |
|---|---|---|
| `data class` | `equals`/`hashCode`/`toString`/`copy`/destructuring | `creational/prototype` |
| `copy()` | Prototype and Memento in one generated method — **shallow** | `creational/prototype` |
| `object` | Thread-safe lazy singleton by class-loading contract | `creational/singleton` |
| `companion object` | Factory methods, constants, `operator fun invoke` | `creational/factorymethod` |
| `sealed class` / `sealed interface` | Closed hierarchy → exhaustive `when` | `behavioral/state`, `behavioral/visitor` |
| `enum class` with abstract members | Behaviour per constant | `behavioral/strategy` |
| `@JvmInline value class` | Type safety with no allocation in the common path | `kotlinidioms/inlinereified` |
| Classes `final` by default | Requires `kotlin-allopen` for Spring CGLIB proxies | `spring/aop` |
| `init` blocks | Validation that runs on every construction path, including `copy()` | `spring/configurationproperties` |
| Nested vs `inner` class | `inner` holds a reference to the outer instance | `behavioral/memento` |
| Visibility: `private`/`internal` | `internal` is how a factory becomes the only door in | `creational/factorymethod` |

## Functions

| Feature | Why it matters | See |
|---|---|---|
| Default arguments | Eliminates most Builder usage and telescoping constructors | `creational/builder` |
| Named arguments | Call sites read as documentation; order-independent | `creational/builder` |
| Top-level functions | No `Utils` class holder needed | `structural/facade` |
| Extension functions | Adapter with zero allocation; **statically dispatched** | `kotlinidioms/extensions` |
| Extension properties | Computed only — no backing field | `kotlinidioms/extensions` |
| Nullable receivers | `fun String?.orPlaceholder()` — callable on `null` without `?.` | `kotlinidioms/extensions` |
| Member extensions | Two receivers; scoped to a class — the DSL mechanism | `kotlinidioms/dsl` |
| `infix` | Binary operations that read as prose: `"age" gt 18` | `kotlinidioms/dsl` |
| `operator` overloading | `plus`, `times`, `invoke`, `get`/`set`, `unaryPlus` | `structural/composite` |
| `vararg` | `rules(a, b, c)` | `structural/composite` |
| `tailrec` | Self-recursion compiled to a loop — direct recursion only | `kotlinidioms/functional` |
| Local functions | Scoped helpers that close over the enclosing scope | `behavioral/state` |
| Function types + `typealias` | A function type **is** a Strategy interface | `behavioral/strategy` |
| `fun interface` (SAM) | Lambda-implementable single-method interface | `behavioral/observer` |
| Function references `::` | `String::trim`, `System::nanoTime`, `::validateEmail` | `kotlinidioms/functional` |

## Inline and generics

| Feature | Why it matters | See |
|---|---|---|
| `inline` | Non-local `return` and `reified` — not mainly performance | `kotlinidioms/inlinereified` |
| `crossinline` | Inlined lambda called from another context | `kotlinidioms/inlinereified` |
| `noinline` | The lambda must be stored or passed on | `kotlinidioms/inlinereified` |
| `reified` | Recovers the erased type: `filterInstances<String>()` | `structural/proxy` |
| Declaration-site variance `in`/`out` | PECS declared once, not at every use | `kotlinidioms/generics` |
| Use-site variance | Kotlin's `? extends T` | `kotlinidioms/generics` |
| Star projection `<*>` | "Some type, and I don't care" | `kotlinidioms/generics` |
| Generic constraints, `where` | Multiple upper bounds without a marker supertype | `kotlinidioms/generics` |
| F-bounded polymorphism | Fluent APIs that keep the subclass type | `kotlinidioms/generics` |
| Phantom types | Encode state in the type: `Email<Validated>` | `kotlinidioms/generics` |

## Delegation

| Feature | Why it matters | See |
|---|---|---|
| Class delegation `by` | Decorator/Adapter with no forwarding boilerplate | `structural/decorator` |
| `by lazy` | Thread-safe virtual proxy for a property | `structural/proxy` |
| `Delegates.observable` / `vetoable` | Observer at property granularity | `behavioral/observer` |
| Map-backed properties | Typed façade over a schemaless payload | `kotlinidioms/delegation` |
| `ReadWriteProperty` | Custom delegates: audited, validated, derived | `kotlinidioms/delegation` |
| `provideDelegate` | Logic at property creation — how `by inject()` works | `kotlinidioms/delegation` |

## Control flow and expressions

| Feature | Why it matters | See |
|---|---|---|
| `when` as an expression | Exhaustive over sealed/enum — the compiler finds every site | `behavioral/visitor` |
| `when (val x = ...)` | Bind and branch in one | `behavioral/command` |
| Destructuring | `val (a, b) = pair`, `for ((k, v) in map)` | `kotlinidioms/functional` |
| String templates + raw strings | `"$name"`, `"""..."""` | `structural/bridge` |
| Ranges `..`, `in` | `port in 1..65535` | `kotlinidioms/delegation` |
| `require` / `check` / `error` | Argument vs state vs unreachable — three different messages | `creational/builder` |
| Labelled returns `return@let` | Escaping the right scope | `production/cacheaside` |

## Scope functions

`let` · `run` · `with` · `apply` · `also` · `takeIf` · `takeUnless` · `use` — the full table, the
idiomatic use of each, and four traps: `kotlinidioms/scopefunctions`.

## Collections

| Feature | Why it matters | See |
|---|---|---|
| Read-only vs mutable interfaces | `List` is not `MutableList` — immutability by default | `creational/builder` |
| `Sequence` / `sequence { }` / `yieldAll` | Lazy, early-terminating, infinite | `behavioral/iterator` |
| Eager vs lazy | Eager wins on small data — `asSequence()` can be a pessimisation | `behavioral/iterator` |
| `groupBy`/`associateBy`/`partition`/`fold` | Replace loops that are easy to get subtly wrong | `kotlinidioms/functional` |
| `windowed`/`chunked`/`zipWithNext` | Adjacent-element work without index arithmetic | `kotlinidioms/functional` |
| `firstNotNullOfOrNull` | Chain of Responsibility in one call | `behavioral/chainofresponsibility` |
| `buildString` | Efficient string assembly | `structural/bridge` |
| `ArrayDeque` | Undo/redo stacks | `behavioral/command` |

## Errors

| Feature | Why it matters | See |
|---|---|---|
| No checked exceptions | Failure handling is a design decision, not a compiler mandate | `kotlinidioms/result` |
| Sealed result hierarchies | Expected failures as values, named and exhaustive | `kotlinidioms/result` |
| `runCatching` / `Result` | Convenient at boundaries; **catches `CancellationException`** | `kotlinidioms/result` |
| `Either` with `map`/`flatMap`/`fold` | Railway-oriented composition; fail-fast vs accumulate | `kotlinidioms/result` |

## Coroutines

| Feature | Why it matters | See |
|---|---|---|
| `suspend` | Non-blocking without callbacks | `kotlinidioms/coroutines` |
| Structured concurrency | Children bound to a scope — no orphaned work | `kotlinidioms/coroutines` |
| `coroutineScope` vs `supervisorScope` | Fail together vs fail independently | `kotlinidioms/coroutines` |
| `async`/`await`/`awaitAll` | Parallel decomposition | `kotlinidioms/coroutines` |
| `withTimeout` / `withTimeoutOrNull` | Mandatory on every external call | `kotlinidioms/coroutines` |
| Cooperative cancellation, `ensureActive` | Cancellation needs a suspension point to work | `kotlinidioms/coroutines` |
| `Semaphore` + `withPermit` | Bounded parallelism | `kotlinidioms/coroutines` |
| `Flow` (cold) | Nothing runs until collected | `kotlinidioms/coroutines` |
| `StateFlow` vs `SharedFlow` (hot) | State vs events — getting this backwards drops data | `behavioral/observer` |
| `select` | Race branches; **cancel the losers in `finally`** | `kotlinidioms/coroutines` |

## Annotations and interop

| Feature | Why it matters | See |
|---|---|---|
| `@DslMarker` | Stops nested DSL receivers leaking — never optional | `kotlinidioms/dsl` |
| Annotation use-site targets `@field:` | Required for JSR-380 on constructor properties | `spring/configurationproperties` |
| `@JvmInline` | Value classes | `kotlinidioms/inlinereified` |
| `@Suppress` | Documented, narrow suppressions only | `kotlinidioms/extensions` |
| Custom annotations | Refactor-safe AOP pointcuts | `spring/aop` |
| `kotlin-allopen` | Opens `@Component` classes so CGLIB can proxy them | `spring/aop` |

## Testing idioms

| Feature | See |
|---|---|
| Backtick test names — `` fun `rejects a negative quantity`() `` | every test file |
| `@Nested inner class` for grouping | `src/test/.../CreationalPatternsTest.kt` |
| Injected `sleep`/`now` so time-dependent code tests instantly | `production/retry` |
| Lambda test doubles instead of a mocking framework | `behavioral/strategy` |
| `runBlocking` for suspend functions | `src/test/.../KotlinIdiomsTest.kt` |
