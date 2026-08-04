# improvement-137: `doc-standards` skill + repo-wide documentation dedup cleanup

**Type:** process/documentation-infrastructure — a standing checklist (new skill) plus a one-pass
cleanup applying it across every documentation surface in the repo.
**Module:** cross-cutting — new `.claude/skills/doc-standards/SKILL.md`; touches 13× `CLAUDE.md`,
15× `README.md` (repo-wide count, includes nested/tool READMEs — the actual dedup pass in Step 4
scopes to the module-level pairs, not generated/frontend-bundle READMEs), `docs/architecture/01-08-*.md`,
`docs/ai/*.md`, `.claude/commands/*.md`, `.claude/skills/deep-review/**`, `.claude/rules.md`,
`scripts/ci/entrypoint.sh`, `scripts/ai/`.
**Priority:** 🔴 top — highest priority per explicit user request (2026-08-04), ranked above
`improvement-135` and ahead of `improvement-136` (the latter is explicitly paused pending further
user discussion, so this executes first).
**When:** Ready to execute — user approved the plan structure and one amendment (see "Amendment"
below); write-up only so far, implementation not yet started. Run before the (not-yet-written)
`architecture-control-plane-FINAL.md` plan the user has referenced but will supply later — this
cleanup makes every doc that future plan reads shorter and drift-free first, but nothing here
depends on that plan existing.

## Relationship to improvement-135 — deliberately not merged

Discussed directly with the user (2026-08-04): `improvement-135` and this issue both touch
documentation-infrastructure, and specifically both touch `docs/ai/*.md`, but are different in
shape and were kept as separate issues rather than merged:

- **135 item 3** (does `context-loading.md` empirically reduce reads) is an intentionally
  long-running, evidence-accumulation process — `## Operational notes` blocks recorded on every
  future completed issue, aggregated later via `/sync-docs --full-audit`. It cannot be
  "implemented and verified in one pass" by design; the issue stays open until real data
  accumulates.
