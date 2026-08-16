package com.example.kotlindp.patterns

import com.example.kotlindp.patterns.structural.adapter.CachingRepositoryAdapter
import com.example.kotlindp.patterns.structural.adapter.InMemoryRepository
import com.example.kotlindp.patterns.structural.adapter.LegacyPaymentGatewayAdapter
import com.example.kotlindp.patterns.structural.adapter.LegacyPaymentSdk
import com.example.kotlindp.patterns.structural.adapter.Money
import com.example.kotlindp.patterns.structural.adapter.chargeDollars
import com.example.kotlindp.patterns.structural.adapter.toMoney
import com.example.kotlindp.patterns.structural.bridge.CsvRenderer
import com.example.kotlindp.patterns.structural.bridge.InventoryReport
import com.example.kotlindp.patterns.structural.bridge.JsonRenderer
import com.example.kotlindp.patterns.structural.bridge.MarkdownRenderer
import com.example.kotlindp.patterns.structural.bridge.SalesReport
import com.example.kotlindp.patterns.structural.bridge.TopSalesReport
import com.example.kotlindp.patterns.structural.composite.FsDirectory
import com.example.kotlindp.patterns.structural.composite.FsFile
import com.example.kotlindp.patterns.structural.composite.plus
import com.example.kotlindp.patterns.structural.composite.render
import com.example.kotlindp.patterns.structural.composite.rule
import com.example.kotlindp.patterns.structural.composite.rules
import com.example.kotlindp.patterns.structural.composite.walk
import com.example.kotlindp.patterns.structural.decorator.CachingExecutor
import com.example.kotlindp.patterns.structural.decorator.LoggingExecutor
import com.example.kotlindp.patterns.structural.decorator.Query
import com.example.kotlindp.patterns.structural.decorator.RealQueryExecutor
import com.example.kotlindp.patterns.structural.decorator.RetryingExecutor
import com.example.kotlindp.patterns.structural.decorator.TimingExecutor
import com.example.kotlindp.patterns.structural.decorator.decorateWith
import com.example.kotlindp.patterns.structural.facade.AuditLog
import com.example.kotlindp.patterns.structural.facade.CheckoutFacade
import com.example.kotlindp.patterns.structural.facade.CheckoutResult
import com.example.kotlindp.patterns.structural.facade.InventoryService
import com.example.kotlindp.patterns.structural.facade.Order
import com.example.kotlindp.patterns.structural.facade.PaymentService
import com.example.kotlindp.patterns.structural.facade.PricingService
import com.example.kotlindp.patterns.structural.facade.ShippingService
import com.example.kotlindp.patterns.structural.flyweight.Amount
import com.example.kotlindp.patterns.structural.flyweight.CurrencyMeta
import com.example.kotlindp.patterns.structural.flyweight.InternPool
import com.example.kotlindp.patterns.structural.proxy.CachingImageProxy
import com.example.kotlindp.patterns.structural.proxy.DiskImageStore
import com.example.kotlindp.patterns.structural.proxy.ImageStore
import com.example.kotlindp.patterns.structural.proxy.LazyImageStore
import com.example.kotlindp.patterns.structural.proxy.Principal
import com.example.kotlindp.patterns.structural.proxy.ProtectionProxy
import com.example.kotlindp.patterns.structural.proxy.RemoteImageStore
import com.example.kotlindp.patterns.structural.proxy.recordingProxy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class StructuralPatternsTest {

    @Nested
    inner class AdapterTest {

        private val adapter = LegacyPaymentGatewayAdapter(LegacyPaymentSdk())

        @Test
        fun `adapter translates cents to dollars and status codes to a typed result`() {
            val result = adapter.charge("cust-1", Money(2_500, "USD"))

            assertTrue(result.success)
            assertEquals("legacy-tx-001", result.reference)
        }

        @Test
        fun `declines and errors map to distinct typed failures`() {
            assertEquals("declined", adapter.charge("blocked-1", Money(100, "USD")).error)
            assertTrue(adapter.charge("cust-1", Money(0, "USD")).error!!.startsWith("sdk error"))
        }

        @Test
        fun `unsupported currency is rejected at the boundary`() {
            assertThrows(IllegalArgumentException::class.java) {
                adapter.charge("cust-1", Money(100, "XYZ"))
            }
        }

        @Test
        fun `delegating adapter forwards untouched members and overrides one`() {
            val backing = InMemoryRepository(mapOf("1" to "one", "2" to "two"))
            val caching = CachingRepositoryAdapter(backing)

            assertEquals("one", caching.findById("1"))
            assertEquals("one", caching.findById("1"))
            assertEquals(1, caching.cacheSize())

            // forwarded by `by delegate`, not written by hand
            assertEquals(2, caching.count())
            assertEquals(listOf("one", "two"), caching.findAll())
        }

        @Test
        fun `extension function adapts without a wrapper object`() {
            assertTrue(LegacyPaymentSdk().chargeDollars("cust", 10.0))
            assertFalse(LegacyPaymentSdk().chargeDollars("blocked", 10.0))
        }

        @Test
        fun `map adapts to a domain type`() {
            val money = mapOf("amount_cents" to 500, "currency" to "EUR").toMoney()
            assertEquals(Money(500, "EUR"), money)

            assertThrows(IllegalArgumentException::class.java) { emptyMap<String, Any?>().toMoney() }
        }
    }

    @Nested
    inner class DecoratorTest {

        @Test
        fun `decorator adds behaviour and delegates the rest`() {
            val log = mutableListOf<String>()
            val executor = LoggingExecutor(RealQueryExecutor(), log)

            executor.execute(Query("SELECT 1"))

            assertEquals(listOf("→ SELECT 1", "← 1 row(s)"), executor.log())
            assertTrue(executor.healthy()) // never overridden — supplied by `by inner`
        }

        @Test
        fun `logging decorator records failures without swallowing them`() {
            val executor = LoggingExecutor(RealQueryExecutor())

            assertThrows(IllegalStateException::class.java) { executor.execute(Query("BOOM")) }
            assertTrue(executor.log().any { it.startsWith("✗") })
        }

        @Test
        fun `caching decorator skips the delegate on a repeat query`() {
            val real = RealQueryExecutor()
            val caching = CachingExecutor(real)

            caching.execute(Query("SELECT 1"))
            caching.execute(Query("SELECT 1"))
            caching.execute(Query("SELECT 2"))

            assertEquals(2, real.invocations)
            assertEquals(2, caching.cachedQueries())
        }

        @Test
        fun `retry decorator re-invokes the delegate until it succeeds`() {
            var attempts = 0
            val flaky = object : com.example.kotlindp.patterns.structural.decorator.QueryExecutor {
                override fun execute(query: Query): List<Map<String, Any?>> {
                    attempts++
                    if (attempts < 3) throw IllegalStateException("transient")
                    return listOf(mapOf("ok" to true))
                }

                override fun healthy() = true
            }

            assertEquals(1, RetryingExecutor(flaky, attempts = 3).execute(Query("x")).size)
            assertEquals(3, attempts)
        }

        @Test
        fun `retry decorator gives up after the configured attempts`() {
            val executor = RetryingExecutor(RealQueryExecutor(), attempts = 2)
            val error = assertThrows(IllegalStateException::class.java) { executor.execute(Query("BOOM")) }
            assertTrue(error.message!!.contains("failed after 2 attempts"))
        }

        @Test
        fun `decorator order changes the semantics`() {
            val real = RealQueryExecutor()

            // caching innermost: the timer sees every call, including cache hits
            val timingOutside = TimingExecutor(CachingExecutor(real))
            timingOutside.execute(Query("SELECT 1"))
            timingOutside.execute(Query("SELECT 1"))
            assertEquals(1, real.invocations)

            // caching outermost: the delegate below it is never reached on a hit
            val real2 = RealQueryExecutor()
            val cachingOutside = CachingExecutor(TimingExecutor(real2))
            cachingOutside.execute(Query("SELECT 1"))
            cachingOutside.execute(Query("SELECT 1"))
            assertEquals(1, real2.invocations)
        }

        @Test
        fun `fold-based composition applies wrappers in order`() {
            val real = RealQueryExecutor()
            val stacked = real.decorateWith(
                { CachingExecutor(it) },
                { TimingExecutor(it) },
            )

            stacked.execute(Query("SELECT 1"))
            stacked.execute(Query("SELECT 1"))

            assertEquals(1, real.invocations)
        }
    }

    @Nested
    inner class FacadeTest {

        private fun facade(payments: PaymentService = PaymentService()) = Triple(
            InventoryService(),
            AuditLog(),
            payments,
        ).let { (inventory, audit, pay) ->
            Pair(
                CheckoutFacade(inventory, PricingService(), pay, ShippingService(), audit),
                Triple(inventory, audit, pay),
            )
        }

        @Test
        fun `happy path returns a typed success`() {
            val (checkout, deps) = facade()
            val result = checkout.checkout(Order("o1", "WIDGET", 2, "cust-1"))

            assertTrue(result is CheckoutResult.Success)
            result as CheckoutResult.Success
            assertEquals(3_000, result.totalCents)
            assertEquals("ship_o1", result.shipmentRef)
            assertTrue(deps.second.entries.contains("checkout.completed o1"))
        }

        @Test
        fun `out of stock short-circuits before payment`() {
            val (checkout, deps) = facade()
            val result = checkout.checkout(Order("o2", "GADGET", 1, "cust-1"))

            assertTrue(result is CheckoutResult.Failure)
            assertTrue((result as CheckoutResult.Failure).reason.contains("out of stock"))
            assertTrue(deps.third.charges.isEmpty())
        }

        @Test
        fun `payment failure releases the reserved stock`() {
            val payments = PaymentService().apply { declineCustomer = "cust-broke" }
            val (checkout, deps) = facade(payments)
            val inventory = deps.first

            val before = inventory.available("WIDGET")
            val result = checkout.checkout(Order("o3", "WIDGET", 2, "cust-broke"))

            assertTrue(result is CheckoutResult.Failure)
            assertEquals(before, inventory.available("WIDGET")) // compensating action ran
            assertTrue(deps.second.entries.contains("checkout.payment_failed o3"))
        }
    }

    @Nested
    inner class CompositeTest {

        private val tree = FsDirectory(
            "root",
            listOf(
                FsFile("a.txt", 100),
                FsDirectory("sub", listOf(FsFile("b.txt", 200), FsFile("c.txt", 300))),
            ),
        )

        @Test
        fun `branch aggregates its leaves recursively`() {
            assertEquals(600, tree.sizeBytes())
            assertEquals(100, FsFile("a.txt", 100).sizeBytes())
        }

        @Test
        fun `lazy walk visits every node and can stop early`() {
            assertEquals(5, tree.walk().count())
            assertEquals("b.txt", tree.walk().first { it.name == "b.txt" }.name)
        }

        @Test
        fun `plus operator builds the tree without mutation`() {
            val extended = tree + FsFile("d.txt", 50)

            assertEquals(650, extended.sizeBytes())
            assertEquals(600, tree.sizeBytes())
        }

        @Test
        fun `render walks leaf and branch uniformly`() {
            val rendered = tree.render()
            assertTrue(rendered.startsWith("root/"))
            assertTrue(rendered.contains("  a.txt (100b)"))
            assertTrue(rendered.contains("    b.txt (200b)"))
        }

        @Test
        fun `composed validation rules accumulate violations`() {
            data class Signup(val email: String, val age: Int)

            val validator = rules<Signup>(
                rule("email", "must contain @") { "@" in it.email },
                rule("age", "must be 18+") { it.age >= 18 },
            )

            assertTrue(validator.validate(Signup("a@b.com", 30)).isEmpty())

            val violations = validator.validate(Signup("nope", 12))
            assertEquals(2, violations.size)
            assertEquals(setOf("email", "age"), violations.map { it.field }.toSet())
        }

        @Test
        fun `a single rule and a group of rules are the same type`() {
            val single = rule<String>("v", "non-empty") { it.isNotEmpty() }
            val group = rules(single, rule<String>("v", "short") { it.length < 5 })

            assertEquals(0, single.validate("ok").size)
            assertEquals(1, group.validate("far-too-long").size)
        }
    }

    @Nested
    inner class ProxyTest {

        @Test
        fun `virtual proxy defers construction until a call needs it`() {
            var built = 0
            val proxy = LazyImageStore { built++; DiskImageStore() }

            assertEquals(1024, proxy.sizeOf("x")) // answered from metadata
            assertEquals(0, built)
            assertFalse(proxy.initialised)

            proxy.bytes("x")
            assertEquals(1, built)
            assertTrue(proxy.initialised)
        }

        @Test
        fun `protection proxy fails closed`() {
            val real = DiskImageStore()
            val reader = ProtectionProxy(real, Principal("alice", setOf("reader")))
            val stranger = ProtectionProxy(real, Principal("mallory", emptySet()))

            assertEquals(1024, reader.bytes("x").size)
            assertThrows(SecurityException::class.java) { stranger.bytes("x") }
            assertEquals(1024, stranger.sizeOf("x")) // unprotected member still forwards
        }

        @Test
        fun `caching proxy calls the subject once per key`() {
            val real = DiskImageStore()
            val proxy = CachingImageProxy(real)

            proxy.bytes("a")
            proxy.bytes("a")
            proxy.bytes("b")

            assertEquals(2, real.loads)
            assertEquals(2, proxy.cacheSize())
        }

        @Test
        fun `remote proxy translates transport failures`() {
            val store = RemoteImageStore { throw java.io.IOException("connection reset") }
            val error = assertThrows(IllegalStateException::class.java) { store.bytes("x") }

            assertTrue(error.message!!.contains("remote image fetch failed"))
            assertTrue(error.cause is java.io.IOException)
        }

        @Test
        fun `dynamic proxy records every call and unwraps real exceptions`() {
            val log = mutableListOf<String>()
            // The type argument must name an *interface* — a JDK dynamic proxy cannot proxy a class.
            val proxied: ImageStore = recordingProxy<ImageStore>(DiskImageStore(), log)

            proxied.sizeOf("a")
            proxied.bytes("a")

            assertEquals(listOf("sizeOf", "bytes"), log)
        }

        @Test
        fun `dynamic proxy over a concrete class is rejected by the JDK`() {
            assertThrows(IllegalArgumentException::class.java) {
                recordingProxy(DiskImageStore(), mutableListOf())
            }
        }
    }

    @Nested
    inner class BridgeTest {

        private val sales = listOf("widget" to 1_500L, "gadget" to 4_200L)

        @Test
        fun `one abstraction works with every renderer`() {
            assertTrue(SalesReport(CsvRenderer(), sales).export().startsWith("product,revenue"))
            assertTrue(SalesReport(MarkdownRenderer(), sales).export().startsWith("# Sales"))
            assertTrue(SalesReport(JsonRenderer(), sales).export().startsWith("""{"title":"Sales""""))
        }

        @Test
        fun `one renderer works with every abstraction`() {
            val csv = CsvRenderer()
            assertTrue(SalesReport(csv, sales).export().contains("widget,1500"))
            assertTrue(InventoryReport(csv, mapOf("SKU1" to 7)).export().contains("SKU1,7"))
        }

        @Test
        fun `a refined abstraction needs no renderer changes`() {
            val top = TopSalesReport(CsvRenderer(), sales, limit = 1).export()

            assertTrue(top.contains("gadget,4200"))
            assertFalse(top.contains("widget"))
        }

        @Test
        fun `content type travels with the renderer, not the report`() {
            assertEquals("text/csv", SalesReport(CsvRenderer(), sales).contentType())
            assertEquals("application/json", InventoryReport(JsonRenderer(), emptyMap()).contentType())
        }

        @Test
        fun `renderer configuration is independent of the abstraction`() {
            assertTrue(SalesReport(CsvRenderer(separator = ';'), sales).export().contains("widget;1500"))
        }
    }

    @Nested
    inner class FlyweightTest {

        @Test
        fun `factory returns the same shared instance per key`() {
            CurrencyMeta.clearPool()

            assertSame(CurrencyMeta.of("USD"), CurrencyMeta.of("USD"))
            assertEquals(1, CurrencyMeta.poolSize())

            CurrencyMeta.of("EUR")
            assertEquals(2, CurrencyMeta.poolSize())
        }

        @Test
        fun `extrinsic state is passed in, not stored`() {
            val usd = CurrencyMeta.of("USD")

            assertEquals("$12.34", usd.format(1_234))
            assertEquals("$0.05", usd.format(5))
        }

        @Test
        fun `intrinsic state differs per currency including minor units`() {
            assertEquals("¥1234", CurrencyMeta.of("JPY").format(1_234))
            assertEquals("€12.34", CurrencyMeta.of("EUR").format(1_234))
        }

        @Test
        fun `lightweight objects reference shared metadata`() {
            val a = Amount(1_000, "USD")
            val b = Amount(9_999, "USD")

            assertSame(a.meta, b.meta)
            assertEquals("$10.00", a.formatted())
        }

        @Test
        fun `intern pool collapses equal values and stays bounded`() {
            val pool = InternPool<String>(maxSize = 2)

            val first = pool.intern(StringBuilder("US").toString())
            val second = pool.intern(StringBuilder("US").toString())
            assertSame(first, second)

            pool.intern("CA")
            pool.intern("GB") // over the cap — returned as-is, pool does not grow
            assertEquals(2, pool.size())
        }
    }
}
