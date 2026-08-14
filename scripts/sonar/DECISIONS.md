# Architecture & Technical Decisions — scripts/sonar

---

## ADR-001: SonarQube setup via Docker, no pom.xml changes

**Status:** Accepted

**Context:** Decided 2026-05-15. Needed a way to run SonarQube analysis against the project without
adding dev-only infrastructure (plugins, properties) to the shared `pom.xml`.

**Decision:** SonarQube analysis is configured entirely in `scripts/sonar/` — no plugin or
properties added to `pom.xml`. The scanner runs in a `sonarsource/sonar-scanner-cli` container via
`docker cp` (same pattern as Playwright). The SonarQube server runs separately in Docker
(`scripts/sonar/docker-compose.sonar.yml`, port 9099). `scripts/sonar/run.sh` starts the server
automatically if not running, copies source and compiled classes, runs analysis, and prints the
dashboard URL:
```bash
bash scripts/sonar.sh   # Linux / WSL
scripts\sonar.bat       # Windows
```

**Token:** stored in `scripts/sonar/sonar-project.properties` (local dev instance, admin:admin, not
sensitive).

---

## ADR-002: `sonar.java.binaries` required for full Java analysis

**Status:** Accepted

**Context:** Decided 2026-05-15. Without bytecode, SonarQube's Java sensor fails with
`AnalysisException: please provide compiled classes` — source-only analysis is not supported for
Java projects.

**Decision:** Compiled `target/classes` directories are copied alongside source files into the
scanner container and referenced via `sonar.java.binaries`. The project must be compiled locally
(by IDE or `mvn compile`) before running `run.sh`.

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
scan without waiting on gate computation. `scripts/ci/entrypoint.sh`'s `sonar` stage takes the
default (blocking) — the whole point of wiring this stage into `scripts/ci.sh` was for the gate to
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

## ADR-005: `sonar.coverage.exclusions` for `ui/query/elements/**`; JaCoCo gap discovered

**Status:** Accepted

**Context:** Decided 2026-07-23. After converting `ui/query/elements/*` to plain classes, a
`bash scripts/sonar.sh` run failed the quality gate on two conditions:

1. `new_violations = 2` — both traced to already-known false positives from an earlier session
   (S4276 on `AdvertisementSaveService.save()`'s `Function<EntityRef, Long>` param — genuinely
   needs to stay `Function`, not `ToLongFunction`, since the real caller can return `null`; S2065
   `transient` fields on `SettingsOverlay`/`TimelineQueryBlock` — both extend Vaadin `Component`
   subclasses that are genuinely `Serializable`, but `sonar.java.libraries` being empty (see
   ADR-003) means the scanner can't see that). Resolved via targeted `@SuppressWarnings` at each
   site — not a workaround, since both are confirmed non-issues, just annotated as such instead of
   left to keep tripping the gate every run. A third finding (`SignUpDialog.java` S7467, "replace
   `e` with unnamed pattern") was a stale record — `e` is genuinely used in the adjacent
   `log.warn(..., e)` call — same `@SuppressWarnings` treatment.
2. `new_coverage = 0.0%` (threshold 80%) — added `sonar.coverage.exclusions` for
   `ui/query/elements/**` (the classes actually touched by this change) as a narrow, scoped
   exclusion, since these are pure Vaadin UI wiring classes verified by Playwright e2e, not JUnit.

**Decision:** targeted `@SuppressWarnings` for the two confirmed false positives plus the stale
finding, and a narrow `sonar.coverage.exclusions` entry for `ui/query/elements/**`.

**Found but deliberately not fixed here:** `sonar.coverage.jacoco.xmlReportPaths` was never
configured anywhere in this project — Sonar has never received real coverage data for *any*
module, meaning `new_coverage` reads `0.0%` for any leak period containing new lines regardless of
actual test quality. The narrow `ui/query/elements/**` exclusion only masks this for the files
touched in this change — the next PR touching any other module hits the same wall. Tracked in the
backlog (`improvement-114`) rather than fixed inline — wiring JaCoCo project-wide (5+ modules,
unit+integration aggregation) is a separate, non-trivial infrastructure task, and even once wired
it will only raise coverage for the service layer (which has real JUnit tests) — the UI layer's
Playwright-based strategy doesn't feed JaCoCo without separate server-side instrumentation, a
materially bigger, likely-not-worth-it lift.

---

## ADR-006: SonarQube server container gets a pull-then-up freshness check, not just an API health check

**Status:** Accepted

**Context:** `run.sh`'s previous logic only touched the SonarQube server container when the API
health check (`curl .../api/system/status`) failed — if the container was already running, `run.sh`
never checked whether its image was current, and never explicitly handled "container doesn't
exist" as its own case (relied on `docker compose up -d`'s implicit create-if-missing behavior
inside that same `if` branch, undocumented as such).

**Decision:** Replaced the conditional block with an unconditional `docker compose pull -q`
followed by `docker compose up -d`, run on every invocation:
- `pull` only downloads when the registry image digest differs from what's cached locally — a
  near-instant no-op on every run where nothing changed upstream, so this doesn't meaningfully slow
  down the common case.
- `up -d` then covers all three real container states in one call: creates the container from
  scratch if it doesn't exist yet, recreates it in place if `pull` just fetched an image newer than
  the one the running container was built from, or leaves an already-current, already-running
  container untouched. No hand-rolled `docker inspect` branching needed for any of the three states
  — Compose's own reconciliation already does this correctly.
- The existing `until curl ... "status":"UP"` wait loop runs unconditionally afterward too — cheap
  to re-check even when the container didn't change, and correct in every case (already-ready
  container passes on the first iteration; a freshly (re)created one waits for real startup).

