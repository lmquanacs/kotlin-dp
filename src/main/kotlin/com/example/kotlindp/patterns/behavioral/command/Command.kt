package com.example.kotlindp.patterns.behavioral.command

/**
 * # Command
 *
 * Turn a request into an object, so it can be stored, queued, logged, parameterised, and undone.
 *
 * The give-away that you need it: you want to do something *with* an action rather than just
 * perform it — retry it, schedule it, record it in an audit log, replay it, or reverse it.
 *
 * A bare lambda is already a command. What a lambda cannot give you is **undo** and
 * **serialisability**, which is why the interface form still earns its keep.
 */

// ---------------------------------------------------------------------------------------------
// Receiver — the thing commands act upon.
// ---------------------------------------------------------------------------------------------

class TextDocument(initial: String = "") {
    var content: String = initial
        private set

    fun insert(at: Int, text: String) {
        content = content.substring(0, at) + text + content.substring(at)
    }

    fun delete(at: Int, length: Int): String {
        val removed = content.substring(at, at + length)
        content = content.removeRange(at, at + length)
        return removed
    }
}

// ---------------------------------------------------------------------------------------------
// The command interface, with undo.
// ---------------------------------------------------------------------------------------------

interface Command {
    val description: String
    fun execute()
    fun undo()
}

/**
 * An undoable command must capture whatever it needs to reverse itself — here the inserted length.
 * That is the discipline the pattern imposes: undo is only possible if `execute` records enough.
 */
class InsertText(
    private val doc: TextDocument,
    private val at: Int,
    private val text: String,
) : Command {
    override val description = "insert '$text' at $at"
    override fun execute() = doc.insert(at, text)
    override fun undo() {
        doc.delete(at, text.length)
    }
}

/**
 * Deletion must remember what it removed — the classic reason undo is harder than it looks.
 * `lateinit` is wrong here (it would throw if `undo` ran first); a nullable with a clear error is
 * honest about the ordering requirement.
 */
class DeleteText(
    private val doc: TextDocument,
    private val at: Int,
    private val length: Int,
) : Command {
    private var removed: String? = null

    override val description = "delete $length chars at $at"

    override fun execute() {
        removed = doc.delete(at, length)
    }

    override fun undo() {
        val text = checkNotNull(removed) { "undo called before execute" }
        doc.insert(at, text)
    }
}

/**
 * A macro command — Composite applied to Command. Note `undo` runs in **reverse order**; forgetting
 * that is the classic macro-undo bug.
 */
class MacroCommand(private val commands: List<Command>) : Command {
    override val description = commands.joinToString("; ") { it.description }
    override fun execute() = commands.forEach { it.execute() }
    override fun undo() = commands.asReversed().forEach { it.undo() }
}

// ---------------------------------------------------------------------------------------------
// The invoker — owns history and undo/redo.
// ---------------------------------------------------------------------------------------------

/**
 * Two stacks is the whole algorithm. The one rule people miss: executing a *new* command must clear
 * the redo stack, or redo would replay commands against a history that no longer exists.
 */
class CommandHistory(private val limit: Int = 100) {
    private val undoStack = ArrayDeque<Command>()
    private val redoStack = ArrayDeque<Command>()

    fun execute(command: Command) {
        command.execute()
        undoStack.add(command)
        if (undoStack.size > limit) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun undo(): Boolean {
        val command = undoStack.removeLastOrNull() ?: return false
        command.undo()
        redoStack.add(command)
        return true
    }

    fun redo(): Boolean {
        val command = redoStack.removeLastOrNull() ?: return false
        command.execute()
        undoStack.add(command)
        return true
    }

    fun log(): List<String> = undoStack.map { it.description }
}

// ---------------------------------------------------------------------------------------------
// The lightweight form: commands as functions.
// ---------------------------------------------------------------------------------------------

/**
 * When you do not need undo, a command is just `() -> Unit`, and a queue of them is a task list.
 *
 * Keeping [failures] rather than letting the first exception escape is the deliberate part: a
 * command queue that stops halfway through leaves the system in an unknown state.
 */
class TaskQueue {
    private val tasks = ArrayDeque<Pair<String, () -> Unit>>()
    val executed = mutableListOf<String>()
    val failures = mutableListOf<Pair<String, Throwable>>()

    fun enqueue(name: String, task: () -> Unit) {
        tasks.add(name to task)
    }

    fun drain() {
        while (tasks.isNotEmpty()) {
            val (name, task) = tasks.removeAt(0)
            runCatching(task)
                .onSuccess { executed += name }
                .onFailure { failures += name to it }
        }
    }
}

/**
 * ## Serialisable commands
 *
 * For a queue that survives a restart, the command must be *data*, not a closure — a lambda cannot
 * be persisted. Model the command as a sealed data class and interpret it on the far side:
 *
 * ```kotlin
 * sealed class DocCommand {
 *     data class Insert(val at: Int, val text: String) : DocCommand()
 *     data class Delete(val at: Int, val length: Int) : DocCommand()
 * }
 * fun DocCommand.applyTo(doc: TextDocument) = when (this) { … }
 * ```
 *
 * This is exactly how event sourcing, CQRS command buses, and database write-ahead logs work: the
 * intent is stored, the effect is derived.
 *
 * ## Where it pays off
 *
 * Undo/redo, job queues with retry, audit logs of intent (not just outcome), transactional outbox,
 * and macro/batch operations.
 *
 * ## Cost
 *
 * A class per action. Do not convert every method call into a command — reach for it when you
 * genuinely need to *keep* the action around.
 */
