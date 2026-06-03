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
Always use project scripts — never raw docker/mvn commands:
- `bash scripts/deploy-dev.sh` — dev deploy (JAR hot-swap, ~3-4 min)
- `bash scripts/deploy.sh` — full rebuild (~7-10 min)
- `bash scripts/playwright.sh [scenario]` — Playwright tests
- `mvn clean test 2>&1 | tee /tmp/test.log` — JUnit tests

**Run all scripts synchronously** (no background, no awk pipe, no polling):
- Use `timeout: 600000` on the Bash tool call
- Full output is visible directly — no filtering, no grep
- User sees all logs as-is

**Before running Playwright** — kill old processes first:
1. `docker exec pw-runner pkill -f "node.*playwright" 2>/dev/null; true`
2. Then run: `bash scripts/playwright.sh [scenario] 2>&1`

## After Interruption
After any [Request interrupted by user] — full stop. No further tool calls, no continuation, no fixes.
Wait for the next explicit user message before doing anything.

## Error Reporting
When running any script or command that fails, immediately read the error output and show the specific error lines in the chat. Never just report "it failed" without the actual error details.
