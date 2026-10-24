# `.claude/` — Architectural Decisions

## ADR-005: `/optimize` reuses `deep-review-orchestrator` with a selectable lens instead of its own review pipeline

**Status:** Accepted

**Applied:** 1

**Context:** Nothing in the project looks for code that need not be maintained, measures what removing it would save, or keeps the results between sessions. `/review` finds design problems and verifies each one, but it never edits code, does not measure and files every finding as its own task. The built-in `simplify` skill and `/code-review --fix` look only at the latest diff and apply fixes at once, with no ranking, no measurement and no approval per batch. Removable code found so far sits in the deferred-findings collection without an estimate of what each entry would save.

**Decision:** One command, `/optimize` (a command file with its helper script in the folder of the same name), with four modes chosen by its first argument: discover (read-only), report, apply and verify. Discovery adds one read-only lens, `code-reduction-reviewer`, that estimates the saving of each finding and lists the ideas it rejected; it runs through `deep-review-orchestrator`, which gets three optional prompt inputs (`lenses:`, `exclude:`, `output: json`) and a `path <dir>` scope, so the orchestrator's Haiku verification, cross-file pass and backlog cross-check are reused unchanged and `/review` behaves as before when no option is given. Findings, rejected ideas and the log of what each run inspected live in one standing backlog inventory. Measuring a batch and checking its hard rules (nothing under `marketplace-orchestrator/`, no new dependency, no reflection) is done by a plain script without a model call; applying a batch uses the Approval Rule, the per-unit Haiku dispatch of `/autopilot` and the smallest tests first, and stops after every batch. The command carries `disable-model-invocation: true` so only the user starts it.

**Rejected alternatives:** A separate review pipeline for `/optimize` (duplicates the verification, the cross-file pass and the backlog check the orchestrator already has). A mode of `/review` (mixes a read-only design review with a measure-and-apply workflow, and `/review` is meant to stay a thin wrapper). Extending the DRY, KISS and YAGNI lens (that lens gives a design verdict, while this work needs estimates and a different output). OpenRewrite as a standing part (Lombok is already used in about 176 files, so the yield is small, and it ties the command to the build; kept as a possible one-off experiment). SonarQube duplication data as a starting point (a Java duplicate is counted only from 10 repeated statements, and it needs one more toolset of the Sonar agent). PMD CPD (the same coarse token-based duplication, plus a new tool).

---

## ADR-003: `/autopilot` step 2 and `/sync-docs` diff-mode apply the standing per-unit Haiku-dispatch rule, with rework-rate metrics to validate it

**Status:** Accepted

**Context:** `/review`'s step-4 verification and `/sync-docs --full-audit`'s Step A3 research
agents already dispatch on `model: "haiku"`, but neither `/autopilot`'s own implementation phase
(step 2) nor `/sync-docs`'s default diff-mode had any Haiku-dispatch guidance at all — step 2
always implements directly on the inherited model, and diff-mode's Javadoc/pom.xml/infra-header
checks (the same mechanical per-file shape full-audit's Step A3 already proved cheap to
parallelize) were never split out. An earlier draft of this decision gated both behind an invented
numeric threshold (3+ independent units for autopilot, 8+ files for sync-docs) — re-checking
against this project's own standing "Well-specified mechanical implementation work defaults to a
Haiku-model agent" rule (`.claude/rules.md`) found that rule carries no count or size threshold at
all: it applies per-unit, to even a single fully-specified change, as soon as the exact before/after
is known with no open judgment call. The invented thresholds contradicted that existing rule rather
than extending it, so they were dropped before this ADR was finalized. No real operational data
existed yet either way for the general case (checked `backlog/completed/tasks/` for prior
`## Operational notes` blocks from autopilot runs — none exist, the convention is too new) —
settling the threshold question with real evidence, rather than an invented number, is exactly
what this decision's own new metrics (below) are for.

