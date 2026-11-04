# Architecture & Technical Decisions — scripts/

---

## ADR-017: AI dev containers (Claude Code, opencode) install their CLI with an explicit tag and `--engine-strict`; versions are Dockerfile ARGs, Node 24

**Status:** Accepted

**Applied:** 1

**Context:** `Dockerfile.claude` installed Node 20 and ran a bare `npm install -g
@anthropic-ai/claude-code`. Claude Code 2.1.198 and later declare `engines.node >=22`; under
Node 20 a bare-name install silently resolves the newest engine-compatible release (2.1.197) even
though `latest` was 2.1.302 (verified with `npm install --dry-run` under Node 20.20.2 / npm
10.8.2). An explicit tag (`@latest`, `@x.y.z`) installs the newest release under Node 20 with only
an `EBADENGINE` warning, and `--engine-strict` turns that warning into a hard failure. The
container runs with `docker run --rm`, so the in-container auto-updater's newer binary is
discarded on every exit and each start runs the image's baked-in version; `claude.bat --update`
also reused Docker's cached npm layer. Net effect: Sonnet 5.5 (needs Claude Code 2.1.284 or later)
never appeared in `/model`, however often the image was rebuilt.

**Decision:**
- Versions are Dockerfile `ARG`s with defaults living only in `Dockerfile.claude`
  (`BASE_IMAGE=eclipse-temurin:25-jdk-jammy`, `NODE_MAJOR=24`, `CLAUDE_CODE_VERSION=latest`,
  `CLAUDE_CODE_REFRESH=0`), the same pattern `scripts/ci/Dockerfile` uses for its tool version.
  `scripts/claude.bat` holds only the image/container names and passes a fresh
  `CLAUDE_CODE_REFRESH` on `--update`; the `ARG` is declared after the apt layer, so only the
  Claude Code layer rebuilds and the ~1 GB apt layer stays cached.
- The install is `npm install -g --engine-strict @anthropic-ai/claude-code@${CLAUDE_CODE_VERSION}`
  (explicit tag plus strict engines), so a Node/package mismatch fails the build loudly instead of
  silently shipping an older release. `claude --version` is printed in the build log.
- Node 24, not 22: both satisfy `engines.node >=22`, but 22's security support ends 2027-04-30
  versus 2028-04-30 for 24.
- `CLAUDE_CODE_VERSION` defaults to `latest` on purpose: a newly launched model can require a
  Claude Code newer than the `stable` channel serves.
- `Dockerfile.opencode` and `scripts/opencode.bat` follow the identical shape (`BASE_IMAGE`,
  `NODE_MAJOR=24`, `OPENCODE_VERSION=latest`, `OPENCODE_REFRESH=0`, `opencode-ai@${OPENCODE_VERSION}`
  with `--engine-strict`, `opencode --version` in the build log) — one decision for both
  containers. `opencode-ai` declares no `engines` today, so `--engine-strict` is a no-op there that
  keeps the two Dockerfiles interchangeable; the old Node 20 had left security support on 2026-04-30.

**Rejected alternatives:**
- Pinning the base image to a digest: recommended by Docker for supply-chain integrity, but this
  is a dev tool container on a moving `jammy` tag and the upkeep outweighs the benefit.
- A `docker-compose.claude.yml` with `build.args`: would move `claude.bat` from `docker
  build`/`docker run` to compose for no gain.
- Root `.env` as the version source: it feeds the compose deploy scripts, and parsing it in batch
  is fragile.
- The `stable` release channel, `DISABLE_AUTOUPDATER` (the in-container updater is harmless since
  `--rm` discards it), and version overrides passed through `claude.bat` variables (a second
  source of truth).

**Consequences:** rebuilding always yields the newest Claude Code; bumping any version is a
one-line `ARG` default change; a Node major below the package's `engines.node` breaks the build
visibly; both containers are bumped and rebuilt the same way.

