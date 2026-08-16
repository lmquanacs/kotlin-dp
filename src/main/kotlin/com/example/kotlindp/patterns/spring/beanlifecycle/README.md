# Bean lifecycle and scopes — Singleton, done properly

A Spring bean is a Singleton **scoped to the application context**, which is strictly better than an
`object` (see `creational/singleton`): constructor-injectable, replaceable in a test slice, destroyed
with the context — so tests don't leak into each other.

## Where initialisation goes

- **Constructor** — anything derivable from injected dependencies. Preferred: the object is never
  observable half-built.
- **`@PostConstruct`** — work that must happen *after* injection and isn't safe in a constructor:
  warming a cache, registering with discovery, validating config.

The distinction matters for one specific reason: **a proxied method called from a constructor does
nothing.** The proxy doesn't exist yet, so `@Transactional`, `@Cacheable`, and `@Async` are inert
during construction. `@PostConstruct` runs after the proxy is in place.

**`@PreDestroy` doesn't run on `kill -9`.** It's for tidiness (flushing, deregistering), never for
correctness. Anything that must survive a crash has to be durable before the process ends.

Prefer the annotations over `InitializingBean`/`DisposableBean`, which couple your class to Spring.
For third-party types, use `@Bean(initMethod = ..., destroyMethod = ...)`.

## Scopes

**Singleton (default)** — one instance per context. Must be **stateless or thread-safe**. This is the
number-one Spring bug: a `var` field on a `@Service` is shared by every concurrent request.

**Prototype** — new instance per injection point. The trap: **a prototype injected into a singleton
is created once**, at the singleton's construction, and never again — so it behaves as a singleton.
For a fresh instance per call, inject `ObjectProvider<T>` and call `getObject()`.

## `@Bean` methods

Factory Method for types you don't own — an HTTP client, `ObjectMapper`, a thread pool.

`@Configuration` classes are themselves CGLIB-proxied, so calling one `@Bean` method from another
returns the **same singleton** rather than a second instance. That proxying is also why
`@Configuration` classes must not be `final` — allopen handles it in Kotlin.

## Startup order

Constructor injection determines the graph, so dependencies initialise first. **Don't use
`@DependsOn` to fix ordering** unless the dependency is genuinely invisible — it's a comment the
compiler can't check.

For startup work, prefer `ApplicationRunner`/`CommandLineRunner` over `@PostConstruct`: they run
after the *whole* context is ready, so calling other beans is safe.

## The two mistakes

1. **Mutable state on a singleton bean.** Shared by every request thread.
2. **Prototype injected into a singleton.** Created once. Use `ObjectProvider`.
