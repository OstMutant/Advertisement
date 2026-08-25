# improvement-135: Validate the AI-navigation layer (improvement-134) actually works, gate ADR-index drift in CI

**Type:** process/AI-tooling meta — validation and hardening of the `.claude/nav/` layer built in
improvement-134, not new navigation content.
**Module:** cross-cutting — `.claude/nav/`, `scripts/ai/`, `scripts/ci/`, `.claude/rules.md`.
**Priority:** 🟡 top — ranked ahead of the "Nice to have" batches, alongside/after improvement-124
(user's explicit request). Item 1 addressed a drift that already existed in the repo, not a
hypothetical — now closed.
**When:** independent, no blockers. Item 1 done (2026-07-31). Items 2-4 need a small amount of
design work on measurement methodology before they're actionable (see each item) — not started.

## Problem

improvement-134 built `.claude/nav/adr-index.md` (generated), `.claude/nav/context-loading.md`, and
`.claude/nav/flows.md` on the premise that they reduce token cost and improve command/skill routing.
That premise was never validated — the layer was accepted on the strength of its design rationale
alone. Two concrete gaps surfaced during improvement-124's execution (2026-07-31):

1. **`.claude/nav/adr-index.md` is already stale.** `platform-commons/DECISIONS.md` ADR-026 and
   `marketplace-app/DECISIONS.md` ADR-070/ADR-071 (all added earlier in this same session) are
   missing from the generated index. Root cause: regeneration is wired as a *manual* step inside
   the `/decision` skill's instructions (per `scripts/ai/DECISIONS.md` ADR-001) — it only fires
   when a contributor goes through `/decision`. These three ADRs were added via direct file edits
   during an `/autopilot` run, which never invokes `/decision`, so the mandatory step silently
   never ran. Nothing catches this class of miss today.
2. **A second, independent problem the same investigation surfaced:** ADR numbers are per-`DECISIONS.md`-file,
   not global — `marketplace-app/DECISIONS.md` already had its own, unrelated ADR-026 (rate
   limiting) before this session added `platform-commons/DECISIONS.md`'s ADR-026 (the `UserPort`
   split). `generate-adr-index.sh`'s output table has one `ADR` column with no module qualifier
   baked into the cell text — a reader (human or AI) who sees "ADR-026" cited without its module
   name can silently land on the wrong decision. This is a pre-existing design gap in the index
   format itself, not just a regeneration-timing bug.

Separately, nothing has measured whether the layer delivers on its stated goal (token
efficiency, routing accuracy) at all.

## Scope

### 1. Drift gate: generated `adr-index.md` must match its source `DECISIONS.md` files — ✅ DONE

**Corrected during implementation (2026-07-31) — no git hook.** Checked directly: this repo has
**no active git hook today** (`.git/hooks/` holds only Git's stock `.sample` files) and **no
automated CI trigger either** — `improvement-028` ("Minimal CI pipeline", GitHub Actions) is still
open/unimplemented, and `scripts/ci.sh` is only ever invoked manually (`/ci`). A tracked
`scripts/git-hooks/pre-commit` + install script was considered and dropped — it's opt-in
infrastructure to guard against a gap that Claude itself created (bypassing `/decision` during an
`/autopilot` run), and the more direct fix is closing that gap at the source: a standing rule
Claude re-reads before every action, not a git mechanism a human has to remember to install.
`.claude/commands/autopilot.md`'s inaccurate "pre-commit hook in this repo" claim (the thing that
prompted this whole investigation) is corrected too.

