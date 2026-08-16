package com.example.kotlindp.patterns.creational.prototype

/**
 * # Prototype
 *
 * Create new objects by copying an existing instance instead of constructing from scratch.
 *
 * Java implements this with `Cloneable` and `clone()` — an API widely considered a mistake:
 * `clone()` bypasses constructors, `Cloneable` is a marker interface with no `clone` method on it,
 * and the default is a shallow copy that silently shares mutable state.
 *
 * Kotlin's `data class` gives you `copy()`, which is a generated, type-safe, constructor-respecting
 * prototype operation with named parameters for the fields you want to change.
 */

/**
 * The everyday form: `copy()` on a data class.
 *
 * `copy()` calls the primary constructor with the current values, overriding only the named
 * arguments you pass — so `init` blocks and validation still run, unlike `clone()`.
 */
data class DocumentTemplate(
    val title: String,
    val author: String,
    val tags: List<String>,
    val watermark: String?,
)

val CONFIDENTIAL_TEMPLATE = DocumentTemplate(
    title = "Untitled",
    author = "unknown",
    tags = listOf("confidential"),
    watermark = "CONFIDENTIAL",
)

fun newConfidentialDoc(title: String, author: String): DocumentTemplate =
    CONFIDENTIAL_TEMPLATE.copy(title = title, author = author)

// ---------------------------------------------------------------------------------------------
// The shallow-copy trap, and how to handle it.
// ---------------------------------------------------------------------------------------------

/**
 * **The single thing to know about `copy()`: it is shallow.**
 *
 * Fields are copied by reference. If a field is a mutable object, the copy and the original share
 * it, and mutating through one is visible through the other. This is the same bug `clone()` has —
 * Kotlin just makes it easy to avoid by defaulting to immutable types.
 *
 * Here [labels] is deliberately mutable to demonstrate the hazard; [deepCopy] is the fix.
 */
class MutableCanvas(
    var width: Int,
    var height: Int,
    val labels: MutableList<String>,
) {
    /** Explicit deep copy: rebuild the mutable members rather than sharing them. */
    fun deepCopy(): MutableCanvas = MutableCanvas(width, height, labels.toMutableList())

    /** Shallow copy — shares [labels] with the original. Shown so the test can prove the difference. */
    fun shallowCopy(): MutableCanvas = MutableCanvas(width, height, labels)
}

// ---------------------------------------------------------------------------------------------
// A prototype registry.
// ---------------------------------------------------------------------------------------------

/**
 * The GoF form: a registry of pre-configured prototypes cloned on demand. Useful when the
 * "expensive" part of creation is not allocation but *configuration* — a fully-tuned object graph
 * assembled once at startup and copied per request.
 */
interface Cloneable<T> {
    fun duplicate(): T
}

data class ReportConfig(
    val name: String,
    val columns: List<String>,
    val pageSize: Int,
    val filters: Map<String, String>,
) : Cloneable<ReportConfig> {
    // `copy()` already does the work; `duplicate()` just gives the registry a uniform handle.
    override fun duplicate(): ReportConfig = copy()
}

class PrototypeRegistry<T : Cloneable<T>> {
    private val prototypes = mutableMapOf<String, T>()

    fun register(key: String, prototype: T) {
        prototypes[key] = prototype
    }

    /** Returns a fresh copy — callers can mutate their instance without disturbing the prototype. */
    fun create(key: String): T =
        (prototypes[key] ?: error("No prototype registered for '$key'")).duplicate()

    fun keys(): Set<String> = prototypes.keys.toSet()
}

/**
 * ## Practical note
 *
 * `copy()` and default arguments interact in a way that surprises people: `copy()` passes the
 * *current* value of every field you don't name, so defaults are not re-applied. That's the correct
 * behaviour for a prototype, but it means a nullable field explicitly set to `null` stays `null`
 * rather than reverting to its default.
 *
 * For deep copies of large graphs, serialisation round-trips (Jackson, kotlinx.serialization) are
 * simpler and less error-prone than hand-written `deepCopy()` chains — at a real CPU cost. Measure
 * before choosing.
 */