**Sources:**
- [Docker build best practices](https://docs.docker.com/build/building/best-practices/) — pin versions, version variables for easier bumps, `apt-get update` and `install` in one `RUN`
- [Claude Code: install with npm](https://code.claude.com/docs/en/setup#install-with-npm) — npm package requires Node 22+, upgrade with `@latest`, specific version via `@x.y.z`, `stable` vs `latest` channels
- [Node.js release schedule](https://endoflife.date/nodejs) — Node 22 and 24 support windows
- [How to Build Docker Images with Cache Busting](https://oneuptime.com/blog/post/2026-01-30-docker-cache-busting/view) — third-party article, not official Docker documentation: `ARG` as a cache-bust value, declared as late as possible

---

## ADR-016: MinIO → SeaweedFS local dev/CI storage backend swap

**Status:** Accepted

**Context:** MinIO Community Edition's GitHub repo was archived upstream 2026-04-25 after a
maintenance wind-down — license changed Apache 2.0 → AGPLv3 (2025-05), Admin GUI removed, Docker
Hub publishing stopped. No further upstream security fixes will land on it. The project needed a
replacement local S3-compatible storage backend for `scripts/deploy-and-run/`'s dev stack and
`scripts/ci/dagu/ci.yaml`'s isolated e2e stack.

**Decision:** Replaced MinIO with SeaweedFS (`chrislusf/seaweedfs` image) — Apache 2.0 licensed,
actively maintained (36+ committers/12mo, 35.3k stars). `weed server -s3` runs
master+volume+filer+S3-gateway as one process; `s3.json` identities (admin write + anonymous
bucket-scoped read) generated inline at container startup from `.env`-sourced credentials, passed
as real container environment variables (not embedded in the generating shell string, to avoid
outer-shell substitution corrupting the JSON before escaping runs). Readiness is checked via the
S3 gateway port alone (not the master's `/cluster/status` too) — a single readiness check,
matching the same "one readiness mechanism, not two" principle `scripts/ci/DECISIONS.md` already
established for a different service. A shared entrypoint script mounted via host-path bind mount
was attempted first to avoid duplicating this logic between `run.sh`'s raw `docker run` path and
`docker-compose.seaweedfs.yml`; reverted because host-path bind mounts don't work reliably in this
sandbox — the same pre-existing constraint `scripts/ci/DECISIONS.md` documents for its own
container-source-sync mechanism — so duplication between the two paths is accepted as a trade-off.

**Rejected alternatives:** Garage (AGPLv3-licensed, far smaller community — 83 contributors total
vs. SeaweedFS's active base) was considered and rejected on license + maintenance-activity
grounds.

---

## ADR-015: `--in-container` mode — sync reliability (no `exec`, container-internal directory restoration, OverlayFS `sync` before copy-out)

**Status:** Accepted

**Context:** `--in-container` (ADR-014's engine, undocumented decision of its own) runs a wrapped
script inside a disposable `dev-shell` container via `scripts/utils/ensure-dev-shell.sh`/
`sync-source.sh`, so a script's live state survives regardless of which shell triggered it. Three
real, confirmed bugs made this unreliable: (1) `run.sh` used `exec docker exec ...` to invoke the
inner run, which replaced the host process entirely and never returned control to copy results
back out; (2) `sync_source_into`'s wipe-and-`git ls-files`-recreate never restores gitignored
output directories (`scripts/logs/`, `playwright/pw-report/`, ...) since git tracks no files
there, and `docker cp` can create only one missing leaf directory, not a multi-level missing
path — silently dropping any script output written two or more levels under a freshly-wiped
root; (3) OverlayFS (`dev-shell`'s container filesystem) can leave a just-written file's data in
dirty pages not yet visible to a separate `docker cp` read issued immediately after the writing
process exits — confirmed via two real runs where `docker cp` for one path silently returned
nothing while an adjacent, near-identical `docker cp` in the same function succeeded.

**Decision:** (1) `run.sh`'s `--in-container` branch drops `exec`, captures the inner `docker
exec`'s real exit code, and calls a new `sync_artifacts_from_dev_shell`
(`scripts/utils/sync-artifacts-from-dev-shell.sh`) before returning it — mirrors
`scripts/ci/run.sh`'s own `sync_artifacts()` for the same class of problem. (2) `sync_source_into`
now also runs a container-internal `docker exec "$container" mkdir -p .../scripts/logs
.../playwright/pw-report ...` right after the wipe+extract — a container-internal `mkdir`, not a
raw host-side one, so the WSL-bind-mount-unsafe finding does not apply. (3) `run.sh` runs `docker
exec dev-shell sync` between the inner run finishing and the copy-out step, forcing OverlayFS to
flush before `docker cp` reads.

**Rejected alternative:** wrapping each `docker cp` in a blind retry loop — would mask the symptom
without addressing why a fresh read can race a completed write; `sync` is the documented,
deterministic primitive for this exact class of problem.

**Consequences:** `--in-container` now reliably returns both the HTML/log artifacts and
`tree.txt`/`raw.log` to the host after every run, confirmed via two real end-to-end runs (both
`pw-report/index.html` and `scripts/logs/playwright/run.log` landing automatically, no manual
`docker cp`).

---

## ADR-014: Activity Monitor — token-efficient step-checklist narration wrapping backgrounded scripts, chained ahead of the agent's own Monitor tool

**Status:** Accepted

**Context:** The agent's `Monitor` tool tails a backgrounded script's raw stdout directly, so every
raw log line (Testcontainers/Maven chatter especially) lands in the agent's own context regardless
of whether it comments on it — cost scales with a script's log verbosity, not its actual signal.

**Decision:** `scripts/activity-monitor/run.sh` wraps a target command instead of tailing a
pre-existing log file — it spawns the command, captures its raw output to its own log, redacts
secrets unconditionally, and mechanically parses the existing `AGENTIC_SUCCESS_BLOCK`/
`AGENTIC_ERROR_BLOCK` contract (ADR-013) via one shared `profiles/agentic.sh` — not one profile per
script, since all 7 top-level scripts emit the same JSON shape — into a small step-state table.
Content with no matching profile falls back to a single cheap-model (Haiku) narration call per
batch. Pass/fail is always the wrapped command's own real exit code, never narration. The agent
backgrounds `run.sh` instead of tee-ing raw output, and points `Monitor` at the resulting
`tree.txt` (redrawn only on a real step transition) instead of the raw log. A human can run the
identical command standalone in a second terminal pane (`--watch`) for the same view with no agent
involved — both modes share one engine, no duplicated rendering logic.

Two real findings shaped the implementation, not assumptions: `jq` is not installed anywhere in
this sandbox and is not a dependency of any other script in this repo — confirmed directly before
use, so `profiles/agentic.sh` parses the fixed-shape JSON via targeted `sed` instead (`jq` stays a
real dependency only of the `generic` profile's model-narration fallback, guarded with a visible
error instead of a silent failure if missing). `scripts/deploy-and-run.sh` has no execute bit in
this checkout, and this project's own convention always invokes scripts via an explicit `bash
scripts/X.sh` — `run.sh`'s script-name detection recognizes an interpreter prefix (`bash`/`sh`/
`python3`) and reads the real script from the next argument instead of the interpreter's own name.

`scripts/deploy-and-run/run.sh` gained 3 additional `emit_agentic_success_block` calls
(infra/build/start-container, alongside its existing `start-application`) so the checklist reflects
real step boundaries instead of one coarse marker per script. `ci.sh` is explicitly excluded — it
keeps its own existing Dagu-API-based `watch-run.py` mechanism (already gives exact, mechanical,
parallel-branch-aware step status for free; this tool would only duplicate it).

**Rejected alternatives:**
- A `PostToolUse` hook intercepting `Monitor`'s own tool-call result to redact/narrate before the
  agent's context — checked directly against Claude Code's real hooks documentation: `PostToolUse`
  fires once per tool call, and `Monitor` is fire-and-forget (its own tool call returns
  immediately; every subsequent notification arrives as a separate async conversation event, not
  part of that original tool call) — so it cannot intercept `Monitor`'s live streamed
  notifications. Also independently ruled out regardless: hooks only exist inside a Claude Code
  session, so they could never cover the standalone/no-agent usage mode either.
- A bespoke profile file per script — rejected once confirmed 5 of 7 (now all 7) scripts already
  emit the identical `agentic-output.sh` JSON shape; one shared profile plus a small per-script
  metadata map (step labels, container association) avoids duplicating the same parsing logic 7
  times.

**Deferred, not solved here:** a Sonar quality-gate failure caused only by the already-tracked
JaCoCo/`new_coverage` gap should render as a non-critical warning rather than a full error, but
that needs Sonar's own per-condition quality-gate breakdown, not just the single `description`
string `AGENTIC_ERROR_BLOCK` carries today — not implemented; a Sonar quality-gate failure
currently renders as a plain error regardless of cause.

**Extended, same session:** a long-running step redraws the tree only on a real transition, so a
human watching had no way to tell "still working" from "stalled" during a multi-minute step. Added
per-step elapsed/duration display, a one-line description per step, and stall detection —
adaptive, learned from `$WORK_DIR/history.tsv`'s own past-run durations (2× a step's own historical
max) rather than one hand-picked constant, plus a direct `docker inspect` container-state check for
container-associated steps. A CPU-usage-based "is it actually working" signal was considered and
rejected: this project's heaviest steps delegate real work into Docker containers, invisible to the
wrapping script's own host process tree, so a CPU check would read near-zero exactly during the
steps most worth watching.

---

## ADR-013: Agentic JSON output envelope (AGENTIC_SUCCESS_BLOCK/AGENTIC_ERROR_BLOCK) for script results
**Status:** Accepted

**Context:** An AI agent reading a script's raw output only had free-text signals (`=== FAILED
===`, an exit code) to work from — no structured way to tell a failure's category or whether
retrying is worth it without a human reading the log. A large mission-style prompt proposed a full
DAG-aware execution contract (run-identity hierarchy, concurrency-safe state directories, event
streams, dependency gates) — checked against the real project state and found overbuilt for
today's actual scale (linear scripts, no concurrent self-invocation, no state files written today;
full analysis in the originating issue). The real private certification document independently
describes the same minimal shape actually needed: a structured response with
`errorCategory`/`isRetryable`/`description` (`transient`/`validation`/`business`/`permission`
taxonomy), used in the same shape on both success and failure.

**Decision:** A new shared library, `scripts/utils/agentic-output.sh` (same shared-library pattern
as the existing `scripts/utils/ensure-docker-plugins.sh`), provides two functions —
`emit_agentic_success_block(current_step)` and `emit_agentic_error_block(category, is_retryable,
current_step, description)`. Both print one JSON line (`AGENTIC_SUCCESS_BLOCK: {...}` /
`AGENTIC_ERROR_BLOCK: {...}`), including a `durationSeconds` field read from bash's own `$SECONDS`
(each caller sets `SECONDS=0` as its first executable line). Category is restricted to the four
real certification categories — no invented fifth (`environment`), which does not appear in the
source material.

Wired into all 7 top-level `scripts/*.sh` entry points' own `run.sh`/`reset.sh` —
`deploy-and-run/run.sh`, `deploy-and-run/reset.sh`, `sonar/run.sh`, `build-and-test/run.sh`,
`run-all-tests/run.sh`, `ci/run.sh`, `playwright/run.sh` — at every real exit path, not only the
generic `trap ERR` catch-all, since bash's `ERR` trap does not fire on an explicit `exit N`
(verified directly). The wiring mechanism was matched to each script's own real control-flow shape
rather than forced uniformly:
- Scripts already `set -e`-shaped (`deploy-and-run/run.sh`, `deploy-and-run/reset.sh`,
  `sonar/run.sh`, `build-and-test/run.sh`) got a `trap ERR`-based emit plus explicit emits at each
  additional explicit-exit branch.
- Scripts with no `set -e`, a single linear command, and one captured `$?`/final `exit`
  (`ci/run.sh`, `playwright/run.sh`) got emit calls placed directly at each existing exit point, no
  trap added.
- `run-all-tests.sh` deliberately runs its build-and-test and deploy+playwright branches to
  completion independently even if one fails, then combines both exit codes — adding
  `set -e`/`trap ERR` there would break that intentional behavior, so one emit call was added only
  at its own already-existing combined-result point.

**Rejected alternatives:**
- Full mission scope (run-identity hierarchy, concurrency-safe state directories, event streams,
  programmatic dependency gates) — real cost, no demonstrated need today.
- A uniform `trap ERR` wired identically into every script regardless of its control-flow shape —
  rejected because it would silently break `run-all-tests.sh`'s intentional dual-branch behavior.
- An invented `environment` error category — rejected once checked directly against the real
  certification source, which uses `permission`, not `environment`, as the fourth category.

**Consequences:** Every script invocation now ends with one machine-parseable JSON line
(`AGENTIC_SUCCESS_BLOCK`/`AGENTIC_ERROR_BLOCK`, including `durationSeconds`) an AI agent can parse
instead of scraping free text — `durationSeconds` specifically feeds `.claude/rules.md`'s
mandatory `## Operational notes` script-run block. No error path has been exercised live in any of
the 7 scripts yet — only a syntax check plus one live success-path run of `deploy-and-run.sh`. A
future script added under `scripts/` should follow the same pattern rather than reinventing
free-text-only failure reporting.

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
