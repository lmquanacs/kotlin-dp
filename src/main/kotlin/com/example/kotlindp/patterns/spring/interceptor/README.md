# Filters and interceptors — Chain of Responsibility on the request path

The middleware form of Chain of Responsibility (`behavioral/chainofresponsibility`), built into the
servlet stack. Every request passes an ordered chain; each link can act before, act after, or
short-circuit by not calling the next.

Spring gives two layers, and picking the wrong one is the usual mistake.

## Filter — servlet level, before Spring MVC

Sees **every** request, including static resources, error dispatches, and requests that never reach a
controller. Can replace the request/response objects.

Use for: correlation IDs, security, request/response wrapping, compression, CORS.

```kotlin
@Bean
fun correlationIdFilter() = FilterRegistrationBean(CorrelationIdFilter()).apply {
    addUrlPatterns("/patterns/*")
    order = 1
}
```

Register via `FilterRegistrationBean`, not a bare `@Component` — the latter applies to `/*` with an
unspecified order, which is rarely what you want.

## HandlerInterceptor — Spring MVC level, after routing

Runs **after** the request is mapped to a handler, so it knows *which* controller method will run and
can read annotations on it. A filter cannot.

| Hook | When |
|---|---|
| `preHandle` | before the controller; returning `false` stops the request |
| `postHandle` | after the controller, **before** the body is written |
| `afterCompletion` | **always**, including on exception — cleanup goes here |

**Don't modify the response in `postHandle` for a `@RestController`.** By then the body may already
be committed by the message converter, so the change is silently lost. Use a filter or
`ResponseBodyAdvice`.

Register with path patterns via `WebMvcConfigurer`. Registering broadly and checking the path inside
`preHandle` puts routing logic in the wrong place and costs a call on every request.

## Choosing

| Need | Use |
|---|---|
| Correlation ID, security, CORS, compression | **Filter** |
| Behaviour depending on the target handler or its annotations | **HandlerInterceptor** |
| Behaviour on specific *beans*, not requests | **AOP** |
| Mutating the response body | **`ResponseBodyAdvice`** |

## Ordering

Filters always run outside interceptors. Within each layer, set order explicitly. Leaving it to
chance is the same bug as `fold` vs `foldRight` in the hand-written pipeline — everything works until
someone adds a link.

## The ThreadLocal rule

Anything put in a `ThreadLocal` (or MDC) **must** be removed in `finally`/`afterCompletion`. Servlet
threads are pooled, so a leaked value is inherited by an unrelated later request — producing logs
attributed to the wrong user, and a genuine data-leak class of bug.
