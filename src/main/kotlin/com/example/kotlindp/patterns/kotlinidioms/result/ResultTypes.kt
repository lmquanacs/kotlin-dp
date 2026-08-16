package com.example.kotlindp.patterns.kotlinidioms.result

/**
 * # Typed errors: sealed results and Either
 *
 * The design question this answers: **which failures belong in the type system, and which belong in
 * exceptions?**
 *
 * The rule that holds up in production:
 * - *Expected* failures — validation errors, "not found", a declined payment — are **values**. They
 *   are part of the function's contract and callers must handle them.
 * - *Unexpected* failures — a broken socket, a bug, out of memory — are **exceptions**. Callers
 *   cannot meaningfully handle them; let them propagate to a boundary that logs and gives up.
 *
 * Encoding expected failures as exceptions is the mistake: the signature lies, the compiler cannot
 * help, and the handling ends up scattered across `catch` blocks far from the cause.
 */

// ---------------------------------------------------------------------------------------------
// 1. A domain-specific sealed result — usually the best choice.
// ---------------------------------------------------------------------------------------------

/**
 * A sealed error hierarchy is more useful than a generic `Either<String, T>` because it names the
 * failures. Callers can `when` over them exhaustively and the compiler flags every site when a new
 * failure mode is added.
 */
sealed class PaymentError {
    data class InsufficientFunds(val shortfallCents: Long) : PaymentError()
    data class CardExpired(val expiredOn: String) : PaymentError()
    data class Declined(val code: String, val retryable: Boolean) : PaymentError()
    object RateLimited : PaymentError()
}

sealed class PaymentOutcome {
    data class Charged(val reference: String, val amountCents: Long) : PaymentOutcome()
    data class Failed(val error: PaymentError) : PaymentOutcome()
}

/**
 * The caller cannot ignore the failure and cannot forget a case — that is the whole benefit.
 *
 * Compare with `fun charge(...): String  // throws PaymentException`, where nothing in the
 * signature says a decline is possible and the retryable/non-retryable distinction is lost.
 */
fun describe(outcome: PaymentOutcome): String = when (outcome) {
    is PaymentOutcome.Charged -> "charged ${outcome.amountCents} (${outcome.reference})"
    is PaymentOutcome.Failed -> when (val e = outcome.error) {
        is PaymentError.InsufficientFunds -> "short by ${e.shortfallCents}"
        is PaymentError.CardExpired -> "card expired ${e.expiredOn}"
        is PaymentError.Declined -> if (e.retryable) "retry: ${e.code}" else "declined: ${e.code}"
        PaymentError.RateLimited -> "rate limited"
    }
}

// ---------------------------------------------------------------------------------------------
// 2. A reusable Either, for when you want combinators.
// ---------------------------------------------------------------------------------------------

/**
 * `out` on both parameters makes [Either] covariant, so `Either<Nothing, Int>` is usable wherever
 * `Either<String, Int>` is expected — that is what lets [right] and [left] have such clean types.
 */
sealed class Either<out L, out R> {
    data class Left<out L>(val value: L) : Either<L, Nothing>()
    data class Right<out R>(val value: R) : Either<Nothing, R>()

    val isRight: Boolean get() = this is Right

    /** Transform the success side; failures pass through untouched. */
    inline fun <T> map(transform: (R) -> T): Either<L, T> = when (this) {
        is Left -> this
        is Right -> Right(transform(value))
    }

    /** Chain another fallible step. This is what makes railway-style composition work. */
    inline fun <T> flatMap(transform: (R) -> Either<@UnsafeVariance L, T>): Either<L, T> = when (this) {
        is Left -> this
        is Right -> transform(value)
    }

    inline fun <T> mapLeft(transform: (L) -> T): Either<T, R> = when (this) {
        is Left -> Left(transform(value))
        is Right -> this
    }

    /** Collapse both sides into one type — the standard way to leave Either-land. */
    inline fun <T> fold(onLeft: (L) -> T, onRight: (R) -> T): T = when (this) {
        is Left -> onLeft(value)
        is Right -> onRight(value)
    }

