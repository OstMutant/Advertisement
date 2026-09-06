---
name: module-readme-standards
description: Conventions for a Java module's own README.md -- what it provides, its data flow (input -> processing -> output/persistence), its dependencies -- for facts that don't fit inside any single file's own Javadoc/comments.
allowed-tools: Read Edit Write
---

# Module README Standards

Conventions for a Java module's own `README.md` — every module listed in the root `pom.xml`'s
`<modules>` gets one, derived dynamically from that list rather than a fixed roster named here, so
a newly added module is in scope automatically and this file never needs editing just because the
reactor grew. A module is exempt only if listed under "Exempt modules" below — never assumed exempt
by omission. Sibling to `module-doc-standards` (Javadoc/`pom.xml` comment conventions for the files
themselves) — that skill's "Atomic unit first, then directory-level index" principle (itself an
application of `.claude/rules.md`'s "One fact, one canonical home" rule) is the governing principle
both Javadoc and README placement decisions defer to; this skill never restates it, only applies
it. Distinct from `infra-readme-standards` (the same file-vs-README split, applied to script-group
directories instead of Java modules).

## Exempt modules

None currently. Add a module here, with a one-line reason, only when its owner explicitly decides
it doesn't need a `README.md` — never remove a module's README requirement unilaterally just
because writing one is more work.

## README — what belongs here, and only here

A module's `README.md` covers only what cannot be fully answered by reading one file's own
Javadoc/comments alone — a class's role *relative to other classes in the module*, the module's
overall purpose, what it depends on and why, what depends on it. What any single class or method
does, on its own, is never README's job, at any level of brevity — not even a one-sentence gloss
"for navigation." If a fact is already covered by a class's own Javadoc, it must never appear in
`README.md` in any form — not restated, not reworded, not summarized. Before writing any README
sentence, check: could this be answered by reading one file's own Javadoc alone? If yes, drop the
sentence and, if the fact isn't actually documented there yet, add it to that file's own Javadoc
instead (`module-doc-standards`'s domain), not here.

## The default shape — three sections, adapted per module's real kind

The starter modules already converged, independently, on the same three-section shape — this
skill formalizes that existing convention rather than inventing a new one:

```markdown
# <module-name>

<one-paragraph module purpose>

## What it provides

- <capability, one bullet per real feature>
- **SPI implementation:** `<Port>` (who calls it)

## Data flow

<prose (or a Mermaid flowchart, for a module whose real flow genuinely branches): what comes in
(a Port/Hook call, a REST request, a DTO), which classes touch it and in what order, what goes out
(a return value, a DB write, a call into another module) — the sequence, never any one class's own
Javadoc content.>

## Dependencies

- `<module>` — <what it's used for, by module name only — never a class living inside that other
  module>. Include genuinely load-bearing negative facts too (e.g. "no Maven dependency on any
  sibling starter" backed by a real Enforcer rule) — a negative fact is still a fact with a
  canonical home, and this is it.
```

This is the default for a Spring Boot starter module — not a rigid template forced onto every
module regardless of its real shape. A module that isn't a starter (a plain library like
`query-lib`, or the top-level application like `marketplace-app`) uses whatever section names
actually fit its real content (e.g. `Package structure`/`SQL Usage` for a library exposing a small
API surface, `Responsibilities`/`UI patterns` for the application shell) — judge each module's own
real content, don't force the three-section shape where it doesn't fit.

## `Data flow` — sequence between classes, never a single class's own Javadoc

Grounded in the same principle `infra-readme-standards`'s own `## Flow` section already applies to
a script-group directory's file-to-file sequence — a Java module's classes are this domain's
equivalent of that domain's files. A one-row-per-class table (the earlier "Key classes" shape this
section replaces) structurally invites restating each class's own Javadoc one row at a time,
exactly the "one fact, two homes" duplication `.claude/rules.md` forbids — confirmed as a real,
recurring drift by direct review, not a theoretical concern. Describing the *sequence* instead
(what comes in, which class does what to it next, what goes out) is inherently a multi-class,
module-level fact — the relationship between classes, not any one class's own behavior — so it
structurally can't degrade into a Javadoc restatement the way a per-class table can.

