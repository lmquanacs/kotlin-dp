# Extension functions and properties

Add members to a type you don't own — no inheritance, no wrapper.

## The one fact that explains everything

**An extension compiles to a static function taking the receiver as its first parameter.** It is not
added to the class and it is not virtual. Every surprise below follows from that.

## Distinctive capabilities

**Nullable receivers.** The receiver type may be nullable, so the extension is callable on `null`
*without* `?.`. That's how `orEmpty()` and `isNullOrBlank()` work, and it's impossible with a regular
method.

```kotlin
fun String?.orPlaceholder(placeholder: String = "—"): String =
    if (this.isNullOrBlank()) placeholder else this
```

**Domain vocabulary on primitives.** `20.dollars`, `99.cents`, `date.isWeekend`,
`start daysUntil end`.

**Extension properties** can have a getter but **no backing field** — there's nowhere to put one, so
they must be computed.

## Three things that surprise people

**(a) Static dispatch.** An extension that looks like an override is not one:

```kotlin
fun Shape.describe()  = "shape"
fun Circle.describe() = "circle"
val s: Shape = Circle()
s.describe()          // "shape" — resolved from the DECLARED type
```

Need polymorphism? You need a real `open` member.

**(b) Members always win.** An extension with the same signature as an existing method is silently
never called — and worse, a library adding a member in a later version can silently change your
call's behaviour. Name extensions distinctly.

**(c) No access to `private` members.** They're static functions outside the class. This is a
feature: an extension can never break an invariant it can't reach.

## Member extensions — the DSL mechanism

An extension declared *inside* a class has two receivers and is only in scope within that class.
That's what lets a DSL expose verbs only inside its own block, and why `"a" shouldBe "b"` is
available in a test DSL and nowhere else.

## Guidelines

**Do**
- Add domain vocabulary to stdlib and third-party types.
- Write null-safe helpers with nullable receivers.
- Keep `toX()` conversions as extensions, so the source type stays unaware of the target — this is
  how a domain model stays free of DTO knowledge.

**Don't**
- Use an extension when the behaviour belongs to a class you own. Put it in the class.
- Expect polymorphism.
- Declare hundreds of extensions on `Any` or `String` at package level — they pollute completion for
  the whole project. Scope them to a package that must be imported.
