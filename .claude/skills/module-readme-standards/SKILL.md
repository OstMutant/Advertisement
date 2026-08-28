---
name: module-readme-standards
description: Conventions for a Java module's own README.md -- what it provides, its key classes, its dependencies -- for facts that don't fit inside any single file's own Javadoc/comments.
allowed-tools: Read Edit Write
---

# Module README Standards

Conventions for a Java module's own `README.md` — the seven starter/library modules
(`advertisement-spring-boot-starter`, `attachment-spring-boot-starter`, `audit-spring-boot-starter`,
`user-spring-boot-starter`, `query-lib`, `integration-tests`, `marketplace-app`). Sibling to
`module-doc-standards` (Javadoc/`pom.xml` comment conventions for the files themselves) — that
skill's "Atomic unit first, then directory-level index" principle (itself an application of
`.claude/rules.md`'s "One fact, one canonical home" rule) is the governing principle both Javadoc
and README placement decisions defer to; this skill never restates it, only applies it. Distinct
from `infra-readme-standards` (the same file-vs-README split, applied to script-group directories
instead of Java modules).

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

## Key classes

| Class | Role |
|---|---|
| `<Class>` | <one-line role — relative to other classes, not a restatement of its own Javadoc> |

## Dependencies

- `<module>` — <what it's used for>. Include genuinely load-bearing negative facts too (e.g. "no
  Maven dependency on any sibling starter" backed by a real Enforcer rule) — a negative fact is
  still a fact with a canonical home, and this is it.
```

This is the default for a Spring Boot starter module — not a rigid template forced onto every
module regardless of its real shape. A module that isn't a starter (a plain library like
`query-lib`, or the top-level application like `marketplace-app`) uses whatever section names
actually fit its real content (e.g. `Package structure`/`SQL Usage` for a library exposing a small
API surface, `Responsibilities`/`UI patterns` for the application shell) — judge each module's own
real content, don't force the three-section shape where it doesn't fit.

## `Key classes` table — role is relative, not a Javadoc restatement

The `Role` column states what a class *is for in this module's own design* — its place in the flow,
why it exists as a separate unit — not a copy of that class's own Javadoc (which, per
`module-doc-standards`, is one line or none already). Test: if the `Role` cell would read
word-for-word the same if the class had zero other classes in this module, it's restating the
class's own Javadoc, not stating a module-level fact — cut it or reword it to state the relative
role instead.

## `Dependencies` — the module level, not the entry level

States *why the module as a whole* depends on another module — not a per-dependency justification
for each individual `pom.xml` entry (that's `module-doc-standards`'s job, only for the genuinely
non-obvious ones). A `Dependencies` section entry for `platform-commons` naming the SPI/DTO types
actually used is a module-level fact; a `pom.xml` comment on that same `<dependency>` line
explaining a specific version pin is a file-level fact — both can legitimately exist without
duplicating each other, because they answer different questions.

## Independent review — verify placement, not just accuracy

After writing or regenerating a module's `README.md`, check every sentence against
`module-doc-standards`'s domain: could this fact be answered by reading one class's own Javadoc
instead? A README sentence that duplicates a class's Javadoc — even if factually correct — is
itself a finding to report, per the governing "Atomic unit first, then directory-level index"
principle.
