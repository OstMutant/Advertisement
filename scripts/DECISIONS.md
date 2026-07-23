# Architecture & Technical Decisions — scripts/

---

## ADR-001: All operations via project scripts — no raw commands
**Status:** Accepted

**Context:** Raw `docker`, `mvn`, or `docker compose` commands bypass correct flags, env vars,
Docker network settings, and startup detection — producing inconsistent results.

**Decision:** All build, deploy, and test operations must be performed via scripts in `scripts/`
and `playwright/`.

| Operation | Script |
|---|---|
| Full prod rebuild + start | `bash scripts/deploy.sh` |
| Fast JAR hot-swap | `bash scripts/deploy-dev.sh` |
| Run all Playwright tests | `bash scripts/playwright.sh` |
| Run one scenario | `bash scripts/playwright.sh <scenario>` |
| SonarQube analysis | `bash scripts/sonar.sh` |
| Run Testcontainers repository tests + plain unit tests | `bash scripts/integration-tests.sh` |

**Consequences:** If a new recurring operation is needed, add a script — do not document raw
commands as the canonical way to run it.

---

## ADR-002: scripts/ folder for all developer scripts
**Status:** Accepted

**Context:** Root-level scripts cluttered the project root. Scripts are developer tooling,
not project artifacts.

**Decision:** All root-level developer scripts (`.bat`, `.sh`) except `mvn.bat` live in `scripts/`.
Each script resolves the project root via `cd /d "%~dp0.."` (bat) or `$(dirname "$0")/..` (sh).

**Consequences:** `mvn.bat` stays at the root — invoked too frequently during development
to be ergonomic elsewhere.

---

## ADR-003: deploy.sh startup detection
**Status:** Accepted — **code has since reverted to the originally-rejected approach** (see
correction below); documenting current reality rather than the original design

**Context:** Polling `docker logs` repeatedly wastes cycles and adds arbitrary sleep delays.

**Original decision (no longer what the code does):** wait for `"Started Application"` via a
single streaming `docker logs -f | grep -qm1` call with an external `timeout`.

**Correction (verified 2026-07-13):** current `scripts/deploy.sh` (lines ~191-201) does not do
this — it uses exactly the pattern this ADR originally rejected:
```bash
end=$((SECONDS + 180))
while true; do
  if docker logs "$APP_CONTAINER" 2>&1 | grep -q "Started Application"; then break; fi
  if [ $SECONDS -ge $end ]; then echo "=== FAILED: startup timed out ==="; exit 1; fi
  sleep 2
done
```
A `while`/`sleep 2` polling loop, non-streaming `docker logs` (re-reads the full log each
iteration rather than `-f` following it), timeout tracked via `$SECONDS` rather than the `timeout`
command. `git log -p` on this file shows the loop was introduced in a later commit than this ADR,
with no corresponding ADR update. In practice this has run reliably across many deploys this
session with no observed flakiness — the "wastes cycles" concern in the original Context does not
appear to have materialized as an actual problem at this polling interval (2s) and timeout (180s).
Documenting current behavior as-is rather than reverting working deploy code to match a
stale ADR; if the original streaming approach is preferred, that's a separate, deliberate change
to make to `deploy.sh` itself, not a docs fix.

**Consequences:** `deploy.sh`'s current startup-detection mechanism is the polling loop shown
above, not the streaming `grep -qm1` originally decided here.

---

## ADR-004: run-all-tests.sh — sequential Maven suites, parallel Playwright
**Status:** Accepted

**Context:** Running `unit-tests.sh`, `integration-tests.sh`, and `playwright.sh` one at a time
during daily iteration is slow. Naive full 3-way parallelism was considered and rejected:
`unit-tests.sh` (`./mvnw -pl query-lib,marketplace-app -am test`) and `integration-tests.sh`
(conditional `./mvnw install -pl platform-commons,advertisement-/user-/taxon-spring-boot-starter
-am -DskipTests`) can both compile the *same* starter modules into their shared `target/`
directories. Running them concurrently right after editing one of those starters risks a genuine
Maven build race (one process reading/writing `target/classes` while the other recompiles it), not
just a performance hit. `playwright.sh`, by contrast, never touches the Maven reactor — it only
drives an already-built, already-running `marketplace-app` Docker container via `docker cp`/`docker
exec`, so it has nothing to race with the Maven-based suites.

**Decision:** `scripts/run-all-tests.sh` runs `unit-tests.sh` → `integration-tests.sh`
sequentially in one stream, while `playwright.sh` starts in parallel with that pair from the very
beginning (backgrounded, own log file) and is `wait`-ed on at the end. Each suite's own
flags/scenario args are grouped behind `--unit "..."` / `--integration "..."` / `--playwright
"..."` and forwarded unchanged — no new flag vocabulary, no duplication of each script's own
argument parsing.

Side effect worth noting (not a design requirement, just an observed consequence of the ordering):
since `unit-tests.sh` already compiles the shared starter modules via its own `-am` reactor build
moments earlier, `integration-tests.sh`'s subsequent staleness-check/install step (see
`integration-tests/DECISIONS.md` ADR-007) typically finds a warm compile cache ("Nothing to
compile") and completes faster than a cold invocation would — even though `unit-tests.sh` never
installs anything to `~/.m2` itself (`test` goal, not `install`), so the install step still always
actually runs, just faster.

**Consequences:** True 3-way parallelism (including unit-tests and integration-tests running
concurrently) was explicitly out of scope for this decision — it would require isolating one of
the two Maven-based suites into a separate git worktree (or equivalent) so their `target/`
directories never overlap. Not pursued because the sequential-plus-parallel-Playwright shape
already captures most of the available time savings without new infrastructure. The individual
scripts (`unit-tests.sh`, `integration-tests.sh`, `playwright.sh`) remain the default, single-suite
entry points for day-to-day iteration — `run-all-tests.sh` is an additional, opt-in grouping, not a
replacement.

---

## ADR-005 through ADR-008: moved to scripts/ci/DECISIONS.md

The local CI runner's ADRs (one CI-runner container via Docker-outside-of-Docker, background
execution with a live progress file, most-extensive-by-default stage selection, report retention)
now live in `scripts/ci/DECISIONS.md` (its own ADR-001 through ADR-004) — `scripts/ci/` grew its
own `DECISIONS.md`/`README.md` once the tool had enough surface area to warrant it, matching
`scripts/sonar/`'s precedent of a nested tool directory with its own ADR file.

