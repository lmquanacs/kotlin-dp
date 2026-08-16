package com.example.kotlindp.patterns

import com.example.kotlindp.patterns.kotlinidioms.coroutines.callOrNull
import com.example.kotlindp.patterns.kotlinidioms.coroutines.computeCancellable
import com.example.kotlindp.patterns.kotlinidioms.coroutines.fetchAllTolerantly
import com.example.kotlindp.patterns.kotlinidioms.coroutines.firstOf
import com.example.kotlindp.patterns.kotlinidioms.coroutines.loadProfile
import com.example.kotlindp.patterns.kotlinidioms.coroutines.mapConcurrently
import com.example.kotlindp.patterns.kotlinidioms.coroutines.safely
import com.example.kotlindp.patterns.kotlinidioms.delegation.CountingEventStore
import com.example.kotlindp.patterns.kotlinidioms.delegation.InMemoryEventStore
import com.example.kotlindp.patterns.kotlinidioms.delegation.Order
import com.example.kotlindp.patterns.kotlinidioms.delegation.ServerConfig
import com.example.kotlindp.patterns.kotlinidioms.delegation.Settings
import com.example.kotlindp.patterns.kotlinidioms.delegation.StandardDelegates
import com.example.kotlindp.patterns.kotlinidioms.delegation.ValidatingEventStore
import com.example.kotlindp.patterns.kotlinidioms.dsl.AssertionScope
import com.example.kotlindp.patterns.kotlinidioms.dsl.client
import com.example.kotlindp.patterns.kotlinidioms.dsl.config
import com.example.kotlindp.patterns.kotlinidioms.dsl.html
import com.example.kotlindp.patterns.kotlinidioms.dsl.verify
import com.example.kotlindp.patterns.kotlinidioms.extensions.Circle
import com.example.kotlindp.patterns.kotlinidioms.extensions.HtmlEscaper
import com.example.kotlindp.patterns.kotlinidioms.extensions.Money
import com.example.kotlindp.patterns.kotlinidioms.extensions.Repository
import com.example.kotlindp.patterns.kotlinidioms.extensions.Shape
import com.example.kotlindp.patterns.kotlinidioms.extensions.daysUntil
import com.example.kotlindp.patterns.kotlinidioms.extensions.describe
import com.example.kotlindp.patterns.kotlinidioms.extensions.dollars
import com.example.kotlindp.patterns.kotlinidioms.extensions.isWeekend
import com.example.kotlindp.patterns.kotlinidioms.extensions.orPlaceholder
import com.example.kotlindp.patterns.kotlinidioms.extensions.plus
import com.example.kotlindp.patterns.kotlinidioms.extensions.replaceAt
import com.example.kotlindp.patterns.kotlinidioms.extensions.second
import com.example.kotlindp.patterns.kotlinidioms.extensions.sizeOrZero
import com.example.kotlindp.patterns.kotlinidioms.extensions.times
import com.example.kotlindp.patterns.kotlinidioms.extensions.toSlug
import com.example.kotlindp.patterns.kotlinidioms.functional.Fibonacci
import com.example.kotlindp.patterns.kotlinidioms.functional.LineItem
import com.example.kotlindp.patterns.kotlinidioms.functional.PricingService
import com.example.kotlindp.patterns.kotlinidioms.functional.Sale
import com.example.kotlindp.patterns.kotlinidioms.functional.curried
import com.example.kotlindp.patterns.kotlinidioms.functional.deltas
import com.example.kotlindp.patterns.kotlinidioms.functional.gcd
import com.example.kotlindp.patterns.kotlinidioms.functional.memoized
import com.example.kotlindp.patterns.kotlinidioms.functional.priceLines
import com.example.kotlindp.patterns.kotlinidioms.functional.ranked
import com.example.kotlindp.patterns.kotlinidioms.functional.revenueByRegion
import com.example.kotlindp.patterns.kotlinidioms.functional.runningTotals
import com.example.kotlindp.patterns.kotlinidioms.functional.slugify
import com.example.kotlindp.patterns.kotlinidioms.functional.splitBySize
import com.example.kotlindp.patterns.kotlinidioms.functional.then
import com.example.kotlindp.patterns.kotlinidioms.functional.topProductPerRegion
import com.example.kotlindp.patterns.kotlinidioms.functional.warn
import com.example.kotlindp.patterns.kotlinidioms.generics.AnimalShelter
import com.example.kotlindp.patterns.kotlinidioms.generics.Animal
import com.example.kotlindp.patterns.kotlinidioms.generics.Box
import com.example.kotlindp.patterns.kotlinidioms.generics.Dog
import com.example.kotlindp.patterns.kotlinidioms.generics.Email
import com.example.kotlindp.patterns.kotlinidioms.generics.Kennel
import com.example.kotlindp.patterns.kotlinidioms.generics.Record
import com.example.kotlindp.patterns.kotlinidioms.generics.SelectBuilder
import com.example.kotlindp.patterns.kotlinidioms.generics.admitDogs
import com.example.kotlindp.patterns.kotlinidioms.generics.countItems
import com.example.kotlindp.patterns.kotlinidioms.generics.describeAll
import com.example.kotlindp.patterns.kotlinidioms.generics.feedFrom
import com.example.kotlindp.patterns.kotlinidioms.generics.latestById
import com.example.kotlindp.patterns.kotlinidioms.generics.maxOfList
import com.example.kotlindp.patterns.kotlinidioms.generics.send
import com.example.kotlindp.patterns.kotlinidioms.inlinereified.Cents
import com.example.kotlindp.patterns.kotlinidioms.inlinereified.OrderId
import com.example.kotlindp.patterns.kotlinidioms.inlinereified.TypedRegistry
import com.example.kotlindp.patterns.kotlinidioms.inlinereified.UserId
import com.example.kotlindp.patterns.kotlinidioms.inlinereified.asOrNull
import com.example.kotlindp.patterns.kotlinidioms.inlinereified.filterInstances
import com.example.kotlindp.patterns.kotlinidioms.inlinereified.firstNegative
import com.example.kotlindp.patterns.kotlinidioms.inlinereified.forEachIndexedFast
import com.example.kotlindp.patterns.kotlinidioms.inlinereified.placeOrder
import com.example.kotlindp.patterns.kotlinidioms.inlinereified.repeatSafely
import com.example.kotlindp.patterns.kotlinidioms.inlinereified.withFallback
import com.example.kotlindp.patterns.kotlinidioms.result.Either
import com.example.kotlindp.patterns.kotlinidioms.result.PaymentError
import com.example.kotlindp.patterns.kotlinidioms.result.PaymentOutcome
import com.example.kotlindp.patterns.kotlinidioms.result.Registration
import com.example.kotlindp.patterns.kotlinidioms.result.describe as describePayment
import com.example.kotlindp.patterns.kotlinidioms.result.getOrElse
import com.example.kotlindp.patterns.kotlinidioms.result.parsePort
import com.example.kotlindp.patterns.kotlinidioms.result.registerAccumulating
import com.example.kotlindp.patterns.kotlinidioms.result.registerFailFast
import com.example.kotlindp.patterns.kotlinidioms.result.right
import com.example.kotlindp.patterns.kotlinidioms.scopefunctions.Server
import com.example.kotlindp.patterns.kotlinidioms.scopefunctions.connectionString
import com.example.kotlindp.patterns.kotlinidioms.scopefunctions.defaultServer
import com.example.kotlindp.patterns.kotlinidioms.scopefunctions.describeUser
import com.example.kotlindp.patterns.kotlinidioms.scopefunctions.evenOrNull
import com.example.kotlindp.patterns.kotlinidioms.scopefunctions.normalisedPorts
import com.example.kotlindp.patterns.kotlinidioms.scopefunctions.readAll
import com.example.kotlindp.patterns.kotlinidioms.scopefunctions.report
import com.example.kotlindp.patterns.kotlinidioms.scopefunctions.sanitisedName
import com.example.kotlindp.patterns.kotlinidioms.scopefunctions.trackedServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

