# Architecture & Technical Decisions — scripts/sonar

---

## ADR-010: JaCoCo coverage wired reactor-wide; `sonar.sh` now runs real tests, not `--no-unit --no-integration`
**Status:** Accepted

**Context:** `new_coverage` always read `0.0%` on every quality-gate run regardless of real test
quality — no JaCoCo agent was ever attached to any test JVM, so Sonar never received coverage data
for any module. Two compounding causes: (1) no `jacoco-maven-plugin` anywhere in the reactor, and
(2) `scripts/sonar/run.sh` built the reactor via `build-and-test.sh --no-unit --no-integration`
(deliberately skipping tests for scan speed), so even a JaCoCo agent would have had nothing to
record. This project's own test-placement convention (see `.claude/rules/integration-tests.md`)
adds a real wrinkle: the 7 domain starters carry no test code of their own — their repository
coverage only exists via `integration-tests`' Testcontainers-based tests — so a naive per-module
`jacoco:report` would report `0%` for every starter regardless of real coverage, since `report`
only attributes exec hits to classes present in the *same* module's own `target/classes`.

**Decision:** `jacoco-maven-plugin` (0.8.14, the first version with official Java 25 support) added
to root `pom.xml`'s `<build><plugins>` (inherited reactor-wide, not `pluginManagement` — every
module needs it applied, none opts in individually), `prepare-agent` + `report` bound to the `test`
phase. `integration-tests/pom.xml` additionally runs `report-aggregate` instead of plain `report` —
it already reactor-depends on every domain starter plus `platform-commons`, exactly what
`report-aggregate` needs to attribute its own exec data back to those starters' real classes.
`build-and-test.sh` copies both report shapes (4 per-module reports + 1 aggregate) into
`$REPORTS_DIR/jacoco/`. `scripts/sonar/run.sh` now invokes `build-and-test.sh --unit --integration`
(not `--no-unit --no-integration`) — coverage data doesn't exist until tests actually run, so a
scan this deliberately skipped tests for speed could never carry real numbers; a sonar run is now
noticeably slower, an accepted trade-off since a scan with no real test execution can't report real
coverage. `sonar.coverage.jacoco.xmlReportPaths` in `sonar-project.properties` lists all 5 report
paths (Sonar's XML importer safely merges overlapping coverage per file+line when a class appears
in more than one report). `sonar.coverage.exclusions` widened from the narrow
`ui/query/elements/**` carve-out to the whole `marketplace-app/.../ui/**` tree — the Vaadin UI
layer is deliberately verified via Playwright, never JUnit, so it should never count against the
coverage threshold in the first place.

**This revises ADR-001's "no pom.xml changes" constraint, narrowly.** `jacoco-maven-plugin` is a
general-purpose Java coverage tool, not Sonar-specific config — Sonar is one consumer of its
output, not the reason it exists. ADR-001's actual concern (keeping *Sonar-specific* plugins/
properties out of the shared `pom.xml`) still holds: no Sonar plugin or Sonar property was added to
`pom.xml`, only a standard coverage instrumentation plugin every module now carries regardless of
whether Sonar ever runs.

---

## ADR-001: SonarQube setup via Docker, no pom.xml changes

**Status:** Accepted — the "no pom.xml changes" constraint is partially revised by ADR-010
(`jacoco-maven-plugin` added to root `pom.xml`); every other decision in this entry (Docker-based
scanner, no Sonar-specific plugin/properties in `pom.xml`) still holds.

**Context:** Decided 2026-05-15. Needed a way to run SonarQube analysis against the project without
adding dev-only infrastructure (plugins, properties) to the shared `pom.xml`.

**Decision:** SonarQube analysis is configured entirely in `scripts/sonar/` — no plugin or
properties added to `pom.xml`. The scanner runs in a `sonarsource/sonar-scanner-cli` container via
`docker cp` (same pattern as Playwright). The SonarQube server runs separately in Docker
(`scripts/sonar/docker-compose.sonar.yml`, port 9099). `scripts/sonar/run.sh` runs
`scripts/build-and-test.sh` first (no local Java needed), which refreshes each module's
`target/classes` into the shared `maven-cache` volume; the scanner container mounts that volume
directly (`-v maven-cache:/root/.m2`) and copies the needed classes into its own
`/tmp/sonar-src/<module>/target/classes` internally (container-to-container, via `docker exec`),
referenced via `sonar.java.binaries` — SonarQube's Java sensor requires compiled bytecode
(`AnalysisException: please provide compiled classes` otherwise; source-only analysis is not
supported for Java projects), and this way no local compile is ever required. `run.sh` starts the
server automatically if not running, copies source files, runs analysis, and prints the dashboard
URL.
```bash
bash scripts/sonar.sh   # Linux / WSL
scripts\sonar.bat       # Windows
```

**Token:** stored in `scripts/sonar/sonar-project.properties` (local dev instance, admin:admin, not
sensitive).

---

## ADR-003: `sonar.java.libraries` intentionally left empty

**Status:** Accepted

**Context:** Decided 2026-05-15. Copying the full Maven local repository (~hundreds of MB) into the
scanner container via `docker cp` is impractical.

