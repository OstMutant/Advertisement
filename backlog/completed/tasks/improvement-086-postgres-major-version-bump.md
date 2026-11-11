# improvement-086: PostgreSQL 15 → 18 major version bump

**Type:** improvement — infrastructure/database maintenance.
**Module:** `.env` (`POSTGRES_IMAGE`), `scripts/deploy-and-run/docker-compose.db.yml`,
`scripts/deploy-and-run/run.sh` (its own Postgres container, hardcoded to the old tag until this task),
`integration-tests/` (Testcontainers Postgres image, sourced from the same `.env` value per
`.claude/rules/integration-tests.md`).
**Priority:** ⚪ low — not urgent; PostgreSQL 15 is still within its supported window (~5-year
support cycle from its 2022 release), no forced-EOL pressure yet. Trigger-based, not immediately
actionable.
**When:** was deferred; decided 2026-11-11 (the user) to do it now: PostgreSQL 15 reaches the end of
community support around November 2027 (five years from its release; derived, not read from the
official page), about a year away.

## Problem

Checked directly (web search): the project runs `postgres:15-alpine` (per `.env`'s
`POSTGRES_IMAGE`, the single source of truth per `integration-tests/CLAUDE.md`). Current stable is
**PostgreSQL 18.4** as written on 2026-07-19; on 2026-11-11 it is 18.6 (August 2026), with an 18.7 update announced, and PostgreSQL 19 is still
not generally available (beta, then a release candidate) — three major versions behind. Unlike the routine library bumps in
[improvement-040](../completed/tasks/improvement-040-spring-boot-vaadin-minor-bump.md), a Postgres **major** version
bump is a genuinely different risk category:

- Extension compatibility (`pg_trgm`, used by the trigram index on `advertisement.title`, plus anything else in use)
  needs re-verification against the target major version.
- Behavioral changes across 3 major versions (15→16→17→18) can affect query planner behavior,
  default configuration values, and deprecated-syntax removal — not just a recompile-and-go bump.
- This project's own `POSTGRES_IMAGE` single-source-of-truth design (improvement-027/044) means the
  bump is a one-line `.env` change *mechanically*, but the actual verification surface (does every
  repository's SQL still behave identically, do migrations replay cleanly against a fresh major
  version) is real work, not automatic.

## Facts checked and plan (2026-11-11)

Found in the project: the version is in three places, not one: `.env` (read by the compose file and by the Testcontainers tests), and `scripts/deploy-and-run/run.sh`, which hardcodes `postgres:15-alpine` twice (the image pull and the `docker run`) together with the data mount `/var/lib/postgresql/data`.

Found about the image: from PostgreSQL 18 the official image keeps its data in `/var/lib/postgresql/18/docker` and declares its volume at `/var/lib/postgresql`; a volume mounted at the old `/var/lib/postgresql/data` is the wrong place. Existing volumes hold PostgreSQL 15 files, which 18 cannot open, so the databases are created again (the database is not in production): the development one with `deploy-and-run.sh --reset`, the CI end-to-end stack with `ci.sh --reset-e2e-stack`.

Plan:

1. `.env`: `POSTGRES_IMAGE=postgres:18-alpine`.
2. `docker-compose.db.yml`: mount the volume at `/var/lib/postgresql`.
3. `run.sh`: read `POSTGRES_IMAGE` from `.env` like the other defaults, use it for the pull and the `docker run`, mount at `/var/lib/postgresql`, correct its header.
4. Check: the full integration suite on PostgreSQL 18 (Liquibase, the JDBC driver, Testcontainers, the trigram index), then the full `/ci` with `--reset-e2e-stack`, `/sync-docs`, `/deep-review`.

## Suggested fix

Not scoped in detail here since this is deferred/trigger-based — when picked up:

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

- [improvement-038](../completed/tasks/improvement-038-pg-trgm-title-index.md) (completed) — added the trigram index whose
  extension (`pg_trgm`) the bump must be re-verified against.
- [improvement-044](../completed/tasks/improvement-044-shared-env-config-consolidation.md) (completed) — established
  `POSTGRES_IMAGE` as the single source of truth this bump would update; also the source of the
  still-open note that `scripts/deploy.sh`'s own Postgres pull/run is a separate, still-hardcoded
  path (step 5 above).
- `integration-tests/CLAUDE.md` — documents `POSTGRES_IMAGE` sourcing and the singleton-container
  test mechanism this bump would exercise.
- [improvement-040](../completed/tasks/improvement-040-spring-boot-vaadin-minor-bump.md) — the routine
  library-version-bump issue this was split out from (different risk shape: infra/data-behavior
  compatibility across major versions, not a library recompile).

## Result (2026-11-11)

`.env` sets `POSTGRES_IMAGE=postgres:18-alpine`; `scripts/deploy-and-run/docker-compose.db.yml` and `scripts/deploy-and-run/run.sh` mount the data volume at `/var/lib/postgresql`; `run.sh` reads `POSTGRES_IMAGE` like its other defaults (the caller's environment, then `.env`, then a hardcoded `postgres:18-alpine`) for the image pull and the `docker run`; the headers of the three files say what the bump needs. An existing database volume from PostgreSQL 15 cannot be opened by 18, so the development database is created again with `deploy-and-run.sh --reset` and the CI end-to-end stack with `ci.sh --reset-e2e-stack`. The full integration suite passes on PostgreSQL 18 (Liquibase replay, the JDBC driver, Testcontainers, the trigram index). The first full `/ci` ended `partially_succeeded`: only its `e2e` step failed, in the global sign-up setup, while `unit` and `integration` ran beside it (the page did not finish rendering within 10 s); the `e2e` stage alone on a reset stack ended `succeeded`. `/deep-review` was not run: the change has no application code. Not done: `run.sh` still keeps a hardcoded `postgres:18-alpine` fallback that repeats the `.env` value.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: 164713
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: no
- context_loading_matched: n/a
- flows_situation: infrastructure version bump, then doc sync and full CI check
- flows_chosen: /ci, /sync-docs
- flows_matched: yes
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 4
- haiku_decomposition_rework_needed: 0 / 4
- adrs_referenced: none

### Agent calls
- header audit of the three changed infra files | subagent_type=general-purpose (haiku) | tokens=123402 | tool_uses=8 | duration_s=123 | mode=background | batch=solo
- header notes for the volume mount and the container reuse | subagent_type=general-purpose (haiku) | tokens=78320 | tool_uses=6 | duration_s=34 | mode=background | batch=solo
- header and comment fixes in `.env` and `run.sh` | subagent_type=general-purpose (haiku) | tokens=95153 | tool_uses=18 | duration_s=51 | mode=background | batch=solo
- end-to-end failure details of the first CI run | subagent_type=dagu-analyst | tokens=41311 | tool_uses=4 | duration_s=25 | mode=background | batch=solo
- closing edits (result, notes, backlog rows, archive entry) | subagent_type=general-purpose (haiku) | tokens=88257 | tool_uses=10 | duration_s=47 | mode=background | batch=solo

### Script/command runs
- scripts/ci.sh (all stages, `--reset-e2e-stack`) | duration_s=1208 | mode=background | result=fail
- scripts/ci.sh --e2e --reset-e2e-stack | duration_s=1169 | mode=background | result=pass
