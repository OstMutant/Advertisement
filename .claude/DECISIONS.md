# `.claude/` — Architectural Decisions

## ADR-002: Formalize `/deep-review`'s reasoning layer as real, isolated `.claude/agents/*.md` subagents

**Status:** Accepted

**Context:** `improvement-169` investigated a much larger "Hybrid Agentic Review Factory" proposal
(a mechanical Semgrep/ArchUnit/SonarQube-MCP detection layer feeding a 4-lens reasoning layer) and
rejected the mechanical layer outright — two of its proposed rules directly contradicted this
project's own documented architecture. The narrowest surviving candidate — formalizing
`.claude/skills/deep-review/references/diff-mode.md`'s already-working 4-lens parallel-review
pattern as real, named `.claude/agents/*.md` files instead of inline prompt text — was picked up
as `improvement-171`.

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
   to be better covered elsewhere: a targeted `ArchitectureRulesTest` rule for the security case
   (folded into `improvement-111`), fault-injection integration tests for the data-integrity case
   (split into `improvement-172`), both cheaper and more reliable than an LLM guessing at
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
   new `backlog/issues/*.md` file (an action the standing Approval Rule requires a human to approve
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