`docker-compose.sonar.yml` gained a header comment stating this container's lifecycle is fully
owned by `run.sh` — starting/stopping it by hand bypasses the freshness check the next `run.sh`
invocation expects to have already happened.

**Refinement (same session) — embedded-H2 DB migration dead end, discovered by actually running
the new logic against a real stale local container.** Verifying the change above against this
sandbox's real `sonarqube` container (last pulled ~3 months earlier) surfaced a real failure mode
the design didn't originally account for: the version jump landed the server in
`DB_MIGRATION_NEEDED`, and the existing `until ... "status":"UP"` loop has no way to ever leave
that state — it would have hung forever. Calling `POST /api/system/migrate_db` directly to
diagnose returned `{"state":"NOT_SUPPORTED","message":"Upgrade is not supported on embedded
database."}` — this `docker-compose.sonar.yml` has no separate DB service, so SonarQube Community
Edition runs on its bundled H2 database, and H2-backed instances cannot be schema-migrated across
versions via the API at all; this is a hard dead end, not a slow-but-eventually-successful state.
Fixed: the wait loop now calls `migrate_db` once on first seeing `DB_MIGRATION_NEEDED`; if the
response carries `"state":"NOT_SUPPORTED"`, it runs `docker compose down -v` (wipes this
container's own three named volumes) followed by `up -d` to start fresh on the new image, then
keeps polling the same loop. Accepted data loss: local scan history/dashboards only — the real
source of truth is the code itself, and a fresh baseline analysis re-establishes them within the
same `run.sh` invocation. Verified end to end against the real broken container: wipe-and-restart
recovered cleanly (`STARTING` → `UP` with no further `DB_MIGRATION_NEEDED`), followed by a full
`sonar-scanner` run reaching `ANALYSIS SUCCESSFUL`.

**Refinement (same session) — scanner container gets the same image-freshness policy, plus a
shared dangling-image prune for both.** The scanner container (`sonarsource/sonar-scanner-cli
:latest`, a second Docker-pulled image this same script manages) only ever got recreated on "missing
or not running" — never checked whether a newer `:latest` had been published, so it could drift
stale indefinitely once created. Since this container isn't Compose-managed, there's no `up -d`
reconciliation to lean on the way the server container has — `docker pull -q` followed by comparing
`docker image inspect -f '{{.Id}}'` (the freshly pulled image) against `docker inspect -f
'{{.Image}}'` (the running container's actual image id) decides by hand whether a recreate is
needed, recreating only on an actual mismatch (or a missing/non-running container) rather than
unconditionally every run. Both freshness checks can leave a now-untagged image behind (the
replaced `sonarqube:community` or `sonar-scanner-cli:latest`) — `docker image prune -f` runs once
after both checks, same reasoning `deploy.sh` already documents for its own post-build prune: only
dangling (untagged) images are removed, by definition unreferenced by any tag/container, so this
can never touch another stack's active image. Verified directly: a stray 2.52GB dangling
`sonarqube:community` image left behind by the very first test of this change (before the prune
call existed) was the concrete finding that prompted adding it — confirmed clean (`docker images
-f dangling=true` empty, exactly 2 named `sonar*` containers, both on current images) after a full
end-to-end `run.sh` run with the finished logic.

---

## ADR-007: strip CRLF from the stored token before using it for Basic Auth

**Status:** Accepted

**Context:** This repo's working tree uses CRLF line endings (`core.autocrlf`). `cut` on a CRLF
line leaves a trailing `\r` on the extracted `sonar.token` value, which silently corrupts the
Basic Auth header sent to SonarQube's API — it reports the token "invalid" even though the visible
characters are correct, with no obvious cause from the error alone.

**Decision:** every read of `sonar.token` from `sonar-project.properties` in `run.sh` pipes through
`tr -d '\r'` before use (token validation and the scanner's own `SONAR_TOKEN` env var).

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

**CRLF bug found and fixed while verifying.** This repo's working tree is CRLF (`core.autocrlf`,
same fact ADR-007 above already deals with for the token). Python's default text-mode `open()`
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
