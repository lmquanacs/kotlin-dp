package com.example.kotlindp.patterns

import com.example.kotlindp.patterns.spring.aop.AuditAspect
import com.example.kotlindp.patterns.spring.aop.ReportService
import com.example.kotlindp.patterns.spring.aop.TimingAspect
import com.example.kotlindp.patterns.spring.applicationevents.AuditListener
import com.example.kotlindp.patterns.spring.applicationevents.EmailListener
import com.example.kotlindp.patterns.spring.applicationevents.HighValueOrderListener
import com.example.kotlindp.patterns.spring.applicationevents.InventoryListener
import com.example.kotlindp.patterns.spring.applicationevents.OrderPublishingService
import com.example.kotlindp.patterns.spring.beanlifecycle.BufferFactory
import com.example.kotlindp.patterns.spring.beanlifecycle.InfrastructureConfig
import com.example.kotlindp.patterns.spring.beanlifecycle.RequestMetrics
import com.example.kotlindp.patterns.spring.beanlifecycle.TaskReporter
import com.example.kotlindp.patterns.spring.beanlifecycle.TaskRunner
import com.example.kotlindp.patterns.spring.beanlifecycle.WarmingCache
import com.example.kotlindp.patterns.spring.conditionalbeans.StorageClient
import com.example.kotlindp.patterns.spring.conditionalbeans.UploadService
import com.example.kotlindp.patterns.spring.configurationproperties.PaymentClient
import com.example.kotlindp.patterns.spring.configurationproperties.PaymentProperties
import com.example.kotlindp.patterns.spring.configurationproperties.PoolProperties
import com.example.kotlindp.patterns.spring.dependencyinjection.AlertService
import com.example.kotlindp.patterns.spring.dependencyinjection.FraudCheck
import com.example.kotlindp.patterns.spring.dependencyinjection.FraudCheckRegistry
import com.example.kotlindp.patterns.spring.dependencyinjection.FraudService
import com.example.kotlindp.patterns.spring.dependencyinjection.InstrumentedFraudService
import com.example.kotlindp.patterns.spring.dependencyinjection.NotificationSender
import com.example.kotlindp.patterns.spring.interceptor.CorrelationIdFilter
import com.example.kotlindp.patterns.spring.interceptor.MetricsInterceptor
import com.example.kotlindp.patterns.spring.servicelayer.OrderFailure
import com.example.kotlindp.patterns.spring.servicelayer.OrderResult
import com.example.kotlindp.patterns.spring.servicelayer.OrderService
import com.example.kotlindp.patterns.spring.servicelayer.Product
import com.example.kotlindp.patterns.spring.servicelayer.ProductRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

