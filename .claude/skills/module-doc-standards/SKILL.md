---
name: module-doc-standards
description: Comment conventions for Java source files, pom.xml, and Liquibase changelogs -- Javadoc on classes/methods (including the mechanically-required SPI interface convention), inline comments, pom.xml dependency comments. Sibling to module-readme-standards (module-level README.md).
allowed-tools: Read Edit Write
---

# Module Doc Standards

Comment conventions for a Java module's own source files — `.java` classes/methods, `pom.xml`, and
Liquibase changelogs (`db/*-changelog/changes/*.xml`) — as distinct from `module-readme-standards`
(a module's own `README.md`, for facts that don't fit inside any single file's own comments) and
from `infra-doc-standards`/`infra-readme-standards` (the same file-vs-README split, applied to
infrastructure/tooling files instead).

Governed by `.claude/rules.md`'s "One fact, one canonical home" rule's "Atomic unit first, then
directory-level index" principle: a class's or method's own Javadoc is the canonical home for what
that class/method does; `README.md` only covers what spans more than one file. This skill states
this domain's own mechanics; it does not restate the general principle.

## Base standard

Grounded in Oracle's own [How to Write Doc Comments for the Javadoc
Tool](https://www.oracle.com/technical-resources/articles/java/javadoc-tool.html) — the
authoritative Javadoc convention, same tier as the Google Shell Style Guide `infra-doc-standards`
is grounded in. Its own core rule: *"The first sentence of each doc comment is reused in the
indexes... this sentence should summarize the thing described, concisely but completely."* That
first-sentence-is-everything emphasis is already exactly this project's own "one line or none"
rule — we don't deviate from Oracle here, we just make the "one line" the *only* line far more
often, since Lombok/records already make most classes/methods self-documenting and Oracle's own
optional block tags (`@param`/`@return`/`@throws`) are rarely needed on top of the summary
sentence.

`.claude/rules.md` already governs every code comment, Javadoc included, project-wide:

- **"Code comments: one line or none, never an issue/ticket number"** — applies to a class's own
  top-level Javadoc exactly as it applies to an inline `//` comment. A multi-line Javadoc block
  explaining background/rationale in full is the same violation as a multi-line inline comment —
  Javadoc syntax doesn't grant an exemption. "One line" means one logical statement, not one
  physical text line — a single sentence wrapped across several `*`-prefixed physical lines for
  readability (as every real `*.spi` interface's Javadoc in this repo already does, see below)
  is still "one line" in intent; multiple *separate* sentences/points stacked in one block is what
  the rule actually forbids.
- **"A comment above a method states what that method's own body does"** — applies to a method's
  own Javadoc the same way; verified against the real method body, covers every real branch if it
  claims to summarize the whole method, never a narrative about callers/callees.
- **`@param`/`@return`/`@throws`** — used only when a parameter's valid range, nullability, or a
  thrown exception isn't already fully expressed by the type system (`@NonNull`, a narrow enum,
  `Optional<T>`) — Oracle's own tags exist for exactly the information the signature doesn't
  already carry, not as a mandatory block repeated on every method regardless.

This skill does not restate the two `.claude/rules.md` rules further — every Javadoc comment in
this codebase already answers to both, the same as any other comment. What follows is what those
rules don't already cover.

## SPI interface Javadoc — mechanically required, not optional

Every `*.spi` interface's Javadoc is a real, machine-parsed input, not just a convention: the
generator reads the doc block immediately preceding the `interface` declaration and shows it as
that interface's "purpose" in the SPI Map diagram. The exact mechanical shape (annotations/blank
lines allowed between the block and the declaration, first paragraph up to any `@`-tag) is already
declared canonically in `.claude/rules/platform-commons.md` — referenced here, not restated. This
is the one case in this skill's domain where a multi-physical-line Javadoc block is not just
allowed but expected: a real interface's one-paragraph purpose statement, wrapped for readability,
same "one logical statement" reading as above.

## Class-level Javadoc — always required

Every class carries a class-level Javadoc block, no exception — the class's own canonical home for
what it is and why it exists as its own unit, per `.claude/rules.md`'s "Atomic unit first" rule.
One line or none-of-the-optional-extra applies to its *content* (state the real, non-obvious fact
tersely — a design constraint, why this class exists as its own unit rather than folded into a
neighbor) but not to whether it exists at all: even a `record` DTO with self-explanatory field
names gets a one-line Javadoc stating what it represents, never skipped.

## `pom.xml` — comment only the non-obvious

General Maven convention (no Oracle-tier single authoritative spec exists for `pom.xml` comments,
unlike Javadoc) converges on the same principle from multiple independent sources: comment a
dependency/plugin only when its purpose "might not be immediately obvious to other developers" —
never as a blanket per-entry annotation. This project applies that as: a `<dependency>`/
`<exclusion>`/version pin gets an XML comment (`<!-- ... -->`) only when its presence or its
specific version is not self-explanatory from the artifact id alone — a deliberate version pin
below the dependency-management default, an exclusion working around a real conflict, a dependency
whose purpose isn't implied by its artifact id. One line, same as a Java comment, same ticket-number
ban. A plain, unremarkable `<dependency>` entry (the common case) gets no comment at all —
commenting every dependency "what it's for" restates what the artifact id and
`module-readme-standards`'s `Dependencies` README section already state at the module level; a
`pom.xml` comment is for *this one entry's* own non-obvious detail, not a restatement of why the
module depends on it.

