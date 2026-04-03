# Advertisement Platform

A personal backend project used as an architectural playground — for exploring trade-offs,
experimenting with patterns, and developing engineering intuition.

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

## Key Technical Decisions

| Decision | Reason |
|---|---|
| Spring JDBC over JPA | Full control over queries, no hidden side effects |
| Composable filter model | Type-safe, reusable query logic without ORM abstractions |
| Immutable entities (`@Value` + `@Builder`) | Predictable state, no accidental mutation |
| Enum-based i18n keys | Compile-time safety for localization strings |
| Rule-oriented validation | Validation logic isolated from UI and service layers |

---

## Running Locally

The project uses three separate Docker Compose files:

| File | Purpose |
|---|---|
| `docker-compose.db.yml` | PostgreSQL |
| `docker-compose.minio.yml` | MinIO (S3-compatible storage, emulates Cloudflare R2) |
| `docker-compose.app.yml` | Application (production build) |

### Option 1 — Dev mode (run from IDE)

Start only the infrastructure:

```bash
docker-compose -f docker-compose.db.yml -f docker-compose.minio.yml up -d
```

Then run the application from your IDE with the `dev` Spring profile active.  
The `dev` profile connects to `localhost:5432` and `localhost:9000`, and loads test seed data via Liquibase.

MinIO console: http://localhost:9001 — login: `admin` / `admin12345`  
The `advertisement` bucket is created automatically on first start.

Open the app: http://localhost:8080

### Option 2 — Full Docker (local production simulation)

Builds and runs everything in containers — useful for verifying the production build before deploying.

```bash
docker-compose -f docker-compose.db.yml -f docker-compose.minio.yml -f docker-compose.app.yml up --build
```

To stop and remove volumes:

```bash
docker-compose -f docker-compose.db.yml -f docker-compose.minio.yml -f docker-compose.app.yml down -v
```

### Option 3 — Cloud DB + R2 (Neon + Cloudflare R2)

Use only the app compose file and override environment variables:

```bash
docker-compose -f docker-compose.app.yml up --build
```

Uncomment the Neon and R2 sections in `docker-compose.app.yml` and fill in your credentials before running.

---

## Running Without Docker

```bash
git clone https://github.com/OstMutant/Advertisement.git
cd Advertisement
./mvnw spring-boot:run
```

Requires a running PostgreSQL instance matching the `application-dev.yml` config,
and a running MinIO instance (or any S3-compatible storage).

---

## Status & Roadmap

Actively evolving. Architectural decisions may be revisited, implementations replaced.

Planned directions:
- Extend rule-based validation capabilities
- Improve composability of the generic filtering layer
- Add architectural decision records (ADR)
- Introduce integration and contract tests
- Explore alternative API adapters

---

## Author's Note

I value clarity over convenience.  
I prefer explicitness over magic.  
I build systems to be understood, not just used.

Feedback and architectural discussions are welcome.