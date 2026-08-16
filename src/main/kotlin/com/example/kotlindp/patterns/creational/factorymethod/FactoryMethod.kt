package com.example.kotlindp.patterns.creational.factorymethod

/**
 * # Factory Method
 *
 * Defer the choice of *which* concrete type to instantiate to a dedicated method, so callers depend
 * on the interface and never on a constructor.
 *
 * The classic GoF form makes the factory method `abstract` on a creator class and lets subclasses
 * override it. Kotlin usually collapses that into a `companion object` function or a top-level
 * function, because you rarely need a whole creator hierarchy just to pick a subclass.
 */

interface Notification {
    val channel: String
    fun send(to: String, message: String): String
}

internal class EmailNotification : Notification {
    override val channel = "email"
    override fun send(to: String, message: String) = "[email] to=$to body=$message"
}

internal class SmsNotification : Notification {
    override val channel = "sms"
    override fun send(to: String, message: String) = "[sms] to=$to body=$message"
}

internal class PushNotification : Notification {
    override val channel = "push"
    override fun send(to: String, message: String) = "[push] to=$to body=$message"
}

/**
 * The channels the factory knows about. Modelling this as an `enum` instead of a raw `String`
 * makes the `when` below exhaustive — add a constant and the compiler points at every place that
 * must handle it. That compile-time nudge is the whole reason to prefer enums/sealed types at
 * factory boundaries.
 */
enum class Channel { EMAIL, SMS, PUSH }

/**
 * Factory method as a companion function.
 *
 * Note the concrete classes are `internal`: callers outside this module physically cannot write
 * `EmailNotification()`, so the factory is the only door in. Enforcing that with visibility is far
 * more reliable than a comment asking people to use the factory.
 */
object NotificationFactory {
    fun create(channel: Channel): Notification = when (channel) {
        Channel.EMAIL -> EmailNotification()
        Channel.SMS -> SmsNotification()
        Channel.PUSH -> PushNotification()
        // No `else` branch: `when` over an enum used as an expression is checked for exhaustiveness.
    }
}

/**
 * A second, very Kotlin-flavoured spelling: give the interface's companion an `invoke` operator so
 * the factory call *looks* like a constructor.
 *
 * `Transport("http")` reads like instantiation but is really `Transport.Companion.invoke("http")`,
 * which means you can add caching, validation, or return a shared instance later without changing
 * a single call site. This is how `kotlin.collections.List`-style pseudo-constructors work.
 */
interface Transport {
    fun deliver(payload: String): String

    companion object {
        operator fun invoke(scheme: String): Transport = when (scheme.lowercase()) {
            "http", "https" -> HttpTransport(scheme)
            "file" -> FileTransport
            else -> throw IllegalArgumentException("Unsupported transport scheme: $scheme")
        }
    }
}

private class HttpTransport(private val scheme: String) : Transport {
    override fun deliver(payload: String) = "POST $scheme://…  ($payload)"
}

/** Stateless implementation — one shared instance is enough, so it is an `object`. */
private object FileTransport : Transport {
    override fun deliver(payload: String) = "write to disk ($payload)"
}

/**
 * The polymorphic GoF form, for when the *creator* itself varies and carries behaviour that
 * depends on the created product.
 *
 * `parse` is the template: it never knows which [Notification] it is building, only that
 * [defaultNotification] hands it one. Subclasses supply the product.
 */
abstract class NotificationDispatcher {

    /** The factory method. Subclasses decide the concrete product. */
    protected abstract fun defaultNotification(): Notification

    /** Shared behaviour written entirely against the interface. */
    fun dispatch(to: String, message: String): String = defaultNotification().send(to, message)
}

class MarketingDispatcher : NotificationDispatcher() {
    override fun defaultNotification(): Notification = EmailNotification()
}

class AlertDispatcher : NotificationDispatcher() {
    override fun defaultNotification(): Notification = SmsNotification()
}
