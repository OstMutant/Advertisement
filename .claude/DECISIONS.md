# `.claude/` — Architectural Decisions

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

## ADR-004: Per-task ADR usage tracking, aggregated into a reference count on the generated ADR index

**Status:** Accepted

**Context:** `.claude/nav/adr-index.md` already tracks each ADR's existence, module, status, and
an opportunistically-stamped `Verified:` date — but nothing tracks whether an ADR is actually
*used*: consulted or followed during real implementation work. Over time this makes it impossible
to tell which ADRs are load-bearing (frequently consulted, worth keeping precise and current) from
ones nobody has looked at since they were written (candidates for archiving, or at least lower
priority for the `Verified:` stamping effort). Measuring this needed a source of real per-task
data, the same philosophy already established for `review_signal_ratio`/`flows_matched`/
`context_loading_matched` — recorded honestly per run, aggregated later, not guessed.

**Decision:**
1. New field in `.claude/rules.md`'s standing `## Operational notes` block: `adrs_referenced:
   <ADR-NNN (module), ADR-MMM (module), ... or n/a>` — every ADR actually consulted or followed
   during that task, in the exact `ADR-NNN (module)` key shape `adr-index.md`'s own first column
   already uses (so the two join without reformatting).
2. `.claude/nav/scripts/generate-adr-index.sh` gains a pre-pass that scans every
   `adrs_referenced:` line across `backlog/tasks/*.md` and `backlog/completed/tasks/*.md`, tallies
   a reference count per `ADR-NNN (module)` key, and threads it into the generated table as a new
   `Referenced` column (defaulting to 0 for any ADR never cited this way) — mechanical, generated,
   never hand-edited, same discipline as every other column in this file.

**Rejected alternatives:** A hand-maintained counter line inside each `DECISIONS.md` entry itself
(e.g. `**Referenced:** N`) — rejected because it would need mechanical editing of up to 16
different files every time any ADR gets referenced anywhere, fragile and against this file's own
"generated, not hand-edited" precedent for aggregate ADR facts.

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
