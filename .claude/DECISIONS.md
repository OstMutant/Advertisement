# `.claude/` — Architectural Decisions

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
