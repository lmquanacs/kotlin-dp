# kotlin-dp

A production-oriented catalogue of design patterns in Kotlin, built on Spring Boot 2.5 + Kotlin
1.5.30 with Gradle.

Every pattern is a folder containing a heavily-commented implementation and a `README.md`. Every
pattern is exercised by a test — including the Spring ones, which run against a real booted
application context.

## Stack

| | |
|---|---|
| Spring Boot | 2.5.15 |
| Kotlin | 1.5.30 |
| Gradle | 7.6.4 (via wrapper) |
| JVM target | 11 |

## Contents

- **[Pattern catalogue](src/main/kotlin/com/example/kotlindp/patterns/)** — the index, with a
  one-line Kotlin verdict per pattern.
- **[Kotlin feature map](docs/KOTLIN_FEATURES.md)** — every language feature used here, what it's
  for, and which folder demonstrates it.
- **[MCP client example](examples/mcp-client-example.md)** — a worked design walkthrough applying
  the catalogue to one realistic system, including which patterns to leave out.

| Group | Patterns |
|---|---|
| [Creational](src/main/kotlin/com/example/kotlindp/patterns/creational/) | singleton, factory method, abstract factory, builder, prototype |
| [Structural](src/main/kotlin/com/example/kotlindp/patterns/structural/) | adapter, bridge, composite, decorator, facade, flyweight, proxy |
| [Behavioral](src/main/kotlin/com/example/kotlindp/patterns/behavioral/) | chain of responsibility, command, interpreter, iterator, mediator, memento, null object, observer, state, strategy, template method, visitor |
| [Kotlin idioms](src/main/kotlin/com/example/kotlindp/patterns/kotlinidioms/) | DSLs, delegation, typed errors, extensions, scope functions, inline/reified, functional, generics, coroutines |
| [Production](src/main/kotlin/com/example/kotlindp/patterns/production/) | retry with jitter, circuit breaker, object pool, cache-aside |
| [Spring Boot](src/main/kotlin/com/example/kotlindp/patterns/spring/) | dependency injection, configuration properties, bean lifecycle, application events, AOP, service layer, interceptors, conditional beans |

## Build & test

```bash
./gradlew build          # compile + test + package
./gradlew test
./gradlew bootRun
```

The runnable jar lands in `build/libs/kotlin-dp-0.0.1-SNAPSHOT.jar`.

### Endpoints

```bash
curl 'http://localhost:8080/?name=Kotlin'                  # Hello, Kotlin!
curl 'http://localhost:8080/hello?name=Spring'             # {"message":"Hello, Spring!"}
curl 'http://localhost:8080/patterns/orders/catalogue'     # service-layer pattern demo
curl -X POST 'http://localhost:8080/patterns/orders' \
     -H 'Content-Type: application/json' \
     -d '{"sku":"WIDGET","quantity":2}'
```

## Notes on the approach

Patterns are presented with a verdict, not just a translation. Several are here mainly so you can
recognise when *not* to reach for them — Builder (named arguments win), Visitor (sealed + `when`
wins), Flyweight and Object Pool (measure first), Null Object (`?.` wins).

The Spring group shows where the framework already implements a pattern, so you don't hand-roll a
worse version: `List<T>` injection is a Strategy registry, `@ConditionalOnProperty` is an Abstract
Factory, `@EventListener` is Observer, AOP is Decorator.

## Toolchain note

The Gradle wrapper is pinned to **7.6.4** on purpose. The Spring Boot 2.5 and Kotlin 1.5.30 Gradle
plugins do not load on Gradle 8+ — on Gradle 9 the Boot plugin fails with
`Configuration.getUploadTaskName()` not found. Always use `./gradlew`, never a system-installed
`gradle`.

Verified on JDK 21 with Gradle 7.6.4, compiling to JVM target 11. Every build prints a
"Deprecated Gradle features were used" warning; it is expected and harmless for these plugin
versions.
