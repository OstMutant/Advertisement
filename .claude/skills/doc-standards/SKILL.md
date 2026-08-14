# Doc Standards

A checklist to consult **before writing or editing any documentation file** — `CLAUDE.md`,
`README.md`, `docs/architecture/*.md`, `docs/ai/*.md`, `.claude/commands/*.md`,
`.claude/rules.md`, or a skill's own `SKILL.md`/`references/*.md`. Same relationship to
documentation that an ADR checklist has to decisions: not optional, not a style suggestion,
consulted every time, before the content is written — not applied as cleanup afterward.

**Out of scope:** `DECISIONS.md` (append-only history — write what happened, accurately;
optimizing an ADR for brevity over completeness is the wrong trade) and `backlog/issues/*.md`
(already has its own format, defined in `.claude/commands/feature.md`). This carve-out covers ADR
*content* only — `DECISIONS.md` still follows `.claude/rules.md`'s "No issue/ticket numbers ...
in current-state documentation" rule (drop the `improvement-NNN` citation from each entry, keep
the decision and its reasoning). Also out of scope: infrastructure/tooling files (bash/batch
scripts, `docker-compose*.yml`, `.properties`) — a separate concern, covered by the sibling
`infra-doc-standards` skill.

## Why this exists

Confirmed, not hypothetical: the same dependency/SPI facts were independently stated in up to
three places per module (`CLAUDE.md`, `README.md`, `docs/architecture/02-spi-map.md`), and a
stale hard-coded module count ("9 modules") survived in at least six
files after a tenth module (`provider-profile-spring-boot-starter`) was added — including inside
`deep-review`'s own full-mode scope, which meant a review tool silently under-covered the repo.
Neither happened because anyone was careless. It happened because nothing forced a "does this
already exist somewhere?" check before writing. This skill is that check, made structural.

## The core rule

**One fact, one canonical home.** Before adding a sentence that states a fact (a dependency, an
SPI implementation, a class's existence, a count, an enumerated list), check the ownership table
below. If the fact already has a canonical home, reference it — don't restate it. If you're
creating a new kind of fact with no canonical home yet, decide where it lives *before* writing it
in two places by accident.

**Facts vs. constraints — the actual test, not just a label.** A **fact** is true regardless of
who's reading it (X depends on Y, X implements Y, there are N modules). A **constraint** is a rule
about how to change code safely (never re-derive `version` from a fresh `findById`; always guard
optional SPI wiring via `ifAvailable()`). Constraints stay local to the file where the change
would actually happen, even if they mention a fact that's canonically documented elsewhere —
don't strip a constraint down to a bare cross-reference just because it touches a documented fact.
Only facts get deduplicated; constraints are allowed to repeat *context*, just not *the fact
itself* if it's already fully stated canonically.

**A constraint sentence with zero module-specific content is actually a fact.** If the same
sentence appears word-for-word in every module's file with no module-specific clause attached
(e.g. "No Vaadin dependency. No UI code here." repeated verbatim across every starter's
`CLAUDE.md`), it isn't really "a rule local to this module" — it's a system-wide fact wearing a
constraint's phrasing. State it once at the system level (e.g. root `CLAUDE.md`'s Architecture
Guidelines) and let each local file keep only the part that's actually specific to it. The test:
delete the sentence from one file — if every other file's copy is still 100% identical, it was a
fact, not a constraint.

## Canonical ownership table

| Fact type | Canonical home | Everywhere else |
|---|---|---|
| Module → module dependencies | `docs/architecture/architecture-map.html` (Diagrams › Module Dependencies — rendered live from `pom.xml`) | State only a local one-line summary if it's load-bearing for a constraint; otherwise reference the tool |
| Port/Hook implementation mapping | `docs/architecture/architecture-map.html` (Diagrams › SPI Map — rendered live from real Java source) | Name the port/hook this file's module implements (one line — that's local and real), don't restate the graph |
| Class existence + one-line role | `README.md`'s class table (per module) | `CLAUDE.md` references it; only restates a class's role if that role *is* a constraint (e.g. "pure delegation — no business logic here") |
| ADR rationale / historical decisions | `DECISIONS.md` (per module) — subject to the worthiness gate in `.claude/commands/decision.md`; not every change belongs here | Reference generically ("see `DECISIONS.md`"), never a specific `ADR-NNN` number and never restate the reasoning inline — see `.claude/rules.md`'s "no ADR number citations outside DECISIONS.md" rule |
| Task-type → what-to-read routing | `docs/ai/context-loading.md` | Don't re-derive routing logic in `flows.md` or a command file |
| Situation → command/skill mapping | `docs/ai/flows.md` | Don't restate in individual command files |
| Backlog issue format | `.claude/commands/feature.md` | Other commands reference it, don't redefine it |
| Cross-cutting standing rules | `.claude/rules.md` | Commands/skills reference a rule by name, don't restate its content |
| Code comment rationale trimmed under the one-line-or-none rule | `DECISIONS.md` (design rationale — why a piece of logic exists or works the way it does) or a module's `README.md` (usage/how-to-run) | The comment itself keeps one line, pointing at the ADR number or README section — same "reference, don't restate" pattern as every other row |

This table itself has one canonical home: **here.** If a one-off task (a cleanup pass, a
migration prompt) needs this table, it references this file — it does not keep its own copy.

## Hard-coded references — the specific failure mode to watch for

Any number or enumerated list that's typed out instead of computed will eventually go stale the
next time reality changes (a module gets added, an ADR gets superseded, a port gets removed).
Before writing one:

1. **Prefer rewording to avoid the number entirely** — "the modules shown in the graph below"
   instead of "the 9 modules." If the true list is right there in a table/diagram, the count
   adds restatement risk and no information the reader doesn't already have.
2. **If a count is genuinely useful on its own** (not next to the list it's counting), don't
   type it from memory — check it against the actual source (`pom.xml`'s `<modules>`, the real
   file count, the real ADR count) at the moment of writing, and treat it as due for
   re-verification the next time this file is touched for any reason, not just when someone
   happens to notice it's wrong.

## Pre-write checklist

Run through this before saving any documentation edit:

- [ ] Is what I'm adding a **fact** or a **constraint**? (see test above)
- [ ] If fact: does it already have a canonical home in the ownership table? → reference it, don't restate it
- [ ] If it's a new kind of fact with no canonical home yet: have I decided where it lives, and is this the first and only place it's stated?
- [ ] If constraint: is it stated once, here, without also being restated as a "fact" somewhere else that will drift from it?
- [ ] Does this introduce a hard-coded count or list? → reword to avoid it, or verify it against the real source right now
- [ ] Is this the shortest correct statement — no restating context the reader already has from earlier in the same file or from a file this one already references?
- [ ] If this touches something `DECISIONS.md` already explains, does it reference the ADR number instead of restating the rationale?

## Where this gets invoked

Not a new trigger — hooks into what already exists:

- `.claude/commands/sync-docs.md`, `feature.md`, `new-domain.md`, `decision.md` — any command that
  touches documentation as part of its own work should run this checklist before writing, the
  same way `deep-review` is consulted before filing a finding.
- Anyone (human or Claude) hand-editing `CLAUDE.md`/`README.md`/`docs/architecture/*`/
  `docs/ai/*`/a command/a skill directly, outside any command's automated flow.

`.claude/rules.md` carries one pointer line to this file — the rule itself doesn't restate this
checklist, per the exact principle this skill exists to enforce.