**Decision:** `sonar.java.libraries` is not set (third-party jars not copied to the scanner
container). Accepted consequence: unresolved imports during analysis and slightly less precise
results for rules that require type resolution across library boundaries — acceptable for local
code quality checks.

---

## ADR-004: Quality gate blocking by default, opt-out via `--no-gate`

**Status:** Accepted

**Context:** Decided 2026-07-16. The scanner invocation was piped through `tee "$LOG"` for live +
saved output, and the script's own `EXIT_CODE=$?` was reading `tee`'s exit status (always 0), never
the scanner's — so manually adding `-Dsonar.qualitygate.wait=true` alone would not have blocked
anything.

**Decision:** `run.sh` passes `-Dsonar.qualitygate.wait=true` to `sonar-scanner` by default — the
scanner polls the server for the computed quality gate status after upload and the script exits
non-zero if it's `ERROR`. `run.sh --no-gate` (forwarded through `scripts/sonar.sh`) restores the
old informational-only behavior (always exits 0 regardless of the gate result) for a quick manual
scan without waiting on gate computation. `scripts/ci/dagu/ci.yaml`'s `sonar` step takes the
default (blocking) — the whole point of wiring this step into `scripts/ci.sh` was for the gate to
actually fail a CI run, not just produce a report nobody's obligated to look at. Fixed by reading
`${PIPESTATUS[0]}` instead of `$?`. `set -e` is also active in this script, which would otherwise
abort mid-script on a gate failure (before the HTML report gets generated — exactly the output
someone needs to see *why* the gate failed); handled by bracketing just the scanner pipe with
`set +e` / `set -e`, not a trailing `|| true` on the same line (that would itself overwrite
`PIPESTATUS` with `true`'s own exit code before it could be read, since bash treats `true` as its
own one-command pipeline).

**Not done:** turning on `pipefail` globally for the whole script. Several other pipes in this file
extract values via `grep | cut` from files/API responses that are expected to always match under
normal operation (e.g. reading the stored `sonar.token=` line) — global `pipefail` would make a
missing match instantly fatal via `set -e` at points earlier in the script that currently have
their own, more specific error handling further down (e.g. the empty-token check after generating
a new one). Scoping `set +e`/`set -e` to just the one pipe that actually needs its real exit code
avoids that side effect entirely.

---

## ADR-006: SonarQube server and scanner containers get a pull-then-up freshness check, not just an API health check

**Status:** Accepted

**Context:** `run.sh`'s server-container logic only acted when the API health check
(`curl .../api/system/status`) failed — if the container was already running, `run.sh` never
checked whether its image was current, and never explicitly handled "container doesn't exist" as
its own case. The scanner container (`sonarsource/sonar-scanner-cli:latest`, a second
Docker-pulled image this same script manages) had the same gap: it only got recreated on "missing
or not running," never checked whether a newer `:latest` had been published, so it could drift
stale indefinitely once created.

