package com.example.kotlindp.patterns.kotlinidioms.inlinereified

/**
 * # inline, reified, value classes
 *
 * Three features that all trade compile-time work for runtime cost, and are frequently misapplied.
 *
 * - `inline` — copy the function body (and its lambdas) into the call site.
 * - `reified` — because the body is copied, the type argument is known at the call site, so it
 *   survives erasure.
 * - `@JvmInline value class` — a wrapper that mostly disappears at runtime.
 */

// ---------------------------------------------------------------------------------------------
// 1. inline — what it actually buys you.
// ---------------------------------------------------------------------------------------------

/**
 * A non-inline function taking a lambda allocates a `Function` object per call and dispatches
 * virtually through it. `inline` removes both.
 *
 * But the allocation saving is the *least* important benefit — the JIT often handles that anyway.
 * The two things `inline` gives you that nothing else can:
 *
 * 1. **Non-local `return`** — see [firstNegative] below.
 * 2. **`reified` type parameters** — see section 2.
 *
 * Guidance: inline functions that *take lambdas*. Inlining a large function that takes none just
 * bloats bytecode and can make things slower by hurting the instruction cache.
 */
inline fun <T> Iterable<T>.forEachIndexedFast(action: (Int, T) -> Unit) {
    var index = 0
    for (item in this) action(index++, item)
}

/**
 * Non-local return: `return` inside the lambda returns from **[firstNegative]**, not from the
 * lambda. Only possible because the lambda body is inlined.
 *
 * This is why `forEach { return … }` works and `list.map { return … }` inside a non-inline
 * higher-order function does not.
 */
fun firstNegative(numbers: List<Int>): Int? {
    numbers.forEach { if (it < 0) return it }
    return null
}

/**
 * `crossinline` — the lambda is still inlined, but non-local return is forbidden.
 *
 * Needed when the lambda is executed from another context (here, inside another lambda) where
 * returning from the enclosing function would be meaningless or unsafe.
 */
inline fun repeatSafely(times: Int, crossinline action: (Int) -> Unit) {
    val runnable = Runnable { for (i in 0 until times) action(i) }
    runnable.run()
}

/**
 * `noinline` — opt a specific lambda parameter *out* of inlining, so it can be stored in a variable
 * or passed on as a value. An inlined lambda is not an object, so it cannot be stored.
 */
inline fun <T> withFallback(primary: () -> T, noinline fallback: () -> T): T {
    val stored: () -> T = fallback // legal only because of `noinline`
    return runCatching(primary).getOrElse { stored() }
}

// ---------------------------------------------------------------------------------------------
// 2. reified — recovering erased types.
// ---------------------------------------------------------------------------------------------

/**
 * Generics are erased on the JVM: a normal function cannot ask what `T` is. Because an `inline`
 * function's body is copied to the call site — where the type argument *is* known — `reified` lets
 * it use `T` as a real type.
 *
 * Without `reified` this would need a `Class<T>` parameter, which is why so many Java APIs look like
 * `readValue(json, Foo.class)`.
 */
inline fun <reified T> List<*>.filterInstances(): List<T> = filterIsInstance<T>()

inline fun <reified T : Any> Any?.asOrNull(): T? = this as? T

/** The Jackson-style API shape, without the `Class` argument. */
inline fun <reified T> decode(raw: Map<String, Any?>, decoder: (Map<String, Any?>, Class<T>) -> T): T =
    decoder(raw, T::class.java)

/**
 * A typed key-value store — `reified` makes the type argument both the lookup key and the return
 * type, so no cast is needed at the call site and mismatches surface immediately.
 */
class TypedRegistry {
    private val values = mutableMapOf<Class<*>, Any>()

    inline fun <reified T : Any> put(value: T) = putRaw(T::class.java, value)

    inline fun <reified T : Any> get(): T? = getRaw(T::class.java) as? T

    fun putRaw(type: Class<*>, value: Any) {
        values[type] = value
    }

    fun getRaw(type: Class<*>): Any? = values[type]
}

/**
 * **The limits of `reified`:**
 * - it needs `inline`, so it cannot be used on a virtual/abstract/open member;
 * - `T` must be a concrete type at the call site — you cannot forward a non-reified `T` into it;
 * - it does not defeat erasure at runtime: `reified List<String>` still sees `List<*>` for the
 *   element type, because only the outermost type is materialised.
 */

// ---------------------------------------------------------------------------------------------
// 3. value classes — type safety with no allocation.
// ---------------------------------------------------------------------------------------------

/**
 * `@JvmInline value class` wraps one value and is compiled away: at runtime a [UserId] *is* a
 * `String`. You get compile-time distinctness for free.
 *
 * This kills a whole class of production bug — the transposed-argument bug:
 *
 * ```kotlin
 * fun transfer(from: String, to: String, amount: Long)   // easy to swap `from` and `to`
 * fun transfer(from: AccountId, to: AccountId, …)        // still swappable
 * fun grant(user: UserId, order: OrderId)                // NOT swappable — compile error
 * ```
 *
 * `init` blocks work, so validation happens at construction and the type then carries a guarantee.
 */
@JvmInline
value class UserId(val value: String) {
    init {
        require(value.isNotBlank()) { "UserId must not be blank" }
    }
}

@JvmInline
value class OrderId(val value: String)

@JvmInline
value class Cents(val amount: Long) {
    operator fun plus(other: Cents) = Cents(amount + other.amount)
    operator fun times(quantity: Int) = Cents(amount * quantity)
    fun toDisplay(): String = "%d.%02d".format(amount / 100, amount % 100)
}

/** Impossible to call with the arguments transposed. */
fun placeOrder(user: UserId, order: OrderId, total: Cents): String =
    "${user.value} placed ${order.value} for ${total.toDisplay()}"

/**
 * **When a value class does get boxed** (and the allocation comes back):
 * - used as a generic type argument — `List<UserId>` boxes every element;
 * - used as a nullable — `UserId?` must box, since a primitive slot has no null;
 * - stored in a field typed as a supertype or interface.
 *
 * So the guarantee is "no allocation in the common path", not "never allocated". It remains worth
 * using for the type safety alone; treat the performance as a bonus.
 *
 * Also note: two value classes wrapping the same underlying type cannot both be used as parameters
 * in overloads with otherwise identical signatures — after erasure the JVM sees the same method.
 * Kotlin mangles names to cope, which is visible from Java.
 */

/**
 * ## Summary
 *
 * | Feature | Use when | Cost of misuse |
 * |---|---|---|
 * | `inline` | function takes a lambda; you need non-local return or `reified` | bytecode bloat |
 * | `crossinline` | inlined lambda is called from another context | — |
 * | `noinline` | the lambda must be stored or passed on | loses inlining for that lambda |
 * | `reified` | you need the type at runtime | forces `inline`, so no virtual dispatch |
 * | `value class` | a domain wrapper over one primitive | boxes in generics/nullable |
 */
