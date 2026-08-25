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
| Full local deploy + start | `bash scripts/deploy-and-run.sh` |
| Run all Playwright tests | `bash scripts/playwright.sh` |
| Run one scenario | `bash scripts/playwright.sh <scenario>` |
| SonarQube analysis | `bash scripts/sonar.sh` |
| Run Testcontainers repository tests + plain unit tests | `bash scripts/build-and-test.sh --unit --integration` |

**Consequences:** If a new recurring operation is needed, add a script — do not document raw
commands as the canonical way to run it.

---

## ADR-002: scripts/ folder for all developer scripts
**Status:** Accepted, with one carved-out exception — see the update note below.

**Context:** Root-level scripts cluttered the project root. Scripts are developer tooling,
not project artifacts.

**Decision:** All root-level developer scripts (`.bat`, `.sh`) except `mvn.bat` live in `scripts/`.
Each script resolves the project root via `cd /d "%~dp0.."` (bat) or `$(dirname "$0")/..` (sh).

**Consequences:** `mvn.bat` stays at the root — invoked too frequently during development
to be ergonomic elsewhere.

**Update (exception carved out):** `scripts/architecture/` and `scripts/ai/` moved to
`docs/architecture/scripts/` and `.claude/nav/scripts/` respectively — a deliberate exception to this
ADR's "all developer scripts live in `scripts/`" rule, chosen for navigation convenience: both
directories exist purely to generate/verify the docs they now sit next to
(`architecture-model.json`/`architecture-map.html`, `adr-index.md`), so keeping them beside their
own output outweighs strict adherence to the original one-home-for-all-scripts rule for this one
case. Every other script-group directory (`scripts/ci/`, `scripts/sonar/`, `scripts/build-and-test/`,
etc.) still lives under `scripts/` per the original decision — this is a narrow, named exception,
not a reversal.

---

## ADR-003: deploy-and-run.sh startup detection
**Status:** Accepted

**Context:** Polling `docker logs` repeatedly wastes cycles and adds arbitrary sleep delays if
done naively (e.g. tight-loop with no sleep, or re-reading the whole log unnecessarily often).

**Decision:** `scripts/deploy-and-run/run.sh` waits for `"Started Application"` via a
`while`/`sleep 2` polling loop against `docker logs` (non-streaming — re-reads the full log each
iteration rather than following it with `-f`), with the timeout tracked via `$SECONDS`:
```bash
end=$((SECONDS + 180))
while true; do
  if docker logs "$APP_CONTAINER" 2>&1 | grep -q "Started Application"; then break; fi
  if [ $SECONDS -ge $end ]; then echo "=== FAILED: startup timed out ==="; exit 1; fi
  sleep 2
done
```

**Consequences:** This has run reliably across many deploys with no observed flakiness at this
polling interval (2s) and timeout (180s).

---

## ADR-004: run-all-tests.sh — real 3-way parallelism (unit, integration, Playwright)
**Status:** Accepted

**Context:** Running unit tests, integration tests, and Playwright one at a time during daily
iteration is slow. Naive full 3-way parallelism has a real hazard: unit and integration tests both
compile the same starter modules, so running them concurrently against shared, uncontained
`target/`/`~/.m2` state risks a genuine Maven build race (one process reading/writing
`target/classes` while the other recompiles it). Playwright, by contrast, never touches the Maven
reactor — it only drives an already-built, already-running `marketplace-app` Docker container via
`docker cp`/`docker exec`, so it has nothing to race with the Maven-based suites. Playwright also
needs a fresh, known-clean database to avoid state-pollution false failures, and a freshly-deployed
app to test against.

