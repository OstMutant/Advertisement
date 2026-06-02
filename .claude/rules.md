> ## ⛔ NEVER commit without explicit user request
> `git commit` is **forbidden** unless the user says "зроби коміт", "commit", or equivalent.
> `git add` runs automatically after every file change — commit does NOT.
> Violating this rule has happened multiple times. No exceptions.

## Approval Rule
**Every action must be approved by the user before execution — no exceptions.**

Before doing anything, generate and present a detailed prompt — the exact instruction you would give yourself to execute the action. This prompt must include:
- Full file paths
- Exact changes (method signatures, SQL, config values, field names)
- Any side-effects or follow-up steps

Present the prompt, then **STOP and wait for explicit confirmation** before executing.

Example format:
> "Edit `/full/path/File.java`: replace method `getMediaActivity(Long userId)` with `merge(Long userId, List<ActivityItemDto> baseItems)` — do it?"

Wait for explicit confirmation before making any change.

## Module Import Rules

**No direct imports between sibling modules.**
- Starters must NOT import from marketplace or from each other.
- Marketplace may import from starters only via platform-commons contracts (Ports/Hooks/DTOs)
  and published UI components — never via internal impl classes (util, service, repository).

## Git Workflow
- `git add` — run automatically after every file change
- `git commit` — **ONLY** when the user explicitly says to commit — never otherwise

## Language
All repository content must be in **English**: code comments, Javadoc, README files, commit messages, Playwright test descriptions, and any other text checked into the repository.

## Test Coverage After Bug Fixes
After fixing a bug, cover all affected flows with Playwright tests before marking the task complete.

## Scripts
Always use project scripts for build, deploy, and tests — never raw docker/mvn commands:
- `bash scripts/deploy-dev.sh` — **default deploy**: fast JAR hot-swap (requires container running); use this unless a full image rebuild is explicitly needed
- `bash scripts/deploy.sh` — full Docker image rebuild; only when Dockerfile/dependencies change or `--reset` (DB wipe) is needed
- `bash scripts/playwright.sh [scenario]` — Playwright tests
- `mvn clean test` — JUnit tests

**For any long-running command** (builds, tests, docker operations): use `run_in_background: true`, then immediately attach the Monitor tool. Monitor timeout: `timeout_ms: 600000` (10 min).

Standard Monitor pattern for builds (log: `/tmp/deploy-dev.log`):
```
out=/tmp/deploy-dev.log
tail -f "$out" | grep --line-buffered -E "ERROR|BUILD|FAILED|Started|Exception|\[INFO\] Building |\[INFO\] ---" &
pid=$!
while kill -0 $pid 2>/dev/null; do
  sleep 30
  echo "⏳ still building... ($(wc -l < "$out") lines so far)"
done
```

Standard Monitor pattern for tests (`mvn clean test 2>&1 | tee /tmp/test.log`, log: `/tmp/test.log`):
```
out=/tmp/test.log
tail -f "$out" | grep --line-buffered -E "ERROR|FAILED|Tests run|BUILD|Exception|\[ERROR\]" &
pid=$!
while kill -0 $pid 2>/dev/null; do
  sleep 30
  echo "⏳ still testing... ($(wc -l < "$out") lines so far)"
done
```

Standard Monitor pattern for Playwright (`bash scripts/playwright.sh [scenario] > /tmp/pw.log 2>&1`, log: `/tmp/pw.log`):
```
out=/tmp/pw.log
tail -f "$out" | grep --line-buffered -E "passed|failed|flaky|✓|✘|Error|ERROR|FAILED" &
pid=$!
while kill -0 $pid 2>/dev/null; do
  sleep 30
  echo "⏳ running... ($(wc -l < "$out") lines so far)"
done
```

## Error Reporting
When running any script or command that fails, immediately read the error output and show the specific error lines in the chat. Never just report "it failed" without the actual error details.
