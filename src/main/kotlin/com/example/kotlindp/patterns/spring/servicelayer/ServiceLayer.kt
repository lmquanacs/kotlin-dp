package com.example.kotlindp.patterns.spring.servicelayer

import com.example.kotlindp.patterns.spring.aop.AuditLogged
import com.example.kotlindp.patterns.spring.aop.Timed
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * # The service layer: Facade + Repository + typed errors at the edge
 *
 * The layered arrangement most Spring applications use, with the three decisions that make it work
 * rather than degenerate into anaemic pass-through classes:
 *
 * 1. The **service is a Facade** (`structural/facade`) — one method per use case, owning the
 *    orchestration and the compensating actions.
 * 2. The **domain returns sealed results** (`kotlinidioms/result`), not exceptions, for expected
 *    failures.
 * 3. **DTOs are separate from domain types**, and translation happens at the edge — in both
 *    directions.
 */

// ---------------------------------------------------------------------------------------------
// Domain model — no Spring, no HTTP, no serialisation concerns.
// ---------------------------------------------------------------------------------------------

data class Product(val sku: String, val name: String, val priceCents: Long, val stock: Int)

data class PlacedOrder(val id: String, val sku: String, val quantity: Int, val totalCents: Long)

/**
 * Expected failures as a sealed hierarchy. The compiler forces the controller to map every one, so
 * adding a failure mode cannot silently become a 500.
 */
sealed class OrderFailure {
    data class UnknownProduct(val sku: String) : OrderFailure()
    data class InsufficientStock(val sku: String, val available: Int, val requested: Int) : OrderFailure()
    data class InvalidQuantity(val requested: Int) : OrderFailure()
}

sealed class OrderResult {
    data class Placed(val order: PlacedOrder) : OrderResult()
    data class Rejected(val failure: OrderFailure) : OrderResult()
}

// ---------------------------------------------------------------------------------------------
// Repository — the port. An interface, so the service is testable without infrastructure.
// ---------------------------------------------------------------------------------------------

/**
 * Declaring the repository as an interface in the domain's language is what makes the service
 * unit-testable: a test supplies a map-backed implementation and needs no database, no Spring
 * context, and no mocking framework.
 *
 * With Spring Data this interface is all you write — the implementation is a dynamic proxy
 * (`structural/proxy`).
 */
interface ProductRepository {
    fun findBySku(sku: String): Product?
    fun save(product: Product)
    fun all(): List<Product>
}

@Repository
class InMemoryProductRepository : ProductRepository {
    private val products = ConcurrentHashMap<String, Product>()

    init {
        listOf(
            Product("WIDGET", "Widget", 1_500, 10),
            Product("GADGET", "Gadget", 4_200, 0),
        ).forEach { products[it.sku] = it }
    }

    override fun findBySku(sku: String): Product? = products[sku]
    override fun save(product: Product) {
        products[product.sku] = product
    }

    override fun all(): List<Product> = products.values.sortedBy { it.sku }
}

// ---------------------------------------------------------------------------------------------
// Service — the Facade. One method per use case.
// ---------------------------------------------------------------------------------------------

/**
 * Everything about *placing an order* lives here: validation, stock check, price calculation, and
 * the write. A second caller (a scheduled job, a message consumer) gets identical behaviour by
 * calling the same method — which is the reason business logic must not live in the controller.
 *
 * In a real application this method carries `@Transactional`, and the stock decrement plus the order
 * write commit or roll back together.
 */
@Service
class OrderService(private val products: ProductRepository) {

    private val sequence = AtomicInteger()

    @Timed(name = "order.place")
    @AuditLogged(action = "place-order")
    fun place(sku: String, quantity: Int): OrderResult {
        if (quantity <= 0) return OrderResult.Rejected(OrderFailure.InvalidQuantity(quantity))

        val product = products.findBySku(sku)
            ?: return OrderResult.Rejected(OrderFailure.UnknownProduct(sku))

        if (product.stock < quantity) {
            return OrderResult.Rejected(
                OrderFailure.InsufficientStock(sku, product.stock, quantity),
            )
        }

        products.save(product.copy(stock = product.stock - quantity))

        return OrderResult.Placed(
            PlacedOrder(
                id = "ord-${sequence.incrementAndGet()}",
                sku = sku,
                quantity = quantity,
                totalCents = product.priceCents * quantity,
            ),
        )
    }

    fun catalogue(): List<Product> = products.all()
}

// ---------------------------------------------------------------------------------------------
// DTOs — the wire contract, deliberately separate from the domain.
// ---------------------------------------------------------------------------------------------

/**
 * **Why not serialise the domain type directly?** Because then the wire format and the domain model
 * are the same thing, and you can no longer rename a field, add an internal-only property, or change
 * a representation without breaking clients. The DTO is a seam that lets the two evolve apart.
 *
 * Keep the mappers as extension functions: the domain type stays unaware that a DTO exists
 * (`kotlinidioms/extensions`).
 */
