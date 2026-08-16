# Chain of Responsibility

**Intent** — pass a request along a chain of handlers until one deals with it, so the sender doesn't
know which handler will.

## Two shapes hide under one name

**First-match** — handlers are alternatives, the first that can handle it wins. Approvals, routing,
parser alternatives.

**Pipeline / middleware** — *every* handler runs, each wrapping the next. Servlet filters,
`HandlerInterceptor`, OkHttp interceptors, Ktor plugins. This one is far more common in real
systems.

## Kotlin idiom

**First-match: skip the successor field entirely.** A handler that returns `null` for "not mine"
needs no chain link — `firstNotNullOfOrNull` *is* the chain:

```kotlin
typealias Handler<T, R> = (T) -> R?
fun <T, R> chainOf(vararg handlers: Handler<T, R>): (T) -> R? =
    { input -> handlers.firstNotNullOfOrNull { it(input) } }
```

Order becomes visible at the call site instead of buried in constructor nesting, each handler tests
independently, and the chain is data you can reconfigure.

**Pipeline: fold right.**

```kotlin
typealias Next = (Request) -> Response
typealias Middleware = (Request, Next) -> Response

fun buildPipeline(mws: List<Middleware>, terminal: Next): Next =
    mws.foldRight(terminal) { mw, next -> { req -> mw(req, next) } }
```

`foldRight` makes call order match list order. **`fold` (left) silently reverses the chain** — a
genuinely confusing bug to diagnose.

Each middleware gets the request *and* a function to call the rest, giving it three powers: act
before, act after, or **short-circuit** by never calling `next` (that's how auth rejection works).

## Failure modes

1. **Silently unhandled requests.** A chain with no matching handler must not return success. An
   unhandled request is a bug — say so.
2. **Invisible order.** With constructor nesting the order is buried. Prefer a list.
3. **Debuggability.** A long chain makes "why did nothing happen?" hard to answer. Log which handler
   accepted.

## Production use case

Auth → rate limit → tracing → logging → handler pipelines; expense/approval hierarchies; fallback
resolution (cache → local → remote); exception-handler selection.
