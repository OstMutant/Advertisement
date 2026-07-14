# improvement-044: DB and MinIO/S3 credentials duplicated across 4-5 files each — consolidate into a shared root `.env`

**Type:** improvement — consistency/drift-risk. Found while building improvement-027's Batch 0,
after noticing `postgres:15-alpine` was hardcoded independently in both
`scripts/infra/docker-compose.db.yml` and a new Testcontainers Java class — the same investigation
was widened project-wide to find every other instance of this class of duplication.
**Module:** cross-cutting — `scripts/infra/*.yml`, `scripts/deploy.sh`, `marketplace-app`
(`application-dev.yml`), `playwright/`
**Priority:** medium — not a runtime bug today (all the duplicated copies currently agree with
each other), but a real drift risk: renaming a DB user, rotating a MinIO credential, or changing a
port in one file and forgetting the other 3-4 copies produces a confusing, hard-to-diagnose
failure (wrong credentials, connection refused) rather than a clean compile/build error.
**When:** independent, no blockers — not urgent, no current bug, pure hygiene/risk-reduction

## Problem

A project-wide search for hardcoded values duplicated across 2+ files of different formats (YAML
compose files, Spring `application*.yml`, shell scripts, docs) found:

| Value | Locations | Formats involved |
|---|---|---|
| DB name/user/password (`experiments`/`experiments_user`/`experiments_user_password`) | `docker-compose.db.yml`, `docker-compose.app.yml`, `application-dev.yml` (as full JDBC url/user/pass), `deploy.sh` (pull/run + `wait_for_db`), `scripts/database/reset.sh` | YAML + shell + Spring YAML — **4-5 independent copies** |
| MinIO/S3 credentials (`admin`/`admin12345`), bucket (`advertisement`), region (`us-east-1`) | `docker-compose.minio.yml`, `docker-compose.app.yml`, `application-dev.yml`, `deploy.sh`, `playwright/run.sh`, `playwright/CLAUDE.md` | YAML + shell + Spring YAML + docs — **5+ independent copies, the worst offender** |
| Ports (`5432` DB, `9000`/`9001` MinIO, `8080`/`8081` app) | Same file set as above | YAML + shell + Spring — mostly consolidatable |
| `postgres:15-alpine` | `docker-compose.db.yml` (now `${POSTGRES_IMAGE}`, fixed by improvement-027), `deploy.sh` (`docker pull`/`docker run`, still hardcoded — **not yet fixed**), Java `AbstractPostgresIntegrationTest` (now reads the same `.env`, fixed) | YAML + shell + Java |
| Playwright image/version pin (`mcr.microsoft.com/playwright:v1.52.0-jammy` / `playwright@1.52.0`) | `playwright/run.sh` lines 103 and 108 (**same file, same format** — two branches of the same script) | shell only |

**Explicitly not a problem:** `deploy.sh`'s `8081` (vs. the `8080` used elsewhere) is a
**deliberate** deviation — "8080 reserved for local IntelliJ dev server", already documented in
`scripts/CLAUDE.md`. Do not blindly fold this into a shared `PORT` variable; it must stay a
distinct, intentionally different value. `docker-compose.sonar.yml` and
`scripts/build-env/docker-compose.yml` were checked and have no duplicated credentials/ports found
elsewhere — not in scope.

## Suggested fix

Same mechanism improvement-027's Batch 0 already established and validated for `POSTGRES_IMAGE`:
a repo-root `.env` file, read natively by Docker Compose (automatic, no code changes needed on
that side beyond `${VAR}` placeholders in the YAML) and by Spring Boot via `${VAR:default}`
placeholder syntax in `application-dev.yml` (Spring Boot resolves these against environment
variables at startup — same mechanism already used for prod's `DB_HOST`/`DB_PORT`/etc. in
`application-prod.yml`, just extended to dev).

