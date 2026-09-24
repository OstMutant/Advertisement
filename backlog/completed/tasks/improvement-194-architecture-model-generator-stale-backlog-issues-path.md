# improvement-194: `generate-architecture-model.sh` still hardcodes the renamed `backlog/issues/` path

**Type:** bug — stale generator script, breaks the `docs` CI stage on every run. Found via a real
`bash scripts/ci.sh` run failing its `docs` step, root-caused directly (2026-09-16).
**Module:** `docs/architecture/scripts/generate-architecture-model.sh`.
**Priority:** 🔵 low — silent, non-blocking for the Definition of Done (the `docs` stage isn't one
of the enumerated hard gates), but breaks on every CI run with `docs=true`, so worth a quick fix.
**When:** independent, no blockers.

## Problem

`generate-architecture-model.sh` (BACKLOG_SUMMARY node generation block, lines ~2001-2015) still
computes open/completed counts against `backlog/issues/` and `backlog/completed/issues/`:

```bash
open_count=$(find "$REPO_ROOT/backlog/issues" -maxdepth 1 -name "*.md" 2>/dev/null | wc -l | tr -d ' ')
```

Neither directory exists — this project renamed `backlog/issues/` to `backlog/tasks/` (and
`backlog/completed/issues/` to `backlog/completed/tasks/`) a while ago; root `CLAUDE.md`'s own
"Task Lifecycle" section has referred exclusively to `backlog/tasks/`/`backlog/completed/tasks/`
since. `generate-architecture-model.sh` itself was never updated for that rename — it was last
touched for an unrelated change, well after the rename happened.

Under the script's `set -euo pipefail`, `find` on a nonexistent path exits 1 (its own stderr
redirected to `/dev/null`), which fails the whole `open_count=$(...)` pipeline silently — no error
message anywhere, the script just aborts mid-flow right after regenerating `.claude/nav/adr-index.md`.
Confirmed by reproducing directly inside `ci-runner` (`bash -x` isolated the exact failing line).

Two embedded HTML/JS description strings (around lines 3311-3312) also still say "open issues
(backlog/issues/)" / "completed issues (backlog/completed/issues/)".

This breaks the `docs` stage of **any** `bash scripts/ci.sh` run with `docs=true` — not specific to
any particular code change, and not something a normal code-review would catch since it only
surfaces when the generator script itself actually runs.

## Suggested fix

- Point the two `find` calls (and their two matching embedded description strings) at
  `backlog/tasks`/`backlog/completed/tasks` instead of `backlog/issues`/`backlog/completed/issues`.
- Harden both `find | wc -l | tr` pipelines against a missing directory in general — either an
  explicit `[ -d ... ] &&` guard, or drop the `2>/dev/null` so a future case like this fails loudly
  (a clear error message) instead of aborting silently under `set -euo pipefail`.

## Related

- Discovered while verifying `improvement-193`'s own `bash scripts/ci.sh` run — unrelated to that
  task's actual diff (confirmed via `git log`: this generator script was last committed well before
  `improvement-193`'s changes, and none of the renamed-path history touches it either).

## Resolution (2026-09-16)

Fixed all 6 hardcoded references (not just the 2 named above — also caught the 2
`issue_list_json(...)` calls building `openIssues`/`completedIssues` at lines 2014-2015):
`backlog/issues` → `backlog/tasks`, `backlog/completed/issues` → `backlog/completed/tasks`,
everywhere in `generate-architecture-model.sh` (the 2 `find` calls, the 2 `issue_list_json` calls,
and the 2 embedded HTML description strings). Verified directly — ran
`bash docs/architecture/scripts/generate-architecture-model.sh --with-sonar --with-archunit`: exits
0, no longer aborts. Confirmed the regenerated `architecture-model.json`'s `BACKLOG_SUMMARY` node
carries correct real counts: `open_issues: 34` / `completed_issues: 174`, matching
`ls backlog/tasks/*.md | wc -l` / `ls backlog/completed/tasks/*.md | wc -l` exactly. Directory-
existence hardening (the suggested fix's second bullet) was not applied — not needed once the
paths point at real, permanent directories; left as-is rather than adding defensive code for a
scenario that no longer applies.
