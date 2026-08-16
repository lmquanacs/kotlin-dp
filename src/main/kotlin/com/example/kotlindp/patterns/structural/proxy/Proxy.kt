package com.example.kotlindp.patterns.structural.proxy

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy as JdkProxy

/**
 * # Proxy
 *
 * Stand in for another object and control access to it. Same interface as the real subject, so
 * callers cannot tell the difference.
 *
 * Proxy and Decorator have identical structure; the difference is *intent*. A decorator adds
 * behaviour the caller wants (logging, retry). A proxy controls access the caller is not supposed
 * to think about (laziness, remoteness, permission, cost).
 *
 * The four classic flavours are all below, plus Kotlin's built-in one.
 */

interface ImageStore {
    fun bytes(id: String): ByteArray
    fun sizeOf(id: String): Int
}

/** The real subject. Deliberately "expensive" so laziness is observable. */
class DiskImageStore : ImageStore {
    var loads: Int = 0
        private set

    override fun bytes(id: String): ByteArray {
        loads++
        return ByteArray(1024) { (id.hashCode() + it).toByte() }
    }

    override fun sizeOf(id: String): Int = 1024
}

// ---------------------------------------------------------------------------------------------
// 1. Virtual proxy — defer expensive creation until first real use.
// ---------------------------------------------------------------------------------------------

/**
 * Kotlin's `by lazy` *is* a virtual proxy for a property, and it is thread-safe by default
 * ([LazyThreadSafetyMode.SYNCHRONIZED]): the initialiser runs at most once even under a race.
 *
 * `sizeOf` deliberately answers without touching [real], which is the point of a virtual proxy —
 * cheap questions never trigger the expensive construction.
 */
class LazyImageStore(private val factory: () -> ImageStore) : ImageStore {

    private val real: ImageStore by lazy { factory() }

    /** True until the first call that actually needs the subject. */
    var initialised: Boolean = false
        private set

    override fun bytes(id: String): ByteArray {
        initialised = true
        return real.bytes(id)
    }

    /** Answered from metadata; the real store is never constructed. */
    override fun sizeOf(id: String): Int = 1024
}

// ---------------------------------------------------------------------------------------------
// 2. Protection proxy — enforce access control at the boundary.
// ---------------------------------------------------------------------------------------------

data class Principal(val user: String, val roles: Set<String>)

class ProtectionProxy(
    private val real: ImageStore,
    private val principal: Principal,
) : ImageStore by real {

    override fun bytes(id: String): ByteArray {
        // Fail closed: deny unless explicitly permitted.
        if ("reader" !in principal.roles) {
            throw SecurityException("${principal.user} may not read images")
        }
        return real.bytes(id)
    }
}

// ---------------------------------------------------------------------------------------------
// 3. Caching / smart proxy — add bookkeeping around access.
// ---------------------------------------------------------------------------------------------

class CachingImageProxy(private val real: ImageStore) : ImageStore by real {
    private val cache = mutableMapOf<String, ByteArray>()

    override fun bytes(id: String): ByteArray = cache.getOrPut(id) { real.bytes(id) }

    fun cacheSize(): Int = cache.size
}

// ---------------------------------------------------------------------------------------------
// 4. Remote proxy — a local object standing in for something across a network boundary.
// ---------------------------------------------------------------------------------------------

/**
 * The proxy makes a remote call look like a method call. That is its purpose and also its danger:
 * callers forget the call can be slow or fail, which is why timeouts and error translation belong
 * here rather than in the caller.
 */
class RemoteImageStore(private val transport: (String) -> ByteArray) : ImageStore {
    override fun bytes(id: String): ByteArray =
        try {
            transport(id)
        } catch (e: Exception) {
            throw IllegalStateException("remote image fetch failed for $id", e)
        }

    override fun sizeOf(id: String): Int = bytes(id).size
}

// ---------------------------------------------------------------------------------------------
// 5. Dynamic proxy — one handler for every method, generated at runtime.
// ---------------------------------------------------------------------------------------------

/**
 * This is the mechanism behind Spring AOP (`@Transactional`, `@Cacheable`) and behind interface-only
 * clients like Spring Data repositories and Retrofit: no implementation class exists at compile
 * time, so the JDK generates one and routes every call through an [InvocationHandler].
 *
 * `reified` lets the caller write `recordingProxy<ImageStore>(real, log)` with no `Class` argument;
 * the type is available at runtime because the function is `inline`.
 */
inline fun <reified T : Any> recordingProxy(target: T, log: MutableList<String>): T {
    val handler = InvocationHandler { _, method: Method, args: Array<Any?>? ->
        log += method.name
        try {
            method.invoke(target, *(args ?: emptyArray()))
        } catch (e: java.lang.reflect.InvocationTargetException) {
            // Reflection wraps the real exception; unwrap it or callers see the wrong type.
            throw e.targetException
        }
    }
    return JdkProxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java), handler) as T
}

/**
 * ## Notes
 *
 * **JDK dynamic proxies only work on interfaces.** For classes you need CGLIB — which subclasses,
 * and therefore needs the class and its methods to be non-`final`. Kotlin classes are `final` by
 * default, which is exactly why Spring Boot's Kotlin setup includes the `kotlin-allopen` compiler
 * plugin (`kotlin("plugin.spring")` in this project's build file). Without it, `@Transactional` on
 * a Kotlin `@Service` silently does nothing.
 *
 * **Self-invocation doesn't go through the proxy.** Calling `this.otherMethod()` inside a proxied
 * bean bypasses the proxy entirely, so `@Transactional`/`@Cacheable` on the inner method is ignored.
 * This is the single most common Spring AOP bug, and it follows directly from how proxies work.
 */
