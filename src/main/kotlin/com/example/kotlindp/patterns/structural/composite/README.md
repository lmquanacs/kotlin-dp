# Composite

**Intent** — treat individual objects and compositions of objects uniformly by giving both the same
interface. Whenever you have a tree and want "the whole behaves like a part", this is it.

## Kotlin idiom

**Sealed hierarchy.** Leaf and branch are sealed subtypes; `when` over them is exhaustive with no
`else`, so adding a third node kind produces compile errors at every traversal instead of a silently
skipped branch at runtime.

This also fixes GoF's ugliest compromise. The book puts `add`/`remove` on the *common* interface so
leaves and branches are fully interchangeable — which forces leaves to throw on `add`. With sealed
types you keep child management on the branch only and recover it with a safe `is` check.

**Lazy traversal** via `sequence { }` + `yieldAll`: recursive walk that reads like the tree's shape,
and `firstOrNull { }` stops early instead of materialising every node.

```kotlin
fun FsNode.walk(): Sequence<FsNode> = sequence {
    yield(this@walk)
    if (this@walk is FsDirectory) children.forEach { yieldAll(it.walk()) }
}
```

## Production use case

The composable-validation example in this folder is the one you'll actually reuse: a single rule and
a group of rules are the same type, combined with `operator fun plus` so callers write
`emailRule + lengthRule`. Also: permission trees, org charts, UI component trees, nested pricing
rules, query predicate ASTs.

## Trade-offs

**Stack depth.** Recursive evaluation blows the stack on a pathologically deep tree. Real filesystems
are shallow; user-supplied JSON is not. If input depth is untrusted, cap it at parse time or use an
explicit stack.

**`data class` + recursion.** Generated `equals`/`hashCode`/`toString` recurse over the whole
subtree — convenient in tests, a performance trap on large trees, and an infinite loop if the graph
ever gains a cycle. Composite assumes a *tree*; add a parent pointer and `toString()` will
`StackOverflowError`.
