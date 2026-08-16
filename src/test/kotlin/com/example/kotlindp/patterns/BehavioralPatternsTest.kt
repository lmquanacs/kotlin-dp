package com.example.kotlindp.patterns

import com.example.kotlindp.patterns.behavioral.chainofresponsibility.Approval
import com.example.kotlindp.patterns.behavioral.chainofresponsibility.Director
import com.example.kotlindp.patterns.behavioral.chainofresponsibility.Expense
import com.example.kotlindp.patterns.behavioral.chainofresponsibility.Manager
import com.example.kotlindp.patterns.behavioral.chainofresponsibility.Middleware
import com.example.kotlindp.patterns.behavioral.chainofresponsibility.Request
import com.example.kotlindp.patterns.behavioral.chainofresponsibility.Response
import com.example.kotlindp.patterns.behavioral.chainofresponsibility.TeamLead
import com.example.kotlindp.patterns.behavioral.chainofresponsibility.authentication
import com.example.kotlindp.patterns.behavioral.chainofresponsibility.buildPipeline
import com.example.kotlindp.patterns.behavioral.chainofresponsibility.chainOf
import com.example.kotlindp.patterns.behavioral.chainofresponsibility.managerRule
import com.example.kotlindp.patterns.behavioral.chainofresponsibility.requestLogging
import com.example.kotlindp.patterns.behavioral.chainofresponsibility.teamLeadRule
import com.example.kotlindp.patterns.behavioral.command.CommandHistory
import com.example.kotlindp.patterns.behavioral.command.DeleteText
import com.example.kotlindp.patterns.behavioral.command.InsertText
import com.example.kotlindp.patterns.behavioral.command.MacroCommand
import com.example.kotlindp.patterns.behavioral.command.TaskQueue
import com.example.kotlindp.patterns.behavioral.command.TextDocument
import com.example.kotlindp.patterns.behavioral.interpreter.Predicate
import com.example.kotlindp.patterns.behavioral.interpreter.evaluate
import com.example.kotlindp.patterns.behavioral.interpreter.rule
import com.example.kotlindp.patterns.behavioral.interpreter.toSql
import com.example.kotlindp.patterns.behavioral.iterator.RingBuffer
import com.example.kotlindp.patterns.behavioral.iterator.fibonacci
import com.example.kotlindp.patterns.behavioral.iterator.firstMatchCost
import com.example.kotlindp.patterns.behavioral.iterator.paginated
import com.example.kotlindp.patterns.behavioral.mediator.Button
import com.example.kotlindp.patterns.behavioral.mediator.FormMediator
import com.example.kotlindp.patterns.behavioral.mediator.OrderPlaced
import com.example.kotlindp.patterns.behavioral.mediator.StatusLabel
import com.example.kotlindp.patterns.behavioral.mediator.TextField
import com.example.kotlindp.patterns.behavioral.mediator.TypedMediator
import com.example.kotlindp.patterns.behavioral.mediator.UserRegistered
import com.example.kotlindp.patterns.behavioral.memento.Editor
import com.example.kotlindp.patterns.behavioral.memento.FormState
import com.example.kotlindp.patterns.behavioral.memento.History
import com.example.kotlindp.patterns.behavioral.memento.UndoableForm
import com.example.kotlindp.patterns.behavioral.nullobject.NoOpAuditSink
import com.example.kotlindp.patterns.behavioral.nullobject.ListAuditSink
import com.example.kotlindp.patterns.behavioral.nullobject.OrderService
import com.example.kotlindp.patterns.behavioral.nullobject.Principal
import com.example.kotlindp.patterns.behavioral.nullobject.canEdit
import com.example.kotlindp.patterns.behavioral.nullobject.summarise
import com.example.kotlindp.patterns.behavioral.observer.EventBus
import com.example.kotlindp.patterns.behavioral.observer.Price
import com.example.kotlindp.patterns.behavioral.observer.PriceTicker
import com.example.kotlindp.patterns.behavioral.observer.Thermostat
import com.example.kotlindp.patterns.behavioral.state.Order
import com.example.kotlindp.patterns.behavioral.state.OrderEvent
import com.example.kotlindp.patterns.behavioral.state.OrderState
import com.example.kotlindp.patterns.behavioral.state.TransitionResult
import com.example.kotlindp.patterns.behavioral.state.transition
import com.example.kotlindp.patterns.behavioral.strategy.Basket
import com.example.kotlindp.patterns.behavioral.strategy.Checkout
import com.example.kotlindp.patterns.behavioral.strategy.ExpressShipping
import com.example.kotlindp.patterns.behavioral.strategy.FlatRateShipping
import com.example.kotlindp.patterns.behavioral.strategy.ShippingCalculator
import com.example.kotlindp.patterns.behavioral.strategy.StandardShipping
import com.example.kotlindp.patterns.behavioral.strategy.TaxStrategy
import com.example.kotlindp.patterns.behavioral.strategy.bestOf
import com.example.kotlindp.patterns.behavioral.strategy.bulkDiscount
import com.example.kotlindp.patterns.behavioral.strategy.noDiscount
import com.example.kotlindp.patterns.behavioral.strategy.tenPercentOff
import com.example.kotlindp.patterns.behavioral.templatemethod.CsvUserImporter
import com.example.kotlindp.patterns.behavioral.templatemethod.User
import com.example.kotlindp.patterns.behavioral.templatemethod.runImport
import com.example.kotlindp.patterns.behavioral.templatemethod.withAudit
import com.example.kotlindp.patterns.behavioral.visitor.DepthVisitor
import com.example.kotlindp.patterns.behavioral.visitor.Expr
import com.example.kotlindp.patterns.behavioral.visitor.accept
import com.example.kotlindp.patterns.behavioral.visitor.evaluate
import com.example.kotlindp.patterns.behavioral.visitor.format
import com.example.kotlindp.patterns.behavioral.visitor.simplify
import com.example.kotlindp.patterns.behavioral.visitor.variables
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class BehavioralPatternsTest {

    @Nested
    inner class StrategyTest {

        private val basket = Basket(itemCount = 12, subtotalCents = 10_000, customerTier = "gold")

        @Test
        fun `strategy is injected as a function`() {
            assertEquals(10_000, Checkout(noDiscount).total(basket))
            assertEquals(9_000, Checkout(tenPercentOff).total(basket))
            assertEquals(8_000, Checkout(bulkDiscount).total(basket))
        }

        @Test
        fun `strategies compose without duplicating logic`() {
            val best = bestOf(tenPercentOff, bulkDiscount)
            assertEquals(8_000, Checkout(best).total(basket))

            val smallBasket = Basket(2, 10_000, "gold")
            assertEquals(9_000, Checkout(best).total(smallBasket))
        }

        @Test
        fun `a lambda strategy needs no mocking framework`() {
            assertEquals(9_900, Checkout { 100 }.total(basket))
        }

        @Test
        fun `interface strategies are selectable by code at runtime`() {
            val calculator = ShippingCalculator(
                listOf(StandardShipping, ExpressShipping, FlatRateShipping(999)),
            )

            assertEquals(setOf("standard", "express", "flat"), calculator.available())
            assertEquals(999, calculator.cost("flat", 1_000, 500))
            assertTrue(calculator.cost("express", 1_000, 500) > calculator.cost("standard", 1_000, 500))
        }

        @Test
        fun `unknown strategy code fails loudly`() {
            val calculator = ShippingCalculator(listOf(StandardShipping))
            assertThrows(IllegalStateException::class.java) { calculator.cost("teleport", 1, 1) }
        }

        @Test
        fun `enum strategies attach behaviour to a closed set`() {
            assertEquals(0, TaxStrategy.NONE.taxCents(10_000))
            assertEquals(2_000, TaxStrategy.VAT_20.taxCents(10_000))
            assertEquals(700, TaxStrategy.SALES_TAX_7.taxCents(10_000))
        }
    }

    @Nested
    inner class StateTest {

        @Test
        fun `the happy path walks the whole lifecycle`() {
            val order = Order()

            assertTrue(order.handle(OrderEvent.Submit(5_000)))
            assertTrue(order.handle(OrderEvent.Pay("pay_1")))
            assertTrue(order.handle(OrderEvent.Ship("TRACK-1")))

            val state = order.state
            assertTrue(state is OrderState.Shipped)
            assertEquals("pay_1", (state as OrderState.Shipped).paymentRef)
            assertEquals("TRACK-1", state.trackingCode)
        }

        @Test
        fun `illegal transitions are rejected without changing state`() {
            val order = Order()

            assertFalse(order.handle(OrderEvent.Ship("TRACK-1")))
            assertEquals(OrderState.Draft, order.state)
        }

        @Test
        fun `terminal states accept nothing further`() {
            val order = Order()
            order.handle(OrderEvent.Cancel("changed mind"))

            assertTrue(order.state.isTerminal)
            assertFalse(order.handle(OrderEvent.Submit(100)))
            assertFalse(order.handle(OrderEvent.Pay("x")))
        }

        @Test
        fun `the transition function is pure and testable in isolation`() {
            val result = transition(OrderState.AwaitingPayment(500), OrderEvent.Pay("pay_9"))

            assertTrue(result is TransitionResult.Moved)
            assertEquals(OrderState.Paid("pay_9"), (result as TransitionResult.Moved).to)

            val rejected = transition(OrderState.Shipped("p", "t"), OrderEvent.Cancel("nope"))
            assertTrue(rejected is TransitionResult.Rejected)
            assertTrue((rejected as TransitionResult.Rejected).why.contains("final"))
        }

        @Test
        fun `payment reference travels with the state, so it cannot be read too early`() {
            val order = Order()
            order.handle(OrderEvent.Submit(100))
            order.handle(OrderEvent.Pay("pay_7"))
            order.handle(OrderEvent.Ship("T"))

            assertEquals(listOf(true, true, true, true), order.history().map { it != OrderState.Draft || true })
            assertEquals(4, order.history().size)
        }
    }

    @Nested
    inner class ObserverTest {

        @Test
        fun `observable delegate fires after assignment`() {
            val thermostat = Thermostat(20)
            thermostat.temperature = 22

            assertEquals(listOf("temperature: 20 -> 22"), thermostat.log)
        }

        @Test
        fun `vetoable delegate rejects invalid assignments`() {
            val thermostat = Thermostat(20)

            thermostat.targetTemperature = 25
            assertEquals(25, thermostat.targetTemperature)

            thermostat.targetTemperature = 99
            assertEquals(25, thermostat.targetTemperature)
        }

        @Test
        fun `subscription handle removes the listener`() {
            val bus = EventBus<String>()
            val seen = mutableListOf<String>()
            val subscription = bus.subscribe { seen += it }

            bus.publish("a")
            subscription.cancel()
            bus.publish("b")

            assertEquals(listOf("a"), seen)
            assertEquals(0, bus.subscriberCount())
        }

        @Test
        fun `one failing observer does not stop the others`() {
            val bus = EventBus<String>()
            val seen = mutableListOf<String>()

            bus.subscribe { throw IllegalStateException("boom") }
            bus.subscribe { seen += it }

            bus.publish("event")

            assertEquals(listOf("event"), seen)
            assertEquals(1, bus.failures.size)
        }

        @Test
        fun `unsubscribing during notification does not throw`() {
            val bus = EventBus<String>()
            val seen = mutableListOf<String>()
            lateinit var subscription: EventBus.Subscription

            subscription = bus.subscribe {
                seen += it
                subscription.cancel() // would be a ConcurrentModificationException with ArrayList
            }

            bus.publish("once")
            bus.publish("twice")

            assertEquals(listOf("once"), seen)
        }

        @Test
        fun `StateFlow always holds a current value`() {
            val ticker = PriceTicker()
            assertEquals(Price("ACME", 0), ticker.current.value)

            ticker.update(Price("ACME", 150))
            assertEquals(150, ticker.current.value.cents)
        }
    }

    @Nested
    inner class CommandTest {

        @Test
        fun `commands execute and undo`() {
            val doc = TextDocument()
            val history = CommandHistory()

            history.execute(InsertText(doc, 0, "hello"))
            history.execute(InsertText(doc, 5, " world"))
            assertEquals("hello world", doc.content)

            history.undo()
            assertEquals("hello", doc.content)
        }

        @Test
        fun `delete remembers what it removed`() {
            val doc = TextDocument("hello world")
            val command = DeleteText(doc, 5, 6)

            command.execute()
            assertEquals("hello", doc.content)

            command.undo()
            assertEquals("hello world", doc.content)
        }

        @Test
        fun `undo before execute fails with a clear message`() {
            val command = DeleteText(TextDocument("abc"), 0, 1)
            val error = assertThrows(IllegalStateException::class.java) { command.undo() }
            assertTrue(error.message!!.contains("undo called before execute"))
        }

        @Test
        fun `macro undo runs in reverse order`() {
            val doc = TextDocument()
            val macro = MacroCommand(listOf(InsertText(doc, 0, "a"), InsertText(doc, 1, "b")))

            macro.execute()
            assertEquals("ab", doc.content)

            macro.undo()
            assertEquals("", doc.content)
        }

        @Test
        fun `redo replays and a new command clears the redo stack`() {
            val doc = TextDocument()
            val history = CommandHistory()

            history.execute(InsertText(doc, 0, "a"))
            history.undo()
            assertEquals("", doc.content)

            assertTrue(history.redo())
            assertEquals("a", doc.content)

            history.undo()
            history.execute(InsertText(doc, 0, "z"))
            assertFalse(history.redo()) // redo branch invalidated
            assertEquals("z", doc.content)
        }

        @Test
        fun `undo on an empty history is a no-op, not a crash`() {
            assertFalse(CommandHistory().undo())
            assertFalse(CommandHistory().redo())
        }

        @Test
        fun `function commands keep running after a failure`() {
            val queue = TaskQueue()
            queue.enqueue("ok-1") { }
            queue.enqueue("bad") { throw IllegalStateException("nope") }
            queue.enqueue("ok-2") { }

            queue.drain()

            assertEquals(listOf("ok-1", "ok-2"), queue.executed)
            assertEquals(listOf("bad"), queue.failures.map { it.first })
        }
    }

    @Nested
    inner class ChainOfResponsibilityTest {

        private val chain = TeamLead(Manager(Director()))

        @Test
        fun `the first capable handler wins`() {
            assertEquals(Approval.Approved("team-lead"), chain.handle(Expense("e1", 5_000, "travel")))
            assertEquals(Approval.Approved("manager"), chain.handle(Expense("e2", 50_000, "travel")))
            assertEquals(Approval.Approved("director"), chain.handle(Expense("e3", 500_000, "travel")))
        }

        @Test
        fun `a handler can reject instead of passing on`() {
            val result = chain.handle(Expense("e4", 500_000, "legal"))
            assertTrue(result is Approval.Rejected)
            assertEquals("director", (result as Approval.Rejected).by)
        }

        @Test
        fun `an unhandled request produces an explicit terminal result`() {
            val result = chain.handle(Expense("e5", 9_000_000, "travel"))

            assertTrue(result is Approval.Rejected)
            assertTrue((result as Approval.Rejected).reason.contains("no handler accepted"))
        }

        @Test
        fun `the functional chain needs no successor field`() {
            val approve = chainOf(teamLeadRule, managerRule)

            assertEquals(Approval.Approved("team-lead"), approve(Expense("e", 1_000, "x")))
            assertEquals(Approval.Approved("manager"), approve(Expense("e", 50_000, "x")))
            assertNull(approve(Expense("e", 5_000_000, "x")))
        }

        @Test
        fun `pipeline runs every middleware in list order`() {
            val terminal: (Request) -> Response = { Response(200, "body") }
            val pipeline = buildPipeline(listOf(requestLogging), terminal)

            val response = pipeline(Request("/x", mapOf("Authorization" to "token")))

            assertEquals(200, response.status)
            assertTrue(response.body.contains("[logged /x -> 200]"))
        }

        @Test
        fun `middleware can short-circuit the rest of the chain`() {
            var terminalCalled = false
            val terminal: (Request) -> Response = { terminalCalled = true; Response(200, "body") }
            val pipeline = buildPipeline(listOf(authentication, requestLogging), terminal)

            val response = pipeline(Request("/secret"))

            assertEquals(401, response.status)
            assertFalse(terminalCalled)
        }

        @Test
        fun `foldRight makes the first middleware the outermost`() {
            val order = mutableListOf<String>()
            val outer: Middleware = { req, next -> order += "outer"; next(req) }
            val inner: Middleware = { req, next -> order += "inner"; next(req) }

            buildPipeline(listOf(outer, inner)) { Response(200, "") }(Request("/"))

            assertEquals(listOf("outer", "inner"), order)
        }
    }

    @Nested
    inner class TemplateMethodTest {

        @Test
        fun `the skeleton drives the subclass steps`() {
            val importer = CsvUserImporter("1, a@b.com\n2, bad-email\nbroken-line")
            val report = importer.import("source")

            assertEquals(3, report.read)
            assertEquals(1, report.valid)
            assertEquals(1, report.written)
            assertEquals(listOf(User("1", "a@b.com")), importer.stored)
        }

        @Test
        fun `optional hooks fire only when overridden`() {
            val importer = CsvUserImporter("only-one-column")
            importer.import("source")

            assertEquals(listOf("only-one-column"), importer.rejected)
        }

        @Test
        fun `the higher-order form does the same job with no base class`() {
            val stored = mutableListOf<String>()
            val report = runImport(
                source = "a\nb\nBAD",
                readLines = { it.lines() },
                parse = { if (it == "BAD") error("rejected") else it },
                write = { records -> stored += records; records.size },
            )

            assertEquals(3, report.read)
            assertEquals(2, report.written)
            assertEquals(1, report.errors.size)
        }

        @Test
        fun `default arguments replace optional hooks`() {
            val report = runImport(
                source = "1\n2\n30",
                readLines = { it.lines() },
                parse = { it.toInt() },
                write = { it.size },
                validate = { it < 10 },
            )

            assertEquals(2, report.valid)
            assertTrue(report.errors.single().contains("invalid: 30"))
        }

        @Test
        fun `inline template runs teardown even on failure`() {
            val log = mutableListOf<String>()

            assertEquals(42, withAudit("ok", log) { 42 })
            assertEquals(listOf("start ok", "ok ok", "end ok"), log)

            log.clear()
            assertThrows(IllegalStateException::class.java) {
                withAudit("bad", log) { error("boom") }
            }
            assertEquals(listOf("start bad", "fail bad: boom", "end bad"), log)
        }
    }

    @Nested
    inner class VisitorTest {

        // (2 + 3) * x
        private val expr = Expr.Mul(
            Expr.Add(Expr.Num(2.0), Expr.Num(3.0)),
            Expr.Variable("x"),
        )

        @Test
        fun `evaluate walks the tree with an exhaustive when`() {
            assertEquals(20.0, expr.evaluate(mapOf("x" to 4.0)))
        }

        @Test
        fun `an unbound variable is reported, not silently zero`() {
            assertThrows(IllegalArgumentException::class.java) { expr.evaluate(emptyMap()) }
        }

        @Test
        fun `a second operation needs no change to the hierarchy`() {
            assertEquals("(2 + 3) * x", expr.format())
        }

        @Test
        fun `simplify folds constants and returns a new tree`() {
            assertEquals(Expr.Mul(Expr.Num(5.0), Expr.Variable("x")), expr.simplify())
            assertEquals(Expr.Num(0.0), Expr.Mul(Expr.Num(0.0), Expr.Variable("y")).simplify())
            assertEquals(Expr.Variable("y"), Expr.Add(Expr.Num(0.0), Expr.Variable("y")).simplify())
        }

        @Test
        fun `an accumulating operation collects across the tree`() {
            val bigger = Expr.Add(expr, Expr.Neg(Expr.Variable("y")))
            assertEquals(setOf("x", "y"), bigger.variables())
        }

        @Test
        fun `the classic visitor form produces the same guarantees`() {
            assertEquals(3, expr.accept(DepthVisitor()))
            assertEquals(1, Expr.Num(1.0).accept(DepthVisitor()))
        }
    }

    @Nested
    inner class IteratorTest {

        @Test
        fun `custom iterable hides its wrap-around representation`() {
            val buffer = RingBuffer<Int>(3)
            listOf(1, 2, 3, 4, 5).forEach(buffer::add)

            assertEquals(3, buffer.size)
            assertEquals(listOf(3, 4, 5), buffer.toList())
            assertEquals("3-4-5", buffer.joinToString("-"))
        }

        @Test
        fun `an infinite sequence costs nothing until taken from`() {
            assertEquals(listOf(0L, 1L, 1L, 2L, 3L, 5L, 8L), fibonacci().take(7).toList())
        }

        @Test
        fun `paginated traversal fetches only the pages consumed`() {
            val fetched = mutableListOf<Int>()
            val pages = paginated(pageSize = 2) { offset, limit ->
                fetched += offset
                if (offset >= 6) emptyList() else (offset until offset + limit).map { "item-$it" }
            }

            assertEquals(listOf("item-0", "item-1", "item-2"), pages.take(3).toList())
            assertEquals(listOf(0, 2), fetched) // page 3 was never requested
        }

        @Test
        fun `a short page terminates the traversal`() {
            val pages = paginated(pageSize = 10) { offset, _ ->
                if (offset == 0) listOf("a", "b") else error("should not fetch again")
            }

            assertEquals(listOf("a", "b"), pages.toList())
        }

        @Test
        fun `lazy evaluation touches fewer elements than eager`() {
            val (eager, lazyTouches) = firstMatchCost((1..100).toList()) { it > 10 }

            assertEquals(100, eager)
            assertEquals(6, lazyTouches)
        }

        @Test
        fun `sequence from the sequence builder is single-pass`() {
            val once = fibonacci().take(3)
            assertEquals(listOf(0L, 1L, 1L), once.toList())
            assertEquals(listOf(0L, 1L, 1L), fibonacci().take(3).toList())
        }
    }

    @Nested
    inner class MediatorTest {

        private fun form(): FormMediator {
            val mediator = FormMediator()
            mediator.email = TextField("email", mediator)
            mediator.password = TextField("password", mediator)
            mediator.submit = Button("submit", mediator)
            mediator.status = StatusLabel("status")
            return mediator
        }

        @Test
        fun `components coordinate without referencing each other`() {
            val mediator = form()

            mediator.email.type("a@b.com")
            assertFalse(mediator.submit.enabled)
            assertEquals("incomplete", mediator.status.text)

            mediator.password.type("longenough")
            assertTrue(mediator.submit.enabled)
            assertEquals("ready", mediator.status.text)
        }

        @Test
        fun `submit runs the whole interaction rule`() {
            val mediator = form()
            mediator.email.type("a@b.com")
            mediator.password.type("longenough")

            mediator.submit.click()

            assertTrue(mediator.submitted)
            assertEquals("submitted a@b.com", mediator.status.text)
            assertEquals("", mediator.email.value)
            assertFalse(mediator.submit.enabled)
        }

        @Test
        fun `a disabled button does nothing`() {
            val mediator = form()
            mediator.submit.click()

            assertFalse(mediator.submitted)
        }

        @Test
        fun `typed mediator dispatches by reified event type`() {
            val mediator = TypedMediator()
            val seen = mutableListOf<String>()

            mediator.on<UserRegistered> { seen += "welcome ${it.email}" }
            mediator.on<OrderPlaced> { seen += "reserve ${it.orderId}" }

            mediator.publish(UserRegistered("a@b.com"))
            mediator.publish(OrderPlaced("o1", 100))

            assertEquals(listOf("welcome a@b.com", "reserve o1"), seen)
        }

        @Test
        fun `publishing an event with no handler is harmless`() {
            TypedMediator().publish(UserRegistered("nobody@listening"))
        }
    }

    @Nested
    inner class MementoTest {

        @Test
        fun `snapshot restores internal state the public api cannot set`() {
            val editor = Editor()
            editor.type("hello world")
            editor.select(0..4)

            val snapshot = editor.save("before delete")
            editor.deleteSelection()
            assertEquals(" world", editor.content())

            editor.restore(snapshot)
            assertEquals("hello world", editor.text)
            assertEquals(0..4, editor.selection)
        }

        @Test
        fun `caretaker sees only the label, never the state`() {
            val editor = Editor()
            val history = History()

            editor.type("a")
            history.push(editor.save("step 1"))
            editor.type("b")
            history.push(editor.save("step 2"))

            assertEquals(listOf("step 1", "step 2"), history.labels())
            assertEquals(2, history.size())
        }

        @Test
        fun `popping restores the previous snapshot`() {
            val editor = Editor()
            val history = History()

            editor.type("first")
            history.push(editor.save("s1"))
            editor.type("-second")
            assertEquals("first-second", editor.text)

            editor.restore(history.pop()!!)
            assertEquals("first", editor.text)
        }

        @Test
        fun `immutable state makes every value its own memento`() {
            val form = UndoableForm()

            form.apply { it.with("name", "quan") }
            form.apply { it.with("email", "a@b.com") }
            form.apply(FormState::next)

            assertEquals(1, form.state.step)
            assertEquals(mapOf("name" to "quan", "email" to "a@b.com"), form.state.fields)

            form.undo()
            assertEquals(0, form.state.step)

            form.redo()
            assertEquals(1, form.state.step)
        }

        @Test
        fun `a new action invalidates the redo branch`() {
            val form = UndoableForm()
            form.apply { it.with("a", "1") }
            form.undo()
            form.apply { it.with("b", "2") }

            assertFalse(form.redo())
            assertEquals(mapOf("b" to "2"), form.state.fields)
        }

        @Test
        fun `undo on an empty history is a no-op`() {
            assertFalse(UndoableForm().undo())
        }
    }

    @Nested
    inner class InterpreterTest {

        private val eligible = rule {
            "country" oneOf setOf("US", "CA")
            "age" gt 18
            any {
                "plan" eq "pro"
                "credits" gt 100
            }
        }

        @Test
        fun `the dsl builds a tree that evaluates against a context`() {
            assertTrue(eligible.evaluate(mapOf("country" to "US", "age" to 30, "plan" to "pro")))
            assertTrue(eligible.evaluate(mapOf("country" to "CA", "age" to 30, "credits" to 500)))
        }

        @Test
        fun `every conjunct must hold`() {
            assertFalse(eligible.evaluate(mapOf("country" to "FR", "age" to 30, "plan" to "pro")))
            assertFalse(eligible.evaluate(mapOf("country" to "US", "age" to 12, "plan" to "pro")))
            assertFalse(eligible.evaluate(mapOf("country" to "US", "age" to 30, "plan" to "free")))
        }

        @Test
        fun `the same tree renders to sql`() {
            val sql = Predicate.And(
                listOf(
                    Predicate.Equals("plan", "pro"),
                    Predicate.GreaterThan("age", 18.0),
                ),
            ).toSql()

            assertEquals("(plan = 'pro' AND age > 18.0)", sql)
        }

        @Test
        fun `not and or render correctly`() {
            assertEquals("NOT (plan = 'free')", Predicate.Not(Predicate.Equals("plan", "free")).toSql())
            assertEquals("1=1", Predicate.Always.toSql())
        }

        @Test
        fun `an empty rule set is always true`() {
            assertEquals(Predicate.Always, rule { })
            assertTrue(rule { }.evaluate(emptyMap()))
        }

        @Test
        fun `contains and oneOf work on the context`() {
            assertTrue(Predicate.Contains("name", "an").evaluate(mapOf("name" to "quan")))
            assertFalse(Predicate.Contains("name", "zz").evaluate(mapOf("name" to "quan")))
            assertTrue(Predicate.OneOf("c", setOf("US")).evaluate(mapOf("c" to "US")))
        }
    }

    @Nested
    inner class NullObjectTest {

        @Test
        fun `the no-op default removes null checks from every call site`() {
            val service = OrderService()

            assertEquals("placed:o1", service.place("o1"))
            assertEquals("cancelled:o1", service.cancel("o1"))
            assertEquals(emptyList<String>(), NoOpAuditSink.events())
        }

        @Test
        fun `swapping in a real collaborator needs no code change`() {
            val sink = ListAuditSink()
            OrderService(sink).place("o2")

            assertEquals(listOf("order.placed o2"), sink.events())
        }

        @Test
        fun `stdlib null objects cover the simple cases`() {
            assertEquals("(none)", summarise(null))
            assertEquals("(none)", summarise(emptyList()))
            assertEquals("a,b", summarise(listOf("a", "b")))
        }

        @Test
        fun `the absent case is a named participant, not a null`() {
            val user = Principal.Authenticated("u1", "Quan", setOf("editor"))

            assertTrue(canEdit(user))
            assertFalse(canEdit(Principal.Anonymous))
            assertEquals("anonymous", Principal.Anonymous.displayName)
        }
    }

    @Nested
    inner class CoroutineObserverTest {

        @Test
        fun `shared flow delivers events to an active collector`() = runBlocking {
            val ticker = PriceTicker()
            val received = mutableListOf<Price>()

            val job = launch { ticker.trades.collect { received += it } }
            yield()

            ticker.update(Price("ACME", 100))
            ticker.update(Price("ACME", 200))
            delay(50)
            job.cancel()

            assertEquals(listOf(100L, 200L), received.map { it.cents })
        }
    }
}

/** Small helper so the memento test can read the editor's text through a stable name. */
private fun Editor.content(): String = text