**Decision:**
- Server container: an unconditional `docker compose pull -q` followed by `docker compose up -d`
  runs on every invocation. `pull` only downloads when the registry image digest differs from
  what's cached locally — a near-instant no-op on every run where nothing changed upstream. `up -d`
  then covers all three real container states in one call: creates the container from scratch if
  it doesn't exist yet, recreates it in place if `pull` just fetched a newer image, or leaves an
  already-current, already-running container untouched — no hand-rolled `docker inspect` branching
  needed, Compose's own reconciliation already does this correctly. The existing
  `until curl ... "status":"UP"` wait loop runs unconditionally afterward too. If the server lands
  in `DB_MIGRATION_NEEDED` after an image upgrade, the wait loop calls `POST
  /api/system/migrate_db` once; SonarQube Community Edition runs on its bundled H2 database, which
  cannot be schema-migrated across versions via the API (`"state":"NOT_SUPPORTED"`) — on that
  response the loop runs `docker compose down -v` (wipes this container's own three named volumes)
  followed by `up -d` to start fresh on the new image, then keeps polling. Accepted data loss:
  local scan history/dashboards only — a fresh baseline analysis re-establishes them within the
  same `run.sh` invocation.
- Scanner container: since it isn't Compose-managed, there's no `up -d` reconciliation to lean on
  — `docker pull -q` followed by comparing `docker image inspect -f '{{.Id}}'` (the freshly pulled
  image) against `docker inspect -f '{{.Image}}'` (the running container's actual image id) decides
  by hand whether a recreate is needed, recreating only on an actual mismatch (or a
  missing/non-running container) rather than unconditionally every run.
- Both freshness checks can leave a now-untagged image behind (the replaced `sonarqube:community`
  or `sonar-scanner-cli:latest`) — `docker image prune -f` runs once after both checks, same
  reasoning `deploy.sh` documents for its own post-build prune: only dangling (untagged) images are
  removed, by definition unreferenced by any tag/container, so this can never touch another
  stack's active image.

`docker-compose.sonar.yml` carries a header comment stating this container's lifecycle is fully
owned by `run.sh` — starting/stopping it by hand bypasses the freshness check the next `run.sh`
invocation expects to have already happened.

**Consequences:** Verified end to end against a real stale local container: the `DB_MIGRATION_NEEDED`
wipe-and-restart path recovered cleanly (`STARTING` → `UP`), followed by a full `sonar-scanner` run
reaching `ANALYSIS SUCCESSFUL`. The dangling-image prune was confirmed against a real stray 2.52GB
leftover `sonarqube:community` image, cleaned up after a full end-to-end `run.sh` run
(`docker images -f dangling=true` empty afterward, exactly 2 named `sonar*` containers, both on
current images).

---

## ADR-008: `sonar.sources`/`sonar.java.binaries` module list auto-validated against `pom.xml` before every run, not hand-maintained

**Status:** Accepted

**Context:** `sonar-project.properties`'s `sonar.sources`/`sonar.java.binaries` lists every module's
`src/main/java`/`target/classes` path by hand, one line per module. A module added to (or removed
from) `pom.xml`'s `<modules>` has no mechanism forcing a matching edit here — the list can silently
drift stale, meaning Sonar quietly stops scanning a real module (or scans a path that no longer
exists) with no error, since a missing/extra source directory isn't itself invalid config.

**Decision:** `run.sh` validates this list against `pom.xml` on every invocation, before touching
Docker — parses `pom.xml`'s real `<modules>` (excluding `integration-tests`, which is deliberately
never scanned — test-only tooling, never shipped, same exclusion the module already had), compares
against the module names currently listed in `sonar.sources`, and rewrites both the `sonar.sources`
and `sonar.java.binaries` blocks in place if they don't match — adding missing modules, dropping
stale ones. A no-op (no file write, single log line) when already in sync, which is the common
case. Implemented as a small inline Python step (block-boundary detection via the trailing `\`
continuation character, same shape the file already uses) rather than `sed`, since correctly
rewriting a multi-line backslash-continued block is not realistically expressible as a `sed`
one-liner without real risk of corrupting the file.

**CRLF bug found and fixed while verifying.** This repo's working tree is CRLF (`core.autocrlf`) —
the same root cause `run.sh` already strips from the stored token before Basic Auth (`tr -d '\r'`
on every read of `sonar.token`). Python's default text-mode `open()`
does universal-newline translation on read but writes plain `\n` on write unless told otherwise —
an initial version of this script silently rewrote the *entire* file from CRLF to LF on its very
first run, which would have produced a spurious full-file diff (every line touched) on the next
`git diff`, even though the module list itself was already correct and unchanged. Fixed by opening
both the read and the write with `newline=''` (disables the translation, `\r\n` round-trips
byte-for-byte) and detecting each block's real line ending from its own first line rather than
assuming `\n`. Verified directly: ran the validation against the real file with `newline=''` and
confirmed `git diff` shows no changes when the module list already matches (only the two
independent, real header-text edits made this same session show up), then against a deliberately
corrupted copy (one module's lines removed) and confirmed it restores byte-for-byte identical
output to the real file. Also ran a full end-to-end `bash scripts/sonar/run.sh --no-gate` afterward
— validation logs "already matches pom.xml," analysis reaches `ANALYSIS SUCCESSFUL`.

---

## ADR-009: dashboard browsable anonymously — `sonar.forceAuthentication` set to `false` every run, not once by hand

**Status:** Accepted

**Context:** SonarQube requires logging in (default `admin`/`admin`, which itself forces a
password-change prompt on first UI login) before the dashboard is viewable at all, out of the box.
For a purely local, single-user dev instance this is friction with no real security benefit.
`sonar.forceAuthentication=false` is the setting that allows anonymous browsing — but it stopped
being settable via `sonar.properties`/environment variable as of SonarQube 9.0 (confirmed via web
search); on a current version (this instance runs 26.8.0) it's a database-persisted setting,
changeable only through the web UI or the REST API (`POST /api/settings/set`).

**Decision:** `run.sh` calls `POST /api/settings/set -d "key=sonar.forceAuthentication&value=false"`
(via `admin:admin` Basic Auth, same credentials the token-generation step already uses) once per
run, right after the server is confirmed `UP`. Deliberately every run, not a one-time manual API
call: this setting lives in the server's own database, which `docker-compose.sonar.yml down -v`
(the DB-migration-failure recovery path, ADR-006) wipes along with everything else — a one-time
fix would silently revert to requiring authentication again after the next such wipe. Verified
directly: reset the setting via `POST /api/settings/reset`, confirmed anonymous access then returns
`401`, ran `run.sh`, confirmed it returns `200` again afterward — then loaded the real dashboard in
a headless browser (via `host.docker.internal`, reusing the existing screenshot-container tooling)
and confirmed the Overview page renders directly, no login redirect, only a non-blocking "Log in"
button in the corner. The separate "change the default admin password" prompt SonarQube shows on
an actual interactive login with `admin`/`admin` is a different mechanism (tied to using the
factory-default admin credentials for a real authenticated session) — unaffected by this setting
and not something this change addresses; it only matters if someone deliberately logs in as admin,
which normal anonymous dashboard viewing never needs to do.