**Decision:** `scripts/run-all-tests/run.sh` calls `scripts/build-and-test.sh --unit --integration`
for the Maven-based suites — it installs the whole reactor into its own container-isolated `~/.m2`
(a named Docker volume, never the host's real one) *before* either suite runs, so by the time tests
start, neither one writes to shared state anymore, only reads it, eliminating the race. In
parallel with that call, `run-all-tests/run.sh` runs `deploy-and-run.sh` (always clearing app data
first — `--reset-only-db` by default, `--reset` when `--reset` is passed to `run-all-tests.sh`)
sequentially, then `playwright.sh` once that succeeds. Each suite's own flags/scenario args are
grouped behind `--unit "..."` / `--integration "..."` / `--playwright "..."` and forwarded
unchanged — no new flag vocabulary, no duplication of each script's own argument parsing.

**Consequences:** Verified end to end: real 3-way parallelism (unit ‖ integration ‖ Playwright),
unit and integration both PASSED running concurrently with no build race. The two resulting
`build-and-test.sh` invocations in this flow (`run-all-tests.sh`'s own call, and the one
`deploy-and-run.sh` triggers internally to reuse its shared jar) are safe to run concurrently — see
`scripts/build-and-test/run.sh`'s own `BUILD_CONTAINER_NAME` env var, which prevents a Docker
container-name collision the shared-volume `flock` alone doesn't cover. Day-to-day single-suite
iteration outside `run-all-tests.sh` goes through `bash scripts/build-and-test.sh --unit
--no-integration` / `--no-unit --integration` directly.

---

## ADR-009: DB/S3 credentials consolidated into the repo-root `.env`, loaded as fallback defaults (not unconditional overrides) so CI's per-run port overrides survive

**Status:** Accepted

**Context:** DB name/user/password (`experiments`/`experiments_user`/`experiments_user_password`)
and MinIO/S3 credentials (`admin`/`admin12345`, bucket `advertisement`, region `us-east-1`) were
each hardcoded independently across 4-5 files of different formats: `docker-compose.db.yml`,
`docker-compose.minio.yml`, `docker-compose.app.yml`, `application-dev.yml`,
`scripts/deploy-and-run.sh`, `scripts/deploy-and-run/reset.sh` — the same class of duplication already closed
for `POSTGRES_IMAGE` alone. Not a live bug (every copy still agreed), but a real drift risk:
changing one copy and missing the others fails as a confusing "connection refused" at runtime,
not a build error.

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

**`scripts/deploy-and-run.sh` / `scripts/deploy-and-run/reset.sh` — the tricky part:** both already had
`VAR="${VAR:-literal-default}"` override variables (`DB_PORT`, `MINIO_PORT`, etc.) that
`scripts/ci/entrypoint.sh` relies on for its isolated e2e stack (e.g. `DB_PORT=15432`). A naive
`set -a; source .env; set +a` would unconditionally overwrite any already-exported value —
including a CI override — since plain shell assignment doesn't check whether a var came from a
prior export. Instead, `.env` is parsed into `ENV_*`-prefixed variables (never exported directly),
then used only as the *second* fallback tier: `DB_PORT="${DB_PORT:-${ENV_DB_PORT:-5432}}"`. This
preserves the exact existing override precedence (explicit env var wins, `.env` is the new
fallback default, the old hardcoded literal is now only the last-resort fallback if `.env` itself
is missing) — confirmed via a full `bash scripts/deploy-and-run.sh --reset` (fresh DB/MinIO
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
`docker-compose.app.yml`'s `app` service environment and `deploy-and-run.sh`'s app-container `-e
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
  a security hardening pass) and `deploy-and-run.sh`'s deliberate `8081` vs `8080` port distinction
  (untouched, must stay distinct).

---

## ADR-012: `deploy-dev.sh` eliminated

**Status:** Accepted

**Context:** `deploy-dev.sh` ran its own `mvn clean package` for `marketplace-app` directly on
whatever host it was invoked from, entirely separate from `scripts/build-and-test.sh`'s own Maven
invocation for the library modules — two unrelated build steps sharing nothing. On a Windows/WSL
machine without a working local Java install, this failed outright (`Error: JAVA_HOME is not
defined correctly`) until `scripts/build-and-test.sh` was moved onto a shared, containerized build
step (`scripts/build-and-test`, JDK 25 + Docker CLI, no local Java required) to work around the
same Windows/WSL Java-path-translation gap. Once that container's build step also produced
`marketplace-app`'s own JAR (installing the whole reactor, not just the 7 library modules) and
persisted it to a fixed path inside the shared `maven-cache` volume
(`/root/.m2/artifacts/marketplace-app.jar`), `deploy-dev.sh`'s own separate `mvn package` step
became pure duplication — the artifact it needed was already being produced and refreshed by the
shared build step regardless.

**Decision:** `deploy-dev.sh`/`deploy-dev.bat` deleted entirely. A fast JAR hot-swap deploy mode
(`deploy.sh --reload`, hot-swapping a freshly built `marketplace-app.jar` into an already-running
container via `docker cp` + `docker restart`) was tried as its replacement and later reverted —
there is no fast hot-swap deploy mechanism in this repo today. `scripts/build-and-test.sh` only
builds the reactor into the shared `maven-cache` volume; it does not deploy or restart anything.
Restarting the app with a fresh build goes through the standard `deploy-and-run.sh` flow.

**Consequences:** Day-to-day iteration on `marketplace-app` code changes requires a full
`deploy-and-run.sh` run to see them reflected in a running container — there is no shortcut path.
