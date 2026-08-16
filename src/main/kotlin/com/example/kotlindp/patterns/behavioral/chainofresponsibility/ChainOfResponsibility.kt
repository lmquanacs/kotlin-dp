package com.example.kotlindp.patterns.behavioral.chainofresponsibility

/**
 * # Chain of Responsibility
 *
 * Pass a request along a chain of handlers until one deals with it, so the sender never needs to
 * know which handler will.
 *
 * Two distinct shapes hide under this name and they behave differently:
 * - **first-match** — handlers are alternatives; the first that can handle it wins (this file's
 *   [ApprovalHandler]);
 * - **pipeline / middleware** — *every* handler runs, each wrapping the next (this file's
 *   [Middleware]). Servlet filters, Ktor plugins, and HTTP interceptors are this shape.
 */

// ---------------------------------------------------------------------------------------------
// 1. First-match chain.
// ---------------------------------------------------------------------------------------------

data class Expense(val id: String, val amountCents: Long, val category: String)

sealed class Approval {
    data class Approved(val by: String) : Approval()
    data class Rejected(val by: String, val reason: String) : Approval()
    object Escalate : Approval()
}

/**
 * The `successor` link is what makes it a chain. `open` + `protected val next` is the GoF spelling;
 * the functional version further down is usually nicer in Kotlin.
 */
abstract class ApprovalHandler(private val next: ApprovalHandler? = null) {

    protected abstract fun tryHandle(expense: Expense): Approval?

    /**
     * Note the terminal case. A chain with no handler for a request must *not* silently return
     * success — an unhandled request is a bug, and the chain should say so.
     */
    fun handle(expense: Expense): Approval =
        tryHandle(expense) ?: next?.handle(expense)
        ?: Approval.Rejected("system", "no handler accepted expense ${expense.id}")
}

class TeamLead(next: ApprovalHandler? = null) : ApprovalHandler(next) {
    override fun tryHandle(expense: Expense): Approval? =
        if (expense.amountCents <= 10_000) Approval.Approved("team-lead") else null
}

class Manager(next: ApprovalHandler? = null) : ApprovalHandler(next) {
    override fun tryHandle(expense: Expense): Approval? =
        if (expense.amountCents <= 100_000) Approval.Approved("manager") else null
}

class Director(next: ApprovalHandler? = null) : ApprovalHandler(next) {
    override fun tryHandle(expense: Expense): Approval? = when {
        expense.category == "legal" -> Approval.Rejected("director", "legal spend needs counsel")
        expense.amountCents <= 1_000_000 -> Approval.Approved("director")
        else -> null
    }
}

// ---------------------------------------------------------------------------------------------
// 2. The functional first-match chain — Kotlin's better answer.
// ---------------------------------------------------------------------------------------------

/**
 * A handler that returns `null` for "not mine" needs no successor field at all: `firstNotNullOfOrNull`
 * *is* the chain. The order of the list is the order of the chain, visible at the call site instead
 * of hidden in constructor nesting.
 *
 * This is strictly easier to test — each handler is an independent function — and easier to
 * reconfigure, since the chain is data.
 */
typealias Handler<T, R> = (T) -> R?

fun <T, R> chainOf(vararg handlers: Handler<T, R>): (T) -> R? =
    { input -> handlers.firstNotNullOfOrNull { it(input) } }

val teamLeadRule: Handler<Expense, Approval> =
    { if (it.amountCents <= 10_000) Approval.Approved("team-lead") else null }

val managerRule: Handler<Expense, Approval> =
    { if (it.amountCents <= 100_000) Approval.Approved("manager") else null }

// ---------------------------------------------------------------------------------------------
// 3. Pipeline / middleware — every handler runs.
// ---------------------------------------------------------------------------------------------

data class Request(val path: String, val headers: Map<String, String> = emptyMap())
data class Response(val status: Int, val body: String)

/**
 * Each middleware receives the request *and* a function to call the rest of the chain. That gives
 * it three powers: act before, act after, or **short-circuit** by never calling `next`.
 *
 * This is the shape behind Servlet filters, `HandlerInterceptor`, OkHttp interceptors and Ktor
 * plugins — worth recognising because it is far more common in real systems than the first-match
 * form.
 */
typealias Next = (Request) -> Response
typealias Middleware = (Request, Next) -> Response

/**
 * Build the chain by folding **right**, so the resulting call order matches list order: the first
 * middleware in the list is the outermost. Folding left silently reverses the chain, which is a
 * genuinely confusing bug to diagnose.
 */
fun buildPipeline(middlewares: List<Middleware>, terminal: Next): Next =
    middlewares.foldRight(terminal) { middleware, next -> { req -> middleware(req, next) } }

val requestLogging: Middleware = { req, next ->
    val response = next(req)
    response.copy(body = response.body + "\n[logged ${req.path} -> ${response.status}]")
}

/** Short-circuiting middleware: never calls `next`, so nothing downstream runs. */
val authentication: Middleware = { req, next ->
    if (req.headers["Authorization"] == null) Response(401, "unauthorized") else next(req)
}

val timing: Middleware = { req, next ->
    val start = System.nanoTime()
    val response = next(req)
    response.copy(body = response.body + "\n[took ${System.nanoTime() - start}ns]")
}

/**
 * ## Choosing and using
 *
 * | Shape | Use for |
 * |---|---|
 * | First-match | approvals, routing, parsing alternatives, error-handler selection |
 * | Pipeline | auth, logging, tracing, rate limiting, retries, transactions |
 *
 * ## Failure modes
 *
 * 1. **Silently unhandled requests.** Always define terminal behaviour explicitly.
 * 2. **Order is invisible.** With constructor nesting the chain's order is buried; with a list it is
 *    right there. Prefer the list.
 * 3. **Debuggability.** A long chain makes "why did nothing happen?" hard to answer. Log the
 *    handler that accepted the request.
 */
