package com.example.kotlindp.patterns.behavioral.memento

/**
 * # Memento
 *
 * Capture an object's internal state so it can be restored later, **without exposing that state**.
 *
 * The encapsulation clause is the whole point and the part usually botched. If the snapshot is just
 * the object's public fields, you have not applied Memento — you have leaked the internals and
 * anyone can now construct an invalid state.
 *
 * Kotlin makes this cheap: a `data class` snapshot is immutable by construction, `copy()` produces
 * it, and `private` visibility keeps the caretaker from looking inside.
 */

// ---------------------------------------------------------------------------------------------
// Originator + memento.
// ---------------------------------------------------------------------------------------------

/**
 * The editor's state includes [cursor] and [selection], which callers have no business setting
 * directly — they are maintained by the editing operations. The memento can round-trip them; the
 * public API cannot set them.
 */
class Editor {

    /**
     * The memento. `private constructor` means only [Editor] can create one, and the fields are
     * `internal`-invisible to callers outside this file. A caretaker can hold it and hand it back,
     * and can do nothing else with it — which is exactly the contract Memento specifies.
     */
    class Snapshot private constructor(
        internal val text: String,
        internal val cursor: Int,
        internal val selection: IntRange?,
        val label: String,
    ) {
        companion object {
            internal fun of(text: String, cursor: Int, selection: IntRange?, label: String) =
                Snapshot(text, cursor, selection, label)
        }
    }

    var text: String = ""
        private set

    var cursor: Int = 0
        private set

    var selection: IntRange? = null
        private set

    fun type(input: String) {
        text = text.substring(0, cursor) + input + text.substring(cursor)
        cursor += input.length
        selection = null
    }

    fun select(range: IntRange) {
        require(range.first >= 0 && range.last < text.length) { "selection out of bounds" }
        selection = range
    }

    fun deleteSelection() {
        val range = selection ?: return
        text = text.removeRange(range)
        cursor = range.first
        selection = null
    }

    /** Creating the memento — the originator decides what goes in it. */
    fun save(label: String): Snapshot = Snapshot.of(text, cursor, selection, label)

    /** Restoring — only the originator can read the memento's fields. */
    fun restore(snapshot: Snapshot) {
        text = snapshot.text
        cursor = snapshot.cursor
        selection = snapshot.selection
    }
}

/**
 * The caretaker. It stores and orders mementos and never inspects them — note that it can only see
 * `label`, which exists precisely so a UI can render the undo stack without reading state.
 */
class History(private val limit: Int = 50) {
    private val snapshots = mutableListOf<Editor.Snapshot>()

    fun push(snapshot: Editor.Snapshot) {
        snapshots += snapshot
        if (snapshots.size > limit) snapshots.removeAt(0)
    }

    fun pop(): Editor.Snapshot? = snapshots.removeLastOrNull()

    fun labels(): List<String> = snapshots.map { it.label }

    fun size(): Int = snapshots.size
}

// ---------------------------------------------------------------------------------------------
// The Kotlin shortcut: immutable state + copy().
// ---------------------------------------------------------------------------------------------

/**
 * When state is already an immutable data class, *every value is its own memento* — no snapshot
 * type, no copying logic, and no risk of a shallow copy sharing mutable structure.
 *
 * This is the version to reach for in new code. The class above matters when you are wrapping a
 * genuinely mutable object you do not control.
 */
data class FormState(
    val fields: Map<String, String> = emptyMap(),
    val step: Int = 0,
) {
    fun with(field: String, value: String): FormState = copy(fields = fields + (field to value))
    fun next(): FormState = copy(step = step + 1)
}

class UndoableForm(initial: FormState = FormState()) {
    private val past = mutableListOf<FormState>()
    private val future = mutableListOf<FormState>()

    var state: FormState = initial
        private set

    fun apply(transform: (FormState) -> FormState) {
        past += state
        state = transform(state)
        // A new action invalidates the redo branch — same rule as in Command.
        future.clear()
    }

    fun undo(): Boolean {
        val previous = past.removeLastOrNull() ?: return false
        future += state
        state = previous
        return true
    }

    fun redo(): Boolean {
        val next = future.removeLastOrNull() ?: return false
        past += state
        state = next
        return true
    }
}

/**
 * ## Memento vs Command
 *
 * Both give you undo, by opposite means:
 * - **Memento** stores *state* — restore is trivial, memory cost is proportional to state size.
 * - **Command** stores *intent* — memory is tiny, but every command must know how to reverse itself,
 *   which is often the hard part.
 *
 * Real systems mix them: commands for the log, periodic mementos as checkpoints so replay does not
 * start from zero. That is exactly how database recovery and event-sourced systems work.
 *
 * ## Cost
 *
 * Full snapshots of large state are expensive. Mitigations: bound the history (as above), snapshot
 * every N operations, or use persistent/structurally-shared data structures so unchanged parts are
 * not copied.
 */
