# improvement-135: Validate the AI-navigation layer (improvement-134) actually works, gate ADR-index drift in CI

**Type:** process/AI-tooling meta — validation and hardening of the `docs/ai/` layer built in
improvement-134, not new navigation content.
**Module:** cross-cutting — `docs/ai/`, `scripts/ai/`, `scripts/ci/`, `.claude/rules.md`.
**Priority:** 🟡 top — ranked ahead of the "Nice to have" batches, alongside/after improvement-124
(user's explicit request). Item 1 addressed a drift that already existed in the repo, not a
hypothetical — now closed.
**When:** independent, no blockers. Item 1 done (2026-07-31). Items 2-4 need a small amount of
design work on measurement methodology before they're actionable (see each item) — not started.

## Problem

improvement-134 built `docs/ai/adr-index.md` (generated), `docs/ai/context-loading.md`, and
`docs/ai/flows.md` on the premise that they reduce token cost and improve command/skill routing.
That premise was never validated — the layer was accepted on the strength of its design rationale
alone. Two concrete gaps surfaced during improvement-124's execution (2026-07-31):

1. **`docs/ai/adr-index.md` is already stale.** `platform-commons/DECISIONS.md` ADR-026 and
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
- `docs/ai/adr-index.md` regenerated — the live drift this issue was filed over (missing
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

### 2. Measure actual token impact — before/after, on real tasks

Needs a concrete methodology before it's actionable, not just "measure it": proxy metric first
(cheap, immediate) — count how often `docs/ai/*` files are actually `Read` during a session versus
how often the pre-improvement-134 pattern (opening multiple `DECISIONS.md`/`CLAUDE.md` files
speculatively) still happens. A real token-count A/B (same task run with/without the layer
present) is the rigorous version, but is expensive and noisy across task variance — do the proxy
first, only invest in the controlled A/B if the proxy suggests the layer isn't earning its keep.

### 3. Validate `context-loading.md` empirically — does it actually reduce reads

Small, scoped experiment: pick 3-5 representative task types already categorized in
`context-loading.md`, run them with and without that file available, compare actual file-read
counts/patterns. Not an ongoing measurement system — a one-time validation to decide whether the
file earns its maintenance cost.

### 4. Measure workflow routing accuracy — task → correct command/skill

Tests `flows.md` specifically (not the ADR index or context-loading.md): for a sample of task
descriptions, check whether the command/skill actually invoked matches what `flows.md` would have
recommended. This measures skill-description quality as much as `flows.md` itself — scope it to
that file, not the whole `docs/ai/` layer.

### 5. Governing principle for all of the above: do not add new `docs/ai/*` content until a real discovery gap appears

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
- `docs/ai/README.md` — "Staying correct" section, updated to mention `check-adr-index-freshness.sh`
  alongside `/sync-docs --full-audit`'s existing ADR classifier.