class KotlinIdiomsTest {

    @Nested
    inner class DslTest {

        @Test
        fun `function literal with receiver configures the object`() {
            val cfg = config {
                host = "example.com"
                port = 443
                flags += "tls"
            }

            assertEquals("example.com", cfg.host)
            assertEquals(443, cfg.port)
            assertEquals(listOf("tls"), cfg.flags)
        }

        @Test
        fun `nested dsl builds a tree and renders it`() {
            val page = html {
                head { }
                body {
                    this["class"] = "main"
                    p { +"Hello" }
                    div { span { +"nested" } }
                }
            }

            val rendered = page.render()
            assertTrue(rendered.startsWith("<html>"))
            assertTrue(rendered.contains("""<body class="main">"""))
            assertTrue(rendered.contains("Hello"))
            assertTrue(rendered.contains("<span>"))
        }

        @Test
        fun `infix dsl reads as assertions and reports only failures`() {
            val failures = verify {
                1 shouldBe 1
                "kotlin" shouldContain "kot"
                5 shouldBeGreaterThan 3
            }
            assertTrue(failures.isEmpty())

            val broken = verify {
                1 shouldBe 2
                "kotlin" shouldContain "java"
            }
            assertEquals(2, broken.size)
            assertTrue(broken.first().message.contains("expected 2 but was 1"))
        }

        @Test
        fun `assertion scope collects passes and failures separately`() {
            val scope = AssertionScope()
            with(scope) {
                1 shouldBe 1
                1 shouldBe 2
            }
            assertEquals(2, scope.results.size)
            assertEquals(1, scope.results.count { it.passed })
        }

        @Test
        fun `scoped dsl validates and produces an immutable spec`() {
            val spec = client {
                url("https://api.example.com")
                http {
                    timeoutMs = 1_000
                    retry(3)
                }
            }

            assertEquals("https://api.example.com", spec.baseUrl)
            assertEquals(3, spec.retries)
            assertEquals(1_000, spec.timeoutMs)
        }

        @Test
        fun `dsl validation rejects incomplete and out-of-range configuration`() {
            assertThrows(IllegalArgumentException::class.java) { client { http { retry(1) } } }
            assertThrows(IllegalArgumentException::class.java) {
                client {
                    url("https://x")
                    http { retry(99) }
                }
            }
        }

        @Test
        fun `omitted optional block falls back to defaults`() {
            val spec = client { url("https://x") }
            assertEquals(0, spec.retries)
            assertEquals(30_000, spec.timeoutMs)
        }
    }