/**
 * Boots the real application context once and exercises the `spring/` patterns end to end —
 * including the wiring itself, which is the part a unit test cannot check.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SpringPatternsTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var context: ApplicationContext

    @Nested
    inner class DependencyInjectionTest {

        @Autowired private lateinit var fraudService: FraudService
        @Autowired private lateinit var registry: FraudCheckRegistry
        @Autowired private lateinit var alerts: AlertService
        @Autowired private lateinit var instrumented: InstrumentedFraudService

        @Test
        fun `injecting List of T collects every implementation`() {
            assertEquals(setOf("amount", "country", "velocity"), fraudService.availableChecks())
        }

        @Test
        fun `the strategy list is applied without a hand-written registry`() {
            assertEquals(listOf("amount"), fraudService.evaluate(2_000_000, "US"))
            assertEquals(listOf("country"), fraudService.evaluate(100, "XX"))
            assertEquals(emptyList<String>(), fraudService.evaluate(100, "US"))
        }

        @Test
        fun `injecting Map of String to T keys beans by name`() {
            assertTrue(registry.beanNames().contains("amountFraudCheck"))
            assertNotNull(registry.byName("country"))
        }

        @Test
        fun `Primary supplies the default and Qualifier selects a specific bean`() {
            assertTrue(alerts.notifyUser("a@b.com", "hi").startsWith("email->"))
            assertTrue(alerts.alertUser("+123", "urgent").startsWith("sms->"))
        }

        @Test
        fun `Primary also resolves a bare injection of the interface`() {
            assertTrue(context.getBean(NotificationSender::class.java).send("x", "y").startsWith("email->"))
        }

        @Test
        fun `ObjectProvider tolerates a missing optional dependency`() {
            // No MetricsExporter bean is defined — ifAvailable simply does nothing.
            assertEquals(listOf("amount"), instrumented.evaluate(2_000_000, "US"))
        }

        @Test
        fun `every FraudCheck is a container-managed singleton`() {
            val first = context.getBeansOfType(FraudCheck::class.java)
            val second = context.getBeansOfType(FraudCheck::class.java)

            assertEquals(3, first.size)
            first.forEach { (name, bean) -> assertSame(bean, second[name]) }
        }
    }

    @Nested
    inner class ConfigurationPropertiesTest {

        @Autowired private lateinit var properties: PaymentProperties
        @Autowired private lateinit var client: PaymentClient

        @Test
        fun `defaults bind when nothing is configured`() {
            assertEquals("https://payments.example.com", properties.baseUrl)
            assertEquals(Duration.ofSeconds(5), properties.connectTimeout)
            assertEquals(3, properties.maxRetries)
            assertEquals(10, properties.pool.maxSize)
        }

        @Test
        fun `derived values are computed from validated inputs`() {
            assertEquals(Duration.ofSeconds(35), properties.totalTimeout)
            assertTrue(client.describe().contains("retries=3"))
        }

        @Test
        fun `collection properties bind and are usable as domain checks`() {
            assertTrue(properties.currencySupported("USD"))
            assertFalse(properties.currencySupported("JPY"))
            assertFalse(properties.flagEnabled("anything"))
        }

        @Test
        fun `it is a plain data class, so unit tests need no context`() {
            val custom = PaymentProperties(maxRetries = 1, pool = PoolProperties(maxSize = 2))

            assertEquals(1, custom.maxRetries)
            assertEquals(2, custom.pool.maxSize)
        }

        @Test
        fun `cross-field validation runs in init`() {
            val error = assertThrows(IllegalArgumentException::class.java) {
                PaymentProperties(
                    connectTimeout = Duration.ofSeconds(30),
                    readTimeout = Duration.ofSeconds(1),
                )
            }
            assertTrue(error.message!!.contains("read-timeout"))
        }
    }

    @Nested
    inner class BeanLifecycleTest {

        @Autowired private lateinit var cache: WarmingCache
        @Autowired private lateinit var metrics: RequestMetrics
        @Autowired private lateinit var buffers: BufferFactory
        @Autowired private lateinit var runner: TaskRunner
        @Autowired private lateinit var reporter: TaskReporter
        @Autowired private lateinit var infrastructure: InfrastructureConfig

        @Test
        fun `PostConstruct ran after injection completed`() {
            assertTrue(cache.isInitialised())
            assertEquals("preloaded", cache.get("default"))
            assertFalse(cache.isDestroyed())
        }

        @Test
        fun `singleton beans are shared and must be thread-safe`() {
            val before = metrics.total()
            val threads = (1..4).map { Thread { repeat(500) { metrics.record() } } }
            threads.forEach(Thread::start)
            threads.forEach(Thread::join)

            assertEquals(before + 2_000, metrics.total())
        }

        @Test
        fun `a prototype resolved through ObjectProvider is new each time`() {
            assertNotSame(buffers.newBuffer(), buffers.newBuffer())
            assertNotEquals(buffers.newBuffer().id, buffers.newBuffer().id)
        }

        @Test
        fun `Configuration proxying makes inter-bean calls return the same singleton`() {
            assertTrue(reporter.sameRunner(runner))
            // calling the @Bean method again through the proxied config returns the same instance
            assertSame(runner, infrastructure.taskRunner())
        }

        @Test
        fun `beans created by Bean methods work normally`() {
            assertEquals("ran:job(pool=4)", runner.submit("job"))
            assertTrue(runner.running)
            assertEquals("reporter over pool of 4", reporter.report())
        }

        private fun assertNotEquals(a: Any?, b: Any?) = assertFalse(a == b)
    }

    @Nested
    inner class AopTest {

        @Autowired private lateinit var reports: ReportService
        @Autowired private lateinit var timing: TimingAspect
        @Autowired private lateinit var audit: AuditAspect

        @Test
        fun `the aspect decorates the bean without the bean knowing`() {
            timing.reset()
            reports.generate(5)

            assertEquals(1, timing.callCount("report.generate"))
        }

        @Test
        fun `the audit aspect records both outcomes`() {
            audit.entries.clear()

            reports.generate(1)
            assertThrows(IllegalArgumentException::class.java) { reports.generate(-1) }

            assertEquals(
                listOf("ok:generate-report", "fail:generate-report:IllegalArgumentException"),
                audit.entries.toList(),
            )
        }

        @Test
        fun `timing still records when the call fails`() {
            timing.reset()
            assertThrows(IllegalArgumentException::class.java) { reports.generate(-1) }

            assertEquals(1, timing.callCount("report.generate"))
        }

        @Test
        fun `self-invocation bypasses the proxy`() {
            timing.reset()
            reports.quickSummary()

            assertEquals(1, timing.callCount("quickSummary"))
            // summaryHelper() is annotated, but was called via `this` — no advice ran
            assertEquals(0, timing.callCount("never.recorded"))
        }

        @Test
        fun `the bean really is a proxy, thanks to kotlin-allopen`() {
            assertTrue(
                reports.javaClass.name.contains("$\$EnhancerBySpringCGLIB$\$"),
                "expected a CGLIB proxy but got ${reports.javaClass.name}",
            )
        }
    }

    @Nested
    inner class ApplicationEventsTest {

        @Autowired private lateinit var orders: OrderPublishingService
        @Autowired private lateinit var inventory: InventoryListener
        @Autowired private lateinit var emails: EmailListener
        @Autowired private lateinit var highValue: HighValueOrderListener
        @Autowired private lateinit var audit: AuditListener

        @Test
        fun `publishing reaches every listener synchronously`() {
            orders.place("evt-1", "cust-1", 5_000)

            assertTrue(inventory.reserved.contains("evt-1"))
            assertTrue(emails.sent.any { it.contains("evt-1") })
            assertTrue(audit.entries.contains("placed:evt-1"))
        }

        @Test
        fun `a SpEL condition filters before the listener runs`() {
            orders.place("evt-small", "cust-1", 1_000)
            orders.place("evt-big", "cust-1", 500_000)

            assertFalse(highValue.flagged.contains("evt-small"))
            assertTrue(highValue.flagged.contains("evt-big"))
        }

        @Test
        fun `a second event type reaches its own listeners`() {
            orders.place("evt-2", "cust-1", 1_000)
            orders.cancel("evt-2", "customer changed mind")

            assertFalse(inventory.reserved.contains("evt-2"))
            assertTrue(audit.entries.contains("cancelled:evt-2:customer changed mind"))
        }

        @Test
        fun `the publisher does not know who listens`() {
            orders.place("evt-3", "cust-9", 100)
            assertTrue(orders.placedOrders().contains("evt-3"))
        }
    }

    @Nested
    inner class ConditionalBeansTest {

        @Autowired private lateinit var uploads: UploadService
        @Autowired private lateinit var storage: StorageClient

        @Test
        fun `matchIfMissing selects the default family with no configuration`() {
            assertEquals("local", storage.provider)
            assertEquals("local", uploads.activeProvider())
        }

        @Test
        fun `the whole family is activated together`() {
            assertTrue(uploads.upload("Report.PDF", "content").startsWith("/tmp/uploads/"))
            assertTrue(uploads.shareLink("Report.PDF").startsWith("file://"))
        }

        @Test
        fun `ConditionalOnMissingBean supplied the default naming strategy`() {
            assertEquals("/tmp/uploads/uploads/report.pdf", uploads.upload("Report.PDF", "x"))
        }

        @Test
        fun `the S3 family is not in the context`() {
            assertEquals(1, context.getBeansOfType(StorageClient::class.java).size)
        }
    }

    @Nested
    inner class ServiceLayerTest {

        @Autowired private lateinit var orders: OrderService
        @Autowired private lateinit var products: ProductRepository

        @Test
        fun `the service owns the use case and returns a typed result`() {
            products.save(Product("TESTSKU", "Test", 1_000, 5))

            val result = orders.place("TESTSKU", 2)
            assertTrue(result is OrderResult.Placed)
            assertEquals(2_000, (result as OrderResult.Placed).order.totalCents)
            assertEquals(3, products.findBySku("TESTSKU")!!.stock)
        }

        @Test
        fun `expected failures are values, not exceptions`() {
            assertTrue(
                (orders.place("NOPE", 1) as OrderResult.Rejected).failure is OrderFailure.UnknownProduct,
            )
            assertTrue(
                (orders.place("GADGET", 1) as OrderResult.Rejected).failure is OrderFailure.InsufficientStock,
            )
            assertTrue(
                (orders.place("WIDGET", 0) as OrderResult.Rejected).failure is OrderFailure.InvalidQuantity,
            )
        }

        @Test
        fun `the controller maps a success to 200 and a DTO`() {
            products.save(Product("HTTPSKU", "Http", 2_500, 10))

            mockMvc.perform(
                post("/patterns/orders")
                    .contentType("application/json")
                    .content("""{"sku":"HTTPSKU","quantity":2}"""),
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.sku").value("HTTPSKU"))
                .andExpect(jsonPath("$.totalCents").value(5000))
        }

        @Test
        fun `each domain failure maps to its own status code`() {
            mockMvc.perform(
                post("/patterns/orders")
                    .contentType("application/json")
                    .content("""{"sku":"MISSING","quantity":1}"""),
            )
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.code").value("unknown_product"))

            mockMvc.perform(
                post("/patterns/orders")
                    .contentType("application/json")
                    .content("""{"sku":"GADGET","quantity":1}"""),
            )
                .andExpect(status().isConflict)
                .andExpect(jsonPath("$.code").value("insufficient_stock"))

            mockMvc.perform(
                post("/patterns/orders")
                    .contentType("application/json")
                    .content("""{"sku":"WIDGET","quantity":-1}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.code").value("invalid_quantity"))
        }

        @Test
        fun `the catalogue endpoint returns DTOs, not domain objects`() {
            mockMvc.perform(get("/patterns/orders/catalogue"))
                .andExpect(status().isOk)
                // `inStock` is a DTO-only field; `stock` (the domain field) must not leak
                .andExpect(jsonPath("$[0].inStock").exists())
                .andExpect(jsonPath("$[0].stock").doesNotExist())
        }
    }

    @Nested
    inner class InterceptorTest {

        @Autowired private lateinit var metricsInterceptor: MetricsInterceptor

        @Test
        fun `the filter adds a correlation id to the response`() {
            mockMvc.perform(get("/patterns/orders/catalogue"))
                .andExpect(status().isOk)
                .andExpect(header().exists(CorrelationIdFilter.HEADER))
        }

        @Test
        fun `an inbound correlation id is honoured, so traces survive across services`() {
            mockMvc.perform(
                get("/patterns/orders/catalogue").header(CorrelationIdFilter.HEADER, "trace-abc"),
            )
                .andExpect(status().isOk)
                .andExpect(header().string(CorrelationIdFilter.HEADER, "trace-abc"))
        }

        @Test
        fun `the interceptor counts requests on its registered paths`() {
            metricsInterceptor.reset()

            mockMvc.perform(get("/patterns/orders/catalogue")).andExpect(status().isOk)
            mockMvc.perform(get("/patterns/orders/catalogue")).andExpect(status().isOk)

            assertEquals(2, metricsInterceptor.requestCount())
        }

        @Test
        fun `the interceptor does not run outside its path patterns`() {
            metricsInterceptor.reset()

            mockMvc.perform(get("/")).andExpect(status().isOk)

            assertEquals(0, metricsInterceptor.requestCount())
        }

        @Test
        fun `the existing application endpoints are unaffected by the pattern infrastructure`() {
            mockMvc.perform(get("/")).andExpect(status().isOk).andExpect(content().string("Hello, World!"))
        }
    }
}
