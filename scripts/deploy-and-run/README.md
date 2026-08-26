# deploy-and-run

The local prod-simulation deploy pipeline: starts the application alongside PostgreSQL and MinIO,
so the full stack runs the same way locally as it would in a real deployment (production Vaadin
bundle, real Postgres, real S3-compatible storage) — without needing a hosted environment. Also
owns this project's shared local infrastructure (the raw `docker-compose*.yml` files for DB/MinIO/
app, usable independently of the deploy pipeline itself) and the database-truncate script
([`reset.sh`](reset.sh)).

## Flow

Two independent entry points: [`run.sh`](run.sh) (full deploy pipeline) and
[`reset.sh`](reset.sh) (standalone DB truncate) — `reset.sh` is also invoked internally by
`run.sh` itself (`--reset-only-db`) and by [`playwright/run.sh`](../../playwright/run.sh), but
it's a real, independently runnable entry point on its own too, not just an internal helper.

### Deploy pipeline (`run.sh`)

```mermaid
flowchart TD
    A[run.sh] --> B{mode?}
    B -->|--reset| B1[remove containers + volumes] --> C
    B -->|--restart-infra| B2[remove db+minio containers, volumes kept] --> C
    B -->|default| C[start infra: pull images, start db+minio, wait, configure MinIO bucket]
    C --> D{--reset-only-db?}
    D -->|yes| D1[reset.sh --container DB_CONTAINER] --> E
    D -->|no| E{--from-scratch?}
    E -->|no| E2{--with-tests?}
    E2 -->|yes| E1a[build-and-test.sh --unit --integration] --> G
    E2 -->|no| E1b[build-and-test.sh --no-unit --no-integration] --> G
    E -->|yes| F2[build repo-root Dockerfile, full multi-stage, real tagged image] --> G
    G[start app container, wait for Started Application]
    G --> H{Liquibase checksum mismatch?}
    H -->|yes| H1[reset infra, restart app, retry once] --> I[app ready on APP_PORT]
    H -->|no| I
```

See `run.sh`'s own header for the default-vs-`--from-scratch` behavior and
[`DECISIONS.md`](../DECISIONS.md) for the design rationale.

### Standalone DB reset (`reset.sh`)

```mermaid
flowchart TD
    A[reset.sh] --> R2{DB container state?}
    R2 -->|running| R4[docker exec psql -f reset-clean.sql]
    R2 -->|exists, stopped| R3a[docker start, wait pg_isready] --> R4
    R2 -->|no container at all| R3b[docker compose up -d, wait pg_isready] --> R4
    R4 --> R5[all application tables truncated]
```

### Raw docker-compose files (no script entry point)

[`docker-compose.db.yml`](docker-compose.db.yml) / [`docker-compose.minio.yml`](docker-compose.minio.yml) /
[`docker-compose.app.yml`](docker-compose.app.yml) aren't invoked by either script above — see each
file's own header for what it's for and its exact invocation.
