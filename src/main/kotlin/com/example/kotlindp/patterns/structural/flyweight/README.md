# Flyweight

**Intent** — share one instance across many logical objects to cut memory.

State splits in two:
- **intrinsic** — shared, immutable, lives in the flyweight;
- **extrinsic** — unique per use, passed in as a parameter.

This is the one GoF pattern whose justification is purely quantitative. Don't apply it because it's
elegant; apply it when a heap dump says you have millions of near-identical objects.

## Kotlin already gives you flyweights

- **`object`** — one instance per declaration.
- **`enum`** — a fixed set of shared instances, i.e. a flyweight pool with compile-time membership.
  For a *closed* set this beats a hand-written pool every time.
- **`String` literals** — interned by the JVM at class load.
- **Boxed `Int` cache** — the JVM shares `Integer` for −128..127. (Which is why `===` on boxed
  numbers behaves differently inside and outside that window — never compare boxed numbers by
  reference.)
- **`@JvmInline value class`** — nothing to share, because there's no instance at all.

## Implementation notes

Use `ConcurrentHashMap.computeIfAbsent` for the pool. It's atomic get-or-create, so concurrent
callers receive the *same* instance; a plain `HashMap` both corrupts under concurrency and hands out
duplicates, defeating the purpose.

The shared state **must** be immutable. A mutable flyweight is a data race and a
spooky-action-at-a-distance bug affecting every holder at once.

## The leak

An intern pool is a strong reference. Interning unbounded user-supplied strings is a textbook memory
leak. Bound the pool (as `InternPool` here does), use `WeakHashMap`, or intern only from a
known-small domain — country codes, HTTP header names, enum-like tokens.

## Production use case

Currency/locale metadata shared by millions of amounts; interned header and tag names in a parser;
glyph and tile caches; shared immutable config objects across tenants.

## Trade-offs

You trade a hash lookup (plus cache-miss risk) per access for memory, and you force immutability on
the shared part. Millions of objects with few distinct values → good. Thousands → you've made the
code slower *and* harder to read. Measure first.
