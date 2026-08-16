# AOP — Decorator and Proxy applied by the framework

Spring AOP generates a proxy around a bean and routes calls through your advice. That is exactly
Decorator (`structural/decorator`) and Proxy (`structural/proxy`) — except the wrapper is generated
at runtime, so **one aspect decorates a hundred beans**.

`@Transactional`, `@Cacheable`, `@Retryable`, `@Async`, `@PreAuthorize` are all built this way.

The trade: cross-cutting behaviour with no wrapper classes, at the cost of not being able to see from
a call site that anything is happening at all.

## Prefer annotation pointcuts

```kotlin
@Target(AnnotationTarget.FUNCTION) @Retention(AnnotationRetention.RUNTIME)
annotation class Timed(val name: String = "")

@Around("@annotation(timed)")
fun time(joinPoint: ProceedingJoinPoint, timed: Timed): Any? { ... }
```

`@Around("execution(* com.example..service.*.*(..))")` silently stops matching when someone renames a
package. An annotation is explicit at the point of use, refactor-safe, and greppable.

Same detail as the hand-written decorator: **`finally`**, or failed calls are never timed and your
latency metrics quietly exclude the slowest cases.

## The Kotlin trap

Spring AOP uses CGLIB for classes, which **subclasses** the bean. A Kotlin class is `final` by
default, so CGLIB can't proxy it and the advice **silently never runs**.

`kotlin("plugin.spring")` (kotlin-allopen, in this project's build file) opens classes annotated with
`@Component`/`@Service`/`@Configuration`. Without it, `@Transactional` on a Kotlin service is a no-op
— the single most common Kotlin + Spring bug.

## What can and cannot be intercepted

| Call | Intercepted? |
|---|---|
| External call to a public method on a proxied bean | yes |
| Self-invocation (`this.other()`) | **no** |
| `private` / `final` method | **no** |
| Object created with `new` | **no** — not a bean |
| Kotlin class without allopen | **no** — CGLIB can't subclass `final` |

Every row is a bug someone has spent a day on. For self-invocation, the fix is to **move the method
to another bean**, not to inject the bean into itself.

## AOP vs a hand-written decorator

**AOP** — genuinely cross-cutting infrastructure applied uniformly to many beans: timing, auditing,
tracing, transactions, caching, security.

**Hand-written decorator** — domain behaviour, or when only two or three classes are involved.
Explicit wrapping is visible in the constructor and in stack traces, and doesn't depend on proxy
semantics that a `private` modifier can silently break.

## Cost

Stack traces grow `$$EnhancerBySpringCGLIB$$` frames, debugging steps into generated code, and
behaviour changes depending on how a method is called.