    @Nested
    inner class DelegationTest {

        @Test
        fun `class delegation forwards untouched members`() {
            val store = ValidatingEventStore(InMemoryEventStore())

            store.append("created")
            store.append("   ")

            assertEquals(listOf("created"), store.all())
            assertEquals(1, store.size()) // forwarded, never written by hand
            assertEquals(listOf("   "), store.rejected)
        }

        @Test
        fun `delegation composes without inheritance`() {
            val counting = CountingEventStore(ValidatingEventStore(InMemoryEventStore()))

            counting.append("a")
            counting.append("")

            assertEquals(2, counting.appendCalls)
            assertEquals(1, counting.size())
        }

        @Test
        fun `lazy computes once`() {
            val delegates = StandardDelegates()

            assertEquals("computed", delegates.expensive)
            assertEquals("computed", delegates.expensive)
            assertEquals(1, delegates.initialisations)
        }

        @Test
        fun `observable and vetoable behave differently`() {
            val delegates = StandardDelegates()

            delegates.status = "active"
            assertEquals(listOf("status: new->active"), delegates.changes)

            delegates.percentage = 50
            assertEquals(50, delegates.percentage)
            delegates.percentage = 500
            assertEquals(50, delegates.percentage) // rejected
        }

        @Test
        fun `notNull throws when read before it is set`() {
            val delegates = StandardDelegates()
            assertThrows(IllegalStateException::class.java) { delegates.configuredPort }

            delegates.configuredPort = 8080
            assertEquals(8080, delegates.configuredPort)
        }

        @Test
        fun `map-backed properties give a typed facade`() {
            val settings = Settings(mapOf("host" to "localhost", "port" to 8080, "debug" to true))

            assertEquals("localhost", settings.host)
            assertEquals(8080, settings.port)
            assertTrue(settings.debug)
        }

        @Test
        fun `a missing map key fails on access, not construction`() {
            val settings = Settings(mapOf("host" to "localhost"))
            assertThrows(NoSuchElementException::class.java) { settings.port }
        }

        @Test
        fun `custom delegates audit and validate at the assignment`() {
            val log = mutableListOf<String>()
            val cfg = ServerConfig(log)

            cfg.name = "api"
            assertEquals(listOf("name: default -> api"), log)

            cfg.port = 9090
            assertEquals(9090, cfg.port)

            val error = assertThrows(IllegalArgumentException::class.java) { cfg.port = 70_000 }
            assertTrue(error.message!!.contains("port"))
            assertEquals(9090, cfg.port) // invalid value never entered the object
        }

        @Test
        fun `derived delegate cannot go stale`() {
            assertEquals(3_000, Order(unitPriceCents = 1_000, quantity = 3).totalCents)
        }
    }

