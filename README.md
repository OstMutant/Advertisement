# Advertisement Platform

A personal backend project used as an architectural playground — for exploring trade-offs,
experimenting with patterns, and developing engineering intuition.

🚀 **Live Demo:** [advertisement-dtdg.onrender.com](https://advertisement-dtdg.onrender.com/)  
*(Note: Hosted on Render's free tier. Initial load may take up to 50 seconds if the instance is sleeping).*

---

## About

This is not a finished product. There is no fixed feature roadmap.

The focus is on **how** things are built rather than **what** is built:
- explicit control over data flow and SQL
- composable abstractions without framework magic
- clear responsibility boundaries between layers

The project evolves as ideas and architectural directions change.

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

## Deployment

| Service | Role |
|---|---|
| [Render](https://render.com) | Application hosting |
| [Neon](https://neon.tech) | Serverless PostgreSQL |
| [Supabase Storage](https://supabase.com/storage) | S3-compatible file storage |
| [UptimeRobot](https://uptimerobot.com) | Keeps the free-tier instance alive |

### Deploying to Render
Please note that **Render ignores `scripts/infra/docker-compose.app.yml`** and builds the application directly from the `Dockerfile`.

To successfully deploy the app, you **must** manually add the following Environment Variables in your Render Web Service dashboard (under the "Environment" tab):

1. `SPRING_PROFILES_ACTIVE` = `prod` *(Crucial: prevents the app from falling back to `dev` and trying to connect to `localhost`)*
2. Database credentials (e.g., from Neon):
    - `DB_HOST`
    - `DB_NAME`
    - `DB_USER`
    - `DB_PASSWORD`
    - `DB_SSL_PARAMS` = `?sslmode=require&channel_binding=require`
3. Storage credentials (e.g., from Supabase Storage):
    - `S3_ENDPOINT`
    - `S3_BUCKET`
    - `S3_ACCESS_KEY`
    - `S3_SECRET_KEY`
    - `S3_REGION`
    - `S3_PUBLIC_URL`

Render will automatically inject the `PORT` variable, which the application uses to bind the web server.

---

## Running Locally

The project uses three separate Docker Compose files:

| File | Purpose |
|---|---|
| `scripts/infra/docker-compose.db.yml` | PostgreSQL |
| `scripts/infra/docker-compose.minio.yml` | MinIO (S3-compatible storage, emulates Supabase Storage) |
| `scripts/infra/docker-compose.app.yml` | Application (production build) |

### Option 1 — Dev mode (run from IDE)

Start only the infrastructure:

```bash
docker-compose -f scripts/infra/docker-compose.db.yml -f scripts/infra/docker-compose.minio.yml up -d
```

Then run the application from your IDE with the `dev` Spring profile active.  
The `dev` profile connects to `localhost:5432` and `localhost:9000`.

MinIO console: http://localhost:9001 — login: `admin` / `admin12345`  
The `advertisement` bucket is created automatically on first start.

Open the app: http://localhost:8080

### Option 2 — Full Docker (local production simulation)

**Recommended path:** `bash scripts/deploy.sh` — the canonical, actively-maintained way to run
the full production build locally (BuildKit caching, automatic Docker garbage pruning, startup
detection, `--reset`/`--restart-infra`/`--reset-db`/`--no-cache` flags). See
[`scripts/README.md`](scripts/README.md) and [`scripts/CLAUDE.md`](scripts/CLAUDE.md) for details.
**This starts the app on port 8081**, not 8080 (8080 is reserved for Option 1's IDE dev mode).

The raw `docker-compose.app.yml` file below also exists and works, but publishes on **port 8080**
(a different port than `deploy.sh`) and has none of `deploy.sh`'s caching/pruning/flag support —
prefer `deploy.sh` unless you specifically need the bare compose file:

```bash
docker-compose -f scripts/infra/docker-compose.db.yml -f scripts/infra/docker-compose.minio.yml -f scripts/infra/docker-compose.app.yml up --build
```

To stop and remove volumes:

```bash
docker-compose -f scripts/infra/docker-compose.db.yml -f scripts/infra/docker-compose.minio.yml -f scripts/infra/docker-compose.app.yml down -v
```

---

## Helper Scripts

All developer scripts live in `scripts/`. See [`scripts/README.md`](scripts/README.md) for full details.

| Script | Purpose |
|---|---|
| `scripts/deploy.sh` / `scripts/deploy.bat` | Full deploy pipeline: pull images → start infra → build → run → wait for startup |
| `scripts/playwright.sh` / `scripts/playwright.bat` | Run Playwright tests (delegates to `playwright/run.sh`) |
| `scripts/sonar.sh` / `scripts/sonar.bat` | Run SonarQube analysis (delegates to `scripts/sonar/run.sh`) |
| `scripts/clean.bat` | Remove Maven `target/` directories and Vaadin generated files |
| `scripts/collect-code.bat` | Collect all source files into a single `all-code.txt` for AI analysis |
| `scripts/claude.bat` | Start Claude Code container with project and auth mounts |

```bash
bash scripts/deploy.sh                  # default: skip already-running containers
bash scripts/deploy.sh --reset          # wipe everything and start from scratch
bash scripts/deploy.sh --restart-infra  # restart DB + MinIO, redeploy app
```

All scripts resolve the project root automatically — run them from any directory.

---

## Database Scripts

All database scripts live in `scripts/database/`:

| File | Purpose |
|---|---|
| `scripts/database/reset-clean.sql` | Truncates all tables (no seed data). Run automatically by `playwright/run.sh` before every test run. |

---

## Environment Variables

Key variables used by the application.
* For **local Docker testing**, configure them in `scripts/infra/docker-compose.app.yml`.
* For **Production (Render)**, set them directly in the cloud provider's dashboard.

| Variable | Description | Example |
|---|---|---|
| `DB_HOST` | PostgreSQL host | `db` / `ep-xxx.neon.tech` |
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

## Status & Roadmap

Actively evolving. Architectural decisions may be revisited, implementations replaced.

Planned directions:
- Extend rule-based validation capabilities
- Improve composability of the generic filtering layer
- Introduce integration and contract tests
- Explore alternative API adapters (REST)

---

## Author's Note

I value clarity over convenience.  
I prefer explicitness over magic.  
I build systems to be understood, not just used.

Feedback and architectural discussions are welcome.