**What actually shipped:**
- `scripts/ai/generate-adr-index.sh` — `ADR` column now renders `ADR-NNN (module)` instead of a
  bare number, closing the same-number-different-file collision (confirmed live:
  `marketplace-app/DECISIONS.md` already had its own unrelated ADR-026 before this session added
  `platform-commons/DECISIONS.md`'s ADR-026).
- `.claude/nav/adr-index.md` regenerated — the live drift this issue was filed over (missing
  ADR-070/071/026) is closed.
- `scripts/ai/check-adr-index-freshness.sh` (new) — read-only: regenerates into the real file,
  diffs against a backup taken before regenerating, then unconditionally restores the backup on
  exit (`trap ... EXIT`) so the check never leaves the working tree mutated regardless of outcome.
  Exit 1 + a clear message on drift, exit 0 when fresh. Verified both paths directly (forced a
  stale HEAD version through it, confirmed exit 1 and an untouched working tree after).
- New standing rule in `.claude/rules.md`: any `DECISIONS.md` edit, by any workflow, regenerates
  the index in the same operation — the fix for the actual root cause (a mandatory step that only
  fired inside one specific command).
- `scripts/ci/entrypoint.sh` — new unconditional `docs` stage, runs first (fast, no Docker build
  needed), calls `check-adr-index-freshness.sh`, fails the overall CI run on drift. Backstop for
  when the rules.md discipline is skipped, once `/ci` is actually run.

**Known limitation, stated explicitly rather than glossed over:** until `improvement-028` ships
real push/PR-triggered CI, the `scripts/ci.sh` backstop only fires when someone manually runs
`/ci` — it is not a guarantee on every commit. The rules.md rule is the primary defense; it is
Claude's own discipline, re-read before every action, not an external enforcement mechanism.

**Scope extended during item 1 (2026-07-31) — `.claude/nav/flows.md` had the same class of gap.**
Auditing `flows.md` for staleness (a natural next check, same session) found it was missing rows
for `simplify`/`security-review`/`review`/`update-config`/`loop`/`schedule` — all pre-existing
skills, silently uncovered. Root cause was structurally different from the ADR-index case though:
`flows.md` mixed project-local commands (files in this repo, mechanically enumerable) with
built-in Claude Code skills (not files here at all — only visible via the session's own
"Available skills" system-reminder, impossible to grep from inside the repo). Fixed by splitting
`flows.md` into two tables with two different freshness guarantees:
- "Project commands & skills" — every `.claude/commands/*.md`/`.claude/skills/*/SKILL.md` file
  must have a row. Mechanically checked by the new `scripts/ai/check-flows-completeness.sh`
  (same shape as the ADR-index check — read-only, clear pass/fail), wired into the same
  `scripts/ci/entrypoint.sh` `docs` stage and a new sibling `.claude/rules.md` rule (new
  command/skill file → new `flows.md` row, same operation).
- "Built-in Claude Code skills" — explicitly documented as **not** mechanically checkable from
  this repo; re-verified only during `/sync-docs --full-audit`, by comparing against whatever
  skill list is actually available at that time. Honest about being a periodic, judgment-based
  check, not a continuous guarantee — same category of limitation as the `scripts/ci.sh` backstop
  above, stated for the same reason (glossing over it would just reproduce this exact gap later).

### 2, 3, 4 — consolidated (2026-08-01): one recording mechanism, one aggregation trigger

**Superseding note.** Items 2 and 4 below were each first answered with a one-off exercise: item 2
by tallying real (not fabricated) token numbers already produced during this session's own work,
after the fact; item 4 with 6 isolated blind-subagent tests on synthetic task phrasings. Both were
useful first spot-checks, but neither keeps answering the question as the codebase and usage
evolve, and repeating either to stay current is expensive for what it buys — a real snapshot in
item 2's case, a synthetic one in item 4's, but a snapshot either way. Consolidated instead into one
mechanism applied uniformly to items 2, 3, and 4 — record real observations from real tasks as
they're completed, aggregate them periodically:

- **Recording:** `.claude/rules.md` "Final reports record real operational data in a fixed,
  mechanically-parseable block" — every completed issue gets an `## Operational notes` block with
  fixed `key: value` lines (token cost by purpose; `.claude/nav/context-loading.md` task-type/
  consulted/matched; `.claude/nav/flows.md` situation/chosen/matched). Mechanically parseable by
  design, the same way `## ADR-NNN:`/`**Status:**` makes `DECISIONS.md` indexable — not free-form
  prose, so an aggregate pass doesn't have to re-interpret each entry.
- **Aggregation trigger:** `/sync-docs --full-audit` Step A5 (new) — greps `backlog/completed/
  issues/` (+ in-progress `backlog/issues/`) for `## Operational notes` blocks accumulated since
  the last audit, parses the fixed keys directly, and reports: token cost trend by purpose,
  `context_loading_matched` tally by task type, `flows_matched` tally. Feeds `.claude/nav/`'s own
  governing rule (item 5) — real accumulated evidence, not a synthetic snapshot. If too little data
  has accumulated to say anything, that absence is itself reported, not silently skipped.

This mechanism now owns items 2, 3, and 4 going forward. The synthetic spot-checks below are kept
as the initial data points (both found no problem), not the final word — real accumulated data
from `## Operational notes` blocks, reviewed at the next `/sync-docs --full-audit`, supersedes them.

### 2. Measure actual review-skill token cost — track it, don't act on a single number — ✅ DONE (measurement practice adopted; no default changed)

**Reframed (2026-07-31) — narrower and more concrete than the original ".claude/nav/* read-count
proxy" framing.** During this same session, the user raised a directly related, more consequential
cost question first: the multi-agent review skills (`/code-review`'s 8-parallel-finder pattern
especially) are visibly expensive, and there was no real accounting of it — only impression.
Resolved with an explicit split between measurement (do now, cheap, exact) and action (do not take
yet, needs evidence):

- **Measurement, adopted as a standing practice** (see `.claude/rules.md` "Final reports —
  include real agent-call token cost, broken down by purpose"): every `Agent` tool call already
  returns an exact `subagent_tokens` figure. Sum these per purpose (review, research/investigation,
  verification/testing) in every final report and batch write-up from now on — real data, not a
  retrofit. Explicitly *not* a full accounting: no tool reports main-thread (the primary
  conversation's own planning/implementation) token usage, so this is always a lower bound, stated
  as such rather than presented as complete.
- **Demonstrated on this session's own numbers** (reconstructed from the `usage.subagent_tokens`
  figures already returned during Batch 124-A/A2's `/code-review` passes and this issue's own item
  4 routing test, high effort level throughout): review-purpose agent calls ≈ 1.7M tokens across
  two 8-angle passes; verification/routing-test purpose (item 4) ≈ 354K tokens across 6 agents.
  Real numbers, not estimates — and still not the full cost of either batch, since neither includes
  main-thread tokens.
- **Explicitly rejected: lowering review effort level as a response to cost alone.** The user's own
  call, and the right one — `/code-review`'s effort levels trade breadth for noise (`low`/`medium`:
  fewer, higher-confidence findings; `high`/`xhigh`/`max`: broader coverage, may include uncertain
  findings), not simply "cheap and worse." There is no counterfactual evidence from this session
  that a lower effort level would have caught the same real bugs Batch A/A2's `high`-effort passes
  found (the silent-no-op `updateLocale`, the orphaned-row purge bug, the NPEs). Shipping a missed
  bug costs more than the token difference — asymmetric risk, don't trade on a guess.
- **What would actually justify changing a default:** a deliberate, user-approved side-by-side
  comparison on a genuinely small/low-risk future batch — run at a lower effort level once,
  cross-check that no real finding was missed against what a `high`-effort pass would likely have
  caught — repeated a few times before generalizing. Not attempted yet; no such batch has come up
  since this was raised.
- **Final policy (2026-07-31), recorded in `.claude/rules.md`:** the default itself never changes
  silently. The user can always name an explicit effort level for a given run. Absent that, Claude
  may pick a level other than the default only when the change's own size/risk makes it obviously
  justified (not as a routine cost-saving habit) — and whichever level actually ran must be stated
  plainly in the report every time, default or deviated, so the choice is never silent even when
  it was reasonable.

### 3. Validate `context-loading.md` empirically — does it actually reduce reads — ✅ mechanism done, empirical answer pending real data

**Superseded the original plan** (a synthetic 3-5-task with/without experiment) — see the
consolidated "2, 3, 4" section above. The recording mechanism is live as of this issue; the actual
empirical answer requires real `## Operational notes` data to accumulate across genuinely completed
tasks first, then gets reviewed at the next `/sync-docs --full-audit` (Step A5). Not answerable in
one sitting by design — that was the original plan's flaw (a synthetic snapshot, not evolving
evidence).

