# Decorator

**Intent** — add behaviour by wrapping an object, without modifying its class and without a subclass
per combination of features.

## Kotlin idiom

`by` delegation is practically built for this. In Java a decorator hand-writes a forwarding method
for every interface member; in Kotlin `: QueryExecutor by inner` generates all of them, so the
decorator body contains **only the behaviour it adds**:

```kotlin
class TimingExecutor(private val inner: QueryExecutor) : QueryExecutor by inner {
    override fun execute(query: Query): List<Row> {
        val start = clock()
        try { return inner.execute(query) } finally { lastDuration = clock() - start }
    }
}
```

Add a method to the interface tomorrow and every decorator still compiles and still forwards
correctly — the failure mode Java's hand-written forwarding has.

For a *single-method* interface, skip the class entirely and decorate a function type:

```kotlin
typealias Handler = (Query) -> List<Row>
fun Handler.logged(): Handler = { q -> log(q); this(q) }
```

## Two things that bite

**Order is semantics, not style.** `Timing(Caching(real))` times cache hits (near zero);
`Caching(Timing(real))` times only real executions. Stacks read inside-out — the outermost wrapper
runs first.

**`by` delegation is not inheritance.** The delegate is captured at construction, and there's no
`super` dispatch. If a decorated method calls another method on `this`, the call lands on the
*delegate's* implementation, not back through the decorator.

## Production use case

Logging, timing, caching, retry, rate limiting, circuit breaking around a client — each a separate
class, composed per environment.

## Trade-offs

Deep stacks produce unreadable stack traces and make it genuinely hard to tell which layer changed a
result. Three or four is plenty.

In Spring, `@Transactional`, `@Cacheable`, `@Retryable`, and `@Async` are AOP proxies — runtime
decorators. Use the annotations for infrastructure concerns; hand-write decorators for domain
behaviour.
