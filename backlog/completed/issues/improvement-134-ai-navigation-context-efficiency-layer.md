# improvement-134: Additive AI navigation and context-efficiency layer (spec-only audit + proposal)

**Type:** improvement — process/AI-tooling meta, proposal stage (spec-only; no `docs/ai/*.md` file
or `DECISIONS.md` metadata change is implemented until the resulting spec is separately approved).
**Module:** cross-cutting — audits every `CLAUDE.md`, every `DECISIONS.md`, `docs/architecture/*.md`,
`backlog/`, `.claude/skills/deep-review/`, `.claude/commands/*.md`; proposes (does not create) new
`docs/ai/*.md` files and an ADR metadata convention. No production code, tests, or build/CI config
touched by this issue.
**Priority:** 🟡 high — Top priority, ranked ahead of improvement-124 per explicit user request.
Flag: improvement-124 was previously the sole top-priority item with a detailed execution plan
already written; this reorders it to run after improvement-134. Confirm if that reordering isn't
what was intended.
**When:** independent, no blockers. Spec-only — implementing anything the resulting spec proposes
is a separate, later-approved issue, not part of this one's completion criteria.

## Problem

The repo's AI-instruction surface has grown large: 9 module `CLAUDE.md` files (all unconditionally
force-loaded into every session via root `CLAUDE.md`'s `@import` — verified directly, see below),
9 `DECISIONS.md` files with a growing number of ADR entries, and 130+ files under `backlog/`. There
is no established review of whether this surface is well-targeted for token cost vs. output
quality — new instructions keep getting added, nothing has audited whether the existing ones are
easy for Claude to navigate cheaply. Goal (user's framing): review what already exists so AI flows
in this repo produce higher-quality results while consuming fewer tokens — not simply add more
instructions on top of what's already there.

## Scope — SPEC ONLY, additive only

The deliverable is a specification, written back into *this* issue file, proposing narrowly-scoped
additions:
1. ADR discovery metadata for `DECISIONS.md` entries.
2. A lightweight `docs/ai/` navigation index (candidates: `adr-index.md`, `database-ownership.md`;
   `module-index.md` is explicitly NOT pre-approved — see step 2 below).
3. A task-classification / context-loading reference.

Nothing here replaces or restructures the existing `CLAUDE.md`/`DECISIONS.md`/backlog/deep-review
system. Treat existing mechanisms as authoritative unless the audit itself demonstrates a real,
evidenced gap.

**Protected — do not modify while researching or drafting the spec:**
- root `CLAUDE.md`, every module `CLAUDE.md`
- every `DECISIONS.md`
- `ArchitectureRulesTest`
- `.claude/skills/deep-review/`
- `.claude/commands/*.md`
- `docs/architecture/*.md`
- source code, tests, build/CI configuration

The only permitted write while this issue is being worked is this issue file itself
(`backlog/issues/improvement-134-ai-navigation-context-efficiency-layer.md`), updated in place with
audit findings and the final spec — following the precedent set by
`improvement-129-marketplace-feed-modernization.md` (a large research-grounded proposal stored
directly as its own issue file; this repo has no separate `backlog/proposals/` location, so don't
invent one).

## Already-verified findings (carried in from initial triage — re-verify before relying on them further)

- **Confirmed:** root `CLAUDE.md` `@`-imports all 9 module `CLAUDE.md` files unconditionally
  (lines 45, 47, 49, 51, 53, 55, 92, 94, 98, 115, 116 — grep `@[A-Za-z0-9_./-]+CLAUDE\.md`). Every
  one of them is force-loaded into every session's context regardless of task type. This means a
  `docs/ai/module-index.md` **cannot** reduce module-`CLAUDE.md` token cost — anything it adds is
  pure duplication unless it serves a use case the forced `@import` doesn't already cover (e.g. an
  isolated subagent spawned via `Agent`/`Explore`/worktree that may not inherit the same `@import`
  chain — verify this specifically before deciding whether to keep this file at all).
- **Confirmed:** `DECISIONS.md` files (9 of them) are referenced by *path only* in root `CLAUDE.md`
  (not `@`-imported) — not auto-loaded. An ADR discoverability gap is real; this is the
  strongest-value piece of the three proposed mechanisms.
- `improvement-129` is the established precedent for "large proposal stored as its own issue file,
  marked proposal-stage" — reuse that shape.

## What to do