### 4. Measure workflow routing accuracy — task → correct command/skill — ✅ DONE (bounded)

**Method:** 6 realistic task phrasings (not copied from `flows.md`'s own "Situation" wording),
scoped to the "Built-in Claude Code skills" review-family cluster — `code-review`/`simplify`/
`security-review`/`deep-review`/`review`/`fewer-permission-prompts` — the highest-confusion-risk
area flagged during item 1's audit. Each sent to an isolated fresh subagent with **no context
from this conversation and an explicit instruction not to read `.claude/nav/flows.md` or anything
under `.claude/nav/`** — asked only "which command/skill would you reach for and why," no execution.
Result compared against `flows.md`'s own recommendation for that situation.

**Result: 6/6 correct.** Including the two closest-confusable pairs: "clean up code, don't hunt for
bugs" → `simplify` (not `/code-review`), and "PR #57 on GitHub" → `review` (not `/code-review`,
despite the similar name) — both routed correctly with reasoning that named the specific
distinguishing phrase from the skill's own description.

**Important limitation, not glossed over:** subagents see the exact same "Available skills"
system-reminder text any session gets (harness-injected, not sourced from `flows.md`) — so this
result shows the underlying **skill descriptions themselves** disambiguate correctly for this
cluster, more than it proves `flows.md`'s own table adds incremental routing accuracy on top of
them. `flows.md` still has independent value this test didn't measure (faster lookup without
scanning every available skill's full description, and entries where descriptions alone overlap
more, e.g. `/decision` vs `/feature` vs `/sync-docs`) — but that value is about *speed and
project-specific framing*, not proven here to be about *correctness*. Bounded scope (6 cases, one
cluster) — not exhaustive coverage of all 24 rows across both tables; treat as a spot-check that
found no problem in the area of highest suspected risk, not a full routing-accuracy certification.

