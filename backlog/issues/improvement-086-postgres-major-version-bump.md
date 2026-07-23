# improvement-086: PostgreSQL 15 → 18 major version bump

**Type:** improvement — infrastructure/database maintenance.
**Module:** `.env` (`POSTGRES_IMAGE`), `scripts/infra/docker-compose.db.yml`,
`integration-tests/` (Testcontainers Postgres image, sourced from the same `.env` value per
`integration-tests/CLAUDE.md`), `scripts/deploy.sh` (its own separately hardcoded Postgres
pull/run — see `improvement-044`'s still-open follow-up about consolidating this).
**Priority:** low — not urgent; PostgreSQL 15 is still within its supported window (~5-year
support cycle from its 2022 release), no forced-EOL pressure yet. Trigger-based, not immediately
actionable.
**When:** deferred — do when data volume/feature needs justify it, or when PostgreSQL 15's support
window starts actually approaching its end, whichever comes first. Same trigger shape as
[improvement-038](improvement-038-pg-trgm-title-index.md) (also deferred, also DB-related).

## Problem

Checked directly (web search): the project runs `postgres:15-alpine` (per `.env`'s
`POSTGRES_IMAGE`, the single source of truth per `integration-tests/CLAUDE.md`). Current stable is
**PostgreSQL 18.4** (PostgreSQL 19 is in beta as of 2026-07, expected GA September 2026) — three
major versions behind. Unlike the routine library bumps in
[improvement-040](improvement-040-spring-boot-vaadin-minor-bump.md), a Postgres **major** version
bump is a genuinely different risk category:

- Extension compatibility (`pg_trgm`, if/when improvement-038 lands, plus anything else in use)
  needs re-verification against the target major version.
- Behavioral changes across 3 major versions (15→16→17→18) can affect query planner behavior,
  default configuration values, and deprecated-syntax removal — not just a recompile-and-go bump.
- This project's own `POSTGRES_IMAGE` single-source-of-truth design (improvement-027/044) means the
  bump is a one-line `.env` change *mechanically*, but the actual verification surface (does every
  repository's SQL still behave identically, do migrations replay cleanly against a fresh major
  version) is real work, not automatic.

## Suggested fix

Not scoped in detail here since this is deferred/trigger-based (same treatment as
improvement-038) — when picked up:

1. Re-check the current latest stable major version before starting (this issue's data is from
   2026-07-19; PostgreSQL 19 GA in September 2026 would make that the more current target instead
   of 18).
2. Bump `POSTGRES_IMAGE` in the root `.env` (the single source of truth this repo already
   maintains per improvement-027/044) to the target major version's `-alpine` tag.
3. Full Liquibase changelog replay against a fresh container of the new major version (this
   already happens naturally via `integration-tests`' `AbstractPostgresIntegrationTest` singleton
   container and `PostgresContainerSmokeTest` — no new test infrastructure needed, just point the
   existing mechanism at the new tag and watch for failures).
4. Full integration-tests + unit-tests + Playwright e2e run.
5. Resolve `scripts/deploy.sh`'s separately-hardcoded Postgres pull/run at the same time if
   improvement-044's consolidation follow-up hasn't already been done by then — otherwise this
   bump would silently only apply to the Testcontainers/dev-compose path, not `deploy.sh`'s own
   image pull, defeating the single-source-of-truth design.

## Related

- [improvement-038](improvement-038-pg-trgm-title-index.md) — same "deferred, trigger-based, do as
  data volume grows" treatment; listed together in `BACKLOG.md`'s Deferred section.
- [improvement-044](improvement-044-shared-env-config-consolidation.md) (completed) — established
  `POSTGRES_IMAGE` as the single source of truth this bump would update; also the source of the
  still-open note that `scripts/deploy.sh`'s own Postgres pull/run is a separate, still-hardcoded
  path (step 5 above).
- `integration-tests/CLAUDE.md` — documents `POSTGRES_IMAGE` sourcing and the singleton-container
  test mechanism this bump would exercise.
- [improvement-040](improvement-040-spring-boot-vaadin-minor-bump.md) — the routine
  library-version-bump issue this was split out from (different risk shape: infra/data-behavior
  compatibility across major versions, not a library recompile).
