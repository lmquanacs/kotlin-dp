# Proxy

**Intent** — stand in for another object and control access to it, behind the same interface.

Proxy and Decorator are structurally identical; the difference is intent. A **decorator** adds
behaviour the caller wants (logging, retry). A **proxy** controls access the caller isn't supposed to
think about (laziness, remoteness, permission, cost).

## The flavours

| Flavour | Purpose | Kotlin |
|---|---|---|
| Virtual | defer expensive creation | `by lazy` |
| Protection | access control | `by` delegation + a check |
| Caching / smart | bookkeeping around access | `getOrPut` |
| Remote | hide a network boundary | wrapper + error translation |
| Dynamic | one handler for all methods | `JdkProxy` + `reified` |

`by lazy` **is** a virtual proxy for a property, and it's thread-safe by default
(`LazyThreadSafetyMode.SYNCHRONIZED` — the initialiser runs at most once under a race).

`reified` removes the `Class` argument from dynamic proxy creation:

```kotlin
inline fun <reified T : Any> recordingProxy(target: T, log: MutableList<String>): T
val proxied = recordingProxy<ImageStore>(real, log)
```

## Two Spring facts that follow directly from this pattern

**JDK dynamic proxies only work on interfaces.** For classes Spring uses CGLIB, which *subclasses* —
so the class and its methods must be non-`final`. Kotlin classes are `final` by default, which is
exactly why the Spring Boot Kotlin setup includes `kotlin("plugin.spring")` (the allopen compiler
plugin). Without it, `@Transactional` on a Kotlin `@Service` silently does nothing.

**Self-invocation bypasses the proxy.** Calling `this.otherMethod()` inside a proxied bean doesn't
go through the proxy, so `@Transactional`/`@Cacheable` on the inner method is ignored. This is the
most common Spring AOP bug and it's a direct consequence of how proxies work.

## Production use case

Lazy-loading a heavy client; permission checks at a service boundary; Spring Data repositories and
Retrofit clients (interface-only, implementation generated at runtime); a remote proxy where
timeouts and error translation live.

## Trade-offs

A remote proxy makes a network call look like a method call — that's its purpose *and* its danger.
Callers stop thinking about latency and failure. Put timeouts and error translation in the proxy,
because the caller no longer sees a reason to.

Dynamic proxies cost reflection on every call and produce stack traces full of `$Proxy12` frames.
Remember to unwrap `InvocationTargetException` or callers see the wrong exception type.
