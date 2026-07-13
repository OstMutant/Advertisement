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

## ADR-003: deploy.sh startup detection via docker logs + grep
**Status:** Accepted

**Context:** Polling `docker logs` repeatedly wastes cycles and adds arbitrary sleep delays.

**Decision:** `deploy.sh` waits for `"Started Application"` via:
```bash
timeout 180 bash -c 'docker logs -f marketplace-app 2>&1 | grep -qm1 "Started Application"'
```

**Consequences:**
- `docker logs -f` streams stdout continuously; `grep -qm1` exits immediately on first match.
- `timeout 180` prevents hanging indefinitely if startup fails.
- Rejected: polling loop with `sleep` — unreliable timing, wastes cycles.
