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
