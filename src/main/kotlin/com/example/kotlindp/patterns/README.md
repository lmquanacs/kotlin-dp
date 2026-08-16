# Pattern catalogue

One folder per pattern. Each contains a heavily-commented `.kt` file and a `README.md` covering
intent, the Kotlin idiom, a production use case, and the trade-offs.

Every pattern here is exercised by a test in [`src/test/kotlin/com/example/kotlindp/patterns/`](../../../../../../test/kotlin/com/example/kotlindp/patterns/).

---

## Creational

| Pattern | Kotlin verdict |
|---|---|
| [singleton](creational/singleton/) | `object` **is** the pattern. Prefer a Spring bean for anything with state. |
| [factorymethod](creational/factorymethod/) | Companion function, or `operator fun invoke` so it reads like a constructor. |
| [abstractfactory](creational/abstractfactory/) | Families as `object`s; in Spring, `@ConditionalOnProperty` replaces it. |
| [builder](creational/builder/) | **Mostly obsolete** — named + default args. Survives for nested DSLs and validation. |
| [prototype](creational/prototype/) | `data class` `copy()`. Strictly better than `clone()`. Watch the shallow copy. |

## Structural

| Pattern | Kotlin verdict |
|---|---|
| [adapter](structural/adapter/) | Extension function (free) → `by` delegation → wrapper class. |
| [bridge](structural/bridge/) | Fixes combinatorial subclassing: 3×3 classes become 3+3. |
| [composite](structural/composite/) | Sealed hierarchy + exhaustive `when`; `sequence { }` for lazy traversal. |
| [decorator](structural/decorator/) | `by` delegation was built for this. **Order is semantics, not style.** |
| [facade](structural/facade/) | Your `@Service` layer. The value is that compensating actions have one home. |
| [flyweight](structural/flyweight/) | Only after measuring. `enum` and `object` are built-in flyweights. |
| [proxy](structural/proxy/) | `by lazy` is a virtual proxy. Explains two Spring AOP gotchas. |

## Behavioral

| Pattern | Kotlin verdict |
|---|---|
| [chainofresponsibility](behavioral/chainofresponsibility/) | Two shapes: first-match (`firstNotNullOfOrNull`) and pipeline (`foldRight`). |
| [command](behavioral/command/) | A lambda is already a command. The interface earns its place for **undo**. |
| [interpreter](behavioral/interpreter/) | You've already built one. A DSL replaces the parser. |
| [iterator](behavioral/iterator/) | `sequence { }` + `yieldAll`. Paginated traversal is the production case. |
| [mediator](behavioral/mediator/) | Coordination *with rules*. No rules → you wanted Observer. |
| [memento](behavioral/memento/) | Immutable state means every value is its own memento. |
| [nullobject](behavioral/nullobject/) | Largely solved by `?.` — except when the absent case has a **name**. |
| [observer](behavioral/observer/) | `Delegates.observable` → hand-rolled → `StateFlow`/`SharedFlow`. |
| [state](behavioral/state/) | **The biggest win.** Sealed states make illegal states unrepresentable. |
| [strategy](behavioral/strategy/) | A function type is already a strategy interface. |
| [templatemethod](behavioral/templatemethod/) | Prefer a higher-order function; defaults replace optional hooks. |
| [visitor](behavioral/visitor/) | Sealed + `when` replaces it entirely, unless third parties add operations. |

## Kotlin language idioms

| Topic | Covers |
|---|---|
| [dsl](kotlinidioms/dsl/) | Type-safe builders: receivers, `@DslMarker`, `infix`, `operator`, scope control |
| [delegation](kotlinidioms/delegation/) | Class delegation, `lazy`/`observable`/`vetoable`/`notNull`/map-backed, custom delegates |
| [result](kotlinidioms/result/) | Sealed results, `Either`, `runCatching`, fail-fast vs accumulate |
| [extensions](kotlinidioms/extensions/) | Extension fns/properties, nullable receivers, static dispatch, member extensions |
| [scopefunctions](kotlinidioms/scopefunctions/) | `let`/`run`/`with`/`apply`/`also`, `takeIf`, `use` — and four traps |
| [inlinereified](kotlinidioms/inlinereified/) | `inline`/`crossinline`/`noinline`, `reified`, `@JvmInline value class` |
| [functional](kotlinidioms/functional/) | Composition, currying, memoisation, collection pipeline, functional core |
| [generics](kotlinidioms/generics/) | `in`/`out` variance, constraints, F-bounds, phantom types, erasure |
| [coroutines](kotlinidioms/coroutines/) | Structured concurrency, `supervisorScope`, timeouts, cancellation, `Flow` |

## Production resilience

| Pattern | Note |
|---|---|
| [retry](production/retry/) | Exponential backoff **with jitter**. Without it you build a retry storm. |
| [circuitbreaker](production/circuitbreaker/) | Prevents cascading failure. A State machine. Use Resilience4j in anger. |
| [objectpool](production/objectpool/) | Usually counterproductive. Right for connections, threads, sessions. |
| [cacheaside](production/cacheaside/) | Bounded + TTL + **single-flight**. Invalidate on write, don't update. |

## Spring Boot integration

| Pattern | Replaces |
|---|---|
| [dependencyinjection](spring/dependencyinjection/) | Hand-written Strategy registries and factories — `List<T>`, `Map<String,T>`, `ObjectProvider` |
| [configurationproperties](spring/configurationproperties/) | Builder + Value Object, with startup validation |
| [beanlifecycle](spring/beanlifecycle/) | Singleton, done properly; scopes and lifecycle hooks |
| [applicationevents](spring/applicationevents/) | Observer, including `@TransactionalEventListener` |
| [aop](spring/aop/) | Decorator/Proxy applied declaratively — and the Kotlin `final` trap |
| [servicelayer](spring/servicelayer/) | Facade + Repository + sealed failures mapped to HTTP status |
| [interceptor](spring/interceptor/) | Chain of Responsibility on the request path: Filter vs HandlerInterceptor |
| [conditionalbeans](spring/conditionalbeans/) | Abstract Factory selected by configuration |

---

## How to read this

The patterns are not equally useful. If you're picking a few to internalise:

1. **[state](behavioral/state/)** — sealed states are the single biggest Kotlin design improvement
   over Java, and they eliminate a whole class of bug.
2. **[result](kotlinidioms/result/)** — deciding which failures are values and which are exceptions
   shapes every API you write.
3. **[decorator](structural/decorator/)** + **[strategy](behavioral/strategy/)** — `by` delegation
   and function types make both nearly free, so composition stops being the expensive option.
4. **[servicelayer](spring/servicelayer/)** — where the previous three meet in a real Spring app.

And several patterns are here mainly so you can recognise when *not* to use them:
[builder](creational/builder/) (named args win), [visitor](behavioral/visitor/) (sealed + `when`
wins), [flyweight](structural/flyweight/) and [objectpool](production/objectpool/) (measure first),
[nullobject](behavioral/nullobject/) (`?.` wins).
