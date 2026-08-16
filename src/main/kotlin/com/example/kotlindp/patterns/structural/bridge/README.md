# Bridge

**Intent** — separate an abstraction from its implementation so the two vary independently.

## The problem it solves

**Combinatorial subclassing.** 3 report types × 3 output formats = 9 classes by inheritance
(`PdfSalesReport`, `CsvSalesReport`, …), and a fourth format adds 3 more. Bridge composes instead:
3 + 3 = 6 classes, and a new format is *one* class that works with every report immediately.

The bridge is literally one field — the abstraction *holds* an implementor instead of inheriting
from one.

## Bridge vs Adapter

Identical structure, opposite timing. An **adapter** is written after the fact to reconcile two APIs
you didn't design together. A **bridge** is designed up front because you know both dimensions will
vary.

## Kotlin idiom

When the implementor is a single operation, the interface collapses to a function type and there's
no hierarchy left to maintain:

```kotlin
class Report(private val render: (String, List<String>, List<Row>) -> String)
```

That's usually right for a two-implementation bridge. Keep the named interface once the implementor
grows state or more than one member (here, `contentType` alongside `render`).

## Production use case

Report/document generation across formats; a notification abstraction (`Alert`, `Digest`,
`Receipt`) over transports (email, SMS, Slack); persistence abstractions over storage backends;
`slf4j` itself — one logging API bridged to several backends.

## Trade-offs

Bridge is **speculative generality if only one dimension actually varies**. Two axes that each have
exactly one implementation today is a class with a parameter, not a bridge. Wait for the second
format before introducing it — the refactor is cheap, the premature abstraction isn't.
