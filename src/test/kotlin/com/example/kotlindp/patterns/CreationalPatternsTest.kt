package com.example.kotlindp.patterns

import com.example.kotlindp.patterns.creational.abstractfactory.MySqlFactory
import com.example.kotlindp.patterns.creational.abstractfactory.PostgresFactory
import com.example.kotlindp.patterns.creational.abstractfactory.UserRepository
import com.example.kotlindp.patterns.creational.abstractfactory.Vendor
import com.example.kotlindp.patterns.creational.abstractfactory.persistenceFactoryFor
import com.example.kotlindp.patterns.creational.builder.EmailBuilder
import com.example.kotlindp.patterns.creational.builder.HttpRequest
import com.example.kotlindp.patterns.creational.builder.pipeline
import com.example.kotlindp.patterns.creational.builder.withHeader
import com.example.kotlindp.patterns.creational.factorymethod.AlertDispatcher
import com.example.kotlindp.patterns.creational.factorymethod.Channel
import com.example.kotlindp.patterns.creational.factorymethod.MarketingDispatcher
import com.example.kotlindp.patterns.creational.factorymethod.NotificationFactory
import com.example.kotlindp.patterns.creational.factorymethod.Transport
import com.example.kotlindp.patterns.creational.prototype.CONFIDENTIAL_TEMPLATE
import com.example.kotlindp.patterns.creational.prototype.MutableCanvas
import com.example.kotlindp.patterns.creational.prototype.PrototypeRegistry
import com.example.kotlindp.patterns.creational.prototype.ReportConfig
import com.example.kotlindp.patterns.creational.prototype.newConfidentialDoc
import com.example.kotlindp.patterns.creational.singleton.AppInfo
import com.example.kotlindp.patterns.creational.singleton.ConnectionPool
import com.example.kotlindp.patterns.creational.singleton.MetricsRegistry
import com.example.kotlindp.patterns.creational.singleton.RequestCounter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration

class CreationalPatternsTest {

    @Nested
    inner class SingletonTest {

        @BeforeEach
        fun reset() = RequestCounter.reset()

        @Test
        fun `object declaration yields one shared instance`() {
            assertSame(AppInfo, AppInfo)
            assertEquals("kotlin-dp v0.0.1-SNAPSHOT", AppInfo.describe())
        }

        @Test
        fun `atomic state in a singleton survives concurrent increments`() {
            val threads = (1..8).map {
                Thread { repeat(1_000) { RequestCounter.increment() } }
            }
            threads.forEach(Thread::start)
            threads.forEach(Thread::join)

            assertEquals(8_000, RequestCounter.value())
        }

        @Test
        fun `lazy companion instance is created once`() {
            assertSame(ConnectionPool.get(), ConnectionPool.get())
            assertEquals("connection-from-pool-of-10", ConnectionPool.get().borrow())
        }

        @Test
        fun `bean-style registry is independent per instance, unlike an object`() {
            val a = MetricsRegistry()
            val b = MetricsRegistry()
            a.increment("requests")
            a.increment("requests")
            b.increment("requests")

            assertEquals(mapOf("requests" to 2L), a.snapshot())
            assertEquals(mapOf("requests" to 1L), b.snapshot())
        }
    }

    @Nested
    inner class FactoryMethodTest {

        @Test
        fun `factory returns the implementation for each channel`() {
            assertEquals("email", NotificationFactory.create(Channel.EMAIL).channel)
            assertEquals("sms", NotificationFactory.create(Channel.SMS).channel)
            assertEquals("push", NotificationFactory.create(Channel.PUSH).channel)
        }

        @Test
        fun `created notification formats its message`() {
            val sent = NotificationFactory.create(Channel.EMAIL).send("a@b.com", "hi")
            assertEquals("[email] to=a@b.com body=hi", sent)
        }

        @Test
        fun `invoke operator on companion reads like a constructor`() {
            assertTrue(Transport("https").deliver("payload").startsWith("POST https"))
            assertEquals("write to disk (payload)", Transport("file").deliver("payload"))
        }

        @Test
        fun `unknown scheme is rejected`() {
            assertThrows(IllegalArgumentException::class.java) { Transport("carrier-pigeon") }
        }

        @Test
        fun `polymorphic creator picks its own product`() {
            assertEquals("[email] to=x body=y", MarketingDispatcher().dispatch("x", "y"))
            assertEquals("[sms] to=x body=y", AlertDispatcher().dispatch("x", "y"))
        }
    }