State: the real entry point (a Port/Hook method, a REST endpoint, a DTO a caller passes in), each
class that touches it in the real order execution happens, and the real terminus (a return value,
a row written, a call into another module/table). Prose is enough for a module whose real flow is
a straight line (most starters: entry point → one service → one repository → a table) — reach for
a Mermaid `flowchart` only when a module's own real flow genuinely branches (a conditional that
determines which class/path gets touched next), following `infra-readme-standards`'s own guidance
for when a diagram earns its cost over prose. Verify the real order by reading the actual call
chain, the same "traced for its real behavior, not assumed" discipline `infra-readme-standards`
already requires of a Flow diagram — never guessed from class names or file layout.

## `Dependencies` — the module level, not the entry level, and never another module's own class names

States *why the module as a whole* depends on another module, and *which module* (not which class
inside it) depends on this one — not a per-dependency justification for each individual `pom.xml`
entry (that's `module-doc-standards`'s job, only for the genuinely non-obvious ones), and not a
class living inside some other module (that other module's own internal structure is its own
README's fact to state, if it even needs to — this module's `README.md` only needs to say which
*module* is on the other end of the relationship). A `Dependencies` section entry for
`platform-commons` naming the SPI/DTO types actually used is a module-level fact, since those types
are this module's own real compile-time contract; a `pom.xml` comment on that same `<dependency>`
line explaining a specific version pin is a file-level fact — both can legitimately exist without
duplicating each other, because they answer different questions.

## Aggregated cross-file facts get verified by search, never by memory of what was already read

Any README content that aggregates a fact across many files (the `Data flow` section, a
`Dependencies` list, anything collected "from everything in this module") must be verified
complete by an actual search (`Grep`/`Glob` or equivalent) across the real files at the moment of
writing it — never assembled from memory of files already read earlier in the same session. A
class read once, then not re-checked, is exactly how a real class/dependency gets silently dropped
from an aggregate table. This applies regardless of how recently or how thoroughly those files
were read.

## After deleting a class, sweep the module's README for remaining references to it

Whenever a class is deleted, that same module's own `README.md` gets checked for a leftover
reference to it (a step in the `Data flow` section, a mention in the module purpose paragraph) before the change
is considered complete — a stale reference to a class that no longer exists is exactly the kind of
drift this skill exists to prevent, and deleting the class itself is the one moment that reference
is guaranteed to go stale.

## ⛔ Applying this standard — what "run the skill over a module" means

Running this skill against a Java module means: every class/method already has a complete, accurate
Javadoc per `module-doc-standards` (run that skill first if it hasn't been), then regenerate that
module's `README.md` from the now-finished Javadoc (a rewrite, not a piecemeal edit). Class-then-
README order and the no-duplication rule follow `module-doc-standards`'s "⛔ Atomic unit first, then
README" rule. Everything in scope is brought into full compliance with the standard described in
this document — the three-section shape, the `Data flow` section, everything — not a partial pass.
There is no "is this file already compliant" tracking inside this skill itself; that's a per-run
decision, made when the skill is actually invoked, not a state this document maintains.

This applies to every pre-existing line in `README.md`, not only newly-noticed gaps — pre-existing
content earns no presumption of compliance just because it predates this run; every line, old or
new, is re-derived from the finished Javadoc and re-tested against the "README — what belongs here,
and only here" rule before being kept.

"In scope" means the invoked module only — a run scoped to one module (e.g. "run the skill over
`taxon-spring-boot-starter`") fixes only that module's own `README.md` content, never a stale
reference, gap, or unrelated finding noticed in some other module while working, even one
immediately adjacent (a sibling starter, `platform-commons`). Report it instead of fixing it
inline — propose appending it to this project's standing deferred-findings bucket (see
`.claude/rules.md`'s "Out-of-scope-but-valid findings" section for where and how) rather than only
stating it in chat, so the finding survives past this conversation instead of being lost once the
session ends. Scope creep beyond the invoked module is not "thoroughness," it's an unbounded task
turning into a different, unapproved one.

## Independent review — verify placement, not just accuracy

After applying this standard to a module, spawn a fresh agent (no prior context of this
conversation) to independently re-read the actual class Javadoc and the resulting `README.md`
content, and report back whether the written documentation actually covers everything the module
really provides — every real step in the data flow, every real dependency, every load-bearing
negative fact. Catches the class of gap a same-context self-review misses (a real class left out
of the `Data flow` section) precisely because the reviewer starts cold, from the real files, not
from what was already believed to be true while writing.

Also verify placement, not just accuracy: for every fact in `README.md`, confirm it could not be
answered by reading one class's own Javadoc alone. A README sentence that duplicates a class's
Javadoc — even if factually correct — is itself a finding to report, per the governing "Atomic unit
first, then directory-level index" principle.