**Decision:**
1. `/autopilot` step 2 applies the standing Haiku-dispatch rule to each unit of step 1's plan
   individually, with no count or size gate: if a given file-level fix's exact before/after is
   already known with no open judgment call, it's dispatched to Haiku — true even for a single
   such unit. When multiple exist, group dispatches by file/module (same-file fusion, mirroring
   `deep-review-orchestrator`'s step 4) as a batching optimization, never as a precondition for
   using Haiku at all. A single cohesive design requiring continuous judgment across files (the
   common single-feature `/autopilot` case) is still implemented directly — that case was never
   Haiku-eligible under the standing rule either, threshold or not.
2. `/sync-docs` diff-mode applies the same per-file dispatch, no file-count gate — mirrors Full
   Audit Mode's own Step A3, which likewise never gated its own Haiku dispatch on volume.
3. Three new fields added to `.claude/rules.md`'s standing `## Operational notes` block —
   `haiku_decomposition_used`, `haiku_decomposition_dispatch_count`,
   `haiku_decomposition_rework_needed` — so real evidence accumulates over time on whether
   per-unit dispatch (no threshold) is actually safe in practice (a high rework rate would be
   evidence *for* reintroducing a real, data-backed threshold) rather than relying on intuition or
   an invented number. Aggregated by `/sync-docs --full-audit`'s existing Step A5, same as
   `review_signal_ratio`.

**Rejected alternatives:** An invented numeric threshold (3+ units / 8+ files) gating whether Haiku
gets used at all — rejected because it contradicted the existing standing Haiku-dispatch rule's
own, already-settled criterion (fully specified → Haiku, no threshold), and because no real data
justified picking those specific numbers over any other. If a real threshold turns out to be
warranted, this decision's own new metrics are what should justify it later — not another guess.

## ADR-004: Per-task ADR usage tracking, counted in each ADR's own `**Applied:**` line

**Status:** Accepted

**Applied:** 2

**Context:** `.claude/nav/adr-index.md` already tracks each ADR's existence, module, status, and
an opportunistically-stamped `Verified:` date — but nothing tracked whether an ADR is actually
*used* in real work. Without that it is impossible to tell which ADRs are load-bearing (worth
keeping precise and current) from ones nobody has applied since they were written. The first
attempt tallied a free-form `adrs_referenced:` field from every task file at generation time; it
never produced a non-zero count, because the `## Operational notes` block was written once, at
task end, from memory, and a blank field read the same as an unused ADR.

**Decision:**
1. The `## Operational notes` block (`.claude/rules.md`) exists from the moment a task is created
   and is updated during the work. Its `adrs_referenced: <ADR-NNN (module), ... or none>` line lists
   every ADR that actually shaped a decision in that task (merely reading one does not count), in
   the exact key shape `adr-index.md`'s first column uses.
2. The counter lives in the ADR: an optional `**Applied:** N` line after `**Status:**`.
   `/record-decision` writes `**Applied:** 1` into a new ADR (its creating task is the first to
   apply it). Whoever closes a task adds 1 to the `**Applied:**` of every ADR in its
   `adrs_referenced`, except an ADR that task itself created; where the line is absent it is added
   as `**Applied:** 1`. One count per task, however often the ADR is cited.
3. `generate-adr-index.sh` only collects `**Applied:**` into the index's `Applied` column — blank
   when the line is absent, never 0 — and the architecture-map generator shows the same value, so
   the map fills in gradually and truthfully. Tasks closed before this decision are not backfilled.

**Rejected alternatives:** Tallying `adrs_referenced` across task files inside
`generate-adr-index.sh` — depends on a free-form field being filled in correctly, and a typo
silently counts as zero. A separate usage log or second index file — a second source of truth next
to the existing index.

## ADR-002: Formalize `/deep-review`'s reasoning layer as real, isolated `.claude/agents/*.md` subagents

**Status:** Accepted

**Context:** A much larger "Hybrid Agentic Review Factory" proposal (a mechanical
Semgrep/ArchUnit/SonarQube-MCP detection layer feeding a 4-lens reasoning layer) was investigated
and the mechanical layer rejected outright — two of its proposed rules directly contradicted this
project's own documented architecture. The narrowest surviving candidate — formalizing
`.claude/skills/deep-review/references/diff-mode.md`'s already-working 4-lens parallel-review
pattern as real, named `.claude/agents/*.md` files instead of inline prompt text — was picked up
as this decision.

**Decision:**
1. Built `deep-review-orchestrator` as a self-contained coordinator subagent (no dependency on any
   skill) with 4 scope modes: current uncommitted changes, one commit, one module, or the whole
   repo. It dispatches specialized reviewer lenses, verifies every candidate independently, and
   returns a structured result — never runs inline in the main conversation, keeping review
   reasoning and full-file reads out of that context entirely.
2. Two reviewer lenses, not the originally-scoped four: `solid-reviewer` (SRP/ISP/DIP/LSP) and
   `dry-kiss-yagni-reviewer` (DRY/KISS/YAGNI merged into one lens, not split further — DRY and
   YAGNI pull in opposite directions, so one lens weighing both in a single judgment avoids two
   lenses producing contradictory findings on the same code). `security-boundary-reviewer` and
   `data-integrity-reviewer` were drafted, then deleted before use — their real concerns turned out
   to be better covered elsewhere: a targeted `ArchitectureRulesTest` rule for the security case,
   fault-injection integration tests for the data-integrity case, both cheaper and more reliable
   than an LLM guessing at
   diff-review time for already-identified risk classes.
3. Inter-agent data format is structured JSON (`{"findings": [...]}`, content fields like `claim`/
   `failure_scenario` separated from metadata fields like `file`/`line`/`confidence`), not free
   text — verified against independent, non-project sources (real production agent protocols —
   Google A2A, MCP, IBM/BeeAI ACP — all use structured JSON for agent-to-agent messages) before
   adopting it, not taken on a single source's word alone.
4. Verification, cross-file integration, and confidence-routing are three distinct steps, not one:
   every candidate gets a fresh, independent verification subagent (no prior reasoning context,
   confirms against the real current file); a second pass then checks all survivors together for
   cross-file contradictions (guards against the "attention dilution" failure mode a single bulk
   pass suffers); a third step routes by confidence (`high` → automatic path, `medium`/`low` →
   listed as a self-contained human-review item, never auto-filed) since raw self-reported
   confidence is not reliable enough to drive an automated decision on its own.
5. `ReportFindings` (a real tool, but main-thread-only — confirmed against Claude Code's own docs,
   always filtered from a subagent's tool list regardless of `tools:` frontmatter) and writing a
   new `backlog/tasks/*.md` file (an action the standing Approval Rule requires a human to approve
   first) are both handled the same way: the orchestrator prepares the exact payload/content and
   returns it in its result; the dispatcher is responsible for calling `ReportFindings` and for
   presenting the prepared issue file to the user before writing it. The orchestrator has no
   `Write` tool at all.
6. `.claude/skills/deep-review/` (the old `SKILL.md`/`diff-mode.md`/`full-mode.md`) was deleted
   entirely, not left alongside the new agents — a live test run showed the orchestrator falling
   back to the old skill's stale logic (diff-only scope instead of a real file-set module sweep)
   despite explicit instructions not to depend on it; deleting the skill was the fix, since a
   subagent can see the full skill listing by default and nothing short of the file's absence
   reliably prevented the fallback.

**Rejected alternatives:**
- Keep all 4 originally-scoped lenses (`security-boundary-reviewer`, `data-integrity-reviewer`
  included) — rejected once each was checked against what actually catches its concern: a
  mechanical `ArchitectureRulesTest` rule and dedicated integration tests both outperform an LLM
  lens for an already-identified risk pattern in already-existing code; an LLM lens still earns its
  keep for genuinely new code no test covers yet, which neither deleted lens's concern class was.
- Split `dry-reviewer`/`kiss-reviewer`/`yagni-reviewer` into three separate lenses — rejected: DRY
  and YAGNI are in direct tension by design, and two independent lenses would produce contradictory
  findings on the same duplication instead of one coherent judgment.
- Have the orchestrator write the backlog issue file directly (it originally had `Write` in
  `tools:`) — rejected once traced through: an isolated subagent silently creating tracked backlog
  entries bypasses the Approval Rule with no human ever in the loop before the file lands.
- Keep `.claude/skills/deep-review/` alongside the new agents "just in case" — rejected after a
  live run demonstrated it actively causing incorrect behavior (the orchestrator falling back to
  its stale logic), not just sitting inert.

## ADR-001: Split module-specific AI guidance into path-scoped `.claude/rules/*.md`; deduplicate memory against canonical rules

**Status:** Accepted

**Context:** Root `CLAUDE.md`'s `@import` mechanism loads every `@`-imported file
(`.claude/rules.md` plus 13 module `CLAUDE.md` files) unconditionally into every session's
context regardless of which module the task actually touches — confirmed directly, not assumed:
this session's own opening context contained all of it before any module-specific work started,
measuring ~2293 lines / ~155KB baseline every session. Separately, the auto-memory system
(`/root/.claude/projects/-app/memory/`) had accumulated real, demonstrated duplication against
these same canonical files — of 55 memory files, roughly a third restated a rule already present
near-verbatim in `.claude/rules.md`/`CLAUDE.md`, several with stale or actively contradictory
detail.

**Decision:**
1. Investigated Claude Code's `.claude/rules/*.md` + `paths:` frontmatter mechanism directly, not
   from documentation alone — confirmed live with a real probe rule + matching file that the
   mechanism works, that a subagent (`Agent` tool call) inherits the same eager-loaded content the
   main thread gets, and that a `paths:` glob is not anchored to the repo root (it matches any
   path containing that segment, not only a path starting with it).
2. Moved all 13 module `CLAUDE.md` files to `.claude/rules/<module>.md`, each carrying its own
   `paths: ["<module>/**"]` glob. Three of them (`platform-commons`, `scripts`, `playwright`) are
   also read by literal path inside `docs/architecture/scripts/generate-architecture-model.sh`
   (arch-embed markers, Scripts/Playwright card descriptions) — fixed in the same change, verified
   via a full regeneration + a node-by-node description diff against the last real run showing
   zero unintended differences.
3. Root `CLAUDE.md` keeps a one-line-per-module pointer (no longer `@`-imported) so module purpose
   stays discoverable without eager-loading the full detail.
4. Rules with no natural file-path trigger (the Approval Rule, the commit-permission rule, the
   re-read-before-action rule, and a further set of investigation/review-discipline rules migrated
   from memory) stay in the eager `.claude/rules.md` — a path-scoped file would silently not load
   for a decision that never touches a matching file, an empirically confirmed real risk, not a
   hypothetical one.
5. Memory deduplicated in parallel: 55 → 16 files. Each file individually classified (duplicate /
   partial-nuance / unique / stale) against the full canonical text and disposed of accordingly
   (deleted, migrated with nuance preserved, rewritten, or kept as genuinely personal/session-
   scoped) — no bulk assumption.

**Rejected alternatives:**
- Do nothing (status quo) — real duplication/staleness was demonstrated, not just suspected, once
  actually audited file-by-file.
- Move all guidance, including path-independent behavioral rules, into path-scoped files —
  rejected: the empirical glob test showed a rule with no natural file trigger would silently stop
  loading when it mattered.
- Migrate the stale `private/claude/memory/` mirror instead of the real, live memory path —
  rejected: that mirror is gitignored/untracked and unrelated to what the harness actually loads.
