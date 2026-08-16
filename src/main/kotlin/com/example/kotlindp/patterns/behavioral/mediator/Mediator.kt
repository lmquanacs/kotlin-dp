package com.example.kotlindp.patterns.behavioral.mediator

/**
 * # Mediator
 *
 * Replace direct many-to-many references between components with a hub they all talk to, turning an
 * N² web of dependencies into N.
 *
 * The classic symptom: five components each holding references to the other four, and any change to
 * one rippling through all of them. The mediator holds the *interaction rules*; components only know
 * the mediator.
 */

// ---------------------------------------------------------------------------------------------
// Components — they know the mediator, never each other.
// ---------------------------------------------------------------------------------------------

interface Component {
    val name: String
    fun receive(event: String, payload: String)
}

interface Mediator {
    fun notify(sender: Component, event: String, payload: String = "")
}

/**
 * A form field. Notice it has no reference to the submit button, the summary label, or anything
 * else — it just reports what happened to it.
 */
class TextField(override val name: String, private val mediator: Mediator) : Component {
    var value: String = ""
        private set

    val received = mutableListOf<String>()

    fun type(text: String) {
        value = text
        mediator.notify(this, "changed", text)
    }

    override fun receive(event: String, payload: String) {
        received += "$event:$payload"
        if (event == "clear") value = ""
    }
}

class Button(override val name: String, private val mediator: Mediator) : Component {
    var enabled: Boolean = false
        private set

    override fun receive(event: String, payload: String) {
        when (event) {
            "enable" -> enabled = true
            "disable" -> enabled = false
        }
    }

    fun click() {
        if (enabled) mediator.notify(this, "submit")
    }
}

class StatusLabel(override val name: String) : Component {
    var text: String = ""
        private set

    override fun receive(event: String, payload: String) {
        text = payload
    }
}

// ---------------------------------------------------------------------------------------------
// The mediator — the only place the interaction rules live.
// ---------------------------------------------------------------------------------------------

/**
 * All the coupling that would otherwise be spread across the components is concentrated here, where
 * you can read it in one sitting. That concentration is both the benefit and the risk.
 */
class FormMediator : Mediator {
    lateinit var email: TextField
    lateinit var password: TextField
    lateinit var submit: Button
    lateinit var status: StatusLabel

    var submitted = false
        private set

    override fun notify(sender: Component, event: String, payload: String) {
        when {
            event == "changed" && (sender === email || sender === password) -> {
                val valid = "@" in email.value && password.value.length >= 8
                submit.receive(if (valid) "enable" else "disable", "")
                status.receive("update", if (valid) "ready" else "incomplete")
            }

            event == "submit" -> {
                submitted = true
                status.receive("update", "submitted ${email.value}")
                email.receive("clear", "")
                password.receive("clear", "")
                submit.receive("disable", "")
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The Kotlin-flavoured variant: a mediator built from registered handlers.
// ---------------------------------------------------------------------------------------------

/**
 * Instead of one growing `when`, register a handler per event type. This keeps the mediator open to
 * extension and stops it turning into a thousand-line god object — the pattern's main failure mode.
 *
 * Handlers are keyed by [Class] here for simplicity; a sealed event hierarchy plus `when` is the
 * alternative when the event set is closed.
 */
class TypedMediator {
    private val handlers = mutableMapOf<Class<*>, MutableList<(Any) -> Unit>>()

    inline fun <reified E : Any> on(noinline handler: (E) -> Unit) {
        @Suppress("UNCHECKED_CAST")
        register(E::class.java, handler as (Any) -> Unit)
    }

    fun register(type: Class<*>, handler: (Any) -> Unit) {
        handlers.getOrPut(type) { mutableListOf() } += handler
    }

    fun publish(event: Any) {
        handlers[event::class.java]?.forEach { it(event) }
    }
}

data class UserRegistered(val email: String)
data class OrderPlaced(val orderId: String, val totalCents: Long)

/**
 * ## Mediator vs Observer
 *
 * They look similar and solve different problems. **Observer** is one-to-many broadcast: the subject
 * does not know or care who listens. **Mediator** is many-to-many coordination: the hub knows every
 * participant and encodes the *rules* between them.
 *
 * If your mediator has no rules — it only forwards — you wanted an event bus (Observer).
 *
 * ## The failure mode
 *
 * The mediator concentrates coupling instead of removing it. A `FormMediator` is fine; a
 * `SystemMediator` with 40 participants is a god object that everything depends on and nothing can
 * be tested without. Keep one mediator per cohesive interaction group, and prefer the
 * handler-registration form so it stays open to extension.
 *
 * ## Where it shows up
 *
 * UI form coordination, workflow/saga orchestration across services, chat rooms, air-traffic-control
 * style resource arbitration, and Spring's `ApplicationEventPublisher` when you add rules on top.
 */
