# feature-003: Architecture Documentation Sync — ✅ DONE (with one deliberate design change)

**Type:** tooling — process/documentation infrastructure, condensed from the original
`architecture-audit/SPEC.md` + `PLAN.md` (pre-issue-file convention).
**Module:** `.claude/commands/`, `scripts/hooks/`, `docs/architecture/`, per-module `DECISIONS.md`.
**Status:** done, but the original design's central premise (fully automatic doc sync inside the
git pre-commit hook) was deliberately reversed — see below.

## Goal (as originally scoped)

Keep all project documentation in sync with actual source code automatically, triggered as a git
pre-commit hook that calls `claude -p` for each affected documentation target and stages the
result so docs land in the same commit as the code.

## What shipped

- **Baseline docs** — `docs/architecture/01-module-dependencies.md` through `08-scorecard.md`,
  `docs/test-coverage.md` — all present and maintained.
- **Structured ADR format** — every module's `DECISIONS.md` migrated to `## ADR-NNN: Title` /
  `Status:` / `Context:` / `Decision:` / `Consequences:`.
- **The slash command** — shipped as `/sync-docs` (`.claude/commands/sync-docs.md`), not
  `/audit-diff` as originally named. Supports the same base-ref diffing plus a `--full-audit` mode
  (not in the original spec) that ignores the git diff and verifies every `DECISIONS.md`/`README.md`
  claim against current code directly, for periodic use.
- **`scripts/hooks/pre-commit`** + **`scripts/install-hooks.sh`** — both shipped as designed
  (`git config core.hooksPath scripts/hooks`).
- **`scripts/hooks/commit-msg`** — shipped, prepends conventional-commit entries to
  `CHANGELOG.md` under `[Unreleased]` (Step 6 from the original plan), running after pre-commit so
  it lands in the same commit.
- **Issue lifecycle conventions** — `backlog/issues/` → `backlog/completed/issues/` movement,
  the `improvement-NNN` prefix convention — all shipped and actively used (see
  `.claude/rules.md`'s "Issue Lifecycle" section).

## Deliberate deviation from the original design

**The pre-commit hook does NOT call `claude -p` automatically.** The original SPEC's entire
premise was: staged files → automatically mapped to doc targets → an LLM call updates them inline
→ staged → commit proceeds with docs and code atomic. What actually shipped instead: the hook
computes which doc targets *would* be affected and stages already-existing doc files, but explicitly
does **not** invoke Claude — the hook's own header comment states this outright: "Architecture doc
sync (`/sync-docs`) is NOT run automatically — run it manually after significant changes." Running
`/sync-docs` is a deliberate, user-triggered action, never automatic — this was a considered
reversal of the original design, not an unfinished step (see project memory: sync-docs is
manual-only by design).

## Not delivered / partial

- `docs/glossary.md`, `docs/env-reference.md` — proposed in the original baseline list, never
  created.
- `CHANGELOG.md` — the `commit-msg` hook mechanism to populate it exists and works, but the file
  itself does not exist in the repo yet (no conventional-commit-format commit has triggered it, or
  it was removed) — mechanism is live, output is empty.
- The original "Playwright coverage check" interactive prompt shipped in a reduced form: the hook
  asks a y/n question and appends a `<!-- needs-coverage -->` marker comment to
  `docs/test-coverage.md`, rather than the fuller auto-parsing of Playwright pass/fail output the
  original Step 7 described.

## Related

- `.claude/commands/sync-docs.md` — the shipped `/sync-docs` skill.
- `scripts/hooks/pre-commit`, `scripts/hooks/commit-msg`, `scripts/install-hooks.sh` — the shipped
  hook infrastructure.
