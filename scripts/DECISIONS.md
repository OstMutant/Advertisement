# Architecture & Technical Decisions — scripts/

---

## 2026-05-26 — Prefer scripts over raw commands for build and test

**Decision:** All build, deploy, and test operations must be performed via the scripts in `scripts/` and `playwright/`, not via raw `docker`, `mvn`, or `docker compose` commands directly.

| Operation | Script |
|---|---|
| Full prod rebuild + start | `bash scripts/deploy.sh` |
| Fast JAR hot-swap | `bash scripts/deploy-dev.sh` |
| Run all Playwright tests | `bash playwright/run.sh` |
| Run one scenario | `bash playwright/run.sh <scenario>` |
| SonarQube analysis | `bash scripts/sonar.sh` |

**Why:** Scripts encapsulate the correct flags, env vars, Docker network settings, and startup detection. Raw commands bypass these and produce inconsistent results (wrong profiles, missing env vars, volume mount issues in the Claude container).

**Rule:** If a new recurring operation is needed, add a script — do not document raw commands as the canonical way to run it.

---

## 2026-05-16 — scripts/ folder created

**Decision:** All root-level developer scripts (`.bat`, `.sh`) except `mvn.bat` moved here. Each script resolves the project root via `cd /d "%~dp0.."` (bat) or `$(dirname "$0")/..` (sh).

**Why:** Keeps the project root clean. Scripts are developer tooling, not project artifacts.

**Rejected:** Keeping `mvn.bat` in `scripts/` — it is invoked constantly during development and is more ergonomic at the root.

---

## 2026-05-16 — deploy.sh uses docker logs -f + grep -qm1 for startup detection

**Decision:** `deploy.sh` waits for `"Started Application"` via:
```bash
timeout 180 bash -c 'docker logs -f marketplace-app 2>&1 | grep -qm1 "Started Application"'
```

**Why:** Polling `docker logs` repeatedly wastes cycles and adds arbitrary sleep delays. `docker logs -f` streams stdout continuously; `grep -qm1` exits immediately on first match. `timeout 180` prevents hanging indefinitely if startup fails.

**Rejected:** Polling loop with `sleep` — unreliable timing, wastes cycles between checks.
