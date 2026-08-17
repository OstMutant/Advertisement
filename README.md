# Advertisement Platform

A production-oriented service marketplace, built as a hands-on playground for exploring backend
and architectural trade-offs in a real, working system rather than a toy example.

[Engineering Highlights](#engineering-highlights) · [Architecture](#architectural-principles) · [Module Docs](#module-layout) · [Testing Strategy](#testing-strategy)

---

## What is it?

A marketplace where users publish service/product listings, browse and filter a shared catalog,
and administrators moderate everything through a full audit trail. Every module doubles as a
demonstration of one specific engineering pattern — SPI-based module decoupling, immutable audit
snapshots, optimistic concurrency, SQL without an ORM — applied to a real feature, not an isolated
sample.

## What can users do?

- Create and manage listings — rich HTML descriptions (sanitized server-side), photos, video
- Browse the catalog with dynamic filter/sort/pagination by category, city, and listing type
- Sign up, manage account settings (locale, page sizes), edit or restore a profile
- Share a listing link with a rich social-media preview (Open Graph, JSON-LD)
- As an admin/moderator: review every change through a per-entity activity timeline with
  field-level diffs, restore prior versions, manage categories and taxonomy

---

## Engineering Highlights

**Optimistic concurrency** — `Advertisement`, `Taxon`, and `User` updates carry a `version` column;
a stale write is rejected with `OptimisticLockingFailureException` instead of silently overwriting
a concurrent change.

**Auditing** — every domain write is captured as an immutable, versioned snapshot, not a mutable
log line. Snapshots are diffed at read time into a field-level activity timeline, so "what
changed" is always derived from real before/after state, never hand-maintained.

**Attachment lifecycle** — uploads go to S3-compatible storage with transactional metadata (a
failed post-save step rolls back the DB row, verified by a real-transaction Testcontainers test),
scheduled cleanup of orphaned objects, snapshot-based restore, and audit integration.

**Testing** — three layers, each catching a different class of regression: plain JUnit for pure
logic, Testcontainers-backed repository tests against a real Postgres for SQL correctness, and
Playwright for full browser-driven end-to-end flows.

---

## About

This is not a finished product — there is no fixed public feature roadmap, and the product side
keeps evolving. The engineering foundation underneath it is the actual point of the project:
- explicit control over data flow and SQL
- composable abstractions without framework magic
- clear responsibility boundaries between layers

---

## Architectural Principles

**Explicit over implicit**  
No ORM, no JPA. All SQL is written manually via Spring JDBC.
No hidden query generation or implicit persistence behavior.

**Immutable data flow**  
Entities and DTOs are immutable. No shared mutable state between layers.
Data transformations are predictable and traceable.

**UI as a thin adapter**  
Vaadin handles layout and interaction wiring only.
No business logic lives inside UI components.

**Declarative where it matters**  
Validation rules, localization keys, and filter definitions are expressed
declaratively and kept strongly typed.

---

## Module Layout

```
advertisement-parent
├── query-lib                         — framework-agnostic SQL query-building library
├── integration-tests                 — Testcontainers repository tests + fixtures (test-only)
├── platform-commons                  — shared kernel: DTOs, domain events, SPI interfaces
├── audit-spring-boot-starter         — audit subsystem: write side + read side
├── attachment-spring-boot-starter    — photo/attachment module + S3 storage
├── user-spring-boot-starter          — User domain + Spring Security integration
├── advertisement-spring-boot-starter — Advertisement domain
├── taxon-spring-boot-starter         — Taxonomy domain: categories, tags, classifiers
└── marketplace-app                   — Vaadin application (all UI)
```

Per-module documentation:

| Module | README | Decisions |
|---|---|---|
| query-lib | [README](query-lib/README.md) | [DECISIONS](query-lib/DECISIONS.md) |
| integration-tests | [README](integration-tests/README.md) | [DECISIONS](integration-tests/DECISIONS.md) |
| platform-commons | — | [DECISIONS](platform-commons/DECISIONS.md) |
| audit-spring-boot-starter | [README](audit-spring-boot-starter/README.md) | [DECISIONS](audit-spring-boot-starter/DECISIONS.md) |
| attachment-spring-boot-starter | [README](attachment-spring-boot-starter/README.md) | [DECISIONS](attachment-spring-boot-starter/DECISIONS.md) |
| user-spring-boot-starter | [README](user-spring-boot-starter/README.md) | — |
| advertisement-spring-boot-starter | [README](advertisement-spring-boot-starter/README.md) | — |
| taxon-spring-boot-starter | — | [DECISIONS](taxon-spring-boot-starter/DECISIONS.md) |
| marketplace-app | [README](marketplace-app/README.md) | [DECISIONS](marketplace-app/DECISIONS.md) |
| playwright | [README](playwright/README.md) | [DECISIONS](playwright/DECISIONS.md) |
| scripts | [README](scripts/README.md) | [DECISIONS](scripts/DECISIONS.md) |

---

## Key Technical Decisions

| Decision | Reason |
|---|---|
| Spring JDBC over JPA | Full control over queries, no hidden side effects |
| Composable filter model | Type-safe, reusable query logic without ORM abstractions |
| Immutable entities (`@Value` + `@Builder`) | Predictable state, no accidental mutation |
| Enum-based i18n keys | Compile-time safety for localization strings |
| Rule-oriented validation | Validation logic isolated from UI and service layers |
| SPI extension pattern | Starters extend app behaviour without knowing each other |

---

## Feature Highlights

- **Advertisements** — CRUD with ownership checks, dynamic filter/sort/pagination, HTML description sanitization (OWASP HTML Sanitizer), soft delete + restore, optimistic locking.
- **Users** — registration with rate limiting, role-based access (Admin/Moderator/User), per-user settings, profile edit and restore.
- **Taxonomy** — categories/tags with per-locale translations, soft-deletable, many-to-many assignment to any entity type.
- **Attachments** — photo/video uploads to S3-compatible storage, YouTube embeds, media history for restore.
- **Audit trail** — every domain write captured as a versioned snapshot; per-entity timeline and diff view, per-actor activity feed.
- **i18n** — English/Ukrainian, enum-based translation keys (missing keys fail fast, never a silent fallback).
- **Deep links & rich previews** — shareable advertisement links with Open Graph meta tags and JSON-LD for social/search previews.

---

## Testing Strategy

Three independent layers, each targeting a different failure mode:

| Layer | Tool | What it catches |
|---|---|---|
| Unit | Plain JUnit 5 (+ Mockito) | Pure logic regressions — no Docker, no database |
| Integration | JUnit 5 + Testcontainers (real Postgres) | SQL correctness — filters, sorts, pagination, optimistic locking, real Liquibase schema |
| End-to-end | Playwright | Full browser-driven flows across the actual Vaadin UI, including auth, CRUD, media, and the audit timeline |

See [Module Layout](#module-layout) above for each layer's own README/DECISIONS, and
`scripts/README.md` for how to run each one (or all three orchestrated together via
`scripts/run-all-tests.sh` / `scripts/ci.sh`).

---

## Running Locally

The project uses three separate Docker Compose files:

| File | Purpose |
|---|---|
| `scripts/deploy-and-run/docker-compose.db.yml` | PostgreSQL |
| `scripts/deploy-and-run/docker-compose.minio.yml` | MinIO (S3-compatible storage, emulates Supabase Storage) |
| `scripts/deploy-and-run/docker-compose.app.yml` | Application (production build) |

### Option 1 — Dev mode (run from IDE)

Start only the infrastructure:

```bash
docker-compose -f scripts/deploy-and-run/docker-compose.db.yml -f scripts/deploy-and-run/docker-compose.minio.yml up -d
```

Then run the application from your IDE with the `dev` Spring profile active.  
The `dev` profile connects to `localhost:5432` and `localhost:9000`.

MinIO console: http://localhost:9001 — login: `admin` / `admin12345`  
The `advertisement` bucket is created automatically on first start.

Open the app: http://localhost:8080

### Option 2 — Full Docker (local production simulation)

**Recommended path:** `bash scripts/deploy-and-run.sh` — the canonical, actively-maintained way to run
the full production build locally (BuildKit caching, automatic Docker garbage pruning, startup
detection, `--reset`/`--restart-infra`/`--reset-only-db`/`--no-cache` flags). See
[`scripts/README.md`](scripts/README.md) and [`scripts/CLAUDE.md`](scripts/CLAUDE.md) for details.
**This starts the app on port 8081**, not 8080 (8080 is reserved for Option 1's IDE dev mode).

The raw `docker-compose.app.yml` file below also exists and works, but publishes on **port 8080**
(a different port than `deploy-and-run.sh`) and has none of `deploy-and-run.sh`'s caching/pruning/flag support —
prefer `deploy-and-run.sh` unless you specifically need the bare compose file:

```bash
docker-compose -f scripts/deploy-and-run/docker-compose.db.yml -f scripts/deploy-and-run/docker-compose.minio.yml -f scripts/deploy-and-run/docker-compose.app.yml up --build
```

To stop and remove volumes:

```bash
docker-compose -f scripts/deploy-and-run/docker-compose.db.yml -f scripts/deploy-and-run/docker-compose.minio.yml -f scripts/deploy-and-run/docker-compose.app.yml down -v
```

---

## Helper Scripts

All developer scripts live in `scripts/`. See [`scripts/README.md`](scripts/README.md) for full details.

| Script | Purpose |
|---|---|
| `scripts/deploy-and-run.sh` / `scripts/deploy-and-run.bat` | Full deploy pipeline: pull images → start infra → build → run → wait for startup |
| `scripts/run-local.bat` | Run the app via Maven with no Docker image rebuild (dev or prod Vaadin mode) |
| `scripts/build-and-test.sh` / `scripts/build-and-test.bat` | Build the whole reactor, optionally run unit/integration tests in parallel — no local Java needed |
| `scripts/playwright.sh` / `scripts/playwright.bat` | Run Playwright end-to-end tests (delegates to `playwright/run.sh`) |
| `scripts/run-all-tests.sh` / `scripts/run-all-tests.bat` | `build-and-test.sh` (unit + integration in parallel) plus Playwright in parallel with that, one command |
| `scripts/sonar.sh` / `scripts/sonar.bat` | Run SonarQube static analysis (delegates to `scripts/sonar/run.sh`) |
| `scripts/ci.sh` / `scripts/ci.bat` | Isolated local CI pipeline — unit + integration + e2e + Sonar in one backgrounded pass |
| `scripts/clean.bat` | Remove Maven `target/` directories and Vaadin generated files |
| `scripts/collect-code.bat` | Collect all source files into a single `all-code.txt` for AI analysis |
| `scripts/claude.bat` | Start Claude Code container with project and auth mounts |

```bash
bash scripts/deploy-and-run.sh                  # default: skip already-running containers
bash scripts/deploy-and-run.sh --reset          # wipe everything and start from scratch
bash scripts/deploy-and-run.sh --restart-infra  # restart DB + MinIO, redeploy app
```

All scripts resolve the project root automatically — run them from any directory.

---

## AI-Assisted Development Workflow

This project is built and maintained with [Claude Code](https://claude.com/claude-code) as an
active part of the engineering process, not just autocomplete. A set of custom slash commands
wraps the day-to-day loop — build, test, document, review — into single, repeatable steps:

| Command | Purpose |
|---|---|
| `/build-and-test` | Build the reactor (+ optional tests) |
| `/deploy-and-run` | Rebuild the Docker image and start the app |
| `/playwright` | Run the Playwright end-to-end suite |
| `/sonar` | Run SonarQube static analysis |
| `/run-all-tests` | Unit tests → integration tests sequentially, Playwright in parallel |
| `/ci` | Full isolated CI pipeline — unit + integration + e2e + Sonar, backgrounded |
| `/sync-docs` | Reconcile architecture docs (`DECISIONS.md`/`CLAUDE.md`) with the actual code |
| `/decision` | Record a new architectural decision in the relevant module's `DECISIONS.md` |
| `/feature` | Scaffold a new tracked backlog issue, ranked into the priority list |
| `/autopilot` | Approve a scoped task once, then implement + test + document it end-to-end |

Every module keeps its own `DECISIONS.md` (an ADR log — why, not just what) and `CLAUDE.md` (the
working agreement for changes in that module). Those same documents drive the AI-assisted workflow
and double as onboarding notes for a human contributor — one source of truth either way.

### Running the AI dev container

`scripts/claude.bat` starts Claude Code itself in an isolated Docker container (built from
`Dockerfile.ai`), with the project directory and a per-account auth config mounted:

```bat
scripts\claude.bat your.email@gmail.com
```

Chat history and project context are shared across accounts (same mounted project directory);
only the auth config folder is per-account, so switching accounts on rate limits keeps the
conversation going.

Once the container is up: running the deploy script (`scripts/deploy-and-run.sh`) to build and start the
app, then the Playwright script (`scripts/playwright.sh`) against it, is already a complete,
working test environment — real Postgres, real MinIO, a real production-mode build, and a full
browser-driven end-to-end suite — with no extra setup beyond those two commands.

---

## Database Scripts

All database scripts live in `scripts/deploy-and-run/`:

| File | Purpose |
|---|---|
| `scripts/deploy-and-run/reset.sh` / `scripts/deploy-and-run/reset.bat` | Truncates all application data without restarting the app or touching MinIO volumes (~1s) — runs `reset-clean.sql` |
| `scripts/deploy-and-run/reset-clean.sql` | Truncates all tables (no seed data). Run automatically by `playwright/run.sh` before every test run. |

---

## Environment Variables

Key variables used by the application.
* For **local Docker testing**, configure them in `scripts/deploy-and-run/docker-compose.app.yml`.
* For **production**, set them directly in the hosting provider's dashboard/secrets manager.

| Variable | Description | Example |
|---|---|---|
| `DB_HOST` | PostgreSQL host | `db` / a managed Postgres hostname |
| `DB_PORT` | PostgreSQL port | `5432` |
| `DB_NAME` | Database name | `experiments` |
| `DB_USER` | Database user | `experiments_user` |
| `DB_PASSWORD` | Database password | — |
| `DB_SSL_PARAMS` | Optional SSL query params | `?sslmode=require` |
| `S3_ENDPOINT` | S3-compatible storage endpoint | `http://minio:9000` |
| `S3_BUCKET` | Bucket name | `advertisement` |
| `S3_ACCESS_KEY` | S3 access key | — |
| `S3_SECRET_KEY` | S3 secret key | — |
| `S3_REGION` | S3 region | `us-east-1` / `auto` |
| `S3_PUBLIC_URL` | Public base URL for file access | `http://localhost:9000/advertisement` |

---

## Running Without Docker

Requires a running PostgreSQL instance and a running MinIO instance (or any S3-compatible storage)
matching the `application-dev.yml` config.

```bash
git clone https://github.com/OstMutant/Advertisement.git
cd Advertisement
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## Roadmap

Actively evolving on both sides: the engineering foundation keeps absorbing new patterns
(the audit/attachment/taxon starters, Testcontainers-based integration tests, and the isolated
local CI runner are all recent additions), and the product surface keeps growing on top of it.
Architectural decisions may be revisited and implementations replaced — that's the point of
treating this as a playground, not a frozen codebase.

Planned directions:
- Extend rule-based validation capabilities
- Improve composability of the generic filtering layer
- Explore alternative API adapters (REST)
- Broaden the marketplace's public-facing feature set (provider profiles, richer discovery)

---

## Author's Note

I value clarity over convenience.  
I prefer explicitness over magic.  
I build systems to be understood, not just used.

Feedback and architectural discussions are welcome.
