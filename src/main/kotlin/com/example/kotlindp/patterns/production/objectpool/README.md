# Object Pool

**Intent** — reuse expensive-to-create objects instead of allocating new ones.

## Start with the warning

Object pooling was widely used in early Java and is now **usually counterproductive**. Modern JVM
allocation is a pointer bump, and generational GC collects short-lived objects almost for free.
Pooling plain objects makes code slower *and* introduces lifecycle bugs.

Pooling is right when the cost is **not allocation but acquisition** — a TCP handshake, a TLS
negotiation, an OS thread, a database session. That's why connection pools and thread pools exist
and generic object pools mostly don't.

## Four decisions that make a pool safe

1. **Bounded.** An unbounded pool isn't a pool — under load it's an unbounded resource leak.
2. **A borrow timeout.** Waiting forever turns resource exhaustion into a total hang with no
   diagnostic. Failing after a bounded wait produces a stack trace that names the pool.
3. **Validate on borrow, on return, or both.** A pool that hands out dead connections is worse than
   no pool.
4. **Lazy creation** up to the cap, not all at startup.

## The API that prevents the defining bug

Every object pool ever written has leaked because someone returned early, or threw, and never called
`release`. **Don't expose `borrow`/`release` as the primary API:**

```kotlin
inline fun <T : Poolable, R> ObjectPool<T>.use(block: (T) -> R): R {
    val item = borrow()
    return try { block(item) } finally { release(item) }
}
```

`inline` means the lambda can `return` from the caller and the object is still released.

## The subtle hazard

**A pooled object carries state between uses.** A connection left inside an open transaction, a
buffer still holding the previous caller's bytes, a `ThreadLocal` set by whoever borrowed it last —
these are the bugs that make pooling expensive to get right. Reset on release, validate on borrow.

## When to pool

| Resource | Use |
|---|---|
| Database connections | **HikariCP** — don't write this |
| Threads | `ExecutorService` / coroutine dispatchers |
| HTTP connections | the client's own pool (OkHttp, Apache) |
| Large buffers | only when allocation shows up in a profile |

## When not to

Anything cheap to construct. A pool adds synchronisation, a validity protocol, a leak risk, and state
that outlives a single use — for objects the JVM would allocate in nanoseconds.