---

## ADR-009: DB/S3 credentials consolidated into the repo-root `.env`, loaded as fallback defaults (not unconditional overrides) so CI's per-run port overrides survive

**Status:** Accepted

**Context:** DB name/user/password (`experiments`/`experiments_user`/`experiments_user_password`)
and MinIO/S3 credentials (`admin`/`admin12345`, bucket `advertisement`, region `us-east-1`) were
each hardcoded independently across 4-5 files of different formats: `docker-compose.db.yml`,
`docker-compose.minio.yml`, `docker-compose.app.yml`, `application-dev.yml`,
`scripts/deploy.sh`, `scripts/database/reset.sh` — the same class of duplication improvement-027
already closed for `POSTGRES_IMAGE` alone. Not a live bug (every copy still agreed), but a real
drift risk: changing one copy and missing the others fails as a confusing "connection refused"
at runtime, not a build error. → [improvement-044](../backlog/completed/issues/improvement-044-shared-env-config-consolidation.md).

**Decision:** Extend the repo-root `.env` (Docker Compose's native mechanism, already used for
`POSTGRES_IMAGE`) with `DB_NAME`/`DB_USER`/`DB_PASSWORD`/`DB_PORT`/`S3_ACCESS_KEY`/`S3_SECRET_KEY`/
`S3_BUCKET`/`S3_REGION`/`S3_PORT`. Docker Compose files (`docker-compose.db.yml`/`.minio.yml`/
`.app.yml`) reference `${VAR}` directly — including inside `minio-init`'s inline shell entrypoint,
since Compose substitutes `${VAR}` in any string field, not just `environment:` blocks.
`marketplace-app/application-dev.yml` uses `${VAR:default}` Spring placeholder syntax, with the
default matching `.env`'s current value exactly — a deliberate safety net so an IDE dev run (which
never sources `.env`) keeps working unmodified; this does mean the *default* literal is still a
second copy of the value, an acknowledged residual duplication Spring's inability to natively read
`.env` files makes unavoidable without extra script plumbing IDE runs don't go through anyway.

**`scripts/deploy.sh` / `scripts/database/reset.sh` — the tricky part:** both already had
`VAR="${VAR:-literal-default}"` override variables (`DB_PORT`, `MINIO_PORT`, etc.) that
`scripts/ci/entrypoint.sh` relies on for its isolated e2e stack (e.g. `DB_PORT=15432`). A naive
`set -a; source .env; set +a` would unconditionally overwrite any already-exported value —
including a CI override — since plain shell assignment doesn't check whether a var came from a
prior export. Instead, `.env` is parsed into `ENV_*`-prefixed variables (never exported directly),
then used only as the *second* fallback tier: `DB_PORT="${DB_PORT:-${ENV_DB_PORT:-5432}}"`. This
preserves the exact existing override precedence (explicit env var wins, `.env` is the new
fallback default, the old hardcoded literal is now only the last-resort fallback if `.env` itself
is missing) — confirmed via a full `bash scripts/deploy.sh --reset` (fresh DB/MinIO
volumes+containers+image) and a full Playwright e2e run (48/48 green).

`playwright/run.sh`'s DB/S3-flag `echo` lines (a printed usage-example message, not runtime logic)
were deliberately left hardcoded — cosmetic duplication only, consistent with the originating
issue's "doc mentions" exclusion. Its actual runtime duplication —
`mcr.microsoft.com/playwright:v1.52.0-jammy` appearing twice in the same file plus the separate
`playwright@1.52.0`/`@playwright/test@1.52.0` npm pins — was extracted into
`PLAYWRIGHT_VERSION`/`PLAYWRIGHT_IMAGE` variables at the top of the script instead (a same-file,
same-format duplication, unrelated to the `.env` story but cheap to fix in the same pass, per the
issue's own item 5).

**What was deliberately left hardcoded, not parameterized:** `DB_PORT: 5432` inside
`docker-compose.app.yml`'s `app` service environment and `deploy.sh`'s app-container `-e
DB_PORT=5432` both refer to the **container-internal** Docker-network port (`db`'s own listening
port, always 5432 regardless of the host-side `${DB_PORT}` mapping) — conflating this with the
host-facing `.env` value would be semantically wrong even though they share the same number today.
Same reasoning for `S3_ENDPOINT: http://minio:9000` (minio's internal port). Only genuinely
host-facing occurrences (`S3_PUBLIC_URL`, the host port mappings themselves) were parameterized.

**Consequences:**
- Renaming a DB user or rotating a MinIO credential going forward is a one-line `.env` change
  instead of a 4-5-file hunt — the drift-risk class of bug this ADR closes.
- `scripts/ci/entrypoint.sh`'s isolated e2e stack (port overrides via env vars) is unaffected —
  verified its override precedence survives the `.env`-as-fallback change.
- Explicitly out of scope, per the originating issue: secrets management (these remain committed,
  non-production dev credentials, same as before — moving them to `.env` is a pure refactor, not
  a security hardening pass) and `deploy.sh`'s deliberate `8081` vs `8080` port distinction
  (untouched, must stay distinct).
