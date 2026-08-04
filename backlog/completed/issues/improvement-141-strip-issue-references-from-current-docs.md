# improvement-141: Current documentation must never reference issue numbers or embed dated "resolved" history

**Type:** documentation/process — repo-wide hygiene pass, no code changes
**Module:** cross-cutting — `docs/architecture/*.md`, `docs/ai/*.md`, every `CLAUDE.md`, every
  `README.md`, `.claude/skills/deep-review/*`, `.claude/commands/feature.md`/`sync-docs.md`/
  `autopilot.md`, comments in 6 `.sh` scripts, and every `DECISIONS.md` (12 files)
**Priority:** 🟡 high/medium ROI — real maintainability fix (docs actively drift and mislead),
  large but mechanical effort, explicit user-directed work in progress now
**When:** independent, no blockers. **Done 2026-08-04** — all phases (0-9) checked off.

## Problem

Two related but distinct smells found while discussing `improvement-138`'s Track A/B split:

1. **"Current state" documentation embeds dated, historical "✅ RESOLVED" narrative blocks**
   instead of just stating the current fact. Confirmed in:
   - `docs/architecture/06-coupling-analysis.md` — 4 blocks (e.g. `### ✅ RESOLVED: Marketplace →
     Starter Internal Imports (2026-06-15)`)
   - `docs/architecture/07-risk-report.md` — 5 blocks
   - `docs/architecture/README.md` — 2 mentions

   These files are supposed to describe what's true *now*; instead a reader has to mentally
   subtract "resolved" sections to find the current picture, and the "resolved" framing rots the
   moment nobody remembers to prune it after the next change makes it irrelevant.

2. **Current-state files hard-reference specific `improvement-NNN` issue numbers**, coupling
   "what's true now" to "which past ticket made it true" — a link that only matters for archival
   curiosity, not for using the file day-to-day, and that goes stale as issues get renumbered,
   merged, or archived. Confirmed counts (`grep -rc "improvement-[0-9]"`):
   - `docs/architecture/*.md`: `02-spi-map.md` (1), `03-bounded-contexts.md` (3),
     `04-database-erd.md` (4), `05-sequence-diagrams.md` (1), `06-coupling-analysis.md` (9),
     `07-risk-report.md` (7), `08-scorecard.md` (4)
   - `docs/ai/*.md`: `adr-index.md` (10 — generated, see Phase 7 below), `flows.md` (3),
     `README.md` (5)
   - `CLAUDE.md` (8 files): root, `advertisement-spring-boot-starter`,
     `attachment-spring-boot-starter`, `integration-tests`, `platform-commons`,
     `provider-profile-spring-boot-starter`, `scripts`, `taxon-spring-boot-starter`
   - `README.md` (5 files): `advertisement-spring-boot-starter`, `attachment-spring-boot-starter`,
     `docs/ai`, `integration-tests`, `scripts`
   - `.claude/skills/deep-review/`: `SKILL.md`, `references/diff-mode.md`,
     `references/full-mode.md`
   - `.claude/commands/`: `feature.md`, `sync-docs.md`, `autopilot.md`
   - `.sh` script comments: `deploy.sh`, `scripts/ai/screenshot-architecture-map.sh`,
     `scripts/ai/generate-architecture-model.sh`, `scripts/ci/run.sh`,
     `scripts/database/reset.sh`, `scripts/sonar/run.sh`
   - Every `DECISIONS.md` (12 files) — counts: `marketplace-app` (113, by far the largest — mix
     of inline `improvement-NNN` mentions and `→ [improvement-NNN-slug](../backlog/completed/
     issues/improvement-NNN-slug.md)` "Related" links), `platform-commons` (18),
     `attachment-spring-boot-starter` (10), `integration-tests` (9), `scripts/ai` (6),
     `taxon-spring-boot-starter` (5), `scripts/ci` (5), `scripts` (3), `audit-spring-boot-starter`
     (3), `query-lib` (3), `playwright` (0 — confirmed clean already), `scripts/sonar` (0 —
     confirmed clean already)

`.claude/skills/doc-standards/SKILL.md` currently carves `DECISIONS.md` out of its "one fact, one
canonical home" rule entirely ("append-only history — write what happened, accurately"). That
carve-out is correct for ADR *content* (the decision and its reasoning must stay complete, not be
trimmed for brevity) but was silently read as also permitting issue-number cross-references —
which is a separate concern this issue closes.

## Decision (agreed with user, 2026-08-04)