    @Nested
    inner class ResultTest {

        @Test
        fun `sealed outcomes force the caller to handle every failure`() {
            assertEquals(
                "charged 500 (ref)",
                describePayment(PaymentOutcome.Charged("ref", 500)),
            )
            assertEquals(
                "short by 250",
                describePayment(PaymentOutcome.Failed(PaymentError.InsufficientFunds(250))),
            )
            assertEquals(
                "retry: try_later",
                describePayment(PaymentOutcome.Failed(PaymentError.Declined("try_later", true))),
            )
            assertEquals(
                "rate limited",
                describePayment(PaymentOutcome.Failed(PaymentError.RateLimited)),
            )
        }

        @Test
        fun `either maps the success side and passes failures through`() {
            assertEquals(4, right(2).map { it * 2 }.getOrNull())

            val failed: Either<String, Int> = Either.Left("nope")
            assertNull(failed.map { it * 2 }.getOrNull())
        }

        @Test
        fun `fail-fast stops at the first violation`() {
            val bad = Registration("no-at-sign", 12, "FR")
            val result = registerFailFast(bad)

            assertFalse(result.isRight)
            assertEquals("invalid email: no-at-sign", result.fold({ it }, { "ok" }))
        }

        @Test
        fun `accumulating collects every violation`() {
            val bad = Registration("no-at-sign", 12, "FR")
            val result = registerAccumulating(bad)

            val errors = result.fold({ it }, { emptyList() })
            assertEquals(3, errors.size)
        }

        @Test
        fun `a valid registration passes both strategies`() {
            val good = Registration("a@b.com", 30, "US")

            assertTrue(registerFailFast(good).isRight)
            assertTrue(registerAccumulating(good).isRight)
        }

        @Test
        fun `runCatching converts a throwing call into a value`() {
            assertEquals(8080, parsePort("8080").getOrNull())
            assertTrue(parsePort("abc").fold({ it.contains("not a number") }, { false }))
            assertTrue(parsePort("99999").fold({ it.contains("out of range") }, { false }))
        }

        @Test
        fun `getOrElse supplies a default from the failure`() {
            val failed: Either<String, Int> = Either.Left("boom")
            assertEquals(-1, failed.getOrElse { -1 })
            assertEquals(5, right(5).getOrElse { -1 })
        }
    }

    @Nested
    inner class ExtensionsTest {

        @Test
        fun `extensions add vocabulary to types we do not own`() {
            assertEquals("hello-world", "  Hello World!  ".toSlug())
            assertEquals(listOf(1, 9, 3), listOf(1, 2, 3).replaceAt(1, 9))
            assertEquals(2, listOf(1, 2, 3).second())
        }

        @Test
        fun `nullable receivers work without a safe call`() {
            val missing: String? = null
            assertEquals("—", missing.orPlaceholder())
            assertEquals("value", "value".orPlaceholder())

            val noList: List<Int>? = null
            assertEquals(0, noList.sizeOrZero())
            assertEquals(2, listOf(1, 2).sizeOrZero())
        }

        @Test
        fun `domain vocabulary on primitives and operators`() {
            assertEquals(Money(2_000), 20.dollars)
            assertEquals(Money(3_000), 20.dollars + 10.dollars)
            assertEquals(Money(6_000), 20.dollars * 3)
        }

        @Test
        fun `adding mismatched currencies is rejected`() {
            assertThrows(IllegalArgumentException::class.java) {
                Money(100, "USD") + Money(100, "EUR")
            }
        }

        @Test
        fun `extensions are dispatched statically, not virtually`() {
            val asCircle = Circle()
            val asShape: Shape = asCircle

            assertEquals("circle", asCircle.describe())
            assertEquals("shape", asShape.describe()) // declared type wins
        }

        @Test
        fun `a member always shadows an extension`() {
            assertEquals("member:1", Repository().find("1"))
        }

        @Test
        fun `member extensions are scoped to their class`() {
            val escaper = HtmlEscaper(escapeQuotes = true)
            assertEquals(listOf("&lt;b&gt;", "&quot;q&quot;"), escaper.escapeAll(listOf("<b>", "\"q\"")))

            assertEquals(listOf("\"q\""), HtmlEscaper(escapeQuotes = false).escapeAll(listOf("\"q\"")))
        }

        @Test
        fun `date extensions read as domain language`() {
            val saturday = LocalDate.of(2026, 8, 15)
            assertTrue(saturday.isWeekend)
            assertFalse(LocalDate.of(2026, 8, 17).isWeekend)
            assertEquals(2L, saturday daysUntil LocalDate.of(2026, 8, 17))
        }
    }