1. **Audit** — delegate to a research subagent (Explore or general-purpose, "very thorough"
   breadth) to protect this session's context window; the primary agent must independently verify
   the subagent's key claims against real files before relying on them, never relay uncritically.
   - Read every module `CLAUDE.md`, every `DECISIONS.md`, `docs/architecture/03-bounded-contexts.md`
     + `06-coupling-analysis.md`, `ArchitectureRulesTest`, `backlog/BACKLOG.md` +
     `BACKLOG-ARCHIVE.md` + `backlog/issues/` + `backlog/completed/issues/`,
     `.claude/skills/deep-review/`, `.claude/commands/*.md`.
   - Get real counts (Java file count, ADR/decision-entry count across all 9 `DECISIONS.md`,
     backlog issue count) — do not reuse unverified figures without re-checking them.
   - Read `improvement-131-priority-emoji-rubric-doc-practice-mismatch.md` in full before designing
     any ADR metadata format — its `**Priority:**` tier rubric (🟢/🟡/🔵/⚪, backfilled onto all 27
     pre-existing issues) is the closest existing precedent for a structured-metadata-on-a-markdown
     convention in this repo and must be reconciled with, not duplicated by, any new ADR metadata
     format.
   - Check `improvement-133` (deferred-findings bucket) and `improvement-130` (backlog folder
     rename) for overlap.
   - Check `improvement-066`/`improvement-067` (documentation-drift precedents) before designing
     drift-detection for any manual/subjective metadata field.
   - Check `improvement-120` (the `advertisement`↔`user_information` FK decoupling) — any proposed
     `docs/ai/database-ownership.md` must explicitly name the risk that a stale copy could silently
     re-imply the old FK-coupled ownership model deliberately removed by that decision.