data class PlaceOrderRequest(val sku: String = "", val quantity: Int = 0)

data class OrderResponse(val id: String, val sku: String, val quantity: Int, val totalCents: Long)

data class ProductResponse(val sku: String, val name: String, val priceCents: Long, val inStock: Boolean)

data class ErrorResponse(val code: String, val message: String)

private fun PlacedOrder.toResponse() = OrderResponse(id, sku, quantity, totalCents)

private fun Product.toResponse() = ProductResponse(sku, name, priceCents, inStock = stock > 0)

/**
 * Translation of domain failures to HTTP. This function is where the mapping is *decided*, and the
 * exhaustive `when` guarantees a new [OrderFailure] cannot be forgotten.
 *
 * Note the status codes are chosen per failure — 404 for an unknown product, 409 for a stock
 * conflict, 400 for a bad request. Collapsing them all to 400 throws away the information the sealed
 * hierarchy exists to preserve.
 */
private fun OrderFailure.toHttp(): Pair<HttpStatus, ErrorResponse> = when (this) {
    is OrderFailure.UnknownProduct ->
        HttpStatus.NOT_FOUND to ErrorResponse("unknown_product", "No product with sku '$sku'")

    is OrderFailure.InsufficientStock ->
        HttpStatus.CONFLICT to ErrorResponse(
            "insufficient_stock",
            "Requested $requested of '$sku' but only $available available",
        )

    is OrderFailure.InvalidQuantity ->
        HttpStatus.BAD_REQUEST to ErrorResponse("invalid_quantity", "Quantity must be positive, was $requested")
}

// ---------------------------------------------------------------------------------------------
// Controller — thin. Parse, delegate, translate.
// ---------------------------------------------------------------------------------------------

/**
 * The controller contains no business logic. Its whole job is the edge: bind the request, call one
 * service method, map the result to a status and a body.
 *
 * If a controller method has an `if` about *domain* rules, that rule is in the wrong layer.
 */
@RestController
@RequestMapping("/patterns/orders")
class OrderController(private val orders: OrderService) {

    @GetMapping("/catalogue")
    fun catalogue(): List<ProductResponse> = orders.catalogue().map { it.toResponse() }

    @PostMapping
    fun place(@RequestBody request: PlaceOrderRequest): ResponseEntity<Any> =
        when (val result = orders.place(request.sku, request.quantity)) {
            is OrderResult.Placed -> ResponseEntity.ok(result.order.toResponse())
            is OrderResult.Rejected -> {
                val (status, body) = result.failure.toHttp()
                ResponseEntity.status(status).body(body)
            }
        }

    @GetMapping("/{sku}")
    fun product(@PathVariable sku: String): ResponseEntity<Any> =
        orders.catalogue().firstOrNull { it.sku == sku }
            ?.let { ResponseEntity.ok(it.toResponse()) }
            ?: ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse("unknown_product", "No product with sku '$sku'"))
}

/**
 * `@RestControllerAdvice` is the Chain of Responsibility for *unexpected* exceptions — the ones the
 * sealed result deliberately does not model.
 *
 * The division of labour matters: expected failures are values handled by the controller's `when`;
 * genuinely exceptional ones land here and become a 500 with a safe message. Never let a raw
 * exception message reach a client — it leaks internals.
 *
 * `basePackages` scopes this advice to the patterns package so it cannot change the behaviour of
 * the rest of the application.
 */
@RestControllerAdvice(basePackages = ["com.example.kotlindp.patterns.spring.servicelayer"])
class PatternsExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun onIllegalArgument(e: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse("bad_request", e.message ?: "invalid request"))

    @ExceptionHandler(Exception::class)
    fun onUnexpected(e: Exception): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse("internal_error", "Unexpected error"))
}

/**
 * ## Layer rules that keep this from rotting
 *
 * | Layer | May depend on | Must not know about |
 * |---|---|---|
 * | Controller | service, DTOs | repositories, SQL |
 * | Service | repositories, domain | HTTP, `HttpStatus`, servlets |
 * | Repository | domain | services, DTOs |
 * | Domain | nothing | all of the above |
 *
 * The most valuable one is the middle row: **no `HttpStatus` in a service.** The moment a service
 * returns HTTP concepts, it can only be called from a controller — and the scheduled job that needs
 * the same use case has to duplicate it.
 *
 * ## The anaemic-layer smell
 *
 * If a service method is a one-line delegation to a repository, the layer is paying for itself in
 * ceremony and returning nothing. Either it owns real behaviour — validation, orchestration,
 * transactions, compensations — or the layer should not exist.
 */