**Every `pom.xml` carries a file-level header, no exception**, an XML comment block at the top:

```xml
<!-- Description: what this module is/builds, one line. -->
```

placed before the `<project>` opening tag.

## Liquibase changelogs — `remarks=` convention referenced, not restated

`db/*-changelog/changes/*.xml`'s `<column>`/`<createTable>` `remarks="..."` attribute is already
governed by root `CLAUDE.md`'s "Database Changes" guideline — the single source of truth for a
column/table's business meaning, parsed live by the Database ERD diagram. Referenced here as this
skill's third atomic-unit type, not restated. Liquibase's own separate `<comment>` element (an
optional per-changeset annotation, distinct from `remarks=`) is not currently used anywhere in this
repo's changelogs (verified directly) and is not adopted by this skill — `remarks=` on the affected
`<column>`/`<createTable>` already carries the business-meaning fact a `<comment>` would otherwise
duplicate. Adopting `<comment>` for changeset-level "why now" rationale is a real option surfaced
by researching Liquibase's own best practices, not yet decided — flag it as a future finding rather
than adopting it unilaterally here.

**Master changelog — always carries a file-level header, no exception**, an XML comment before the
`<databaseChangeLog>` opening tag:

```xml
<!-- Description: entry point Liquibase loads for this module -- includes, in order, every change
  file under changes/. -->
```

**Change files** under `changes/*.xml` also always carry a header — each one is a genuine per-file
fact (what specific schema change this file represents), fully contained within that one file,
distinct from `remarks=`'s per-column/table altitude. **Not yet designed:** the exact header shape
for a change file is still open — flag before writing one, don't invent a field structure inline.

## Where comment rationale that got trimmed actually goes

When the one-line-or-none rule trims out real rationale that was going to be the comment's
content, it doesn't just disappear — it goes to whichever of these is the fact's actual canonical
home, per the same "reference, don't restate" pattern as everywhere else:

| What got trimmed | Canonical home | What the comment keeps |
|---|---|---|
| Design rationale — why this logic exists or works this way | `DECISIONS.md` (per module), if it clears the worthiness gate in `.claude/commands/record-decision.md` | One line pointing at `.claude/nav/adr-index.md` — never a specific `ADR-NNN` |
| Usage/how-to-run context that isn't about *this* method's own logic | The module's own `README.md` (`module-readme-standards`'s domain) | One line naming the fact, not the explanation |

## Pre-write checklist

- [ ] Is this Javadoc/comment one logical statement (one line, or one wrapped paragraph), or genuinely none needed? (see "Class-level Javadoc" above)
- [ ] Does it cite an issue/ticket number? → remove it, route the rationale per the table above
- [ ] If it's a method comment, does it describe what the body actually does, verified by reading it — not a narrative about callers?
- [ ] If it's a `*.spi` interface, does its Javadoc immediately precede the `interface` declaration with no stray blank line/comment breaking that adjacency? (see "SPI interface Javadoc" above — a missing or malformed block silently blanks that interface's purpose in the SPI Map)
- [ ] If it's a `pom.xml` comment, is the dependency/version genuinely non-obvious — or is this restating what the artifact id or the module's own `README.md` `Dependencies` section already says?
- [ ] If it's a Liquibase `<column>`/`<createTable>`, does it carry a real `remarks=` attribute? (per root `CLAUDE.md`)
- [ ] Could this fact be answered by reading the module's own `README.md` instead — if so, it doesn't belong here at all (see `module-readme-standards`)

## Applying this standard — what "run the skill over a module" means

Running this skill against a Java module means: every `.java` file's class/method Javadoc, every
`pom.xml` dependency comment, and every Liquibase changelog's `remarks=` attribute in that module
is checked against the pre-write checklist above — not just new code, every pre-existing line, the
same "no presumption of compliance just because it predates this run" discipline
`infra-readme-standards` already applies to its own directories. A found violation (a multi-
paragraph rationale block, a ticket-number citation, a missing/malformed `*.spi` Javadoc) is fixed
in place: trim the comment to its one-line/one-paragraph form, and if real rationale was trimmed
out, route it per the "Where comment rationale that got trimmed actually goes" table above — never
just delete it silently.

## Where this gets invoked

Anyone (human or Claude) writing or editing a `.java` file's own Javadoc/comments, a `pom.xml`'s
dependency comments, or a Liquibase changelog's `remarks=` attribute, in any of the Java-module
directories in this repo — not a new trigger, the same standing discipline `.claude/rules.md`'s two
comment rules already require, made concrete for Javadoc, `pom.xml`, and Liquibase specifically.