    @Nested
    inner class AbstractFactoryTest {

        @Test
        fun `each family produces internally consistent members`() {
            assertEquals("jdbc:postgresql://db:5432/app", PostgresFactory.connection("db", "app").url())
            assertEquals("jdbc:mysql://db:3306/app", MySqlFactory.connection("db", "app").url())
        }

        @Test
        fun `dialects differ in paging syntax and cannot be mixed up`() {
            assertEquals("SELECT 1 LIMIT 10 OFFSET 20", PostgresFactory.dialect().paginate("SELECT 1", 10, 20))
            assertEquals("SELECT 1 LIMIT 20, 10", MySqlFactory.dialect().paginate("SELECT 1", 10, 20))
        }

        @Test
        fun `client code is written once against the abstract roles`() {
            val postgres = UserRepository(persistenceFactoryFor(Vendor.POSTGRES), "h", "app")
            val mysql = UserRepository(persistenceFactoryFor(Vendor.MYSQL), "h", "app")

            assertTrue(postgres.findPage(5, 0).contains("LIMIT 5 OFFSET 0"))
            assertTrue(mysql.findPage(5, 0).contains("LIMIT 0, 5"))
        }

        @Test
        fun `migration runners are vendor specific`() {
            assertEquals("SELECT pg_advisory_lock(1)", PostgresFactory.migrationRunner().lockStatement())
            assertEquals("SELECT GET_LOCK('migration', 10)", MySqlFactory.migrationRunner().lockStatement())
        }
    }

    @Nested
    inner class BuilderTest {

        @Test
        fun `named and default arguments replace a simple builder`() {
            val request = HttpRequest(url = "https://api.example.com", method = "POST")

            assertEquals("POST", request.method)
            assertEquals(Duration.ofSeconds(30), request.timeout)
            assertTrue(request.followRedirects)
        }

        @Test
        fun `dsl builder produces a nested immutable structure`() {
            val result = pipeline("release") {
                env("CI", "true")
                stage("build") { command = "./gradlew build" }
                stage("deploy") {
                    command = "./deploy.sh"
                    retries = 3
                }
            }

            assertEquals("release", result.name)
            assertEquals(listOf("build", "deploy"), result.stages.map { it.name })
            assertEquals(3, result.stages[1].retries)
            assertEquals(mapOf("CI" to "true"), result.environment)
        }

        @Test
        fun `build validates and names the offending element`() {
            val missingCommand = assertThrows(IllegalArgumentException::class.java) {
                pipeline("p") { stage("broken") { retries = 1 } }
            }
            assertTrue(missingCommand.message!!.contains("broken"))

            val noStages = assertThrows(IllegalArgumentException::class.java) {
                pipeline("empty") { env("A", "B") }
            }
            assertTrue(noStages.message!!.contains("at least one stage"))
        }

        @Test
        fun `fluent builder requires its mandatory field`() {
            val email = EmailBuilder().to("a@b.com").subject("hi").cc("c@d.com").build()
            assertEquals("a@b.com", email.to)
            assertEquals(listOf("c@d.com"), email.cc)

            assertThrows(IllegalArgumentException::class.java) { EmailBuilder().subject("orphan").build() }
        }

        @Test
        fun `copy acts as a builder for modification`() {
            val original = HttpRequest(url = "https://x")
            val modified = original.withHeader("Accept", "application/json")

            assertEquals(emptyMap<String, String>(), original.headers)
            assertEquals(mapOf("Accept" to "application/json"), modified.headers)
        }
    }

    @Nested
    inner class PrototypeTest {

        @Test
        fun `copy clones a template and overrides only the named fields`() {
            val doc = newConfidentialDoc("Q3", "quan")

            assertEquals("Q3", doc.title)
            assertEquals("quan", doc.author)
            assertEquals(listOf("confidential"), doc.tags)
            assertEquals("CONFIDENTIAL", doc.watermark)
            assertEquals("Untitled", CONFIDENTIAL_TEMPLATE.title)
        }

        @Test
        fun `shallow copy shares mutable state and deep copy does not`() {
            val original = MutableCanvas(10, 20, mutableListOf("a"))

            val shallow = original.shallowCopy()
            shallow.labels += "from-shallow"
            assertEquals(listOf("a", "from-shallow"), original.labels)

            val deep = original.deepCopy()
            deep.labels += "from-deep"
            assertEquals(listOf("a", "from-shallow"), original.labels)
            assertEquals(listOf("a", "from-shallow", "from-deep"), deep.labels)
        }

        @Test
        fun `registry hands out copies, never the prototype itself`() {
            val registry = PrototypeRegistry<ReportConfig>()
            val prototype = ReportConfig("sales", listOf("a", "b"), 50, mapOf("region" to "EU"))
            registry.register("sales", prototype)

            val first = registry.create("sales")
            assertEquals(prototype, first)
            assertNotSame(prototype, first)
            assertEquals(setOf("sales"), registry.keys())
        }

        @Test
        fun `unknown prototype key fails loudly`() {
            val registry = PrototypeRegistry<ReportConfig>()
            assertThrows(IllegalStateException::class.java) { registry.create("missing") }
        }

        @Test
        fun `data class equality makes copies interchangeable by value`() {
            val a = ReportConfig("r", listOf("x"), 10, emptyMap())
            assertEquals(a, a.duplicate())
            assertFalse(a === a.duplicate())
        }
    }
}
