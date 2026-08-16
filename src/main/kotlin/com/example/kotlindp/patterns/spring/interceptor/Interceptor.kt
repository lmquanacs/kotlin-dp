package com.example.kotlindp.patterns.spring.interceptor

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * # Filters and interceptors — Chain of Responsibility on the request path
 *
 * The middleware form of Chain of Responsibility (`behavioral/chainofresponsibility`), built into
 * the servlet stack. Every request passes through an ordered chain; each link can act before, act
 * after, or short-circuit by not calling the next one.
 *
 * Spring gives you two layers, and choosing the wrong one is the usual mistake.
 */

// ---------------------------------------------------------------------------------------------
// Layer 1: Filter — servlet level, before Spring MVC.
// ---------------------------------------------------------------------------------------------

/**
 * A [Filter] sits at the servlet container level. It sees **every** request — including static
 * resources, error dispatches, and requests that never reach a controller — and it can replace the
 * request or response objects.
 *
 * Use a filter for things that must happen regardless of routing: correlation IDs, security,
 * request/response wrapping, compression, CORS.
 *
 * This one assigns a correlation ID and puts it on the response, which is the single highest-value
 * filter in a distributed system — without it you cannot follow one request across services.
 */
class CorrelationIdFilter : Filter {

    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val httpRequest = request as HttpServletRequest
        val httpResponse = response as HttpServletResponse

        // Honour an inbound ID so a trace survives across service boundaries.
        val correlationId = httpRequest.getHeader(HEADER) ?: UUID.randomUUID().toString()
        httpResponse.setHeader(HEADER, correlationId)

        try {
            // Not calling this would short-circuit the request — that is how an auth filter rejects.
            chain.doFilter(request, response)
        } finally {
            // In real code the MDC cleanup goes here. `finally` matters: with a pooled thread, a
            // value left behind is attributed to the *next* request that thread serves.
            lastCorrelationId.set(correlationId)
        }
    }

    companion object {
        const val HEADER = "X-Correlation-Id"
        val lastCorrelationId = ThreadLocal<String?>()
    }
}

/**
 * `FilterRegistrationBean` gives explicit control over order and URL patterns. Registering the
 * filter as a bare `@Component` applies it to every URL with an unspecified order, which is rarely
 * what you want.
 *
 * The patterns here deliberately scope the filter to this package's endpoints, so it cannot alter
 * the behaviour of the rest of the application.
 */
@Configuration
class FilterConfig {

    @Bean
    fun correlationIdFilter(): FilterRegistrationBean<CorrelationIdFilter> =
        FilterRegistrationBean(CorrelationIdFilter()).apply {
            addUrlPatterns("/patterns/*")
            order = 1
        }
}

// ---------------------------------------------------------------------------------------------
// Layer 2: HandlerInterceptor — Spring MVC level, after routing.
// ---------------------------------------------------------------------------------------------

/**
 * A [HandlerInterceptor] runs inside Spring MVC, **after** the request has been mapped to a handler.
 * That is its advantage: it knows *which* controller method will run, so it can read annotations on
 * the handler and make decisions a filter cannot.
 *
 * Three hooks:
 * - `preHandle` — before the controller. Returning `false` stops the request.
 * - `postHandle` — after the controller, **before** the view/body is written.
 * - `afterCompletion` — always runs, including on exception. Cleanup belongs here.
 *
 * The mistake worth avoiding: modifying the response in `postHandle` for a `@RestController`. By
 * then the body may already be committed by the message converter, so the change is silently lost.
 * Use a filter (or `ResponseBodyAdvice`) for response mutation.
 */
@Component
class MetricsInterceptor : HandlerInterceptor {

    private val started = ThreadLocal<Long>()
    private val requests = AtomicLong()
    private val failures = AtomicLong()

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        started.set(System.nanoTime())
        requests.incrementAndGet()
        return true // false here would short-circuit the request
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        // afterCompletion always runs — this is the only hook safe for cleanup.
        started.remove()
        if (ex != null || response.status >= 500) failures.incrementAndGet()
    }

    fun requestCount(): Long = requests.get()
    fun failureCount(): Long = failures.get()
    fun reset() {
        requests.set(0); failures.set(0)
    }
}

/**
 * Interceptors are registered through [WebMvcConfigurer]. Path patterns are the important part:
 * registering broadly and then checking the path inside `preHandle` puts routing logic in the wrong
 * place and costs a call on every request.
 */
@Configuration
class InterceptorConfig(private val metricsInterceptor: MetricsInterceptor) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(metricsInterceptor)
            .addPathPatterns("/patterns/**")
            .excludePathPatterns("/patterns/health")
    }
}

/**
 * ## Choosing a layer
 *
 * | Need | Use |
 * |---|---|
 * | Correlation ID, security, CORS, compression | **Filter** — sees everything, can wrap req/res |
 * | Behaviour depending on the target handler or its annotations | **HandlerInterceptor** |
 * | Behaviour on specific *beans*, not requests | **AOP** (`spring/aop`) |
 * | Mutating the response body | **`ResponseBodyAdvice`**, not `postHandle` |
 *
 * ## Ordering
 *
 * Filters run outside interceptors, always. Within each layer, order is explicit —
 * `FilterRegistrationBean.order` and interceptor registration order. Leaving it to chance is the
 * same bug as `fold` vs `foldRight` in the hand-written pipeline: everything works until someone
 * adds a link.
 *
 * ## The ThreadLocal rule
 *
 * Anything put in a `ThreadLocal` (or MDC) **must** be removed in `finally`/`afterCompletion`.
 * Servlet threads are pooled, so a leaked value is inherited by an unrelated later request — which
 * produces logs attributed to the wrong user, and is a genuine data-leak class of bug.
 */
