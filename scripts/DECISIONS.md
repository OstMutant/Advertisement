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

## ADR-010: `deploy.sh` auto-recovers from a Liquibase checksum mismatch (stale local dev DB)

**Status:** Accepted

**Context:** [improvement-120](../backlog/completed/issues/improvement-120-advertisement-user-hard-fk-coupling.md)
edited an already-applied Liquibase changeset in place (`01-advertisement-schema.xml`, removing
the `advertisement`→`user_information` FK constraints — deliberate, since the DB has never been
released, so preserving changelog history across a content edit was never a goal). Every local dev
DB that had already run the old version of that changeset then fails Liquibase's own checksum
validation on the next `deploy.sh` run — a `ValidationFailedException` that crashes the app
container on startup. This is a predictable, recurring class of event for this project (any future
in-place changeset edit on a still-unreleased table hits the same wall), not a one-off — worth
teaching the script instead of re-explaining it and manually running `--reset` every time it recurs.

**Decision:** `deploy.sh`'s Step 3 (`wait_for_app`) now distinguishes three outcomes instead of a
flat "started or timed out": `0` = `Started Application` seen, `1` = the app container's logs
contain the Liquibase checksum-mismatch signature (`changesets check sum`/`ValidationFailedException`),
`2` = anything else (container exited for a different reason, or a genuine timeout). On `1`, the
script automatically calls the same volume-wipe logic `--reset` already used (`reset_infra`,
extracted from the `--reset` branch into a reusable function, alongside a new `start_infra`
covering the rest of Step 1's container/wait/bucket setup), then retries starting the app exactly
once. A second failure of any kind after the retry fails loudly (`exit 1` with the log tail), not
silently — this only ever auto-recovers from the one specific, unambiguous signature, never masks
a different underlying problem.

**Two real bash pitfalls hit and fixed while wiring this up, worth recording since they're easy to
reintroduce:**
1. `if ! wait_for_app; then status=$?; ...` — `$?` inside that block is the exit status of the `if`
   condition test itself (always `0`, since the negated test succeeded), **not** `wait_for_app`'s
   real return value. Fixed with the standard idiom `wait_for_app && status=0 || status=$?`, which
   captures the function's own exit code correctly.
2. Even after fixing (1), a bare `wait_for_app` call (or a `set +e`-wrapped one) still triggered
   this script's own `ERR` trap and exited immediately — the trap fires on any non-zero-returning
   command that isn't inside a *tested* construct (`if`/`&&`/`||`/`!`), independent of whether
   `set -e` itself is currently active. `set +e; wait_for_app; set -e` does not exempt the call;
   only wrapping it in `&&`/`||` (as in the idiom above) does, since that's what the trap's own
   exemption rule actually checks for.

**Consequences:**
- Verified end-to-end, not just by inspection: manually corrupted the `databasechangelog` row's
  `md5sum` in the running dev DB to force the exact failure, ran `deploy.sh` unmodified, confirmed
  the auto-recovery message, volume wipe, and successful retry all fired for real, then confirmed
  the checksum was correctly re-applied afterward. Also verified the untouched happy path (normal
  `deploy.sh`, no corruption) still passes straight through unaffected.
- Scoped to local dev only, by construction — the auto-wipe is the same disposable dev DB/MinIO
  volumes `--reset` already destroys today, never anything resembling a deployed database (this
  project has none yet). A future real migration story for a released database is a separate,
  unrelated problem this ADR does not attempt to solve.

## ADR-011: `.env` parser strips a trailing `\r` — CRLF line endings silently broke `deploy.sh`

**Status:** Accepted

**Context:** ADR-009's `.env`-as-fallback parser (`while IFS='=' read -r _env_key _env_value; do
... printf -v "ENV_$_env_key" '%s' "$_env_value"; done`, in both `deploy.sh` and
`scripts/database/reset.sh`) reads the repo-root `.env` line by line. The repo-root `.env` had
picked up Windows (CRLF, `\r\n`) line endings at some point (likely a Windows editor/git
`autocrlf` interaction — not committed maliciously, just drifted). `read` strips the trailing
`\n` but not a preceding `\r`, so every `ENV_*`-prefixed variable this loop produced silently
carried a trailing carriage return — confirmed directly: `printf "%s" "$ENV_S3_PORT" | od -c`
showed `9 0 0 0 \r`, not a clean `9000`.

This surfaced as `deploy.sh` hanging indefinitely on "Waiting for MinIO..." — `MINIO_PORT`
resolved to `"9000\r"`, so the health-check `curl` hit a malformed URL
(`http://localhost:9000\r/minio/health/live`) and never succeeded, even though the MinIO
container itself was healthy and answered instantly to a `curl` run outside the script (verified
directly, `200 OK`). The `until curl ...; do sleep 1; done` loop then retried forever with no
error surfaced — a silent hang, not a crash, so nothing in the script's own output pointed at the
real cause.

**Decision:** Two-part fix, not either alone:
1. Normalized `.env` itself to LF line endings (`sed -i 's/\r$//'`) — removes the root cause for
   every current consumer of this file.
2. Made both parsers in `deploy.sh` and `scripts/database/reset.sh` defensively strip a trailing
   `\r` from the parsed value regardless: `printf -v "ENV_$_env_key" '%s' "${_env_value%$'\r'}"`.
   Belt-and-suspenders — (1) alone would silently break again if any future editor/tool
   reintroduces CRLF into `.env`, since nothing enforces its line-ending style; (2) alone would
   leave the `.env` file itself inconsistent with every other text file in the repo. Neither is a
   substitute for the other.

**Consequences:**
- `integration-tests`' `SharedEnvConfig` (`java.util.Properties.load(InputStream)`) was not
  affected — the JDK `Properties` format explicitly handles `\n`, `\r`, and `\r\n` line
  terminators natively, confirmed by reading its actual parsing logic before ruling it out as a
  second occurrence of this bug, not by assumption.
- Verified end-to-end: killed the hung `deploy.sh` process, confirmed no `docker build` had even
  started yet (the hang was before Step 2), applied both fixes, reran `deploy.sh` from scratch —
  MinIO's wait now resolves immediately, the full build/start pipeline completes normally.
- No new script flag or manual step introduced — this is a pure correctness fix to logic that
  already existed (ADR-009), not a new mechanism.
