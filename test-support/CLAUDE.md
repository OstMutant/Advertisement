## test-support

Shared Testcontainers scaffolding for repository tests, consumed as a `test`-scope dependency by
starters that need a real Postgres for their repository tests.

Java package root: `org.ost.testsupport`

---

## What it owns

- `AbstractPostgresIntegrationTest` — shared singleton-container base class. A single Postgres
  Testcontainers instance, started once via a static initializer (not per test class), shared
  across every test class in one `mvn test` reactor run via Testcontainers' documented "singleton
  container" pattern. Uses `@ServiceConnection` (`spring-boot-testcontainers`) so
  `@SpringBootTest`-based repository tests in consuming starters get their `DataSource`
  autoconfigured against the container automatically — no manual `@DynamicPropertySource` wiring
  needed.
- `SharedEnvConfig` — reads the repo-root `.env` file (walking up from the JVM's working
  directory, so it resolves whether launched from the repo root, a module subdirectory, or an IDE
  test runner). `AbstractPostgresIntegrationTest` uses it to source `POSTGRES_IMAGE`, the single
  source of truth also read natively by `scripts/infra/docker-compose.db.yml` — renaming the
  Postgres version updates both consumers from one place, no drift possible.

---

## Key constraints

- No Spring Boot autoconfiguration of its own — a plain testing library, same shape as
  `query-lib`. Consumers add it as a `test`-scope dependency.
- Requires a reachable Docker daemon at test time — see `scripts/CLAUDE.md` "Unit / Testcontainers
  Tests" for the Docker-in-Docker constraint (never runs inside `deploy.sh`'s image build, only
  via a direct `mvn test`).
- The Postgres image tag is sourced from the repo-root `.env` (`POSTGRES_IMAGE`), the same value
  `scripts/infra/docker-compose.db.yml` reads natively — no hardcoded duplication, though the two
  are never the same running container (Testcontainers always starts its own ephemeral instance,
  never reuses the persistent dev one). `deploy.sh`'s own `docker pull`/`docker run` for Postgres
  still hardcode the tag separately — tracked as a follow-up in
  `features/issues/improvement-044-shared-env-config-consolidation.md`, out of scope here.
- Data isolation across starters sharing the one physical container is not yet settled — an open
  design detail deferred to Batch 1, when a real starter's repository test first consumes this
  base class. See `features/issues/improvement-027-unit-testcontainers-test-layer.md`.
- `PostgresContainerSmokeTest` (Batch 0) proves only the container-starts +
  changelog-applies mechanics, using the plain `liquibase.Liquibase` API directly — not
  `@SpringBootTest`, since this module has no Spring Boot application context of its own to
  bootstrap one. Real repository tests in consuming starters use their own starter's existing
  `@SpringBootTest`/autoconfiguration setup.
