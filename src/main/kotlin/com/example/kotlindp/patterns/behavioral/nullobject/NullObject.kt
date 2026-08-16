package com.example.kotlindp.patterns.behavioral.nullobject

/**
 * # Null Object
 *
 * Provide a do-nothing implementation of an interface so callers never have to null-check.
 *
 * This pattern is a response to Java's null problem — and Kotlin already solved that problem with
 * nullable types, `?.`, and `?:`. So the honest framing is: **Null Object is much less necessary in
 * Kotlin, and knowing when it still helps is the useful part.**
 */

interface AuditSink {
    fun record(event: String)
    fun events(): List<String>
}

class ListAuditSink : AuditSink {
    private val events = mutableListOf<String>()
    override fun record(event: String) {
        events += event
    }

    override fun events(): List<String> = events.toList()
}

/**
 * The null object. `object` means one shared instance, zero allocation cost.
 *
 * The key property: it must be a *valid, silent* implementation — not one that throws. A null object
 * that throws is worse than a null, because it fails later and further from the cause.
 */
object NoOpAuditSink : AuditSink {
    override fun record(event: String) = Unit
    override fun events(): List<String> = emptyList()
}

/**
 * Because the default is the null object, [OrderService] has no null checks and no branching, and
 * production wiring simply passes a real sink. This is the case where the pattern still pays: the
 * *dependency* is optional but the *call sites* are many.
 */
class OrderService(private val audit: AuditSink = NoOpAuditSink) {
    fun place(orderId: String): String {
        audit.record("order.placed $orderId")
        return "placed:$orderId"
    }

    fun cancel(orderId: String): String {
        audit.record("order.cancelled $orderId")
        return "cancelled:$orderId"
    }
}

// ---------------------------------------------------------------------------------------------
// What Kotlin does instead, most of the time.
// ---------------------------------------------------------------------------------------------

/**
 * With a nullable dependency the safe-call operator gives the same "do nothing" behaviour without a
 * second implementation:
 *
 * ```kotlin
 * class OrderService(private val audit: AuditSink? = null) {
 *     fun place(id: String) { audit?.record("order.placed $id") }
 * }
 * ```
 *
 * Prefer this when there are one or two call sites. Prefer the null object when there are twenty,
 * or when the interface has several methods and `?.` starts to litter the code.
 *
 * The stdlib's own null-object-ish helpers are worth knowing, since they cover most cases:
 * `emptyList()`, `emptyMap()`, `emptySequence()`, `orEmpty()`, and `?: default`.
 */
fun summarise(tags: List<String>?): String = tags.orEmpty().joinToString(",").ifEmpty { "(none)" }

// ---------------------------------------------------------------------------------------------
// The variant that is genuinely valuable: null object in a sealed hierarchy.
// ---------------------------------------------------------------------------------------------

/**
 * Here the "absent" case is a *named domain concept*, not merely a missing value — and that is
 * strictly more informative than `User?`.
 *
 * `Anonymous` answers the same questions a real user does, so callers branch only where the
 * distinction genuinely matters, and the exhaustive `when` guarantees they do not forget.
 */
sealed class Principal {
    abstract val displayName: String
    abstract fun hasRole(role: String): Boolean

    data class Authenticated(
        val id: String,
        override val displayName: String,
        val roles: Set<String>,
    ) : Principal() {
        override fun hasRole(role: String) = role in roles
    }

    /** The null object — a legitimate participant, not an absence to be checked for. */
    object Anonymous : Principal() {
        override val displayName = "anonymous"
        override fun hasRole(role: String) = false
    }
}

fun canEdit(principal: Principal): Boolean = principal.hasRole("editor")

/**
 * ## When to use it in Kotlin
 *
 * - An optional collaborator called from many places (metrics, audit, tracing) — a no-op default
 *   beats twenty `?.` calls.
 * - The absent case has a *name* in the domain: `Anonymous`, `FreeTier`, `UnknownLocation`.
 * - You want a default that can be swapped in tests without a mocking framework.
 *
 * ## When not to
 *
 * - One or two call sites: `?.` and `?:` are clearer.
 * - The caller genuinely must react to absence. A null object that silently swallows a missing
 *   configuration turns a startup failure into a mystery at 3am. Absence that *matters* should be
 *   loud, not polite.
 */
