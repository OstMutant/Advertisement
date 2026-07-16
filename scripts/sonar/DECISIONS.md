# Architecture & Technical Decisions — SonarQube

---

## 2026-05-15 — SonarQube setup via Docker, no pom.xml changes

**Decision:** SonarQube analysis is configured entirely in `sonar/` — no plugin or properties added to `pom.xml`. The scanner runs in a `sonarsource/sonar-scanner-cli` container via `docker cp` (same pattern as Playwright). The SonarQube server runs separately in Docker (`sonar/docker-compose.sonar.yml`, port 9099).

**Why:** Keeping analysis tooling out of `pom.xml` avoids polluting the build with dev-only infrastructure. The `sonar/run.sh` script starts the server automatically if not running, copies source and compiled classes, runs analysis, and prints the dashboard URL.

**How to run:**
```bash
bash /app/sonar/run.sh   # Linux / WSL
sonar\run.bat            # Windows
```

**Token:** stored in `sonar/sonar-project.properties` (local dev instance, admin:admin, not sensitive).

---

## 2026-05-15 — sonar.java.binaries required for full Java analysis

**Decision:** Compiled `target/classes` directories are copied alongside source files into the scanner container and referenced via `sonar.java.binaries`.

**Why:** Without bytecode, SonarQube Java sensor fails with `AnalysisException: please provide compiled classes`. Source-only analysis is not supported for Java projects. The project must be compiled locally (by IDE or `mvn compile`) before running `run.sh`.

---

## 2026-05-15 — sonar.java.libraries intentionally empty

**Decision:** `sonar.java.libraries` is not set (third-party jars not copied to scanner container).

**Why:** Copying the full Maven local repository (~hundreds of MB) into the scanner container via `docker cp` is impractical. The consequence is unresolved imports during analysis and slightly less precise results for rules that require type resolution across library boundaries. Acceptable for local code quality checks.

---

## 2026-07-16 — Quality gate blocking by default (improvement-032), opt-out via `--no-gate`

**Decision:** `run.sh` passes `-Dsonar.qualitygate.wait=true` to `sonar-scanner` by default — the
scanner polls the server for the computed quality gate status after upload and the script exits
non-zero if it's `ERROR`. `run.sh --no-gate` (forwarded through `scripts/sonar.sh`) restores the
old informational-only behavior (always exits 0 regardless of the gate result) for a quick manual
scan without waiting on gate computation. `scripts/ci/entrypoint.sh`'s `sonar` stage takes the
default (blocking) — the whole point of wiring this stage into `scripts/ci.sh` was for the gate to
actually fail a CI run, not just produce a report nobody's obligated to look at.

**Why blocking wasn't already the behavior:** the scanner invocation was piped through `tee "$LOG"`
for live + saved output, and the script's own `EXIT_CODE=$?` was reading `tee`'s exit status (always
0), never the scanner's — so even manually adding the wait flag wouldn't have blocked anything
without also fixing this. Fixed by reading `${PIPESTATUS[0]}` instead of `$?`. `set -e` is also
active in this script, which would otherwise abort mid-script on a gate failure (before the HTML
report gets generated — exactly the output someone needs to see *why* the gate failed); handled by
bracketing just the scanner pipe with `set +e` / `set -e`, not a trailing `|| true` on the same
line (that would itself overwrite `PIPESTATUS` with `true`'s own exit code before it could be
read, since bash treats `true` as its own one-command pipeline).

**Not done:** turning on `pipefail` globally for the whole script. Several other pipes in this file
extract values via `grep | cut` from files/API responses that are expected to always match under
normal operation (e.g. reading the stored `sonar.token=` line) — global `pipefail` would make a
missing match instantly fatal via `set -e` at points earlier in the script that currently have
their own, more specific error handling further down (e.g. the empty-token check after generating
a new one). Scoping `set +e`/`set -e` to just the one pipe that actually needs its real exit code
avoids that side effect entirely.
