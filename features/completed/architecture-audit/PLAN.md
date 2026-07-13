# Architecture Documentation Sync — Implementation Plan

## Step 1 — Structured ADR format in DECISIONS.md

Migrate all existing DECISIONS.md files to structured ADR format:
```
## ADR-NNN: Title
Status: Accepted | Superseded by ADR-NNN | Deprecated
Context: ...
Decision: ...
Consequences: ...
```

Files to migrate:
- `marketplace-app/DECISIONS.md`
- `audit-spring-boot-starter/DECISIONS.md`
- `attachment-spring-boot-starter/DECISIONS.md`
- `platform-commons/DECISIONS.md`
- `query-lib/DECISIONS.md`
- `playwright/DECISIONS.md`
- `scripts/DECISIONS.md`

During migration: remove stale entries, mark realized goals as done, assign sequential ADR numbers.

---

## Step 2 — Baseline docs generation (one-time)

Run `/audit-diff` once against the full codebase to generate initial files:

- `docs/architecture/01-module-dependencies.md`
- `docs/architecture/02-spi-map.md`
- `docs/architecture/03-bounded-contexts.md`
- `docs/architecture/04-database-erd.md`
- `docs/architecture/05-sequence-diagrams.md`
- `docs/architecture/06-coupling-analysis.md`
- `docs/architecture/07-risk-report.md`
- `docs/architecture/08-scorecard.md`
- `docs/glossary.md`
- `docs/env-reference.md`
- `docs/test-coverage.md`
- `CHANGELOG.md`

Add `<!-- arch:start -->` / `<!-- arch:end -->` block to `README.md` and populate it.

---

## Step 3 — Slash command `/audit-diff`

Create `.claude/commands/audit-diff.md`.

The command:
1. Accepts optional base ref (default: `main`)
2. Runs `git diff --name-only <ref>...HEAD`
3. Maps changed files to documentation targets per SPEC mapping table
4. Reads actual source for each affected target
5. Writes/updates only affected files
6. Reports what was changed and what was skipped

---

## Step 4 — Pre-commit hook script

Create `scripts/hooks/pre-commit`:

```bash
#!/usr/bin/env bash
set -euo pipefail

STAGED=$(git diff --name-only --cached)

# fast path — no source changes
if ! echo "$STAGED" | grep -qE '\.(java|xml|sql|properties|yml)$'; then
  exit 0
fi

# map staged files to doc targets and call claude -p
# ... mapping logic ...

# stage updated docs
git add docs/ CLAUDE.md DECISIONS.md README.md CHANGELOG.md 2>/dev/null || true

# playwright check (skip in non-interactive / CI)
if [ -t 0 ] && echo "$STAGED" | grep -qE 'marketplace-app/.*ui/|Service\.java|changelog/'; then
  # ... interactive prompt ...
fi
```

---

## Step 5 — Hook installer

Create `scripts/install-hooks.sh`:
```bash
#!/usr/bin/env bash
git config core.hooksPath scripts/hooks
chmod +x scripts/hooks/pre-commit
echo "Git hooks installed (core.hooksPath = scripts/hooks)"
```

Add to `README.md` setup section:
```
After cloning: bash scripts/install-hooks.sh
```

---

## Step 6 — CHANGELOG.md auto-update via commit-msg hook

Create `scripts/hooks/commit-msg` (separate from pre-commit — runs after pre-commit,
receives the finalized message file as $1).

Format:
```markdown
## [Unreleased]
### Added
- feat: ...
### Fixed
- fix: ...
```

---

## Step 7 — test-coverage.md auto-update after Playwright

Extend `scripts/playwright.sh` (or add a post-run hook) to update `docs/test-coverage.md`:
- Parse Playwright output for passed/failed test names
- Mark corresponding flows as `[x]` or `[ ]`
- `git add docs/test-coverage.md` if file changed

---

## Step 8 — Move to completed

When all steps are done and hook is working end-to-end:
```bash
mv /app/features/architecture-audit /app/features/completed/architecture-audit
```

---

## Open Questions

- `claude -p` latency per target group — if >2 min consider post-commit (async, non-blocking)
- DECISIONS.md: additive-only updates or allow removals? (SPEC says removals — confirm before Step 1)
- CHANGELOG.md: read commit message from `COMMIT_EDITMSG` (pre-commit hook runs before message is finalized) — may need to move to `post-commit`
- Which base ref in hook: `main`, `HEAD~1`, or last tag? Recommendation: `origin/main`
