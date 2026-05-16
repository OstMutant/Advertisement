# Architecture & Technical Decisions — scripts/

---

## 2026-05-16 — scripts/ folder created

**Decision:** All root-level developer scripts (`.bat`, `.sh`) except `mvn.bat` moved here. Each script resolves the project root via `cd /d "%~dp0.."` (bat) or `$(dirname "$0")/..` (sh).

**Why:** Keeps the project root clean. Scripts are developer tooling, not project artifacts.

**Rejected:** Keeping `mvn.bat` in `scripts/` — it is invoked constantly during development and is more ergonomic at the root.

---

## 2026-05-16 — deploy.sh uses docker logs -f + grep -qm1 for startup detection

**Decision:** `deploy.sh` waits for `"Started Application"` via:
```bash
timeout 180 bash -c 'docker logs -f advertisement-app 2>&1 | grep -qm1 "Started Application"'
```

**Why:** Polling `docker logs` repeatedly wastes cycles and adds arbitrary sleep delays. `docker logs -f` streams stdout continuously; `grep -qm1` exits immediately on first match. `timeout 180` prevents hanging indefinitely if startup fails.

**Rejected:** Polling loop with `sleep` — unreliable timing, wastes cycles between checks.
