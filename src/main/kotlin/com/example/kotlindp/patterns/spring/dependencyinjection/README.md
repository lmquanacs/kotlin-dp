# Dependency Injection as Strategy + Abstract Factory

**The Spring container *is* a factory.** Most hand-written factories in a Spring codebase
re-implement something the container already does, usually worse.

## Constructor injection is non-negotiable in Kotlin

- Dependencies become `val` — immutable, non-null, no `lateinit`, no `?`.
- The type system enforces that a constructed object is fully initialised. Field injection
  (`@Autowired lateinit var`) defers that to runtime and throws
  `UninitializedPropertyAccessException` when wiring is wrong.
- Testable with `new` — no Spring context, no reflection.
- **A constructor with eight parameters is visibly doing too much.** Field injection hides that.

Since Spring 4.3, a single constructor needs no `@Autowired`, so the Kotlin spelling is just a
primary constructor.

## Four wiring shapes that replace hand-rolled pattern code

**1. `List<T>` — Strategy registry + Composite, for free**

```kotlin
@Service
class FraudService(private val checks: List<FraudCheck>) {
    fun evaluate(...) = checks.filter { it.suspicious(...) }.map { it.name }
}
```

Spring injects *every* implementation. Adding a fourth check = adding a `@Component`; this class
doesn't change and neither does any config. Compare `behavioral/strategy`, where the registry is
hand-written.

Ordering is **not** guaranteed by declaration — use `@Order` when it matters. Relying on incidental
ordering is a bug waiting for a refactor.

**2. `Map<String, T>` — Factory Method with no `when` block.** Keys are bean names.

**3. `@Primary` / `@Qualifier`** — `@Primary` for a genuine default; not for "I got
`NoUniqueBeanDefinitionException` and this made it go away". Prefer a **custom qualifier
annotation** over a string: a typo in `"smsSender"` is a startup failure, a typo in an annotation
name is a compile error.

**4. `ObjectProvider<T>`** — "may not exist" or "resolve later". Replaces three worse habits:
`@Autowired(required = false)`, `@Lazy` papering over a cycle, and `try/catch` around `getBean`.
Combined with a Null Object default, call sites stay free of null checks.

## The rule that saves the most debugging time

**Never inject `ApplicationContext` to call `getBean()`.** That's Service Locator: it hides
dependencies from the constructor, defeats compile-time checking, and turns a startup failure into a
runtime one. If you're reaching for it, you want `List<T>`, `Map<String, T>`, or `ObjectProvider<T>`.

## Circular dependencies

Constructor injection makes a cycle a **startup failure** rather than a subtle runtime bug. That's a
feature. Boot 2.6+ disallows cycles by default; on 2.5 they still resolve via `@Lazy`, but the right
fix is almost always extracting the shared behaviour into a third bean.