- **Current-state files** (`CLAUDE.md`, `README.md`, `docs/architecture/*.md`, `docs/ai/*.md`,
  skill/command `.md` files, `.sh` script comments) state only what's true *now*. No
  `improvement-NNN`/`goal-NNN`/`feature-NNN` references, no dated "✅ RESOLVED"/"as of <date>"
  narrative about a prior state. If a fact changes, the old fact is deleted, not marked resolved
  in place.
- **`DECISIONS.md`** keeps its append-only, historical-by-design character (date + decision +
  reasoning, unchanged) but likewise drops the issue-number reference from every entry — an ADR
  records the decision and why, not which ticket produced it.
- **History lives in `backlog/completed/issues/*.md`** (full detail) **and
  `backlog/completed/BACKLOG-ARCHIVE.md`** (searchable one-line index) — both must keep naming the
  real classes/modules/concepts touched (not just "cleanup pass"), so a keyword grep finds them.
- **The bridge is `git blame`/`git log`, not a hand-maintained pointer.** This repo's commit
  messages already carry the issue number (`feat(improvement-NNN): ...` convention, confirmed
  followed in recent history). No new index or generated artifact is needed for this purpose —
  deliberately not adding one, consistent with `improvement-135` item 5's standing skepticism of
  new AI-navigation layers.

## Suggested fix — phased execution (check off as each lands)

- [x] **Phase 0 — record the rule.** Add a new standing rule to `.claude/rules.md` (abstract
  principle, not this incident's specifics, per that file's own meta-rule) stating the decision
  above. Update `.claude/skills/doc-standards/SKILL.md`'s "Out of scope" line: `DECISIONS.md`
  stays exempt from the fact-dedup ownership table, but a cross-reference to the new rule makes
  clear the issue-number-stripping requirement still applies to it.
- [x] **Phase 1 — `docs/architecture/*.md`.** Remove the 11 "✅ RESOLVED" blocks
  (`06-coupling-analysis.md`, `07-risk-report.md`, `README.md`) — keep only the current
  finding/state each section describes, delete resolved-and-gone findings entirely rather than
  marking them resolved. Strip the 22 remaining `improvement-NNN` references across all 7 numbered
  architecture docs.
- [x] **Phase 2 — `docs/ai/flows.md` + `docs/ai/README.md`.** Strip issue-number references (8
  total). `docs/ai/adr-index.md` is a generated artifact (`scripts/ai/generate-adr-index.sh`) —
  do not hand-edit; it self-corrects once Phase 7 (below) removes the source `improvement-NNN`
  mentions from `DECISIONS.md`, then gets regenerated per `.claude/rules.md`'s standing rule.
- [x] **Phase 3 — 8 `CLAUDE.md` files.** Strip issue-number references; keep the constraint/fact
  itself, drop only the ticket citation.
- [x] **Phase 4 — 5 `README.md` files.** Same treatment.
- [x] **Phase 5 — skills + commands.** `.claude/skills/deep-review/{SKILL.md,references/
  diff-mode.md,references/full-mode.md}`; `.claude/commands/{feature.md,sync-docs.md,
  autopilot.md}`.
- [x] **Phase 6 — 6 shell script comments.** `deploy.sh`, `scripts/ai/screenshot-architecture-
  map.sh`, `scripts/ai/generate-architecture-model.sh`, `scripts/ci/run.sh`,
  `scripts/database/reset.sh`, `scripts/sonar/run.sh`.
- [x] **Phase 7 — 12 `DECISIONS.md` files.** Done — 11 files cleaned by hand; `marketplace-app/
  DECISIONS.md` (113 references, the largest by far) delegated to a subagent given the exact
  pattern already established by the 11 hand-edited files, then independently spot-verified
  (final grep: 0 hits, no content/reasoning lost, ADR-045's Context section checked directly for
  a duplication risk the agent flagged and said it corrected — confirmed clean). Also caught and
  fixed 2 shell scripts missed by Phase 6's original scope (`integration-tests/run.sh`,
  `playwright/run.sh` — outside `scripts/`, so absent from Phase 6's original file list) and one
  `feature-NNN`-pattern reference in `platform-commons/DECISIONS.md` that the `improvement-`/
  `goal-` only grep used during initial scoping had missed.
- [x] **Phase 8 — regenerate + verify.** `bash scripts/ai/generate-adr-index.sh` (180 entries,
  0 issue-number references) and `bash scripts/ai/generate-architecture-model.sh` (depends on
  `docs/architecture/*.md`/`DECISIONS.md`, went stale from Phases 1/7's edits — regenerated, 25
  nodes, 0 issue-number references) both committed. `check-adr-index-freshness.sh`,
  `check-flows-completeness.sh`, `check-architecture-model-freshness.sh`,
  `check-hardcoded-counts.sh` all pass. `bash scripts/unit-tests.sh` green. Final repo-wide
  verification: `grep -rln "improvement-[0-9]\|goal-[0-9]\|feature-[0-9]" --include="*.md"
  --include="*.sh" .` outside `backlog/` returns exactly one file, `.claude/rules.md` — a
  deliberate, user-approved exception (see Phase 9 note below), not a miss.
