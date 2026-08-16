# Typed configuration — Builder + Value Object, done by the framework

`@ConfigurationProperties` + `@ConstructorBinding` produces an **immutable data class with defaults
and validation** — exactly what a Builder is usually hand-written to construct.

## The real benefit is fail-fast

Not typing. **A misconfigured value stops the application at startup**, with a message naming the
property, instead of throwing at 3am the first time that code path runs.

## What it replaces

```kotlin
@ConstructorBinding
@ConfigurationProperties(prefix = "payments")
@Validated
data class PaymentProperties(
    @field:NotBlank val baseUrl: String = "https://payments.example.com",
    val connectTimeout: Duration = Duration.ofSeconds(5),
    @field:Min(0) @field:Max(10) val maxRetries: Int = 3,
    @field:Valid val pool: PoolProperties = PoolProperties(),
    val enabledCurrencies: Set<String> = setOf("USD", "EUR"),
) {
    init {
        require(readTimeout >= connectTimeout) { "..." }   // cross-field rules
    }
    val totalTimeout: Duration get() = connectTimeout + readTimeout
}
```

Gone: `@Value` on scattered fields (untyped, unvalidated), a hand-written builder with defaults, and
manual `require` calls in application code.

**`@ConstructorBinding` is what makes it immutable.** Without it Spring needs setters, so every
field becomes a `var` and the object stays mutable for the process lifetime.

**Boot 2.5 gotcha:** constructor-bound classes must be registered via `@EnableConfigurationProperties`
or `@ConfigurationPropertiesScan`. A plain `@Component` will *not* work.

## Use real types

`Duration` binds from `5s`, `500ms`, `PT5S`. Using a real type rather than `Long` removes a whole
class of unit-confusion bugs — the ones where someone passes seconds to a millisecond parameter.
Same for `DataSize`, `URI`, enums, `Set<T>`.

Relaxed binding means `connect-timeout`, `connectTimeout`, `CONNECT_TIMEOUT`, and
`PAYMENTS_CONNECTTIMEOUT` all bind to the same property — one class, works with YAML, properties, and
env vars.

## Guidelines

- **One properties class per cohesive concern**, named after its prefix. A single `AppProperties`
  with 60 fields is a god object.
- **Always give defaults** that work in development, so a fresh checkout starts.
- **`@Validated` + JSR-380 for field rules; `init` for cross-field rules.**
- Prefer this over `@Value` everywhere.

## In tests

It's a plain data class, so unit tests just construct it: `PaymentProperties(maxRetries = 1)`. No
context, no `@TestPropertySource`. Same advantage constructor injection gives, applied to config.
