# Conditional beans — Abstract Factory selected by configuration

`@ConditionalOnProperty`, `@Profile`, and `@ConditionalOnMissingBean` let the *container* pick the
implementation family — precisely what Abstract Factory (`creational/abstractfactory`) does, without
a hand-written factory or a `when` over an enum.

The key difference from a runtime factory: **selection happens once, at startup.** A misconfigured
value fails immediately, and there's no per-call dispatch.

## One `@Configuration` per family

```kotlin
@Configuration
@ConditionalOnProperty(name = ["storage.provider"], havingValue = "s3")
class S3StorageConfig {
    @Bean fun storageClient(): StorageClient = S3StorageClient("app-uploads")
    @Bean fun storageSigner(): StorageSigner = S3StorageSigner("app-uploads")
}
```

Grouping the family in one class is what makes this Abstract Factory rather than a set of unrelated
conditionals: **client and signer activate together**, so a local client can never be paired with an
S3 signer.

## Always give one family `matchIfMissing = true`

```kotlin
@ConditionalOnProperty(name = ["storage.provider"], havingValue = "local", matchIfMissing = true)
```

A configuration key with no default means every new developer and every CI job starts with a startup
failure.

## `@ConditionalOnMissingBean`

How every Spring Boot auto-configuration works: provide a default *only if the application hasn't
defined its own*. Right for a shared library or starter module; within a single application it's
usually over-engineering — just declare the bean.

**Ordering caveat:** it's evaluated in registration order, so it's reliable only in
auto-configuration (which runs last) or with `@AutoConfigureAfter`. Between two ordinary
`@Configuration` classes it's a race you can lose.

## `@Profile` vs `@ConditionalOnProperty`

- **`@Profile`** — coarse, environment-shaped (`dev`, `test`, `prod`). Good for swapping whole sets
  of infrastructure beans.
- **`@ConditionalOnProperty`** — fine-grained, independently switchable, visible in configuration
  rather than a launch argument.

**Prefer properties.** Profiles multiply: four flags expressed as profiles gives sixteen
combinations, of which you've tested two.

## Diagnosing it

When the wrong implementation is active, run with `--debug` (or
`logging.level.org.springframework.boot.autoconfigure=DEBUG`) for the **condition evaluation
report** — every condition and why it matched. Answers "why is this bean missing?" faster than any
amount of reading.

## The failure mode

A typo in a property value selects no family and the app fails with `NoSuchBeanDefinitionException` —
clear, but only at startup. Guard by giving one family `matchIfMissing = true`, and by binding the
provider name to an **enum** in `@ConfigurationProperties` so an invalid value is rejected with a
message naming the property.
