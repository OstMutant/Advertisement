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