**Carry forward the `--project-directory` requirement Batch 0 found the hard way.** Compose's
default project directory (where it looks for `.env`) is the directory containing the first `-f`
file — `scripts/infra/`, not the repo root where `.env` actually lives. Every compose invocation
touched by this issue (`docker-compose.minio.yml`, `docker-compose.app.yml`, any script or header
comment showing a manual command) must include `--project-directory .` (or an absolute
`--project-directory "$ROOT"` in scripts) or the new `${DB_*}`/`${S3_*}` variables silently
resolve empty, the same failure mode already fixed for `POSTGRES_IMAGE` in
`scripts/database/reset.sh`. See `scripts/CLAUDE.md` "Deployment" section for the confirmed,
tested fix and the `docker compose` CLI plugin install (also needed once, not present in this
sandbox by default — same pattern as `docker buildx`).

1. Extend `/app/.env` (created by improvement-027) with the DB and MinIO/S3 values:
   ```
   POSTGRES_IMAGE=postgres:15-alpine
   DB_NAME=experiments
   DB_USER=experiments_user
   DB_PASSWORD=experiments_user_password
   DB_PORT=5432
   S3_ACCESS_KEY=admin
   S3_SECRET_KEY=admin12345
   S3_BUCKET=advertisement
   S3_REGION=us-east-1
   S3_PORT=9000
   ```
2. Update `docker-compose.db.yml`, `docker-compose.minio.yml`, `docker-compose.app.yml` to
   reference `${DB_NAME}`/`${DB_USER}`/etc. instead of hardcoded values (Compose loads `.env`
   automatically from the working directory — already relied on for `POSTGRES_IMAGE`).
3. Update `marketplace-app/src/main/resources/application-dev.yml` to use
   `${DB_USER:experiments_user}` etc. — Spring Boot needs the env vars actually exported into its
   process environment for this to resolve to anything other than the fallback default, so
   `deploy-dev.sh`/`run-local.bat` need to `source .env` (or equivalent) before launching, not just
   rely on Spring finding the file itself (Spring does not natively read `.env`, unlike Compose).
4. Update `deploy.sh`'s hardcoded `docker pull`/`docker run` references (both the Postgres image
   tag left over from improvement-027 and the DB/MinIO credential flags) to source `.env` at the
   top of the script (`set -a; source .env; set +a` is the standard idiom) instead of hardcoding.
5. Update `playwright/run.sh` to source the same `.env` for its DB/S3 env var flags, and extract
   the Playwright image/version pin duplicated on lines 103 and 108 into a single local shell
   variable at the top of the script — unrelated to the `.env` story (it's a same-file, same-format
   duplication, not a cross-format one), but cheap to fix in the same pass.

## Explicitly out of scope

- Doc mentions of the Playwright image/version (`playwright/README.md`, `playwright/CLAUDE.md`,
  `scripts/README.md`) — cosmetic drift risk only (a stale doc doesn't break a build), not worth
  templating docs from a script variable.
- `deploy.sh`'s `8081` vs `8080` — deliberate, must stay distinct (see Problem section).
- Secrets management (these are dev-only, non-production credentials already committed in plain
  YAML today — moving them to `.env` doesn't change that; `.env` is not `.gitignore`d, same as the
  current compose files, so this is a pure refactor, not a security hardening pass).

## Required test coverage

None new — this is a config-plumbing refactor, not a behavior change. Verify by running
`deploy.sh --reset` (full infra recreation from the new `.env`-driven compose files) and the full
Playwright e2e suite — both must stay green, proving the consolidated values still match what the
app actually connects with.

## Related

- `features/completed/issues/improvement-027-unit-testcontainers-test-layer.md` — Batch 0
  established and validated the `.env` + Docker-Compose-native-load pattern this issue extends,
  for `POSTGRES_IMAGE` specifically. This issue is the wider rollout the same session's
  investigation surfaced.
- `scripts/CLAUDE.md` — documents the deliberate `8081`/`8080` port distinction that must not be
  collapsed by this issue.
