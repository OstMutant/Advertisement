Analyze what changed in the codebase and update only the affected architecture documentation.

Usage:
  /sync-docs          — compare current HEAD against origin/main
  /sync-docs <ref>    — compare current HEAD against <ref> (branch, tag, or SHA)

---

## Step 1 — Determine changed files

Run: `git diff --name-only <ref>...HEAD`
Default ref: `origin/main`

If no Java, SQL, or pom.xml files changed → print "No architectural changes detected." and stop.

---

## Step 2 — Map changed files to documentation targets

Use this mapping table:

| Changed file pattern | Documentation targets |
|----------------------|-----------------------|
| `**/pom.xml` | `docs/architecture/01-module-dependencies.md` |
| `platform-commons/**/spi/**`, `**/*Port*.java`, `**/*Hook*.java`, `**/*Impl.java` | `docs/architecture/02-spi-map.md` |
| New packages or moved domain classes | `docs/architecture/03-bounded-contexts.md` |
| `**/db/changelog/**` | `docs/architecture/04-database-erd.md` |
| `**/*Service.java`, `**/*Repository.java` | `docs/architecture/05-sequence-diagrams.md` |
| Any `*.java` | `docs/architecture/06-coupling-analysis.md`, `07-risk-report.md`, `08-scorecard.md` |
| Any `*.java` or `**/pom.xml` | `CLAUDE.md` (per changed module), `DECISIONS.md` (per changed module) |
| Any `*.java` or `**/pom.xml` | `features/issues/` — create/close/update tracked issues |

Print which targets are affected before proceeding.

---

## Step 3 — Read actual source

For each affected target, read the relevant source files:
- For `01`: read all `pom.xml` files
- For `02`: read all files in `platform-commons/**/spi/` and `platform-commons/**/api/`; find `*PortImpl`, `*HookImpl`, `Default*Port` classes
- For `04`: read all Liquibase migration files in `**/db/changelog/changes/`
- For `05`: read changed `*Service.java` and `*Repository.java` files
- For `06/07/08`: read all changed Java files and scan for import violations

---

## Step 4 — Update affected files

**docs/architecture/ files** — rewrite only the sections that changed. Keep unchanged sections intact.

**DECISIONS.md** (per module) — ADR audit:
- Mark realized open goals as done (add date)
- Replace inline bug/improvement descriptions with `→ [issue-NNN](../features/issues/...)` links
- Add new ADR entry if a new architectural pattern or constraint was introduced
- Update Status of superseded entries
- Remove entries whose patterns no longer exist in code

**CLAUDE.md** (per module) — factual corrections only:
- Update "What it owns" if new classes were added or removed
- Fix stale package paths
- Do NOT rewrite prose

**features/issues/** — lifecycle:
- Create new issue file if a violation or open goal is detected that is not yet tracked
- Close issue (move to `features/completed/issues/`) if the code confirms it is resolved
- Update issue file if scope or constraints changed

**README.md** — update only the block between `<!-- arch:start -->` and `<!-- arch:end -->`:
- Replace the module dependency diagram with the updated one from `01-module-dependencies.md`
- Keep all other README content untouched

---

## Step 5 — Report

Print a summary:
- Which files were updated
- Which issues were created / closed / updated
- Which ADRs were added or status-changed
- Any new violations found

---

## Documentation Rules (from SPEC)

- Do NOT invent architecture — only document what exists in code
- If something cannot be proven from code, mark it UNKNOWN
- All diagrams must use Mermaid syntax
- All conclusions must reference actual class names or file paths
- ADR format: `## ADR-NNN: Title` + `Status:` + `Context / Decision / Consequences`
- Issue format: `prefix-NNN-title.md` with Type / Module / Priority / Problem sections
- Never duplicate issues — update existing ones rather than creating new
