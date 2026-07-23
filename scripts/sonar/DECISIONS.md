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

---

## 2026-07-23 — improvement-113 findings: `sonar.coverage.exclusions` for `ui/query/elements/**`; JaCoCo gap discovered

**Decision:** After improvement-113 (converting `ui/query/elements/*` to plain classes), a
`bash scripts/sonar.sh` run failed the quality gate on two conditions:

1. `new_violations = 2` — both traced to already-known false positives from an earlier session
   (S4276 on `AdvertisementSaveService.save()`'s `Function<EntityRef, Long>` param — genuinely
   needs to stay `Function`, not `ToLongFunction`, since the real caller can return `null`; S2065
   `transient` fields on `SettingsOverlay`/`TimelineQueryBlock` — both extend Vaadin `Component`
   subclasses that are genuinely `Serializable`, but `sonar.java.libraries` being empty (see
   2026-05-15 entry above) means the scanner can't see that). Resolved via targeted
   `@SuppressWarnings` at each site — not a workaround, since both are confirmed non-issues, just
   annotated as such instead of left to keep tripping the gate every run. A third finding
   (`SignUpDialog.java` S7467, "replace `e` with unnamed pattern") was a stale record — `e` is
   genuinely used in the adjacent `log.warn(..., e)` call — same `@SuppressWarnings` treatment.
2. `new_coverage = 0.0%` (threshold 80%) — added `sonar.coverage.exclusions` for
   `ui/query/elements/**` (the classes actually touched by improvement-113) as a narrow, scoped
   exclusion, since these are pure Vaadin UI wiring classes verified by Playwright e2e, not JUnit.

**Found but deliberately not fixed here:** `sonar.coverage.jacoco.xmlReportPaths` was never
configured anywhere in this project — Sonar has never received real coverage data for *any*
module, meaning `new_coverage` reads `0.0%` for any leak period containing new lines regardless of
actual test quality. The narrow `ui/query/elements/**` exclusion only masks this for the files
touched today; the next PR touching any other module hits the same wall. Filed as
[improvement-114](../../backlog/issues/improvement-114-sonar-jacoco-coverage-not-wired.md) rather
than fixed inline — wiring JaCoCo project-wide (5+ modules, unit+integration aggregation) is a
separate, non-trivial infrastructure task, and even once wired it will only raise coverage for the
service layer (which has real JUnit tests) — the UI layer's Playwright-based strategy doesn't feed
JaCoCo without separate server-side instrumentation, a materially bigger, likely-not-worth-it lift.
