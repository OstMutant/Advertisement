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