    @Nested
    inner class ScopeFunctionsTest {

        @Test
        fun `let runs only on a non-null receiver`() {
            assertEquals("user <a@b.com>", describeUser("A@B.com"))
            assertEquals("anonymous", describeUser(null))
        }

        @Test
        fun `let scopes an intermediate value in a chain`() {
            assertEquals(listOf(80, 443), normalisedPorts(" 443, 80, 443, 99999 "))
        }

        @Test
        fun `apply configures and returns the receiver`() {
            val server = defaultServer()
            assertEquals("0.0.0.0", server.host)
            assertEquals(9090, server.port)
        }

        @Test
        fun `also performs a side effect and returns unchanged`() {
            val log = mutableListOf<String>()
            val server = trackedServer(log)

            assertEquals(9090, server.port)
            assertEquals(1, log.size)
            assertTrue(log.single().contains("0.0.0.0:9090"))
        }

        @Test
        fun `run and with compute a value from the receiver`() {
            val server = Server("db", 5432)

            assertEquals("db:5432", connectionString(server))
            assertTrue(report(server).contains("host: db"))
            assertTrue(report(server).contains("connections: 0"))
        }

        @Test
        fun `takeIf turns a condition into something chainable`() {
            assertEquals("quan", sanitisedName("  quan  "))
            assertEquals("unnamed", sanitisedName("   "))
            assertEquals(4, evenOrNull(4))
            assertNull(evenOrNull(3))
        }

        @Test
        fun `use closes the resource`() {
            val reader = java.io.StringReader("payload")
            assertEquals("payload", readAll(reader))
        }
    }

    @Nested
    inner class InlineReifiedTest {

        @Test
        fun `non-local return works because the lambda is inlined`() {
            assertEquals(-3, firstNegative(listOf(1, 2, -3, -4)))
            assertNull(firstNegative(listOf(1, 2, 3)))
        }

        @Test
        fun `inline higher-order function iterates with an index`() {
            val seen = mutableListOf<String>()
            listOf("a", "b").forEachIndexedFast { i, v -> seen += "$i=$v" }

            assertEquals(listOf("0=a", "1=b"), seen)
        }

        @Test
        fun `crossinline lambda runs from another context`() {
            val seen = mutableListOf<Int>()
            repeatSafely(3) { seen += it }

            assertEquals(listOf(0, 1, 2), seen)
        }

        @Test
        fun `noinline lambda can be stored and used as a fallback`() {
            assertEquals("primary", withFallback({ "primary" }, { "fallback" }))
            assertEquals("fallback", withFallback({ error("boom") }, { "fallback" }))
        }

        @Test
        fun `reified recovers the erased type`() {
            val mixed = listOf(1, "two", 3.0, "four")

            assertEquals(listOf("two", "four"), mixed.filterInstances<String>())
            assertEquals(listOf(1), mixed.filterInstances<Int>())
        }

        @Test
        fun `reified safe cast returns null on a mismatch`() {
            val value: Any = "text"
            assertEquals("text", value.asOrNull<String>())
            assertNull(value.asOrNull<Int>())
        }

        @Test
        fun `typed registry uses the type as both key and return type`() {
            val registry = TypedRegistry()
            registry.put("a string")
            registry.put(42)

            assertEquals("a string", registry.get<String>())
            assertEquals(42, registry.get<Int>())
            assertNull(registry.get<Double>())
        }

        @Test
        fun `value classes make transposed arguments a compile-time impossibility`() {
            val result = placeOrder(UserId("u1"), OrderId("o1"), Cents(1_234))
            assertEquals("u1 placed o1 for 12.34", result)
        }

        @Test
        fun `value class validation runs at construction`() {
            assertThrows(IllegalArgumentException::class.java) { UserId("  ") }
        }

        @Test
        fun `value class operators keep the domain type`() {
            assertEquals(Cents(300), Cents(100) + Cents(200))
            assertEquals(Cents(500), Cents(100) * 5)
            assertEquals("1.05", Cents(105).toDisplay())
        }
    }

