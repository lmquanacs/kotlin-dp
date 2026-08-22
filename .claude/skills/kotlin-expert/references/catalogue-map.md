# Catalogue map — by symptom

The repo's own index ([patterns/README.md](../../../../src/main/kotlin/com/example/kotlindp/patterns/README.md))
is organised by pattern name, which only helps if you already know the name. This
one is organised by the problem you actually have.

Paths are relative to `src/main/kotlin/com/example/kotlindp/patterns/`. Read the
folder's `README.md` first; open the `.kt` only when you need exact syntax.

**Load one folder, not the group.** Each is 200–400 lines. Three folders is a
reasonable ceiling for one task.

---

## Structuring behaviour

| Symptom | Folder | Note |
|---|---|---|
| An object behaves differently depending on what state it's in | `behavioral/state` | The biggest win in Kotlin — sealed states make illegal states unrepresentable |
| Swappable algorithm chosen at runtime | `behavioral/strategy` | A function type *is* the interface; `typealias` it |
| A chain of handlers, first match wins | `behavioral/chainofresponsibility` | `firstNotNullOfOrNull`; pipeline shape uses `foldRight` |
| An operation over a closed type hierarchy | `behavioral/visitor` | Usually don't — sealed + `when` replaces it |
| Undo/redo, or an action to queue and replay | `behavioral/command` | A lambda is already a command; the interface earns its place for undo |
| Snapshot and restore state | `behavioral/memento` | Immutable state means every value is its own memento |
| A fixed skeleton with pluggable steps | `behavioral/templatemethod` | Prefer a higher-order function; defaults replace optional hooks |
| Components need to coordinate *with rules* | `behavioral/mediator` | No rules → you wanted Observer |
| React to something changing | `behavioral/observer` | `Delegates.observable` → hand-rolled → `StateFlow`/`SharedFlow` |
| A small expression language or rule engine | `behavioral/interpreter` | A DSL replaces the parser |
| Traverse something lazily or paginated | `behavioral/iterator` | `sequence { }` + `yieldAll` |
| Absent value keeps forcing null checks | `behavioral/nullobject` | `?.` wins unless the absent case has a *name* |

## Constructing things

| Symptom | Folder | Note |
|---|---|---|
| Too many constructor parameters | `creational/builder` | Named + default args. Read this to be talked *out* of a builder |
| Choose an implementation by input | `creational/factorymethod` | Companion function, or `operator fun invoke` |
| A whole family of related implementations | `creational/abstractfactory` | In Spring, `@ConditionalOnProperty` replaces it |
| Exactly one instance | `creational/singleton` | `object`; anything stateful should be a Spring bean |
| Copy an object with tweaks | `creational/prototype` | `data class` `copy()` — watch the shallow copy |

## Wrapping and composing

| Symptom | Folder | Note |
|---|---|---|
| Two incompatible interfaces | `structural/adapter` | Extension function (free) → `by` delegation → wrapper class |
| Add behaviour without subclassing | `structural/decorator` | `by` delegation. **Order is semantics, not style** |
| Class explosion from crossing two axes | `structural/bridge` | 3×3 classes become 3+3 |
| Tree of things treated uniformly | `structural/composite` | Sealed hierarchy + `when`; `sequence { }` to traverse |
| Simplify a messy subsystem | `structural/facade` | Your `@Service` layer — compensating actions get one home |
| Control access, lazy-init, or intercept calls | `structural/proxy` | `by lazy` is a virtual proxy; covers two Spring AOP gotchas |
| Too many identical small objects | `structural/flyweight` | Measure first. `enum` and `object` are already flyweights |

## Production concerns

| Symptom | Folder |
|---|---|
| A flaky external call needs retrying | `production/retry` — with jitter; injected `sleep` so tests run instantly |
| A failing dependency is dragging the system down | `production/circuitbreaker` |
| Expensive lookups repeated | `production/cacheaside` |
| Costly resources need reusing | `production/objectpool` — measure first |

## Kotlin language questions

| Symptom | Folder |
|---|---|
| Errors: exception or return value? | `kotlinidioms/result` — sealed results, `Either`, `runCatching`, fail-fast vs accumulate |
| Need a fluent configuration block | `kotlinidioms/dsl` — receivers, `@DslMarker`, `infix`, scope control |
| Boilerplate forwarding, or property-level behaviour | `kotlinidioms/delegation` — `by`, `lazy`, `observable`, custom delegates, `provideDelegate` |
| Adding behaviour to a type you don't own | `kotlinidioms/extensions` — including static-dispatch traps |
| Which of `let`/`run`/`apply`/`also`/`with`? | `kotlinidioms/scopefunctions` — the full table plus four traps |
| Erased generic type needed at runtime | `kotlinidioms/inlinereified` — `inline`/`crossinline`/`noinline`, `reified`, value classes |
| Variance, constraints, phantom types | `kotlinidioms/generics` |
| Composition, currying, memoisation, pipelines | `kotlinidioms/functional` |
| Concurrency | `kotlinidioms/coroutines` — structured concurrency, timeouts, `select`, `Flow` |

## Spring Boot — the framework is already the pattern

| Symptom | Folder |
|---|---|
| Wiring beans, injecting a set of strategies | `spring/dependencyinjection` — `List<T>` injection *is* a Strategy registry |
| Typed, validated configuration | `spring/configurationproperties` — `@field:` targets are required for JSR-380 |
| Work at startup or shutdown | `spring/beanlifecycle` |
| Decouple producer from consumer in-process | `spring/applicationevents` — `@EventListener` is Observer |
| Cross-cutting concern (logging, timing, auth) | `spring/aop` — AOP is Decorator; custom annotations make pointcuts refactor-safe |
| Where business logic goes | `spring/servicelayer` |
| Per-request hooks around controllers | `spring/interceptor` |
| Different bean depending on environment | `spring/conditionalbeans` — `@ConditionalOnProperty` is an Abstract Factory |
