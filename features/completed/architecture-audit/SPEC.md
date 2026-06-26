# Architecture Documentation Sync

Automatically keep all project documentation in sync with actual source code.
Triggered as a git pre-commit hook — runs before every commit, updates only what changed,
and stages the updated docs so they land in the same commit as the code.

**Rules:**
- Do NOT invent architecture.
- Do NOT infer components that do not exist.
- If something cannot be proven from code, mark it as UNKNOWN.
- All diagrams must use Mermaid.
- All conclusions must reference actual code.

---

## Trigger

`scripts/hooks/pre-commit` — git pre-commit hook (version-controlled, installed via `scripts/install-hooks.sh`).

Flow:
1. Read `git diff --name-only --cached` (staged files)
2. Map staged files to documentation targets (see Mapping table)
3. Call `claude -p` with focused prompt for each affected target group
4. `git add` updated docs files
5. **Playwright check** — prompt user if test coverage needs updating (see below)
6. Commit proceeds — code + docs in one atomic commit

---

## File → Documentation Mapping

| Staged file pattern | Documentation targets |
|---------------------|-----------------------|
| `**/pom.xml` | `docs/architecture/01-module-dependencies.md`, `docs/env-reference.md` |
| `platform-commons/**/spi/**`, `**/*Impl.java`, `**/*Port*.java`, `**/*Hook*.java` | `docs/architecture/02-spi-map.md` |
| Domain package changes (new packages, moved classes) | `docs/architecture/03-bounded-contexts.md`, `docs/glossary.md` |
| `**/db/changelog/**` | `docs/architecture/04-database-erd.md` |
| `**/*Service.java`, `**/*Repository.java` | `docs/architecture/05-sequence-diagrams.md` |
| Any `*.java` | `docs/architecture/06-coupling-analysis.md`, `07-risk-report.md`, `08-scorecard.md` |
| Any `*.java` or `**/pom.xml` | `CLAUDE.md` (per-module), `DECISIONS.md` (per-module), `README.md` |
| Any `*.java` or `**/pom.xml` | `features/issues/` — create/close/update issues based on detected violations |
| Any `*.java` in UI layer (`marketplace-app/ui/**`) | Playwright coverage check (interactive prompt) |
| `**/db/changelog/**` or `**/*Service.java` | Playwright coverage check (interactive prompt) |

---

## Architecture Diagrams (`docs/architecture/`)

| File | Content |
|------|---------|
| `01-module-dependencies.md` | Mermaid graph — modules + Maven dependency direction |
| `02-spi-map.md` | Mermaid graph — every `*Port` / `*Hook` + implementations |
| `03-bounded-contexts.md` | Context map — domain modules, upstream/downstream, shared kernel |
| `04-database-erd.md` | Mermaid ER diagram — tables, PKs, FKs, bridges (from Liquibase only) |
| `05-sequence-diagrams.md` | Sequence diagrams — create, update, restore, timeline (real class names) |
| `06-coupling-analysis.md` | Forbidden direct deps, layer violations, cyclic deps |
| `07-risk-report.md` | Largest modules, fan-in/fan-out, god classes, over-engineering |
| `08-scorecard.md` | Modularity, coupling, cohesion, SPI design, domain isolation, DB design, testability |

---

## Living Documentation Audit

During diagram generation the agent reads `DECISIONS.md`, `CLAUDE.md`, `README.md`,
`docs/glossary.md`, `docs/env-reference.md`, and `docs/test-coverage.md`
alongside the source code and cross-validates them. Documentation is updated in-place.

### `features/issues/` — issue lifecycle

The agent maintains issues in `features/issues/` using a prefix convention:

| Prefix | Meaning |
|--------|---------|
| `bug-NNN-*.md` | Known defect, must be fixed |
| `improvement-NNN-*.md` | Enhancement or architectural debt |
| `goal-NNN-*.md` | Open design goal not yet implemented |
| `fail-NNN-*.md` | Known test failure or edge case |

**During audit — issue processing rules:**

- **Create** a new issue file when a violation, regression, or new open goal is detected in code
  that is not yet tracked in `features/issues/`
- **Close** an issue (move to `features/completed/issues/NNN-title.md`) when the code confirms
  the problem no longer exists — do not delete, preserve for history
- **Update** an issue file when scope, constraints, or trigger conditions change
- **Never duplicate** — if an issue already exists, update it rather than create a new one
- **Link from DECISIONS.md** — any open goal or known violation in DECISIONS.md must be a
  one-liner reference `→ [issue-NNN](../features/issues/issue-NNN-*.md)`, not inline prose

**Issue file format:**
```markdown
# prefix-NNN: Short title

**Type:** bug | improvement | goal | fail
**Module:** which module owns the fix
**Priority:** high | medium | low — one-line rationale

## Problem
What is wrong or missing. Reference actual class names.

## Implementation trigger (for deferred items)
What condition justifies starting this work.
```

### `DECISIONS.md` (per module) — highest priority

Each entry must follow structured ADR format:
```
## ADR-NNN: Title
Status: Accepted | Superseded by ADR-NNN | Deprecated
Context: ...
Decision: ...
Consequences: ...
```