    fun getOrNull(): R? = (this as? Right)?.value
}

fun <R> right(value: R): Either<Nothing, R> = Either.Right(value)
fun <L> left(value: L): Either<L, Nothing> = Either.Left(value)

fun <L, R> Either<L, R>.getOrElse(default: (L) -> R): R =
    fold(onLeft = default, onRight = { it })

// ---------------------------------------------------------------------------------------------
// 3. Railway-oriented composition.
// ---------------------------------------------------------------------------------------------

data class Registration(val email: String, val age: Int, val country: String)

fun validateEmail(input: Registration): Either<String, Registration> =
    if ("@" in input.email) right(input) else left("invalid email: ${input.email}")

fun validateAge(input: Registration): Either<String, Registration> =
    if (input.age >= 18) right(input) else left("must be 18 or older, was ${input.age}")

fun validateCountry(input: Registration): Either<String, Registration> =
    if (input.country in setOf("US", "CA", "GB")) right(input) else left("unsupported country: ${input.country}")

/**
 * **Fail-fast**: `flatMap` short-circuits, so the first failure wins and later validations are not
 * even run. Right for pipelines where later steps depend on earlier ones.
 */
fun registerFailFast(input: Registration): Either<String, Registration> =
    validateEmail(input)
        .flatMap(::validateAge)
        .flatMap(::validateCountry)

/**
 * **Accumulate**: run every validation and collect all errors. Right for form validation, where
 * telling a user about one problem at a time is a bad experience.
 *
 * Choosing between these two is a real design decision, not a detail — and it is one exceptions
 * cannot express at all, since the first `throw` ends the story.
 */
fun registerAccumulating(input: Registration): Either<List<String>, Registration> {
    val validations: List<(Registration) -> Either<String, Registration>> =
        listOf(::validateEmail, ::validateAge, ::validateCountry)

    val errors = validations.mapNotNull { validate ->
        validate(input).fold(onLeft = { it }, onRight = { null })
    }

    return if (errors.isEmpty()) right(input) else left(errors)
}

// ---------------------------------------------------------------------------------------------
// 4. kotlin.Result and runCatching — and their sharp edges.
// ---------------------------------------------------------------------------------------------

/**
 * `runCatching` is convenient for wrapping code that throws, but note two things people get wrong:
 *
 * 1. **It catches `Throwable`**, including `OutOfMemoryError` and — critically —
 *    `CancellationException`. Swallowing that inside a coroutine breaks structured concurrency: the
 *    coroutine keeps running after its scope has been cancelled. Inside coroutines, either rethrow
 *    `CancellationException` explicitly or avoid `runCatching`.
 * 2. **`kotlin.Result` is untyped in the failure** — it carries a `Throwable`, so you are back to
 *    inspecting exception types. For domain errors, a sealed hierarchy is better.
 *
 * `runCatching` is at its best right at an integration boundary, converting a throwing third-party
 * call into a value.
 */
fun parsePort(raw: String): Either<String, Int> =
    runCatching { raw.trim().toInt() }
        .fold(
            onSuccess = { if (it in 1..65535) right(it) else left("port out of range: $it") },
            onFailure = { left("not a number: '$raw'") },
        )

/** Rethrowing cancellation is the one-line fix when `runCatching` is used near coroutines. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

/**
 * ## Choosing
 *
 * | Situation | Use |
 * |---|---|
 * | Domain failures with distinct handling | sealed result hierarchy |
 * | Generic pipeline needing map/flatMap | `Either` |
 * | Wrapping a throwing library call | `runCatching` |
 * | Genuinely exceptional / unrecoverable | exceptions |
 * | Absence with no explanation needed | `T?` |
 *
 * Do not convert everything to `Either`. `T?` with `?:` is simpler when the only information is
 * "nothing there", and exceptions remain the right tool for programmer errors.
 *
 * ## In Spring
 *
 * Keep typed results inside the domain and translate at the edge: a controller maps
 * `PaymentError.InsufficientFunds` to 402, `RateLimited` to 429, and so on. Do not let
 * `Either` leak into HTTP responses, and do not let `HttpStatus` leak into the domain.
 */