- **This issue** is a bounded, one-pass cleanup: create the skill, run the dedup passes, verify,
  close. Merging a since-forever-open measurement issue with a closeable cleanup issue would blur
  both issues' completion tracking, and violates this backlog's own "one batch = one pass"
  convention (`BACKLOG.md`'s batching rationale).
- **135 item 5's governing rule** — "do not add new `docs/ai/*` content until items 2-4 show the
  layer earns its cost" — is relevant to Step 4 Pass 2/4 below, which do touch `docs/ai/README.md`
  and audit `context-loading.md`/`flows.md` for duplication. Explicitly recorded here: fixing a
  stale hard-coded fact (e.g. "9 modules") or removing a genuinely duplicated sentence is
  **corrective maintenance**, not the kind of speculative expansion (new files, new metadata
  fields, new schema) item 5 was written to block. This issue does not add any new `docs/ai/*`
  file or field — noted explicitly so this isn't a silent, unexamined exception to that rule.
- Cross-referenced in both issues' "Related" sections instead of merging.

## Amendment agreed with the user before write-up (added to Step 4)

The original task text's Step 4 hard-coded-reference fix (Pass 2) relies solely on a **prose
discipline** — the new skill's pre-write checklist, consulted by whoever edits a doc next. This
project has an established, stronger precedent for exactly this class of problem
(`improvement-135` item 1: `scripts/ai/check-adr-index-freshness.sh` /
`check-flows-completeness.sh`, both automated, CI-wired, build-breaking checks — not prose) and a
standing principle ("the whole reason this project prefers compiler/build enforcement over prose",
quoted directly in `ArchitectureRulesTest`'s own doc comment and echoed in 135's item 1 rationale).

**Added to this task's scope:** a new `scripts/ai/check-hardcoded-counts.sh` (read-only, same
shape as the other two `scripts/ai/check-*.sh` scripts) that greps for numbers adjacent to
"module(s)" (and any other enumerated-count pattern the Pass 2 sweep finds recurring) across
`docs/`, `CLAUDE.md` files, and `README.md` files, and verifies each against its real source
(`pom.xml`'s `<modules>` count, actual file counts) — exits 1 on mismatch. Wired into
`scripts/ci/entrypoint.sh`'s existing `docs` stage alongside the other two checks. The skill's
pre-write checklist remains the primary, first-line discipline (matches the "rules.md rule is the
primary defense" shape 135 already established for the ADR-index case); this script is the
backstop, not a replacement.

---

## Execution approach — agent delegation strategy (agreed with user 2026-08-04)

The discovery legwork in Step 4's dedup passes (~40+ files across module docs, README.md,
architecture docs, AI docs, commands, skills, rules) is delegated to parallel `Explore` agents
instead of the main thread reading every file directly. `Explore` is read-only and cheaper per
token than a general-purpose agent, and routing the raw multi-file reads through it keeps that
volume out of the main conversation — only synthesized findings (fact + duplicate locations,
file:line) come back. Planned split, one `Explore` agent per cluster:

1. 13× module `CLAUDE.md`
2. 15× `README.md`
3. `docs/architecture/*.md` + `docs/ai/*.md`
4. `.claude/commands/*.md` (12 files) + `.claude/skills/*` + `.claude/rules.md`

Synthesis and all actual edits (deciding canonical homes, writing the skill file, rewriting
duplicated sentences into pointers, implementing `check-hardcoded-counts.sh`) happen directly in
the main thread, not delegated further — judgment calls about what's canonical are not handed to
a subagent; `Explore`'s role is discovery only.

**Operational notes requirement:** completion of this issue must include a `## Operational notes`
block (per `.claude/rules.md`) with real token costs split by purpose — `token_cost_research` for
the `Explore`-cluster discovery calls specifically — not a synthetic estimate. This is the first
issue executed with this Explore-cluster-discovery split, so its numbers are also a first data
point on whether the delegation approach is worth repeating on future large doc/code-audit tasks.

---

## Step 1 — Create the `doc-standards` skill

Create `.claude/skills/doc-standards/SKILL.md` with exactly this content:

```markdown
# Doc Standards

A checklist to consult **before writing or editing any documentation file** — `CLAUDE.md`,
`README.md`, `docs/architecture/*.md`, `docs/ai/*.md`, `.claude/commands/*.md`,
`.claude/rules.md`, or a skill's own `SKILL.md`/`references/*.md`. Same relationship to
documentation that an ADR checklist has to decisions: not optional, not a style suggestion,
consulted every time, before the content is written — not applied as cleanup afterward.

**Out of scope:** `DECISIONS.md` (append-only history — write what happened, accurately;
optimizing an ADR for brevity over completeness is the wrong trade) and `backlog/issues/*.md`
(already has its own format, defined in `.claude/commands/feature.md`).

## Why this exists

Confirmed, not hypothetical: the same dependency/SPI facts were independently stated in up to
four places per module (`CLAUDE.md`, `README.md`, `docs/architecture/01-module-dependencies.md`,
`02-spi-map.md`), and a stale hard-coded module count ("9 modules") survived in at least six
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

## Canonical ownership table

| Fact type | Canonical home | Everywhere else |
|---|---|---|
| Module → module dependencies | `docs/architecture/01-module-dependencies.md` | State only a local one-line summary if it's load-bearing for a constraint; otherwise reference the file |
| Port/Hook implementation mapping | `docs/architecture/02-spi-map.md` | Name the port/hook this file's module implements (one line — that's local and real), don't restate the graph |
| Class existence + one-line role | `README.md`'s class table (per module) | `CLAUDE.md` references it; only restates a class's role if that role *is* a constraint (e.g. "pure delegation — no business logic here") |
| ADR rationale / historical decisions | `DECISIONS.md` (per module) | Reference by ADR number, never restate the reasoning inline — this is already done correctly in most existing files; keep doing it |
| Task-type → what-to-read routing | `docs/ai/context-loading.md` | Don't re-derive routing logic in `flows.md` or a command file |
| Situation → command/skill mapping | `docs/ai/flows.md` | Don't restate in individual command files |
| Backlog issue format | `.claude/commands/feature.md` | Other commands reference it, don't redefine it |
| Cross-cutting standing rules | `.claude/rules.md` | Commands/skills reference a rule by name, don't restate its content |

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
```

---

## Step 2 — Add the pointer line to `.claude/rules.md`

Read the current `.claude/rules.md`, and add this line under an appropriate existing section (or
a new short one if none fits — keep it to one line, no surrounding explanation, since the
explanation already lives in the skill):

> Before writing or editing any documentation file, consult `.claude/skills/doc-standards/SKILL.md`.

Do not add anything else to `rules.md` — the checklist itself stays only in the skill, per the
skill's own core rule.

---

## Step 3 — Confirm the deep-review coverage gap, file it separately

**Already confirmed during planning (2026-08-04), not just theorized:** read
`.claude/skills/deep-review/references/full-mode.md`'s Scope section directly — its module list
(lines 20-22) is exactly `marketplace-app, platform-commons, query-lib,
advertisement-spring-boot-starter, attachment-spring-boot-starter, audit-spring-boot-starter,
taxon-spring-boot-starter, user-spring-boot-starter, integration-tests` — 9 modules,
`provider-profile-spring-boot-starter` is absent. Confirmed a real functional gap: every full-mode
run since `provider-profile-spring-boot-starter` was added has silently skipped it.

At execution time: file `backlog/issues/improvement-<next-free-number>-deep-review-missing-provider-profile-module.md`
in the standard format (re-check the next free number at that time — this plan reserved 137 for
itself), then fix `full-mode.md`'s module list as part of Step 4 Pass 4 below. This is small,
self-contained, and safe to land independently of the rest of this issue if useful.

---

## Step 4 — Full dedup + hard-reference cleanup, using the skill from Step 1

Everything below applies the skill's FACT-vs-CONSTRAINT test and ownership table — don't
re-derive either, load `.claude/skills/doc-standards/SKILL.md` and use it directly.

**Scope — every documentation surface, not just module docs:**

| Surface | Files | What to check |
|---|---|---|
| Module docs | 13× `CLAUDE.md`, 15× `README.md` | Cross-duplication with each other and with `docs/architecture/01-08-*.md` |
| Root docs | root `CLAUDE.md`, root `README.md` | Same, plus: does root restate what a module doc already says instead of pointing at it? |
| Architecture docs | `docs/architecture/01-08-*.md` | Duplication *between these eight files themselves* |
| AI docs | `docs/ai/*.md` | Duplication between `context-loading.md` and `flows.md` |
| Commands | `.claude/commands/*.md` (12 files) | Same procedure/format restated across multiple commands |
| Skills | `.claude/skills/deep-review/{SKILL.md,references/*.md}` | Confirm the `SKILL.md`/mode-reference split hasn't drifted — don't flatten it, it's intentional |
| Rules | `.claude/rules.md` | Internal repetition, or restating something a command/module doc already states more specifically |

**Out of scope:** `DECISIONS.md` (append-only history) and `backlog/` — unchanged.

**Confirmed, not hypothetical, starting evidence:**
- Per-module: the same dependency/SPI facts are independently stated in up to four places
  (`CLAUDE.md`, `README.md`, `01-module-dependencies.md`, `02-spi-map.md`) — **spot-verified
  directly during planning** in `advertisement-spring-boot-starter`: the
  `TaxonPort`/`UserPort`/`AttachmentPort` dependency fact appears near-verbatim in all four files.
  Assume the pattern repeats across the other modules, but re-verify each one in Pass 1 below
  rather than batch-editing from this single confirmed case.
- Repo-wide: "9 modules" / "all 9 modules" is hard-coded — **confirmed via direct grep during
  planning** in `docs/architecture/01-module-dependencies.md` (line 5) and `docs/architecture/
  README.md` (lines 10, 127); `docs/ai/README.md` needs its own grep pass at execution time (not
  yet checked). `01-module-dependencies.md`'s own graph/table also needs checking for whether it
  omits `provider-profile-spring-boot-starter` entirely (not yet confirmed either way — check in
  Pass 1/2, don't assume).

**Procedure:**

1. **Pass 1 — module docs**, pilot on `advertisement-spring-boot-starter` first, then the rest:
   a. Read the module's `CLAUDE.md`, `README.md`, and its rows/subgraph in
      `01-module-dependencies.md` + `02-spi-map.md`.
   b. Classify every sentence in `CLAUDE.md`/`README.md` via the skill's FACT-vs-CONSTRAINT test.
   c. For each FACT duplicated across 2+ files: keep it at its canonical home (skill's ownership
      table), replace the others with a short pointer sentence — never a silent deletion.
2. **Pass 2 — hard-coded reference sweep, repo-wide.** Grep for numbers next to "module(s)" and
   any other repeated enumerated list (ADR counts, port/hook counts, test counts). Fix each per
   the skill's "reword to avoid the number" preference; only keep a number if it's checked against
   the real source at the moment of writing. Implement `scripts/ai/check-hardcoded-counts.sh` (see
   "Amendment" above) as part of this pass, not as an afterthought.
3. **Pass 3 — commands.** Compare `.claude/commands/*.md` pairwise for restated procedure/format
   definitions (ADR format, backlog issue format, doc-sync mapping); consolidate to one
   definition + references.
4. **Pass 4 — skills + rules.** Confirm the `deep-review` `SKILL.md`/reference split is still
   clean; fix `full-mode.md`'s module list per Step 3 above; check `.claude/rules.md` for internal
   repetition or restating something stated more specifically elsewhere.
5. **After every file touched:** run `check-adr-index-freshness.sh`, `check-flows-completeness.sh`,
   the new `check-hardcoded-counts.sh`, and a full build (`ArchitectureRulesTest` and friends) to
   confirm nothing broke.
6. **Summarize per file/module touched**, matching `deep-review`'s own output convention: what
   moved where, what was purely restated and removed, what stayed as a constraint, every
   hard-coded reference fixed, and the outcome of the Step 3 coverage-gap check.

---

## Testing strategy

- No unit/integration/Playwright impact expected (documentation-only change) — still run
  `bash scripts/unit-tests.sh` once at the end as a compile/sanity check, since Step 4 Pass 4
  touches `.claude/rules.md`/skill files that other automation reads, and Step 3's companion issue
  touches `full-mode.md` (a skill reference file, not executable, but worth a sanity pass).
- `scripts/ai/check-adr-index-freshness.sh`, `check-flows-completeness.sh`, and the new
  `check-hardcoded-counts.sh` must all pass after every file touched (per Pass 5 above), not just
  once at the end.

## Out of scope

- `DECISIONS.md` files (append-only) and `backlog/` (own format) — unchanged, per the skill's own
  stated exclusions.
- Redesigning `docs/ai/adr-index.md`'s schema or content model — already out of scope per
  `improvement-135`, unchanged here.
- Any code change — this issue is documentation-only.

## Related

- `improvement-135` — deliberately not merged (see "Relationship to improvement-135" above);
  cross-referenced instead. Shares the `docs/ai/*.md` file surface and the general
  drift-prevention philosophy, but different shape of work (long-running measurement vs.
  one-pass cleanup).
- `improvement-136` — paused pending further discussion; this issue runs first since it's ready
  and 136 isn't.
- `scripts/ai/DECISIONS.md` ADR-001, `scripts/ai/check-adr-index-freshness.sh`,
  `check-flows-completeness.sh` — the precedent this issue's new `check-hardcoded-counts.sh`
  mirrors.
- `improvement-138` — "Architecture Control Plane", filed 2026-08-04, sequenced immediately after
  this issue. Its migration table (§14) reads several of the same files this issue deduplicates
  (`docs/architecture/01-08-*.md`, `docs/ai/*.md`) — running this issue first means 138 builds
  against already-cleaned docs instead of ones about to be edited out from under it.

## Execution outcome (completed 2026-08-04)

Executed via `/autopilot`, per the "Execution approach" section above. Step 1 (skill file), Step 2
(rules.md pointer), Step 3 (improvement-139 filed + `full-mode.md` fixed) all done as planned.
Step 4: discovery via 4 parallel `Explore` agents as planned; synthesis found the flagship
`advertisement-spring-boot-starter` CLAUDE.md/README.md case (cited in the issue's own "starting
evidence") was actually already compliant on closer inspection — its CLAUDE.md content is
constraint-flavored (safe-change rules), not restated facts, so no edit was needed there ("checked,
no issue," per `deep-review`'s own convention of reporting negatives). Real fixes applied: module-
count corrections across `docs/architecture/*.md` (9→10, plus a fuller regeneration of
`01-module-dependencies.md`'s graph/table to add the missing `provider-profile-spring-boot-starter`
node/edges), a `RoleChecker`/`OwnershipChecker` dedup in `marketplace-app`, a missing
`UserEditableFields` README entry, `.claude/commands/sync-docs.md`/`deep-review/SKILL.md`
ADR/issue-format references pointed at their canonical commands instead of restated, and
`scripts/ai/check-hardcoded-counts.sh` (Pass 2, wired into `scripts/ci/entrypoint.sh`, `ADR-002` in
`scripts/ai/DECISIONS.md`). Found and disclosed, but deliberately **not** fixed in this pass (too
large for a dedup/count-fix scope, cross-referenced instead): `docs/architecture/02-spi-map.md`'s
SPI diagram/tables still name a removed hook (`AttachmentMediaChangeHook`) and a renamed one
(`AttachmentAuditHook`→`AttachmentAuditPort`), and are missing the `UserPort` 4-way split and
`ProviderProfilePort` — flagged with a top-of-file staleness note pointing at `improvement-138`
rather than a full regeneration; similarly `03-bounded-contexts.md`/`04-database-erd.md`'s deeper
content (domain list, ERD) predates `taxon`/`provider-profile` and needs the same kind of
regeneration `improvement-138` Track A is scoped to do.

Ran `/code-review --fix` (per `.claude/commands/autopilot.md` step 3, high effort, 8 finder angles
+ 8 verifiers) against the session's own diff before the final test/verification pass — all 8
surviving candidates were CONFIRMED and fixed, including two real regressions this session itself
introduced (a dead "What it provides" section reference in `marketplace-app/README.md`; an
incorrect "compile" scope claim in `01-module-dependencies.md` where `taxon`/`provider-profile` are
actually `runtime`-scoped in `marketplace-app/pom.xml`), a stale "7.1/10" architecture score left
uncorrected in two spots while a third was hedged (synced all three to the real current 7.7/10 from
`08-scorecard.md`, which was already up to date), a code-comment rule violation in the new script
(fixed to one-line comments), a new duplication the fix itself introduced (`02-spi-map.md`'s
staleness note restated in `docs/architecture/README.md` instead of referenced), and two more stale
counts outside the original plan's scope (`scripts/CLAUDE.md`'s "7 non-integration-tests reactor
modules" → 9; this issue's own "Module:" line and Step 4 scope table claimed 11×`CLAUDE.md`/
11×`README.md`/11 commands, actually 13/15/12).

`bash scripts/unit-tests.sh` run once at the end (documentation/script-only change, no Java source
touched by this issue): 108/108 tests passed (29 query-lib + 79 marketplace-app, including
`ArchitectureRulesTest`), `BUILD SUCCESS`.

## Operational notes
- token_cost_review: ~1,483,000 (8 code-review finder agents + 8 verifier agents)
- token_cost_research: ~233,800 (4 Explore discovery agents, Step 4 Pass 1)
- token_cost_verification: n/a (unit-tests run directly via script, not an Agent-tool call)
- context_loading_task_type: closest existing row is "Architecture audit / repo-wide review" — no
  row in `context-loading.md` exactly covers a documentation-dedup task
- context_loading_consulted: no
- context_loading_matched: n/a (not consulted before starting)
- flows_situation: repo-wide documentation dedup + new skill creation
- flows_chosen: bespoke Explore-agent-cluster discovery (this issue's own "Execution approach"
  section) + `/code-review --fix`, not a pre-existing `flows.md` row
- flows_matched: no — `flows.md`'s closest row (`/sync-docs --full-audit`, for periodic whole-repo
  doc sanity checks) was not used; this task's shape (new skill + one-time dedup with human-in-the-
  loop discovery) didn't fit that row, and no row covers it explicitly