### 5. Governing principle for all of the above: do not add new `.claude/nav/*` content until a real discovery gap appears

No new navigation file, no new metadata field, no expansion of `adr-index.md`'s schema (e.g. the
previously-rejected `Tags`/`Scope` field) until items 2-4 show the *existing* layer is pulling its
weight, or a specific, evidenced discovery failure demonstrates a gap the current layer can't
cover. This mirrors `CLAUDE.md`'s own "don't design for hypothetical future requirements"
principle, applied to the AI-navigation layer itself — a stale or speculative doc actively misleads
(as item 1 already demonstrates), which is worse than no doc at all.

## Out of scope

- Rebuilding or redesigning `adr-index.md`'s content model beyond the module-qualification fix in
  item 1 — no new fields, no restructuring.
- `module-index.md` / `database-ownership.md` — already evaluated and rejected in improvement-134
  (see `scripts/ai/DECISIONS.md` ADR-001), not reopened here.

## Related

- `backlog/completed/issues/improvement-134-ai-navigation-context-efficiency-layer.md` — the
  original spec/build.
- `scripts/ai/DECISIONS.md` ADR-001 — the manual-regeneration-wired-into-`/decision` design this
  issue's item 1 hardens with a standing `.claude/rules.md` rule (primary) plus a `scripts/ci.sh`
  backstop (secondary, manually-triggered).
- `.claude/nav/README.md` — "Staying correct" section, updated to mention `check-adr-index-freshness.sh`
  alongside `/sync-docs --full-audit`'s existing ADR classifier.
- `improvement-137` — repo-wide documentation dedup + new `doc-standards` skill, filed 2026-08-04.
  Deliberately not merged into this issue (different shape of work — a closeable one-pass cleanup
  vs. this issue's long-running evidence-accumulation item 3), but touches the same `.claude/nav/*.md`
  files this issue owns; its Pass 2/4 edits there are corrective (stale counts, restated facts),
  not new content, so they don't conflict with item 5's governing rule above. See improvement-137's
  own "Relationship to improvement-135" section for the full reasoning.
- `improvement-138` — "Architecture Control Plane" (generated model + AI/human dual-layer
  projection), filed 2026-08-04. Its Track B (a new L0-L5 AI-navigation layer) directly triggers
  this issue's item 5 governing rule and does not start until it's resolved — either item 3's real
  accumulated data shows a gap, or the user explicitly decides Track B is itself the
  evidenced-gap exception (improvement-138's "Finding 3"). Its B2 measurement step must extend
  this issue's `## Operational notes` block rather than introduce a separate one
  (improvement-138's "Finding 4"). Track A is not gated by this issue.

## Operational notes

- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: 354000 (6 isolated routing-test agents for item 4, ~59K tokens each)
- context_loading_task_type: n/a — AI-tooling/process work, no clean match to a context-loading.md row
- context_loading_consulted: no
- context_loading_matched: n/a
- flows_situation: n/a — ad hoc, conversation-driven, not initiated via a flows.md-mapped command
- flows_chosen: n/a
- flows_matched: n/a

**Status: item 1, 2, 4 done; item 3's mechanism built but its empirical answer is pending real
accumulated data — issue stays open, not moved to `completed/issues/`.**
