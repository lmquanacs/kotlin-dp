package com.example.kotlindp.patterns.structural.bridge

/**
 * # Bridge
 *
 * Separate an abstraction from its implementation so the two can vary independently.
 *
 * The problem it solves is **combinatorial subclassing**. With 3 kinds of report and 3 output
 * formats, inheritance gives you 9 classes (`PdfSalesReport`, `CsvSalesReport`, …) and adding a
 * fourth format means 3 more. Bridge composes instead: 3 + 3 = 6 classes, and a new format is one
 * class.
 *
 * Bridge vs Adapter: an adapter is written *after the fact* to reconcile two APIs you did not
 * design together. A bridge is designed *up front* because you know two dimensions will both vary.
 */

// ---------------------------------------------------------------------------------------------
// Dimension 1 — the implementor: how output is rendered.
// ---------------------------------------------------------------------------------------------

data class Row(val cells: List<String>)

interface Renderer {
    val contentType: String
    fun render(title: String, headers: List<String>, rows: List<Row>): String
}

class CsvRenderer(private val separator: Char = ',') : Renderer {
    override val contentType = "text/csv"
    override fun render(title: String, headers: List<String>, rows: List<Row>): String = buildString {
        appendLine(headers.joinToString(separator.toString()))
        rows.forEach { appendLine(it.cells.joinToString(separator.toString())) }
    }
}

class MarkdownRenderer : Renderer {
    override val contentType = "text/markdown"
    override fun render(title: String, headers: List<String>, rows: List<Row>): String = buildString {
        appendLine("# $title")
        appendLine(headers.joinToString(" | ", prefix = "| ", postfix = " |"))
        appendLine(headers.joinToString(" | ", prefix = "| ", postfix = " |") { "---" })
        rows.forEach { appendLine(it.cells.joinToString(" | ", prefix = "| ", postfix = " |")) }
    }
}

class JsonRenderer : Renderer {
    override val contentType = "application/json"
    override fun render(title: String, headers: List<String>, rows: List<Row>): String {
        val objects = rows.joinToString(",") { row ->
            headers.zip(row.cells).joinToString(",", prefix = "{", postfix = "}") { (h, c) -> "\"$h\":\"$c\"" }
        }
        return """{"title":"$title","data":[$objects]}"""
    }
}

// ---------------------------------------------------------------------------------------------
// Dimension 2 — the abstraction: what the report contains.
// ---------------------------------------------------------------------------------------------

/**
 * The abstraction holds a [Renderer] rather than inheriting from one. That single field *is* the
 * bridge.
 *
 * Subclasses define `title`/`headers`/`rows`; none of them knows or cares about formats.
 */
abstract class Report(protected val renderer: Renderer) {
    protected abstract val title: String
    protected abstract val headers: List<String>
    protected abstract fun rows(): List<Row>

    fun export(): String = renderer.render(title, headers, rows())
    fun contentType(): String = renderer.contentType
}

class SalesReport(
    renderer: Renderer,
    private val sales: List<Pair<String, Long>>,
) : Report(renderer) {
    override val title = "Sales"
    override val headers = listOf("product", "revenue")
    override fun rows(): List<Row> = sales.map { (product, revenue) -> Row(listOf(product, revenue.toString())) }
}

class InventoryReport(
    renderer: Renderer,
    private val stock: Map<String, Int>,
) : Report(renderer) {
    override val title = "Inventory"
    override val headers = listOf("sku", "quantity")
    override fun rows(): List<Row> = stock.map { (sku, qty) -> Row(listOf(sku, qty.toString())) }
}

/**
 * A "refined abstraction": extends the abstraction dimension without touching the implementor
 * dimension. It works with every renderer automatically — that is the payoff.
 */
class TopSalesReport(
    renderer: Renderer,
    private val sales: List<Pair<String, Long>>,
    private val limit: Int,
) : Report(renderer) {
    override val title = "Top $limit Sales"
    override val headers = listOf("product", "revenue")
    override fun rows(): List<Row> = sales.sortedByDescending { it.second }
        .take(limit)
        .map { (product, revenue) -> Row(listOf(product, revenue.toString())) }
}

/**
 * ## Kotlin note
 *
 * When the implementor side is a single operation, the interface can just be a function type — the
 * bridge collapses to a constructor parameter and there is no hierarchy to maintain at all:
 *
 * ```kotlin
 * class Report(private val render: (String, List<String>, List<Row>) -> String)
 * ```
 *
 * That is usually the right call for a two-implementation bridge. Keep the named interface once the
 * implementor grows state or more than one member (here, `contentType` alongside `render`).
 *
 * ## When *not* to use it
 *
 * Bridge is speculative generality if only one dimension actually varies. Two axes that each have
 * one implementation today is a class with a parameter, not a bridge. Wait for the second format.
 */
