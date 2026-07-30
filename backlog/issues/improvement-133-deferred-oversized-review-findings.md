# improvement-133: deferred oversized review findings (running collection)

**Type:** improvement — meta/process issue, ongoing collection bucket, not a single fix.
**Module:** cross-cutting — whichever module each entry below actually touches.
**Priority:** 🔵 larger tech-debt — no live bug, entries need a design decision before sizing.
**When:** ongoing; triage entries opportunistically or during a backlog grooming pass, not on a fixed schedule.

## Purpose

A running catch-all for `/code-review`/`/deep-review` (or any other review) findings that are
real and worth fixing, but whose solution is too large to fit in the batch/PR that surfaced them
(a new abstraction, an architectural change, a cross-module refactor). Per `.claude/rules.md`
"Out-of-scope-but-valid findings", such findings are proposed for approval and then appended here
as a new entry — never dropped silently, and never spun into a brand-new issue file per finding.

Each entry below is independent; when one is picked up, evaluate it fresh (the surrounding code
may have moved on since it was logged), size it properly, and either fold it into its own
issue/batch or resolve it directly. Remove the entry once resolved — same lifecycle as any other
finding, just deferred.

## Entries

### 1. query-lib: compile-time enforcement of the `Fields.*` convention (from Batch H review, 2026-07-30)

`query-lib/CLAUDE.md` documents that `SqlBoundFilter.of()`'s `filterProperty` argument must always
be a typed `Fields.*` constant, never a raw string literal — but nothing enforces this at compile
time today; it is convention only, and a new call site can silently reintroduce a raw string (as
`TaxonRepository.java` did before Batch H's item 32 fix). Surfaced by the Altitude review angle
during Batch H's retroactive 8-agent review. Needs a design decision (marker interface? annotation
processor? ArchUnit rule alongside the existing `ArchitectureRulesTest`?) before it can be sized.

### 2. query-lib: `inSet`/`anyOf` null-vs-empty cardinality semantics asymmetry (from Batch H review, 2026-07-30)

`SqlCondition`'s scalar factories (`like`/`equalsTo`/`after`/`before`) return `null` only when the
value is absent; `inSet`/`anyOf` return `null` for an absent *or* empty collection — a real
semantic difference that today is only documented in a Javadoc paragraph (fixed as part of Batch H
item 25), not encoded in the type system. Surfaced by the same Altitude pass. Possible directions:
a `SqlOperator`-level cardinality marker, or splitting the factory method shapes — needs a design
decision, not a mechanical fix.
