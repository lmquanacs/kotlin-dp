# Generics, variance, and type-safe design

Variance answers one question: **if `Dog` is an `Animal`, is `Box<Dog>` a `Box<Animal>`?**

The answer depends on what the box *does* with its type parameter, and Kotlin makes you say so at the
declaration site — which is why Kotlin generics read better than Java's wildcards.

**Mnemonic: producers are `out`, consumers are `in`.** (PECS, declared once on the class instead of
at every use site.)

## `out` — covariance

`out T` means the type only ever comes **out**. Then `Producer<Dog>` is safely a `Producer<Animal>`.
The compiler enforces soundness: with `out T`, `T` can't appear as a parameter type.

This is why `List<T>` is covariant in Kotlin (read-only) and `MutableList<T>` is not.

## `in` — contravariance

`in T` means the type only ever goes **in**. Then `Consumer<Animal>` is safely a `Consumer<Dog>` —
something that handles any animal certainly handles a dog.

This is the counter-intuitive direction, and exactly why `Comparator<in T>` is declared that way: a
`Comparator<Animal>` can sort a `List<Dog>`.

## Invariance and the escape hatch

A class that both produces and consumes must be invariant. **Use-site variance** —
`List<Box<out Animal>>` — is Kotlin's `? extends T`: this function promises to only read.

Prefer declaration-site variance when you own the class; use-site when you don't.

## Constraints

```kotlin
fun <T : Comparable<T>> maxOfList(items: List<T>): T?

fun <T> latestById(items: List<T>): Map<String, T>
    where T : Identifiable, T : Timestamped      // multiple bounds need `where`
```

Multiple bounds let you require several interfaces without inventing a marker supertype.

## Two advanced shapes

**F-bounded polymorphism** — `T : QueryBuilder<T>` lets a base class return the *subclass* type, so a
fluent chain doesn't degrade to the base type after the first inherited call. Worth knowing, worth
avoiding when something simpler exists; in Kotlin an extension function on the subtype usually
solves the same problem.

**Phantom types** — a type parameter used only to mark state, with no value of it ever stored:

```kotlin
fun send(email: Email<Validated>): String    // an unvalidated email cannot be sent
```

Misuse becomes a *compile* error rather than a runtime check. No runtime cost, and no
`if (validated) throw ...` to forget.

## Erasure — what it costs you

Type arguments don't exist at runtime:

- `is List<String>` isn't expressible (`is List<*>` is);
- you can't overload on `List<String>` vs `List<Int>` — same JVM signature;
- `T()` is impossible without a factory parameter or `reified`.

## Star projection

`Box<*>` = "a Box of some unknown type". You can read values as the upper bound and write nothing at
all. Use when the type argument genuinely doesn't matter — logging, counting, equality.
