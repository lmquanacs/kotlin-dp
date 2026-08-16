package com.example.kotlindp.patterns.kotlinidioms.scopefunctions

/**
 * # Scope functions: let, run, with, apply, also
 *
 * Five functions that differ along exactly two axes. Learn the table and the confusion disappears:
 *
 * | Function | Receiver is | Returns | Extension? |
 * |---|---|---|---|
 * | `let`   | `it`   | lambda result | yes |
 * | `run`   | `this` | lambda result | yes |
 * | `with`  | `this` | lambda result | no (takes an argument) |
 * | `apply` | `this` | the receiver   | yes |
 * | `also`  | `it`   | the receiver   | yes |
 *
 * Two questions pick the right one:
 * 1. Do I want the object back, or the block's result? → `apply`/`also` vs `let`/`run`/`with`
 * 2. Do I want `this` or `it`? → configuring the object vs using it as an argument
 */

data class Server(var host: String = "localhost", var port: Int = 8080) {
    val connections = mutableListOf<String>()
    fun connect(client: String) {
        connections += client
    }
}

// ---------------------------------------------------------------------------------------------
// The idiomatic use of each.
// ---------------------------------------------------------------------------------------------

/**
 * **`let`** — its dominant use is null-safe transformation. `?.let { }` runs the block only when
 * non-null, and `it` inside is smart-cast to the non-null type.
 *
 * It also introduces a scoped name for an expression, which keeps a chain readable without a `val`.
 */
fun describeUser(email: String?): String =
    email?.let { "user <${it.lowercase()}>" } ?: "anonymous"

/** `let` in a chain: the intermediate value gets a name and a scope, but no variable leaks out. */
fun normalisedPorts(raw: String): List<Int> =
    raw.split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .let { ports -> ports.filter { it in 1..65535 }.distinct().sorted() }

/**
 * **`apply`** — configure and return the receiver. This is the builder idiom, and it is why every
 * DSL in this repository ends with `.apply(block)`.
 *
 * Use it for *configuration*; if the block computes something instead, you wanted `run`.
 */
fun defaultServer(): Server = Server().apply {
    host = "0.0.0.0"
    port = 9090
}

/**
 * **`also`** — do something *with* the object, then return it unchanged. The `it` receiver signals
 * "this is a side effect, not configuration", which is exactly why it is the right choice for
 * logging and validation in the middle of a chain.
 */
fun trackedServer(log: MutableList<String>): Server = defaultServer()
    .also { log += "created server at ${it.host}:${it.port}" }
    .also { require(it.port in 1..65535) { "invalid port" } }

/**
 * **`run`** — receiver is `this`, returns the block's result. Use it to compute a value *from* an
 * object.
 *
 * The non-extension form `run { }` is also handy for scoping a block of statements to one
 * expression, e.g. inside a `when` branch.
 */
fun connectionString(server: Server): String = server.run { "$host:$port" }

/**
 * **`with`** — same as `run` but takes the receiver as an argument. Reads best when you make several
 * calls on one object and want the object named once, at the top.
 */
fun report(server: Server): String = with(server) {
    buildString {
        appendLine("host: $host")
        appendLine("port: $port")
        appendLine("connections: ${connections.size}")
    }
}

// ---------------------------------------------------------------------------------------------
// The traps.
// ---------------------------------------------------------------------------------------------

/**
 * **Trap 1 — nested scope functions shadow `it`.**
 *
 * ```kotlin
 * outer?.let { o -> inner?.let { i -> … } }   // name them
 * outer?.let { inner?.let { … } }             // which `it`? unreadable, and easy to get wrong
 * ```
 *
 * Rule: name the parameter the moment you nest.
 */
fun combine(first: String?, second: String?): String? =
    first?.let { f -> second?.let { s -> "$f-$s" } }

/**
 * **Trap 2 — `?.let { }` is not an `if`.**
 *
 * `a?.let { … } ?: fallback` runs the fallback when *the block returns null*, not only when `a` is
 * null. If the block can legitimately return null, this silently runs the fallback too.
 * A plain `if (a != null)` is clearer and correct.
 */
fun lookup(map: Map<String, String?>, key: String): String =
    if (map.containsKey(key)) map[key] ?: "(null value)" else "(missing)"

/**
 * **Trap 3 — `apply` on a mutable object hides the mutation.**
 *
 * `apply` returning the receiver makes it easy to write chains that look functional but mutate
 * shared state. Prefer `copy()` on immutable data; keep `apply` for building an object you own.
 *
 * **Trap 4 — chains of four or five scope functions are write-only code.** Two is usually the limit
 * before a named `val` communicates better. Scope functions are a readability tool; past a point
 * they stop paying.
 */

/**
 * ## `takeIf` / `takeUnless`
 *
 * Related and genuinely useful: return the receiver if a predicate holds, else `null` — which turns
 * a condition into something `?:` and `?.let` can chain with.
 */
fun sanitisedName(raw: String): String =
    raw.trim().takeIf { it.isNotEmpty() } ?: "unnamed"

fun evenOrNull(n: Int): Int? = n.takeIf { it % 2 == 0 }

/**
 * ## `use` — the one that matters for correctness
 *
 * Not strictly a scope function, but the same shape: `Closeable.use { }` closes the resource on the
 * way out, including on exception. It is Kotlin's try-with-resources and there is no reason to write
 * the `finally` by hand.
 */
fun readAll(reader: java.io.Reader): String = reader.use { it.readText() }
