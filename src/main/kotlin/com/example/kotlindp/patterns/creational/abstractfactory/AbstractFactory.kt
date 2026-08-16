package com.example.kotlindp.patterns.creational.abstractfactory

/**
 * # Abstract Factory
 *
 * Create *families* of related objects without naming their concrete classes, and guarantee the
 * members of a family are used together.
 *
 * Factory Method picks one product. Abstract Factory picks a matched set. The value is the
 * constraint: it becomes impossible to pair a Postgres connection with a MySQL dialect, because a
 * single factory produces both.
 */

// ---------------------------------------------------------------------------------------------
// Product interfaces — one per role in the family.
// ---------------------------------------------------------------------------------------------

interface Connection {
    fun url(): String
}

interface Dialect {
    /** Vendors disagree on paging syntax; this is exactly the kind of thing that must stay matched. */
    fun paginate(sql: String, limit: Int, offset: Int): String
}

interface MigrationRunner {
    fun lockStatement(): String
}

// ---------------------------------------------------------------------------------------------
// The abstract factory: one method per product role.
// ---------------------------------------------------------------------------------------------

interface PersistenceFactory {
    val vendor: String
    fun connection(host: String, database: String): Connection
    fun dialect(): Dialect
    fun migrationRunner(): MigrationRunner
}

// ---------------------------------------------------------------------------------------------
// Family 1 — PostgreSQL
// ---------------------------------------------------------------------------------------------

private class PostgresConnection(private val host: String, private val db: String) : Connection {
    override fun url() = "jdbc:postgresql://$host:5432/$db"
}

private object PostgresDialect : Dialect {
    override fun paginate(sql: String, limit: Int, offset: Int) = "$sql LIMIT $limit OFFSET $offset"
}

private object PostgresMigrationRunner : MigrationRunner {
    override fun lockStatement() = "SELECT pg_advisory_lock(1)"
}

object PostgresFactory : PersistenceFactory {
    override val vendor = "postgres"
    override fun connection(host: String, database: String): Connection = PostgresConnection(host, database)
    override fun dialect(): Dialect = PostgresDialect
    override fun migrationRunner(): MigrationRunner = PostgresMigrationRunner
}

// ---------------------------------------------------------------------------------------------
// Family 2 — MySQL. Same roles, incompatible details.
// ---------------------------------------------------------------------------------------------

private class MySqlConnection(private val host: String, private val db: String) : Connection {
    override fun url() = "jdbc:mysql://$host:3306/$db"
}

private object MySqlDialect : Dialect {
    // MySQL wants OFFSET first when using the two-argument form — mixing this up with the Postgres
    // spelling is precisely the bug Abstract Factory prevents.
    override fun paginate(sql: String, limit: Int, offset: Int) = "$sql LIMIT $offset, $limit"
}

private object MySqlMigrationRunner : MigrationRunner {
    override fun lockStatement() = "SELECT GET_LOCK('migration', 10)"
}

object MySqlFactory : PersistenceFactory {
    override val vendor = "mysql"
    override fun connection(host: String, database: String): Connection = MySqlConnection(host, database)
    override fun dialect(): Dialect = MySqlDialect
    override fun migrationRunner(): MigrationRunner = MySqlMigrationRunner
}

// ---------------------------------------------------------------------------------------------
// Selecting a family, and consuming one.
// ---------------------------------------------------------------------------------------------

enum class Vendor { POSTGRES, MYSQL }

fun persistenceFactoryFor(vendor: Vendor): PersistenceFactory = when (vendor) {
    Vendor.POSTGRES -> PostgresFactory
    Vendor.MYSQL -> MySqlFactory
}

/**
 * The client is written once, against the abstract roles only. It never learns which vendor it got
 * — swapping databases means changing the factory passed to the constructor and nothing else.
 */
class UserRepository(private val factory: PersistenceFactory, host: String, database: String) {

    private val connection = factory.connection(host, database)

    fun findPage(limit: Int, offset: Int): String {
        val sql = factory.dialect().paginate("SELECT * FROM users", limit, offset)
        return "${connection.url()} :: $sql"
    }
}

/**
 * ## Kotlin note
 *
 * Because each factory is stateless it is an `object`, so the whole family costs zero allocations.
 *
 * When a family has only one or two roles, a data class of function references is lighter than an
 * interface with implementations, and gives the same guarantee that members travel together:
 *
 * ```kotlin
 * data class Persistence(
 *     val connection: (host: String, db: String) -> Connection,
 *     val paginate: (String, Int, Int) -> String,
 * )
 * ```
 *
 * In a Spring application the framework itself is the abstract factory: declare
 * `PersistenceFactory` beans behind `@ConditionalOnProperty("db.vendor")` and inject the interface.
 */