    @Nested
    inner class FunctionalTest {

        private val sales = listOf(
            Sale("EU", "widget", 1_000, 2),
            Sale("EU", "gadget", 4_000, 1),
            Sale("US", "widget", 2_500, 5),
        )

        @Test
        fun `composition applies functions left to right`() {
            assertEquals("hello-world", slugify("  Hello   World "))
            val addThenDouble = { n: Int -> n + 1 } then { n: Int -> n * 2 }
            assertEquals(6, addThenDouble(2))
        }

        @Test
        fun `currying and partial application fix arguments`() {
            val curried = { a: Int, b: Int -> a + b }.curried()
            assertEquals(5, curried(2)(3))
            assertEquals("[WARN] disk full", warn("disk full"))
        }

        @Test
        fun `memoisation caches a pure function`() {
            val calls = AtomicInteger()
            val slow = { n: Int -> calls.incrementAndGet(); n * n }.memoized()

            assertEquals(9, slow(3))
            assertEquals(9, slow(3))
            assertEquals(1, calls.get())
        }

        @Test
        fun `recursive memoisation avoids exponential recomputation`() {
            val fib = Fibonacci()
            assertEquals(832_040L, fib.compute(30))
            assertEquals(31, fib.calls)
        }

        @Test
        fun `collection pipeline aggregates without mutable accumulators`() {
            assertEquals(mapOf("EU" to 5_000L, "US" to 2_500L), revenueByRegion(sales))
            assertEquals(mapOf("EU" to "gadget", "US" to "widget"), topProductPerRegion(sales))
        }

        @Test
        fun `partition splits in one pass`() {
            val (big, small) = splitBySize(sales, 2_000)
            assertEquals(2, big.size)
            assertEquals(1, small.size)
        }

        @Test
        fun `running totals and deltas need no index arithmetic`() {
            assertEquals(listOf(1_000L, 5_000L, 7_500L), runningTotals(sales))
            assertEquals(listOf(4_000L, 2_500L), deltas(runningTotals(sales)))
        }

        @Test
        fun `multi-key sorting reads as the requirement`() {
            assertEquals(
                listOf("gadget", "widget", "widget"),
                ranked(sales).map { it.product },
            )
        }

        @Test
        fun `the pure core is testable without any stub`() {
            val items = listOf(LineItem("a", 1_000, 2), LineItem("b", 500, 1))

            assertEquals(2_500, priceLines(items, 0))
            assertEquals(2_250, priceLines(items, 10))
            assertThrows(IllegalArgumentException::class.java) { priceLines(items, 200) }
        }

        @Test
        fun `the shell wires effects around the pure core`() {
            val audit = mutableListOf<String>()
            val service = PricingService(
                loadItems = { listOf(LineItem("a", 1_000, 3)) },
                discountFor = { 10 },
                audit = { audit += it },
            )

            assertEquals(2_700, service.applyPricing("cart-1"))
            assertEquals(listOf("priced cart-1 -> 2700"), audit)
        }

        @Test
        fun `tailrec recursion does not grow the stack`() {
            assertEquals(6L, gcd(48, 18))
            assertEquals(1L, gcd(17, 5))
        }
    }