2. **Propose, field-by-field, evidence-grounded, marking anything unverifiable `UNKNOWN`:**
   - ADR discovery metadata + a generated `docs/ai/adr-index.md` — highest-confidence piece per the
     verified findings above. For every proposed metadata field, state whether it's mechanically
     derivable (from what, by what deterministic rule) or manual/subjective (if so: drift-detection
     mechanism, who/what corrects it, what happens when it disagrees with the decision text).
   - `docs/ai/database-ownership.md` — concise table only, explicit `improvement-120` risk section,
     source of truth, generation/staleness strategy.
   - Task-classification / context-loading reference — given the verified finding above (module
     `CLAUDE.md` is unconditionally loaded), scope this to what it can actually influence: which
     `DECISIONS.md`/backlog files to open for a given task type, not which `CLAUDE.md` to load
     (that's not a choice Claude makes). Cross-reference `.claude/skills/deep-review/`'s existing
     diff-first/full-mode context strategy rather than redefining it.
   - `docs/ai/module-index.md` — **do not propose this as a committed file by default.** Given the
     forced-`@import` finding above, only include it in the final recommendation if the audit
     surfaces a concrete, real use case not already covered — otherwise the spec's recommendation
     for this piece should be `DO NOT IMPLEMENT`, stated explicitly with the evidence.

3. **Rule-writing constraint (explicit user instruction — applies to every rule/convention this
   spec proposes):** any rule or convention text proposed for `docs/ai/`, ADR metadata, or the
   task-classification reference must be written independent of specific issue numbers (durable,
   not "see improvement-131") — but its *justification*, recorded in this issue file's own audit
   trail, must cite the concrete decisions/incidents it was actually derived from (the same
   separation `DECISIONS.md` ADRs and this repo's `CLAUDE.md` files already keep between "the rule"
   and "the incident that produced it"). Do not write a rule that only makes sense with an issue
   number attached to it.

4. **Write the full spec back into this issue file**, structured per the standard 13-section
   outline (Executive Summary → Recommendation) unless that structure genuinely conflicts with this
   repo's actual issue-writing convention (`Type/Module/Priority/When/Problem/Suggested
   fix/Related`) — in which case adapt the outline to fit that convention and note the mismatch
   briefly, rather than running two conflicting formats side by side.

5. Stop. The resulting spec requires a separate, explicit human approval before any `docs/ai/*.md`
   file or `DECISIONS.md` metadata change is actually implemented — this issue's own completion
   criterion is "spec written and reviewed," not "spec implemented."

## Suggested fix

Not applicable in the usual sense — this issue *is* the audit/spec task, there is no code fix.
Execution shape: spawned two research subagents in parallel to gather the audit facts in step 1
above (facts only, no drafting); the primary session then independently verified the key claims and
wrote the spec below. Where the two subagents' findings touched the same ground (improvement-120),
both were cross-checked against each other before being relied on.

---

## Audit findings and specification (completed 2026-07-31)

### 1. Executive summary

**Already exists and is sound:** module `CLAUDE.md` files (force-loaded via `@import`, see
"Already-verified findings"), `DECISIONS.md`'s `## ADR-NNN: Title` + `**Status:**` + prose
Context/Decision/Consequences format (already 100% consistent across 173 of 174 real entries — the
one exception is a pointer/redirect stub, not a real decision), `ArchitectureRulesTest` (8 ArchUnit
rules, each citing its source-of-truth doc section), `.claude/commands/sync-docs.md` (already has a
changed-file→doc-target mapping table AND, in `--full-audit` mode, a claim-by-claim ADR staleness
classifier: VALID/STALE/SUPERSEDED/DONE-GOAL-NOT-MARKED), and `.claude/skills/deep-review/`
(diff-mode loads only the diff + targeted context; full-mode spawns one subagent per module, each
given that module's own `CLAUDE.md`/`DECISIONS.md` — i.e. per-module conditional `DECISIONS.md`
loading already exists, programmatically, inside deep-review).

**Real gap found:** `DECISIONS.md` files (real count: **11**, not the 9 root `CLAUDE.md` names —
see finding 2.1) are not navigable without opening/grepping full files; there is no index. This is
the one piece of the original 3-part proposal with a genuine, evidenced gap.

**Explicitly not proposed (evidence-driven `DO NOT IMPLEMENT`):** `docs/ai/module-index.md`
(`docs/architecture/03-bounded-contexts.md` already provides a per-domain Contract +
Cross-Domain-Dependencies breakdown, and module `CLAUDE.md` is force-loaded regardless) and
`docs/ai/database-ownership.md` as originally scoped (`docs/architecture/04-database-erd.md`
already provides an exact table→`Module:`/`Changelog:` mapping for all 8 tables, in more detail
than proposed).

**What is proposed:** (a) a generated `docs/ai/adr-index.md`, built from fields that are *already*
100% present in every `DECISIONS.md` entry today — no new authoring burden on the 174 existing
entries; (b) a narrow task-classification reference limited to what it can actually influence
(which `DECISIONS.md`/backlog files to open — not `CLAUDE.md`, which is forced, and not
deep-review's own internal loading, which is already self-sufficient).

### 2. Audit findings, by area

**2.1 — `DECISIONS.md` reality vs. `CLAUDE.md`'s claim.** Root `CLAUDE.md` names 9 `DECISIONS.md`
files. On disk there are **11** — `scripts/ci/DECISIONS.md` and `scripts/sonar/DECISIONS.md` exist
and are omitted from `CLAUDE.md`'s list. Any generator for an ADR index must scan the filesystem
for `**/DECISIONS.md`, never trust the hardcoded 9-file list in `CLAUDE.md` — that list has already
drifted once.

**2.2 — Format is already 100% consistent.** Every file uses `## ADR-NNN: Title` + a `**Status:**`
line immediately below (vocabulary: `Accepted`, `Accepted (done YYYY-MM-DD)`,
`Superseded YYYY-MM-DD (issue-ref) — reason`). Per-file counts (heading-based): attachment 14,
audit 25, integration-tests 11, marketplace-app 69 (~41% of the total — by far the largest file),
platform-commons 25, playwright 3, query-lib 7, scripts 8 (one entry is a pointer stub with no
`Status:` line, not a real decision), taxon 5, scripts/ci 7, scripts/sonar 0 (uses plain date
headers, no `ADR-N` numbering at all — a real outlier). **Total: ~174 real entries.** This exact
format (`## ADR-NNN: Title` / `**Status:**` / `Context`/`Decision`/`Consequences`) is already
codified as authoritative in `.claude/commands/sync-docs.md:174-182` — any new metadata proposal
extends this, never redefines it.

**2.3 — Four representative entries** (module-specific, cross-module, UI/framework,
infra/architectural-exception), confirming `Module`/`Status`/`Title` are mechanically derivable for
every one of them with zero ambiguity:
- `taxon-spring-boot-starter/DECISIONS.md:30` — ADR-002, `DefaultTaxonPort is a coordination layer,
  not pure delegation`. Module: mechanically = the file it lives in (`taxon`). Status: `Accepted`
  (mechanical, read from the `**Status:**` line). Scope: module-specific (deviates from the
  platform-commons "pure delegation" rule, deliberately, for one starter only).
- `platform-commons/DECISIONS.md:72` — ADR-003, `SPI naming convention — Port and Hook suffixes`.
  Cross-module by nature (defines the naming rule every starter follows) — the file-path-derived
  "Module: platform-commons" is mechanically correct but doesn't capture "applies everywhere"; this
  is the one place a manual `Scope: cross-module` tag would add real value over pure file-path
  derivation, see 4.1 below.
- `marketplace-app/DECISIONS.md:2251` — ADR-055, `ConfirmActionDialog converted to a plain class`.
  UI/framework decision, Vaadin-specific, module = marketplace-app (mechanical).
- `marketplace-app/DECISIONS.md:1044` — ADR-034, `No raw cross-starter SQL joins`. Architectural
  exception/infra rule that actually concerns 2 other starters (`advertisement`, `taxon`) even
  though it's filed under `marketplace-app` — same cross-module tagging gap as ADR-003 above.

**2.4 — `docs/architecture/` overlap, confirmed substantial for 2 of 3 proposed files:**
- `03-bounded-contexts.md` (327 lines) already has, per domain: Ownership, Entity, Key Services,
  **Contract** (which `*Port`/`*Hook` it defines), **Cross-Domain Dependencies** — this is
  substantially the same content `docs/ai/module-index.md` would provide. **Live staleness found
  in this exact file**, independent of this issue: lines 209-211 and 284-285 still describe
  `AttachmentMediaChangeHook` as an active SPI ("currently has no implementation"); it was removed
  entirely (improvement-102). A concrete, already-happened instance of the drift class this whole
  proposal is meant to guard against — reported separately, not fixed here (protected path).
- `06-coupling-analysis.md` (247 lines) is a violation-tracking + metrics doc (cyclic-dependency
  checks, starter-to-starter import checks, module-size table, God-package table), not primarily a
  module/SPI map — lower overlap with the original proposal than 03. **Also stale:** line 98-122
  ("Hidden Coupling: Advertisement → User Tight Coupling", severity MEDIUM) and line 239
  (`improvement-011` still listed `~ PARTIAL`/open) both need re-verification — see 2.6.
- `04-database-erd.md` (408 lines) already gives an exact `Module:`/`Changelog:` pair per table for
  all 8 tables — a complete table→owning-module mapping already exists. `docs/ai/database-ownership.md`
  as originally scoped would be pure duplication.

**2.5 — `ArchitectureRulesTest`** (105 lines, 8 ArchUnit rules) mechanically enforces
import/dependency-direction and naming-convention rules (UI-must-not-call-repositories,
starters-must-not-depend-on-Vaadin, `*Port`/`*Hook` must live in `platform-commons`,
no-class-level-`@PreAuthorize`, no-`Optional`-parameters, no `configuration` packages,
`*PortImpl`/`*HookImpl` purity), each `.because(...)` citing the exact `CLAUDE.md`/`rules.md`
section it encodes. No relationship to `DECISIONS.md` content or ADR freshness — useful precedent
only in the general sense that "a prose rule got automated" (`improvement-030`), not as a reusable
mechanism for this proposal.

**2.6 — `improvement-120` cross-check, confirmed by direct read of the resolved issue
(`backlog/completed/issues/improvement-120-advertisement-user-hard-fk-coupling.md`) and its
`BACKLOG-ARCHIVE.md` entry (line 1282, dated 2026-07-25):** three FK constraints
(`fk_advertisement_created_by` RESTRICT, `fk_advertisement_modified_by`/`fk_advertisement_deleted_by`
SET NULL) were removed from `db/advertisement-changelog/changes/01-advertisement-schema.xml`,
replaced by two bulk `AdvertisementPort` methods (`findOwnerIds()`, `clearActorReferences()`) called
from `UserService.cleanup()`, verified against 3 UI scenarios with no regression. **This directly
contradicts what `04-database-erd.md:157-160` and `06-coupling-analysis.md:98-122` currently say**
(both still document the FK/coupling as present). This is not hypothetical — it is the exact
`improvement-120`-shaped drift risk the original request asked to guard against, except it has
already happened, in currently-checked-in docs, independent of anything this issue proposes.

**2.7 — `improvement-066`/`improvement-067`, corrected framing.** These are **not**
documentation-drift incidents as originally assumed when this issue was filed — both are cases
where a fix/pattern established in one code path was never propagated to a structurally similar
sibling path (`UserSettingsRepository`'s version check vs. `ADR-029`'s scheme;
`TaxonTranslationRepository`'s unbounded `IN` clause vs. `improvement-054`'s established fix),
discovered via direct code review, not a doc-vs-reality mismatch. **`improvement-120` (2.6 above) is
the actually-applicable precedent for documentation drift**, not 066/067 — the original framing in
this issue's "What to do" step 1 was wrong and is corrected here.

**2.8 — `improvement-130`, confirmed NO OVERLAP.** Pure directory rename
(`backlog/issues/` → a name that doesn't imply bugs-only), mechanical `git mv` + grep-replace across
58 cross-referencing files. Does not touch, restructure, or index `DECISIONS.md`/backlog content —
different object entirely from what improvement-134 proposes.

**2.9 — `.claude/skills/deep-review/`, context-loading strategy already exists and should not be
redefined:** diff-mode loads only the diff + targeted context per specific check (never the whole
repo); full-mode spawns one subagent per module, each given that module's own `CLAUDE.md`/
`DECISIONS.md` — i.e. deep-review already implements exactly the kind of per-module conditional
`DECISIONS.md` loading a task-classification reference might otherwise try to invent. `docs/architecture/*.md`
is referenced by neither mode — an observation, not something this issue's protected-path scope can
act on.

**2.10 — `.claude/commands/sync-docs.md` is the strongest existing mechanism found, and changes the
metadata/drift-detection design materially.** It already has (a) a changed-file→doc-target mapping
table for normal (diff-scoped) runs, and (b) in `--full-audit` mode, a claim-by-claim ADR
verification pass that classifies every ADR as `VALID`/`STALE`/`SUPERSEDED`/`DONE-GOAL-NOT-MARKED`.
**Drift detection for ADR metadata should reuse this existing classifier, run manually per its
established cadence (root `CLAUDE.md`: "`/sync-docs` ... run manually ... not triggered
automatically") — not invent new tooling.** This directly answers the original request's demand for
a field-by-field drift-detection mechanism: the mechanism already exists, it just isn't currently
pointed at a generated index.

### 3. Recommendations per proposed artifact

**3.1 `docs/ai/adr-index.md` + ADR metadata — RECOMMEND: IMPLEMENT WITH CHANGES.**
The original Step-2 format (`Status | Scope | Modules | Tags`, prepended per entry) is more than
what's needed. `Status` (already present, 100% mechanical, zero authoring cost) and `Module`
(mechanical: the `DECISIONS.md` file path the entry lives in) and `Title` (mechanical: the heading
text itself) together already give a useful 3-column index with **zero new content added to the
174 existing entries** — the index is purely *generated*, not authored. `Tags`/free-form `Scope`
beyond file-path are the one genuinely manual/subjective field (2.3 shows 2 of 4 sampled entries —
ADR-003, ADR-034 — are cross-module despite living in one file, which pure file-path derivation
gets wrong): **defer tags entirely for the initial version.** Ship the 3-column mechanical index
first; only add a manual field later if real usage friction (a specific search that the 3-column
index can't answer) demonstrates the need — don't pre-build for a hypothetical.
Drift detection: reuse `sync-docs --full-audit`'s existing STALE/SUPERSEDED classifier (2.10) — do
not build new tooling.
Generation: a small script parsing `## ADR-NNN: Title` + the next `**Status:**` line, across every
`**/DECISIONS.md` found by filesystem scan (never the hardcoded 9-file list, per 2.1) — output one
line per entry (`ADR-NNN | <module> | <status> | <title>`) into `docs/ai/adr-index.md`. Committed,
regenerated on demand (a manual script run, mirroring `sync-docs`'s own "run manually" convention) —
no new CI infrastructure.

**3.2 `docs/ai/database-ownership.md` — RECOMMEND: DO NOT IMPLEMENT as originally scoped.**
`docs/architecture/04-database-erd.md` already provides the exact table→module→changelog mapping
this file would duplicate, in more detail. The real, actionable finding here isn't a missing index —
it's that `04-database-erd.md` (and `06-coupling-analysis.md`) are themselves currently stale
regarding `improvement-120` (2.6) — a documentation *correction*, not a new navigation file. Outside
this issue's protected-path scope; needs separate approval (see "Immediate findings" below).

**3.3 `docs/ai/module-index.md` — RECOMMEND: DO NOT IMPLEMENT.**
`docs/architecture/03-bounded-contexts.md` already provides per-domain Contract +
Cross-Domain-Dependencies, and module `CLAUDE.md` is unconditionally force-loaded via `@import`
regardless of task type (verified, "Already-verified findings" above) — a redundant index cannot
reduce token cost for Claude, only for a human skimming outside a Claude Code session, which is a
different problem than the one this issue was scoped to solve. Narrow open question, marked
`UNKNOWN`, not worth a committed file for: whether an isolated subagent/worktree (`Agent` tool,
`isolation: "worktree"`) inherits the same root-`CLAUDE.md` `@import` chain as the main session —
not verified in this pass.

**3.4a `docs/ai/README.md` — RECOMMEND: IMPLEMENT, as the layer's own entry point.**
One overview doc tying the layer together, added per explicit user request: for each file in
`docs/ai/` (today: `adr-index.md`, `context-loading.md`) state **what** it is, **why** it exists
(one line, grounded in the audit findings above — e.g. "DECISIONS.md isn't auto-loaded, finding
2.1/2.2"), **where** it fits relative to what already exists (e.g. "complements
`docs/architecture/03-bounded-contexts.md`, does not duplicate it"), **when** it should be
consulted (points at the task-classification table in `context-loading.md` rather than repeating
it), and **how** it's kept fresh (points at the mandatory hooks in 3.5 and the `sync-docs`
cadence). Written issue-number-independent per this issue's own "rule-writing constraint" (section
"What to do", item 3) — it explains the mechanism, not "why we built it in improvement-134".
This is the file a future session should read first on landing in `docs/ai/`, before either index.

**3.4 Task-classification / context-loading reference — RECOMMEND: IMPLEMENT, narrowly scoped.**
Given 2.9 (deep-review already self-sufficient) and 2.10 (sync-docs already has its own
changed-file→doc-target table), this reference's only real job is for *everyday* work that goes
through neither skill (plain feature/bug-fix tasks, `/autopilot` runs): a short table of task type →
which `DECISIONS.md` file(s)/backlog issues/architecture doc(s) are worth opening, explicitly
excluding `CLAUDE.md` loading (not a real choice) and not redefining deep-review's or sync-docs's
own internal logic — link to them instead of duplicating.

**3.4b `docs/ai/flows.md` — RECOMMEND: IMPLEMENT, added per explicit user request, broader than
3.4.** Where `context-loading.md` (3.4) answers "which files should I read for this task," this
file answers the wider question "which existing command/skill/process handles this situation at
all" — a scenario→mechanism map covering the operational surface already scattered across
`CLAUDE.md`'s "Slash commands available" list, `.claude/commands/*.md`, and `.claude/skills/`:
new feature request → `/feature`; architectural decision made → `/decision`; code changed enough
that docs might be stale → `/sync-docs` (diff mode day-to-day, `--full-audit` periodically);
correctness/reuse review needed → `/code-review` (diff) vs `.claude/skills/deep-review` (diff or
full-repo SOLID/DRY sweep) — state the actual distinction between these two, confirmed during this
audit's finding 2.9/2.10, not assumed; a scoped, already-approved multi-step task → `/autopilot`;
routine test/deploy/build cycles → `/run-all-tests`, `/ci`, `/build`, `/deploy-dev`, `/playwright`.
Each entry: the trigger situation, the mechanism, and one line on why that mechanism and not
another (e.g. why `/code-review` and not `/deep-review` for a routine diff). Does not invent new
process — every entry points at a command/skill that already exists; this is a navigation map over
the existing operational surface, same "additive, not a redesign" constraint as the rest of this
issue.

### 3.5 Mandatory integration hooks — where the index must be wired in, not just created

A generated index nobody is required to consult or update drifts exactly like the 4 items in
"Immediate findings" already have — creating `docs/ai/adr-index.md` alone doesn't prevent that.
Wiring it into the existing command/skill surface is not optional polish, it's part of Phase 1/2's
own deliverable, not a follow-up:

- **`.claude/commands/decision.md`** — add a step: after recording a new ADR, update
  `docs/ai/adr-index.md`'s corresponding line in the same change. Mandatory, not best-effort.
- **`.claude/commands/feature.md`** — add a step: before drafting a new issue, check
  `docs/ai/adr-index.md` (if present) for an already-decided overlapping ADR, same spirit as this
  issue's own overlap-analysis discipline. Mandatory.
- **`.claude/commands/sync-docs.md`** — add `docs/ai/adr-index.md` as a target in its existing
  changed-file→doc-target mapping table (any `DECISIONS.md` diff → regenerate the index entry) and
  include it in the `--full-audit` freshness pass alongside its existing ADR classifier. This is the
  single most load-bearing hook — reuses a mechanism that already exists and already runs on a
  known cadence, rather than inventing a second, competing freshness check.
- **`.claude/rules.md`** — one short, durable rule (issue-number-independent, per the "rule-writing
  constraint" in this issue's own scope): consult `docs/ai/adr-index.md` before filing a new ADR or
  backlog issue, when it exists. Same enforcement shape as this file's existing
  "re-read before every action" rules — mandatory, not a suggestion.
- **root `CLAUDE.md`** — add a plain discoverability pointer near the `DECISIONS.md` file list
  (prose reference, e.g. `→ ADR discovery index: docs/ai/adr-index.md` — **not** an `@import`,
  which would force-load it every session and reproduce the exact token-cost problem this proposal
  rejected for `module-index.md`).
- **`.claude/skills/deep-review/SKILL.md`** — explicitly optional, not mandatory: deep-review's
  full-mode already reads each module's own `DECISIONS.md` directly (finding 2.9); forcing it to
  also consult the generated index would add a step with no clear payoff over what it already does.
  Leave unwired unless a concrete gap shows up in practice.

All five mandatory hooks above are protected paths for this issue (same list as "Scope") — they get
edited only during Phase 1/2 execution, once approved, not before.

### 4. Token / context efficiency analysis

| Artifact | Current problem | Token impact | Maintenance | Expected value |
|---|---|---|---|---|
| `adr-index.md` | 174 entries across 11 files, no index; finding "did we decide X" means opening/grepping full files | Real reduction — one generated ~180-line file replaces opening full `DECISIONS.md` files on speculative searches | Regenerate on demand (script), drift-checked via existing `sync-docs --full-audit` | **High** |
| `database-ownership.md` | none — `04-database-erd.md` already answers this | None (would add tokens with no discovery benefit — pure duplication) | N/A, not building it | **Low / negative** |
| `module-index.md` | none — `03-bounded-contexts.md` + forced `@import` already answer this | None (module `CLAUDE.md` cost is already paid every turn regardless) | N/A, not building it | **Low / negative** |
| Task-classification reference | no guidance for non-deep-review/non-sync-docs tasks on which `DECISIONS.md`/backlog files matter | Moderate — prevents speculative opening of irrelevant `DECISIONS.md` files during ordinary feature work | Manual, low-churn (task categories are stable) | **Medium** |

### 5. Implementation sequence (proposed, not executed — separate approval required per scope)

- **Phase 1:** Write the `docs/ai/adr-index.md` generator script + run it once, committed output.
- **Phase 2:** Task-classification reference (`docs/ai/context-loading.md` or similar name),
  cross-linking deep-review/sync-docs rather than duplicating them.
- **Phase 3:** run `/sync-docs` (its own tool, not manual edits) against the findings logged in
  "Immediate findings" below — items 1/2/4 are exactly the kind of drift `/sync-docs` already
  detects and corrects (`docs/architecture/*.md`, `CLAUDE.md`'s module list); item 3
  (`improvement-011` re-verification in `06-coupling-analysis.md`) should be confirmed as part of
  the same pass. Do not hand-edit these protected paths outside of `/sync-docs` — per root
  `CLAUDE.md`, `/sync-docs` is run manually, never automatically, so this phase only fires once
  this issue is approved and picked up for execution, not before.
- **Not phased — explicitly rejected:** `module-index.md`, `database-ownership.md` as originally
  scoped, and per-entry `Tags`/manual metadata on the 174 existing ADRs.

### 6. Risks

- Generated `adr-index.md` going stale relative to new ADRs added after generation — mitigated by
  tying regeneration to the same manual cadence `sync-docs` already uses (root `CLAUDE.md`: "run
  manually ... not triggered automatically"), not a false promise of always-fresh auto-generation.
- False confidence if a future contributor adds a manual `Tags`/`Scope` field later without also
  wiring it into the `sync-docs --full-audit` classifier — any future manual field must extend that
  existing check, per 2.10, not sit outside it.
- The 2 confirmed live doc-staleness bugs (2.4, 2.6) are themselves evidence that "additive
  navigation layer" proposals are not free of the very risk they're meant to reduce if nothing reads
  and validates them periodically — reinforces that Phase 1's generator must be re-run, not
  write-once.

### 7. Recommendation

**IMPLEMENT WITH CHANGES** — narrowed to Phase 1 (`adr-index.md`, mechanically generated, no new
authoring burden) and Phase 2 (task-classification reference, narrowly scoped to what deep-review/
sync-docs don't already cover). `module-index.md` and `database-ownership.md` as originally
conceived: **DO NOT IMPLEMENT**, evidence-backed by 2.4. Per this issue's own step 5: this
recommendation itself still requires separate, explicit approval before Phase 1/2 are actually
built — this section is the spec's conclusion, not authorization to proceed.

### Immediate findings — outside this issue's scope, need a separate decision

Found as a byproduct of this audit, independent of whether the above is approved:
1. `docs/architecture/03-bounded-contexts.md:209-211,284-285` still describes `AttachmentMediaChangeHook`
   as an active SPI; it was removed entirely (`improvement-102`).
2. `docs/architecture/04-database-erd.md:157-160` and `docs/architecture/06-coupling-analysis.md:98-122`
   still document the `advertisement`↔`user_information` FK/coupling as present; it was removed
   (`improvement-120`, implemented 2026-07-25).
3. `docs/architecture/06-coupling-analysis.md:239` lists `improvement-011` as `~ PARTIAL`/open —
   not re-verified against `backlog/completed/issues/` in this pass, `UNKNOWN`.
4. Root `CLAUDE.md`'s `DECISIONS.md` module list (9 files) omits 2 real files
   (`scripts/ci/DECISIONS.md`, `scripts/sonar/DECISIONS.md` — 11 total).
**Decided:** fold into this issue's Phase 3 (see "Implementation sequence" above) rather than a
separate issue — fix via `/sync-docs` at execution time, once this issue is approved. Not executed
now (protected paths for this issue, and `/sync-docs` is manual-only per root `CLAUDE.md`, never
run automatically mid-conversation).

## Related

- `backlog/issues/improvement-129-marketplace-feed-modernization.md` — precedent for a large
  proposal stored as its own issue file.
- `backlog/issues/improvement-131-priority-emoji-rubric-doc-practice-mismatch.md` — closest
  existing precedent for a structured metadata-on-markdown convention; must reconcile with, not
  duplicate.
- `backlog/issues/improvement-133-deferred-oversized-review-findings.md` — overlap check target.
- `backlog/issues/improvement-130-backlog-issues-folder-rename.md` — overlap check target (backlog
  navigation/organization).
- `docs/architecture/03-bounded-contexts.md`, `docs/architecture/06-coupling-analysis.md` — overlap
  check target for any proposed module/dependency index.
- `.claude/skills/deep-review/` — existing context-loading strategy the task-classification
  reference must complement, not redefine; already does per-module conditional `DECISIONS.md`
  loading in full-mode (confirmed, see finding 2.9).
- `.claude/commands/sync-docs.md` — confirmed the actual mechanism for both the changed-file→
  doc-target mapping and ADR staleness classification (VALID/STALE/SUPERSEDED/DONE-GOAL-NOT-MARKED,
  `--full-audit` mode) — the drift-detection design in this spec reuses it rather than inventing
  new tooling (finding 2.10).
- `improvement-120` (completed, `backlog/completed/issues/improvement-120-advertisement-user-hard-fk-coupling.md`,
  archived `BACKLOG-ARCHIVE.md:1282`) — confirmed by direct read: FK decoupling that
  `docs/architecture/04-database-erd.md`/`06-coupling-analysis.md` still fail to reflect (finding
  2.6) — the actually-applicable documentation-drift precedent for this proposal.
- `improvement-102` (completed) — `AttachmentMediaChangeHook` removal that
  `docs/architecture/03-bounded-contexts.md` still fails to reflect (finding 2.4) — a second,
  independent live-drift example.
- `improvement-066`/`improvement-067` (completed) — **corrected in finding 2.7:** these are NOT
  documentation-drift incidents as originally assumed when this issue was filed; both are
  fix-not-propagated-to-a-sibling-code-path cases, found via code review, not a doc-vs-reality
  mismatch. Left in this list only to record the correction, not as a drift-design precedent.
- `improvement-030` — precedent for "a prose rule got automated" (`ArchitectureRulesTest`); same
  shape of reasoning, no direct mechanism reuse for this proposal (finding 2.5).

## Resolution (2026-07-31)

Implemented via `/autopilot`, per the spec above, once approved:

- **Phase 1:** `scripts/ai/generate-adr-index.sh` (mechanical parser, no manual fields) →
  `docs/ai/adr-index.md` (174 entries across 12 `DECISIONS.md` files; `scripts/sonar/DECISIONS.md`'s
  non-`ADR-NNN` format is called out under its own "Known gaps" section rather than silently
  dropped). Decision recorded in the new `scripts/ai/DECISIONS.md` ADR-001.
- **Phase 2:** `docs/ai/context-loading.md` (task-classification reference) and `docs/ai/flows.md`
  (scenario→command/skill map, added mid-run per explicit user request, broader than the original
  context-loading scope). `docs/ai/README.md` added as the layer's own entry point (also added
  mid-run per explicit user request).
- **Mandatory hooks (3.5):** wired into `.claude/commands/decision.md` (regenerate index step +,
  while there, fixed a stale module list and a Step-3 template that didn't match the real
  `## ADR-NNN:`/`**Status:**` convention — pre-existing bug, would have made the new mandatory step
  produce unparseable entries), `.claude/commands/feature.md` (check-before-filing step),
  `.claude/commands/sync-docs.md` (new Step A0 + mapping-table row, and fixed Step A1's
  `-maxdepth 2` → `3`, a pre-existing bug in the very step sitting next to the new one — nested
  `scripts/*/DECISIONS.md` files were being silently skipped by `--full-audit` itself), `.claude/rules.md`
  (mandatory consult-before-filing rule), root `CLAUDE.md` (discoverability pointer + corrected
  `DECISIONS.md` file list from 9 to all 12 real files).
- **Phase 3:** the 4 confirmed stale items fixed directly (`03-bounded-contexts.md`'s
  `AttachmentMediaChangeHook` references removed, `04-database-erd.md`/`06-coupling-analysis.md`'s
  FK-coupling sections marked resolved per `improvement-120`, `06-coupling-analysis.md`'s
  `improvement-011` status corrected to resolved, `CLAUDE.md`'s `DECISIONS.md` list corrected).
- **`/code-review --fix`** (8 parallel finder angles, high effort): found and fixed a real
  correctness bug in the generator script itself — multi-line `**Status:**`/heading text was
  silently truncated (confirmed live on `audit-spring-boot-starter` ADR-021/022, `scripts`
  ADR-003, `scripts/ci` ADR-006); rewrote the `awk` parser as an explicit 3-state machine that
  joins continuation lines until a blank line, and added a stderr warning for any ADR heading
  found with no `**Status:**` line (previously a silent drop). Also fixed: a dangling
  `docs/ai/README.md` cross-reference (pointed at `backlog/completed/issues/` before the issue was
  actually moved there), and two more instances of the same stale-doc pattern Phase 3 was already
  fixing (`03-bounded-contexts.md`'s "Risks & Future Considerations" list and
  `06-coupling-analysis.md`'s own section header both independently still described the
  `improvement-120`/`improvement-011` findings as open, even after the summary table in the same
  file had already been corrected). Skipped (with reason, not silently): a duplicate `find`
  pattern in `scripts/hooks/pre-commit` (unrelated file, marginal value); minor `awk`
  `split()`-vs-`sub()` style preference (script is correct as shipped); repeated
  "additive/non-duplicating" framing text across the 3 new `docs/ai/*.md` files (cosmetic); the
  consult-`adr-index.md` rule appearing in both `.claude/rules.md` and `.claude/commands/feature.md`
  (intentional layering — standing rule vs. operational step, not true duplication); no CI/pre-commit
  freshness check on the generated `adr-index.md` (a deliberate, already-documented tradeoff in
  `scripts/ai/DECISIONS.md` ADR-001 — reuses `sync-docs --full-audit`'s manual cadence rather than
  adding new CI infrastructure).
- No unit/integration/Playwright suite applies — no Java, schema, or UI file was touched; verified
  instead via direct execution (`bash scripts/ai/generate-adr-index.sh` re-run clean, output
  byte-matched what was staged) and the `/code-review` pass above.