- [x] **`.claude/rules.md`'s one remaining reference — resolved by decision, not a miss.** The
  "Out-of-scope-but-valid findings" rule named `backlog/issues/improvement-133-...md` as a literal
  file path Claude must append to — an operational target, not a traceability citation. Per user
  decision (2026-08-04): reworded to describe the file by search terms ("search `backlog/issues/`
  for the file covering 'deferred oversized review findings'") instead of hardcoding the number —
  same effect, no forward-pointer left to go stale.

## Follow-up — Phase 9 (done 2026-08-04)

Found a **second, related pattern this issue's original grep-based scope didn't catch**: dated
"corrected \<date\> — previously X" narrative with **no issue-number attached** (so `grep
improvement-NNN` never surfaces it). This is the same class of problem Phase 1 already fixed in
`docs/architecture/*.md` (the "✅ RESOLVED" blocks) — a current-state file should read as if it
always looked the way it looks now, not carry a diary of what it used to say.

- [x] **Phase 9 — repo-wide "corrected/previously/originally" narrative sweep.** Swept every
  `CLAUDE.md`/`README.md` across the repo, `docs/architecture/*.md`, `docs/ai/*.md`, `.claude/
  skills/*/SKILL.md` + `references/*.md`, `.claude/commands/*.md`, and every `.sh` script (83
  files total) with a multi-pattern grep (`corrected <date>`, `previously`, `originally was/had/
  described/stated/contained/used`, `used to be/have/contain/require/work`, `no longer exists/
  applies/holds (`, `this used to/previously`). Found and fixed 9 real hits: `advertisement-
  spring-boot-starter/README.md`, `integration-tests/CLAUDE.md`, `integration-tests/README.md`,
  `marketplace-app/README.md`, `playwright/CLAUDE.md` (2), `playwright/README.md` (3), `query-lib/
  README.md` (2), `scripts/README.md` — each rewritten to state only the current fact, dated
  "what it used to say" framing deleted entirely, not marked historical in place. `docs/ai/
  adr-index.md`'s 3 hits are legitimate — mechanically generated from `DECISIONS.md` `**Status:**`
  lines, which stay exempt from this rule (dated "what changed and why" is exactly what an ADR is
  for) — left untouched. `bash scripts/unit-tests.sh`: BUILD SUCCESS, all green (query-lib +
  marketplace-app + every starter compiled and tested, including `ArchitectureRulesTest`). A
  second, broader confirmation grep (`as of <date>`, `updated <date>`, `fixed <date>`, `was
  previously`, `is no longer the case`, `no longer true`, `that/which used to`) across the same
  83-file set returned zero further hits.

## Related

- `.claude/skills/doc-standards/SKILL.md` — existing "one fact, one canonical home" rule this
  issue extends with a parallel rule about issue-number references
- `improvement-135` item 5 — the standing "no new AI-navigation layers without evidence" rule this
  fix deliberately stays compatible with (no new generated index invented for the git-blame bridge)
- `improvement-138` — the conversation that surfaced this while discussing Track A/B scope
  (docs/architecture is Track A's own subject matter, but this issue is independent of it — not a
  Track A sub-item, filed separately per explicit user choice)

## Operational notes

One `Agent`-tool call was delegated (Phase 7, `marketplace-app/DECISIONS.md`'s 113 references) —
241,982 tokens, 155 tool uses, ~26 min. Its purpose was bulk mechanical text editing following an
already-established pattern (11 other `DECISIONS.md` files had already been hand-edited first to
prove the pattern out), not review/research/verification as such — doesn't cleanly fit any of the
three tracked buckets below, noted here as context rather than forced into one.

- token_cost_review: n/a (see note above — the one Agent-tool call doesn't fit this bucket)
- token_cost_research: n/a (no Agent-tool research calls; all investigation done directly)
- token_cost_verification: n/a (unit tests / CI gates run directly via Bash, not delegated)
- context_loading_task_type: cross-cutting documentation-only change, no code
- context_loading_consulted: no
- context_loading_matched: n/a
- flows_situation: large, phased, multi-session repo-wide documentation hygiene pass
- flows_chosen: direct execution + one Agent-tool delegation for the largest single file
- flows_matched: n/a — no existing `flows.md` row covers this shape of task