    @Nested
    inner class GenericsTest {

        @Test
        fun `covariance lets a producer of a subtype stand in`() {
            assertEquals("fed rex", feedFrom(Kennel(listOf(Dog("rex")))))
        }

        @Test
        fun `contravariance lets a consumer of a supertype stand in`() {
            val shelter = AnimalShelter()
            admitDogs(shelter, listOf(Dog("rex"), Dog("fido")))

            assertEquals(listOf("rex", "fido"), shelter.admitted)
        }

        @Test
        fun `use-site variance accepts boxes of any subtype`() {
            val boxes = listOf(Box(Dog("rex")), Box<Animal>(Animal("generic")))
            assertEquals(listOf("rex", "generic"), describeAll(boxes))
        }

        @Test
        fun `star projection ignores the type argument entirely`() {
            assertEquals(2, countItems(listOf(Box("a"), Box(1))))
        }

        @Test
        fun `upper bounds and multiple bounds constrain the type`() {
            assertEquals(9, maxOfList(listOf(3, 9, 1)))
            assertNull(maxOfList(emptyList<Int>()))

            val latest = latestById(
                listOf(
                    Record("a", 1, "old"),
                    Record("a", 5, "new"),
                    Record("b", 2, "only"),
                ),
            )
            assertEquals("new", latest["a"]!!.payload)
            assertEquals("only", latest["b"]!!.payload)
        }

        @Test
        fun `f-bounded builder keeps the subclass type through the chain`() {
            val sql = SelectBuilder("users")
                .select("id", "email")
                .where("active = true")
                .select("name")
                .build()

            assertEquals("SELECT id, email, name FROM users WHERE active = true", sql)
        }

        @Test
        fun `phantom type prevents sending an unvalidated email`() {
            val validated = Email.of("a@b.com").validate()
            assertNotNull(validated)
            assertEquals("sent to a@b.com", send(validated!!))

            assertNull(Email.of("not-an-email").validate())
        }
    }

    @Nested
    inner class CoroutinesTest {

        @Test
        fun `parallel decomposition runs children concurrently`() = runBlocking {
            val profile = loadProfile(
                user = "quan",
                fetchOrders = { delay(20); 7 },
                fetchScore = { delay(20); 720 },
            )

            assertEquals(7, profile.orders)
            assertEquals(720, profile.creditScore)
        }

        @Test
        fun `coroutineScope propagates a child failure`() = runBlocking {
            val error = assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    loadProfile("quan", { error("orders down") }, { delay(50); 1 })
                }
            }
            assertEquals("orders down", error.message)
        }

        @Test
        fun `supervisorScope keeps independent results after one failure`() = runBlocking {
            val results = fetchAllTolerantly(listOf("a", "bad", "c")) { key ->
                if (key == "bad") error("nope") else "value-$key"
            }

            assertEquals("value-a", results["a"]!!.getOrNull())
            assertTrue(results["bad"]!!.isFailure)
            assertEquals("value-c", results["c"]!!.getOrNull())
        }

        @Test
        fun `bounded parallelism never exceeds the permit count`() = runBlocking {
            val inFlight = AtomicInteger()
            val peak = AtomicInteger()

            val results = mapConcurrently((1..20).toList(), concurrency = 4) { item ->
                val current = inFlight.incrementAndGet()
                peak.updateAndGet { maxOf(it, current) }
                delay(5)
                inFlight.decrementAndGet()
                item * 2
            }

            assertEquals(20, results.size)
            assertEquals(40, results.last())
            assertTrue(peak.get() <= 4, "peak concurrency was ${peak.get()}")
        }

        @Test
        fun `withTimeoutOrNull returns null instead of throwing`() = runBlocking {
            assertNull(callOrNull(30) { delay(500); "late" })
            assertEquals("fast", callOrNull(500) { "fast" })
        }

        @Test
        fun `cancellation is cooperative and needs a suspension point`() = runBlocking {
            val iterations = AtomicInteger()

            val job = launch(Dispatchers.Default) {
                computeCancellable(50_000_000) { iterations.incrementAndGet() }
            }
            delay(20)
            job.cancelAndJoin()

            assertTrue(iterations.get() < 50_000_000, "ensureActive() should have aborted the loop")
        }

        @Test
        fun `safely rethrows cancellation but captures other failures`() = runBlocking {
            assertTrue(safely { "ok" }.isSuccess)
            assertTrue(safely { error("boom") }.isFailure)

            assertThrows(CancellationException::class.java) {
                runBlocking { safely { throw CancellationException("cancelled") } }
            }
            Unit
        }

        @Test
        fun `firstOf returns the fastest and cancels the losers`() = runBlocking {
            var slowCompleted = false

            val winner = firstOf(
                { delay(200); slowCompleted = true; "slow" },
                { delay(10); "fast" },
            )

            assertEquals("fast", winner)
            delay(300)
            assertFalse(slowCompleted, "the losing coroutine should have been cancelled")
        }
    }
}
