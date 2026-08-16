# Facade

**Intent** — one simple entry point over a complicated subsystem.

A facade doesn't *hide* the subsystem; advanced callers can still reach the parts. It removes the
obligation to understand them for the common case.

## Kotlin idiom

Nothing exotic — a class with constructor-injected collaborators. Two Kotlin-specific moves:

- A facade over **stateless** collaborators can be a file of top-level functions. Kotlin has no
  "everything in a class" rule, so don't invent a `CheckoutUtils` holder.
- Return a **sealed result** rather than throwing. The subsystem's varied exception types get
  translated into a closed set of outcomes the caller is forced to handle.

## The real value

It isn't fewer imports at the call site — it's that the **compensating action** has exactly one
home. Reserving stock and then failing to charge must release the stock. That rule is easy to forget
when every caller orchestrates the subsystem itself, and impossible to forget when there's one
orchestrator.

## Production use case

Your Spring `@Service` layer *is* this pattern. Constructor-inject the collaborators, put
`@Transactional` on the facade method, and the hand-written compensations get replaced by a rollback
wherever the resources are transactional.

Also: an SDK's public surface over its internals; a "checkout"/"onboard user"/"publish" operation
that touches five services.

## Trade-offs

**Facades accrete.** One with 40 methods and 12 injected collaborators is a god object wearing a
pattern's name. Keep one facade per *use-case family*, and split when the injected set stops looking
cohesive.

Don't let a facade become the only path. If it hides the subsystem so completely that unusual cases
can't be served, callers will fork it rather than extend it.
