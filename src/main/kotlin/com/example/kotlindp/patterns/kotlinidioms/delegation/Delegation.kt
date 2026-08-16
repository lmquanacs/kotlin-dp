package com.example.kotlindp.patterns.kotlinidioms.delegation

import kotlin.properties.Delegates
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * # Delegation
 *
 * Kotlin has two distinct delegation features that share a keyword. Confusing them is common.
 *
 * 1. **Class delegation** — `class A(b: B) : I by b`. The compiler generates forwarding methods for
 *    every member of interface `I`.
 * 2. **Property delegation** — `val x by delegate`. Reads and writes of `x` are routed to the
 *    delegate's `getValue`/`setValue`.
 *
 * Together they replace most of what inheritance is (mis)used for.
 */

// ---------------------------------------------------------------------------------------------
// 1. Class delegation — composition with zero boilerplate.
// ---------------------------------------------------------------------------------------------

interface EventStore {
    fun append(event: String)
    fun all(): List<String>
    fun size(): Int
}

class InMemoryEventStore : EventStore {
    private val events = mutableListOf<String>()
    override fun append(event: String) {
        events += event
    }

    override fun all(): List<String> = events.toList()
    override fun size(): Int = events.size
}

/**
 * "Favour composition over inheritance" is easy advice and tedious to follow in Java, because
 * composition means writing a forwarding method per interface member. `by` removes that tax
 * entirely, which is why Kotlin can make classes `final` by default without pain.
 */
class ValidatingEventStore(
    private val delegate: EventStore,
) : EventStore by delegate {

    val rejected = mutableListOf<String>()

    override fun append(event: String) {
        if (event.isBlank()) {
            rejected += event
            return
        }
        delegate.append(event)
    }
}

/**
 * **The trap.** Delegation is not inheritance: there is no virtual dispatch back into the delegating
 * class.
 *
 * [InMemoryEventStore.append] does not call `size()`, but if it did, that call would resolve to
 * [InMemoryEventStore]'s own `size()` — never to an override here. Delegated calls are forwarded to
 * a *separate object* that has no knowledge of the wrapper.
 *
 * This is usually what you want (no fragile base class), but it does surprise people expecting
 * template-method behaviour.
 */
class CountingEventStore(private val delegate: EventStore) : EventStore by delegate {
    var appendCalls = 0
        private set

    override fun append(event: String) {
        appendCalls++
        delegate.append(event)
    }
}

// ---------------------------------------------------------------------------------------------
// 2. Standard property delegates.
// ---------------------------------------------------------------------------------------------

class StandardDelegates {

    /**
     * `lazy` — computed on first access, then cached. Thread-safe by default
     * (`LazyThreadSafetyMode.SYNCHRONIZED`).
     *
     * Use `LazyThreadSafetyMode.NONE` only when access is provably confined to one thread; it is
     * faster but offers no guarantee at all.
     */
    var initialisations = 0
    val expensive: String by lazy {
        initialisations++
        "computed"
    }

    /** `observable` — fires *after* the assignment. */
    val changes = mutableListOf<String>()
    var status: String by Delegates.observable("new") { prop, old, new ->
        changes += "${prop.name}: $old->$new"
    }

    /** `vetoable` — fires *before*; returning false rejects the assignment outright. */
    var percentage: Int by Delegates.vetoable(0) { _, _, new -> new in 0..100 }

    /**
     * `notNull` — a `var` with no sensible initial value, that must be set before first read.
     * Reading it early throws `IllegalStateException` rather than yielding a bogus default.
     * This is the `lateinit` equivalent for primitives, which `lateinit` does not support.
     */
    var configuredPort: Int by Delegates.notNull()
}

/**
 * Map-backed properties — the delegate can be a `Map`. This is how you give a schemaless payload a
 * typed façade, and it is why Kotlin data classes can be constructed straight from a map.
 *
 * A missing key throws `NoSuchElementException` on *access*, not on construction — so this is a
 * convenience, not a validation mechanism.
 */
class Settings(private val values: Map<String, Any?>) {
    val host: String by values
    val port: Int by values
    val debug: Boolean by values
}

// ---------------------------------------------------------------------------------------------
// 3. Writing your own delegate.
// ---------------------------------------------------------------------------------------------

/**
 * A read-only delegate needs `getValue(thisRef, property)`; a read-write one adds `setValue`.
 * Implementing [ReadOnlyProperty]/[ReadWriteProperty] is the typed way to do it.
 *
 * This one records every write with a timestamp — an audit trail added to a property with one word
 * at the declaration site.
 */
class Audited<T>(
    initial: T,
    private val log: MutableList<String>,
) : ReadWriteProperty<Any?, T> {

    private var value: T = initial

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        log += "${property.name}: ${this.value} -> $value"
        this.value = value
    }
}

/**
 * A validating delegate. Note the failure happens at the *assignment*, so the invalid value never
 * enters the object — much easier to debug than discovering it three layers later.
 */
class Validated<T>(
    initial: T,
    private val requirement: String,
    private val predicate: (T) -> Boolean,
) : ReadWriteProperty<Any?, T> {

    private var value: T = initial

    init {
        require(predicate(initial)) { "initial value $initial violates: $requirement" }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        require(predicate(value)) { "${property.name}=$value violates: $requirement" }
        this.value = value
    }
}

/**
 * A delegate that derives its value from another property — read-only, always consistent, and
 * impossible to leave stale, which is the usual bug with a manually-maintained cached field.
 */
class Derived<R, T>(private val compute: (R) -> T) : ReadOnlyProperty<R, T> {
    override fun getValue(thisRef: R, property: KProperty<*>): T = compute(thisRef)
}

class Order(val unitPriceCents: Long, val quantity: Int) {
    val totalCents: Long by Derived<Order, Long> { it.unitPriceCents * it.quantity }
}

class ServerConfig(log: MutableList<String>) {
    var name: String by Audited("default", log)
    var port: Int by Validated(8080, "port in 1..65535") { it in 1..65535 }
}

/**
 * ## `provideDelegate`
 *
 * Adding `operator fun provideDelegate(thisRef, property)` lets a delegate run logic *at property
 * creation* — validating the property's name, registering it, or choosing a different delegate based
 * on it. This is how Gradle's `by project` and DI frameworks' `by inject()` work.
 *
 * ## Costs
 *
 * Each delegated property allocates a delegate object and adds an indirection on every access. For
 * hot loops that matters; almost everywhere else it does not. `lazy` in particular is worth knowing
 * to be a real object with a volatile field, not free.
 */
