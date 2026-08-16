package com.example.kotlindp.patterns.kotlinidioms.generics

/**
 * # Generics, variance, and type-safe design
 *
 * Variance answers one question: **if `Dog` is a `Animal`, is `Box<Dog>` a `Box<Animal>`?**
 *
 * The answer depends on what the box *does* with its type parameter, and Kotlin makes you say so at
 * the declaration site — which is why Kotlin generics read better than Java's wildcards.
 *
 * The mnemonic that actually helps: **producers are `out`, consumers are `in`** (PECS, declared
 * once on the class instead of at every use site).
 */

open class Animal(val name: String)
class Dog(name: String) : Animal(name)
class Cat(name: String) : Animal(name)

// ---------------------------------------------------------------------------------------------
// 1. Covariance — `out`.
// ---------------------------------------------------------------------------------------------

/**
 * `out T` means the type only ever comes **out** of this class. Then `Producer<Dog>` is safely a
 * `Producer<Animal>`, because everything it hands you is a `Dog`, and every `Dog` is an `Animal`.
 *
 * The compiler enforces it: with `out T`, `T` cannot appear as a parameter type. That is what makes
 * covariance sound, and it is why `List<T>` is covariant in Kotlin (read-only) while
 * `MutableList<T>` is not.
 */
interface Producer<out T> {
    fun produce(): T
}

class Kennel(private val dogs: List<Dog>) : Producer<Dog> {
    override fun produce(): Dog = dogs.first()
}

/** Accepts `Producer<Dog>`, `Producer<Cat>`, `Producer<Animal>` — all safe. */
fun feedFrom(producer: Producer<Animal>): String = "fed ${producer.produce().name}"

// ---------------------------------------------------------------------------------------------
// 2. Contravariance — `in`.
// ---------------------------------------------------------------------------------------------

/**
 * `in T` means the type only ever goes **in**. Then `Consumer<Animal>` is safely a `Consumer<Dog>` —
 * something that can handle any animal can certainly handle a dog.
 *
 * This is the direction people find counter-intuitive, and it is exactly why `Comparator<in T>` is
 * declared that way: a `Comparator<Animal>` can sort a `List<Dog>`.
 */
interface Consumer<in T> {
    fun consume(item: T)
}

class AnimalShelter : Consumer<Animal> {
    val admitted = mutableListOf<String>()
    override fun consume(item: Animal) {
        admitted += item.name
    }
}

fun admitDogs(consumer: Consumer<Dog>, dogs: List<Dog>) = dogs.forEach { consumer.consume(it) }

// ---------------------------------------------------------------------------------------------
// 3. Invariance, and the use-site escape hatch.
// ---------------------------------------------------------------------------------------------

/**
 * A class that both produces and consumes `T` must be invariant — `Box<Dog>` is *not* a
 * `Box<Animal>`, because writing an `Animal` into it would break the `Dog` reader.
 */
class Box<T>(private var item: T) {
    fun get(): T = item
    fun set(value: T) {
        item = value
    }
}

/**
 * **Use-site variance** (`out T` in a parameter position) is Kotlin's equivalent of Java's
 * `? extends T`: this function promises to only read, so it accepts a `Box` of any subtype.
 *
 * Prefer declaration-site variance when you own the class; use-site is for when you do not, or when
 * the class genuinely needs both directions.
 */
fun describeAll(boxes: List<Box<out Animal>>): List<String> = boxes.map { it.get().name }

// ---------------------------------------------------------------------------------------------
// 4. Generic constraints.
// ---------------------------------------------------------------------------------------------

/** Upper bound: `T` must be comparable with itself. */
fun <T : Comparable<T>> maxOfList(items: List<T>): T? = items.maxOrNull()

/**
 * Multiple bounds need a `where` clause. This is how you require a type to satisfy several
 * interfaces without inventing a marker supertype.
 */
interface Identifiable {
    val id: String
}

interface Timestamped {
    val updatedAt: Long
}

fun <T> latestById(items: List<T>): Map<String, T> where T : Identifiable, T : Timestamped =
    items.groupBy { it.id }.mapValues { (_, group) -> group.maxByOrNull { it.updatedAt }!! }

data class Record(
    override val id: String,
    override val updatedAt: Long,
    val payload: String,
) : Identifiable, Timestamped

// ---------------------------------------------------------------------------------------------
// 5. Self-referential generics — the fluent-API bound.
// ---------------------------------------------------------------------------------------------

/**
 * `T : Builder<T>` (F-bounded polymorphism) lets a base class return the *subclass* type from its
 * own methods, so a fluent chain does not degrade to the base type after the first inherited call.
 *
 * Worth knowing, and worth avoiding when a simpler design exists — the type signatures get heavy
 * fast, and in Kotlin an extension function on the subtype usually solves the same problem.
 */
abstract class QueryBuilder<T : QueryBuilder<T>> {
    protected val conditions = mutableListOf<String>()

    @Suppress("UNCHECKED_CAST")
    protected fun self(): T = this as T

    fun where(condition: String): T {
        conditions += condition
        return self()
    }

    abstract fun build(): String
}

class SelectBuilder(private val table: String) : QueryBuilder<SelectBuilder>() {
    private val columns = mutableListOf<String>()

    /** Returns [SelectBuilder], so `select(...).where(...).select(...)` still compiles. */
    fun select(vararg cols: String): SelectBuilder {
        columns += cols
        return this
    }

    override fun build(): String {
        val cols = if (columns.isEmpty()) "*" else columns.joinToString(", ")
        val where = if (conditions.isEmpty()) "" else " WHERE ${conditions.joinToString(" AND ")}"
        return "SELECT $cols FROM $table$where"
    }
}

// ---------------------------------------------------------------------------------------------
// 6. Phantom-ish typing: encoding state in the type.
// ---------------------------------------------------------------------------------------------

/**
 * A type parameter used only to mark state — no value of it is ever stored. It makes a misuse a
 * *compile* error rather than a runtime check.
 *
 * Here `send` exists only on `Email<Validated>`, so an unvalidated email cannot be sent. There is no
 * runtime cost and no `if (validated) throw …` to forget.
 */
sealed class ValidationState {
    object Unvalidated : ValidationState()
    object Validated : ValidationState()
}

class Email<S : ValidationState> private constructor(val address: String) {
    companion object {
        fun of(address: String): Email<ValidationState.Unvalidated> = Email(address)
    }

    fun validate(): Email<ValidationState.Validated>? =
        if ("@" in address) Email(address) else null
}

/** Only callable with a validated email — enforced by the compiler. */
fun send(email: Email<ValidationState.Validated>): String = "sent to ${email.address}"

/**
 * ## Erasure, and what it costs you
 *
 * Type arguments do not exist at runtime. Consequences you will hit:
 * - `is List<String>` is not expressible (`is List<*>` is);
 * - you cannot overload on `List<String>` vs `List<Int>` — same JVM signature;
 * - `T()` is impossible without passing a factory or using `reified` (see `kotlinidioms/inlinereified`).
 *
 * ## Star projection
 *
 * `Box<*>` means "a Box of some unknown type": you can read values as the upper bound (`Any?` here)
 * and you cannot write anything at all, because the compiler cannot verify the type. Use it when the
 * type argument genuinely does not matter — logging, counting, equality.
 */
fun countItems(boxes: List<Box<*>>): Int = boxes.size