Active audit — not just append:
- **Remove** entries that describe patterns no longer present in code
- **Remove** entries superseded by a newer decision without annotation
- **Remove** open goals already realized (or mark them done with date if historical context matters)
- **Replace** inline bug/improvement/goal descriptions with `→ [issue-NNN](../features/issues/...)` links
- **Update** entries where the described class/method/table was renamed or moved
- **Update** `Status` field when a decision is superseded or deprecated
- **Add** new entry only when a genuinely new architectural constraint or pattern is introduced
- Result: every remaining entry must be verifiable against current source code; verbose inline issues live in `features/issues/`

### `CLAUDE.md` (per module)

Validation pass — correct stale facts:
- Package root matches actual directory structure
- "What it owns" lists only classes that actually exist
- Key constraints reflect actual code (if a constraint was lifted, remove it)
- Do not rewrite prose — only fix factually wrong statements

### `README.md` (root)
- Verification only — if nothing architectural changed, README is not touched
- One auto-managed block between marker comments:
  ```markdown
  <!-- arch:start -->
  ```mermaid
  graph LR
    ...
  ```
  → Full architecture docs: [docs/architecture/](docs/architecture/)
  <!-- arch:end -->
  ```
- Hook replaces only the content between `<!-- arch:start -->` and `<!-- arch:end -->`
- Block contains: module dependency diagram (`01`) + link to `docs/architecture/`
- All other README content is never touched

### `docs/glossary.md`

Canonical domain terms — one entry per concept:
```
## Taxon
A node in the category tree. Has a parent (except root), a name, and a slug.
Used in: CategoryRepository, AdvertisementFilterDto, CategoryPort.
```
- **Add** term when a new domain concept appears in code (new entity, new DTO concept)
- **Update** definition when the concept is renamed or its scope changes
- **Remove** term when the concept is deleted from the codebase
- Cross-reference actual class names so the glossary stays verifiable

### `docs/env-reference.md`

Complete environment variable reference — single source of truth:
```
| Variable       | Description              | Default (dev)     | Required in prod |
|----------------|--------------------------|-------------------|-----------------|
| DB_HOST        | PostgreSQL host          | localhost         | ✅               |
```
- Source: `docker-compose*.yml`, `application*.properties`, `CLAUDE.md` env sections
- **Add** variable when new infra or feature introduces one
- **Remove** variable when it is no longer referenced in code or config
- Consolidates what is currently scattered across multiple files

### `docs/test-coverage.md`

Playwright test coverage map — which UI flows are covered and which are not:
```
## Advertisements
- [x] create advertisement — spec 03
- [x] edit advertisement — spec 03
- [ ] bulk delete
```
- Updated automatically after each Playwright run (via hook or `/playwright` command)
- Lists covered flows with spec file reference, uncovered flows with `[ ]`
- Serves as a checklist when adding new features

### `CHANGELOG.md` (root)

Auto-generated from conventional commit messages (`feat:`, `fix:`, `refactor:`, `docs:`).
Format: Keep a Changelog (https://keepachangelog.com).
- Updated by hook on every commit — new entry prepended under `[Unreleased]`
- On release: `[Unreleased]` section renamed to the version tag

---

## Playwright Coverage Check (interactive)

When staged files include UI changes (`marketplace-app/ui/**`) or backend changes
that affect user-visible flows (`*Service.java`, `db/changelog/**`), the hook
**does not block** but prints an interactive prompt before committing:

```
⚠  UI/backend changes detected. Do Playwright tests need updating?
   Changed flows: AdvertisementsView, CategoryFilter
   Coverage map: docs/test-coverage.md

   [y] Yes — open test-coverage.md and remind me after commit
   [n] No — proceed
   [s] Skip this check permanently for this session
```

- If `y`: hook prints a reminder, stages `docs/test-coverage.md` with a `[ ] needs coverage` marker,
  then proceeds with commit. Developer updates tests in a follow-up commit.
- If `n`: proceeds silently.
- Non-interactive environments (CI): check is skipped automatically (`[ -t 0 ]` guard).

---

## Slash Command

`.claude/commands/audit-diff.md` — manual trigger for the same logic.
Usage: `/audit-diff` (defaults to `main` as base) or `/audit-diff <sha>`.

---

## Artifact Details

### 01 — Module Dependency Diagram

Mermaid `graph LR`:
- `marketplace-app`, `platform-commons`, all `*-spring-boot-starter` modules
- actual Maven `<dependency>` direction only

### 02 — SPI Dependency Map

Mermaid `graph TD` — every SPI interface (`*Port`, `*Hook`) and every implementation.
Source: `platform-commons` SPI packages + starter `*Impl` classes.

### 03 — Bounded Context Diagram

Domains: User, Advertisement, Audit, Attachment, Category/Taxon.
Show upstream/downstream, integration points, shared kernel.

### 04 — Database ERD

Source: all `db/changelog/changes/*.sql` Liquibase files.
Tables, PKs, FKs, many-to-many bridges. No guessing.

### 05 — Timeline / Audit Flow

Sequence diagrams: entity creation, update, restore, timeline query.
Trace real code path: View → Service → Repository → Audit.

### 06 — Coupling Analysis

- Forbidden direct deps (internal class imports across module boundaries)
- Layer violations: UI → Repository, UI → JDBC, Starter → UI
- Cyclic dependencies

### 07 — Architectural Risk Report

Ranked by severity: largest modules, highest fan-in/fan-out,
excessive constructor deps, god classes, over-engineering areas.

### 08 — Architecture Scorecard

Score with reasoning: modularity, coupling, cohesion, SPI design,
domain isolation, database design, testability.
