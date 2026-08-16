package com.example.kotlindp.patterns.behavioral.templatemethod

/**
 * # Template Method
 *
 * Define the skeleton of an algorithm in one place and let subclasses fill in specific steps,
 * without changing the algorithm's structure.
 *
 * This is the most *inheritance*-bound of the GoF patterns, and Kotlin gives you a composition-based
 * alternative that is usually better. Both are here so the trade-off is concrete.
 */

data class ImportReport(val read: Int, val valid: Int, val written: Int, val errors: List<String>)

// ---------------------------------------------------------------------------------------------
// 1. Classic template method — inheritance.
// ---------------------------------------------------------------------------------------------

/**
 * Two details that make this correct rather than merely compiling:
 *
 * - **[import] is `final`.** In Kotlin methods are final unless marked `open`, so the skeleton is
 *   protected by default — a subclass cannot override the algorithm and defeat the whole point.
 *   (In Java you have to remember `final`; here you have to remember `open`, which is the safer
 *   default.)
 * - **Hooks have defaults.** [onRecordRejected] is `open` with an empty body, so subclasses override
 *   it only if they care. Abstract steps are required; hooks are optional. Keeping those two
 *   categories distinct is what stops the base class becoming a burden.
 */
abstract class DataImporter<T> {

    /** The template method. Not `open`, so the sequence is fixed. */
    fun import(source: String): ImportReport {
        val raw = readLines(source)
        val errors = mutableListOf<String>()
        var valid = 0

        val parsed = raw.mapNotNull { line ->
            val record = runCatching { parse(line) }.getOrElse {
                errors += "parse failed: ${it.message}"
                onRecordRejected(line, it)
                null
            }
            record?.takeIf { r ->
                validate(r).also { ok -> if (ok) valid++ else errors += "invalid: $line" }
            }
        }

        val written = write(parsed)
        return ImportReport(raw.size, valid, written, errors)
    }

    // ---- required steps -------------------------------------------------------------------
    protected abstract fun readLines(source: String): List<String>
    protected abstract fun parse(line: String): T
    protected abstract fun write(records: List<T>): Int

    // ---- optional hooks, with sensible defaults --------------------------------------------
    protected open fun validate(record: T): Boolean = true
    protected open fun onRecordRejected(line: String, cause: Throwable) {}
}

data class User(val id: String, val email: String)

class CsvUserImporter(private val content: String) : DataImporter<User>() {
    val rejected = mutableListOf<String>()
    val stored = mutableListOf<User>()

    override fun readLines(source: String): List<String> =
        content.lineSequence().filter { it.isNotBlank() }.toList()

    override fun parse(line: String): User {
        val parts = line.split(",")
        require(parts.size == 2) { "expected 2 columns, got ${parts.size}" }
        return User(parts[0].trim(), parts[1].trim())
    }

    override fun validate(record: User): Boolean = "@" in record.email

    override fun write(records: List<User>): Int {
        stored += records
        return records.size
    }

    override fun onRecordRejected(line: String, cause: Throwable) {
        rejected += line
    }
}

// ---------------------------------------------------------------------------------------------
// 2. The Kotlin alternative — template as a higher-order function.
// ---------------------------------------------------------------------------------------------

/**
 * The same skeleton with steps as parameters. No inheritance, no base class, and the steps can be
 * supplied inline, reused across templates, or tested on their own.
 *
 * Default arguments replace the "optional hook" mechanism exactly — and more cleanly, because the
 * default is visible in the signature rather than in a base class you have to go and read.
 *
 * Prefer this form unless you have several subclasses sharing genuinely substantial state.
 */
fun <T> runImport(
    source: String,
    readLines: (String) -> List<String>,
    parse: (String) -> T,
    write: (List<T>) -> Int,
    validate: (T) -> Boolean = { true },
    onRejected: (String, Throwable) -> Unit = { _, _ -> },
): ImportReport {
    val raw = readLines(source)
    val errors = mutableListOf<String>()
    var valid = 0

    val parsed = raw.mapNotNull { line ->
        val record = runCatching { parse(line) }.getOrElse {
            errors += "parse failed: ${it.message}"
            onRejected(line, it)
            null
        }
        record?.takeIf { r ->
            validate(r).also { ok -> if (ok) valid++ else errors += "invalid: $line" }
        }
    }

    return ImportReport(raw.size, valid, write(parsed), errors)
}

// ---------------------------------------------------------------------------------------------
// 3. The smallest form — a scoped `inline fun` template.
// ---------------------------------------------------------------------------------------------

/**
 * "Do setup, run the caller's block, always do teardown" is a template method too — and it is how
 * `use`, `runCatching`, and `transaction { }` are written.
 *
 * `inline` means the lambda is compiled into the call site: no object allocation, and non-local
 * `return` works inside the block.
 */
inline fun <R> withAudit(name: String, log: MutableList<String>, block: () -> R): R {
    log += "start $name"
    return try {
        block().also { log += "ok $name" }
    } catch (e: Throwable) {
        log += "fail $name: ${e.message}"
        throw e
    } finally {
        log += "end $name"
    }
}

/**
 * ## Choosing
 *
 * | Situation | Form |
 * |---|---|
 * | Steps vary, no shared state | higher-order function (#2) |
 * | Setup/teardown around caller code | `inline fun` with a lambda (#3) |
 * | Several subclasses sharing substantial state and many steps | inheritance (#1) |
 *
 * ## The classic complaint
 *
 * Template Method inverts control: the base class calls you. That is fine until the base class grows
 * a dozen hooks, at which point nobody can predict the execution order and subclasses become
 * coupled to the parent's internals. If a hook needs to know *when* it is called, the abstraction
 * has already failed — switch to composition.
 *
 * ## In the wild
 *
 * Spring is full of it: `JdbcTemplate`, `RestTemplate`, `TransactionTemplate`,
 * `AbstractController`. Note that the `*Template` classes take a *callback* rather than requiring a
 * subclass — Spring itself moved to form #2.
 */
