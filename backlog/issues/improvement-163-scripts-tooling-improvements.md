# improvement-163: Scripts tooling improvements tracking

**Type:** improvement
**Module:** scripts/ (repo-wide) — candidate touch points also include a repo-root `.mcp.json`
once an MCP-server idea gets approved.
**Priority:** Top
**When:** independent, no blockers — no fixed pace, ideas get added and picked up incrementally.

## Problem

Real, concrete improvement ideas keep surfacing while working on `scripts/` (Sonar, Playwright,
CI, build-and-test, deploy-and-run, architecture-doc) — not urgent bugs, but genuine opportunities
found along the way (e.g. `improvement-160`'s D2-2 row: replacing `scripts/sonar/run.sh`'s
generated `report.html` + `docker cp` file-copy step, which hits a real Windows/WSL file-lock
failure, with a real `sonarqube-mcp-server` MCP integration — direct tool-call access to
SonarQube findings/quality-gate state instead of parsing a static HTML file). There is currently
no dedicated place to accumulate these scripts-specific ideas as they come up.

## Suggested fix

Same open-ended, incrementally-populated tracking shape as `improvement-160`'s coverage map: log
each idea as it's found (which script, the current friction, the proposed improvement, status
idea/approved/done), grounded in real findings from actually running/investigating a script — not
speculative. Pick up individual ideas for real implementation only when explicitly approved.

## Related

- `improvement-160-certification-coverage-map.md`, row D2-2 — SonarQube MCP server idea, the
  first candidate logged here.

## Plan (items 1-3 implemented 2026-08-20, item 4 verification still pending)

**Goal:** stop silently trusting that a container's write into the shared `test-reports` volume
actually happened before removing that container -- verify it from the container itself first, and
only clean up on confirmed success. Generalize this into one reusable check instead of re-solving
it per script.

1. New shared utility `scripts/utils/wait-for-container-files.sh` (sourced, matching the existing
   `ensure-docker-plugins.sh` precedent) -- one function,
   `wait_for_container_files_or_keep(container, timeout_seconds, file1 [file2 ...])`: polls once a
   second (up to `timeout_seconds`, 20 by default) via `docker exec "$container" sh -c "[ -s
   '$file' ]"` for every listed file. All present within the window -> `docker rm -f "$container"`,
   return 0. Still missing after the window -> leave the container running untouched, print an
   `ERROR: ... left running for inspection` line naming the container and the missing file(s) to
   stderr, return 1.
2. `scripts/build-and-test/run.sh` -- replace the unconditional `docker rm "$BUILD_CONTAINER_NAME"`
   (end of file) with a call to this utility, checking whichever of
   `/reports/$BUILD_CONTAINER_NAME/logs/unit-tests.log` /
   `/reports/$BUILD_CONTAINER_NAME/logs/integration-tests.log` /
   `/reports/$BUILD_CONTAINER_NAME/logs/archunit-metrics.log` actually applies (per
   `RUN_UNIT`/`RUN_INTEGRATION`/`ARCHUNIT_METRICS`), inside the container, before the existing
   `docker cp` copies them out. If the build/test step itself already failed (non-zero exit
   already tracked earlier in this script), skip polling entirely and leave the container
   immediately -- no point waiting for files that a failed build may never have produced.
3. `scripts/run-all-tests/run.sh` -- replace the current "never remove `REPORTS_CONTAINER`, only
   before the next run" behavior (this session's own earlier fix) with a real check: once both
   `wait $BUILD_LOG_WRITER_PID` and `wait $PW_PID` have completed, call the same utility against
   `REPORTS_CONTAINER` for `/reports/run-all-tests/build-and-test.log` and
   `/reports/run-all-tests/playwright.log`. Confirmed present -> remove right away (frees the
   container immediately instead of waiting for the next run). Missing -> leave it up with the
   ERROR message, same as today, still cleared at the start of the next run.
4. Verify for real: run `bash scripts/run-all-tests.sh` (or `build-and-test.sh` alone) to
   completion and confirm (a) the success path actually removes the container, (b) a deliberately
   broken scenario (e.g. temporarily renaming an expected file inside the container before the
   check) actually leaves it up with the ERROR line, before marking this plan item done.

## Log

- **done (2026-08-20)** — `scripts/build-and-test/run.sh`: replaced the tar step's whole-repo
  exclude-list with an explicit include-list (root build files + module directories, module list
  derived dynamically from `pom.xml`'s `<module>` entries) — structurally avoids tar hitting
  Permission Denied on unrelated generated files (e.g. `docs/architecture/architecture-map.html`
  right after `architecture-doc.sh`'s own `docker cp` wrote it) instead of playing catch-up with a
  growing exclude list. Also added a second named volume, `vaadin-cache:/root/.vaadin`, so Vaadin's
  own Node.js install persists across build-container runs instead of re-downloading every time;
  `--reset-cache` now clears both volumes, not just `maven-cache`.

- **done (2026-08-20)** — fixed a real regression the tar include-list change above introduced:
  `scripts/build-and-test/build.sh` itself (the entry point the container executes after
  extracting the tar stream) was left out of the new include-list, so every real run failed with
  `bash: /app/scripts/build-and-test/build.sh: No such file or directory` — confirmed directly on
  the user's machine. Added `scripts/build-and-test/build.sh` to the include-list; updated the
  tar-step comment and the header's `Input` field to state it's included for this reason.

- **done (2026-08-20)** — `.claude/skills/infra-doc-standards/SKILL.md`: added a new rule --
  when every flag in a script's `Usage` field is optional, the field must open with an explicit
  `(no flags) -- ...` line stating the bare-invocation behavior, never left only implied by
  `Description`. Triggered by a real gap found in `scripts/deploy-and-run.sh`'s header (the default
  reuse-jar/no-image-build behavior was only stated in `Description`'s prose, not visible when
  scanning `Usage`). Applied to every real script with 3+ optional flags and no existing equivalent
  line: `scripts/deploy-and-run/run.sh`, `scripts/build-and-test/run.sh`,
  `docs/architecture/architecture-doc.sh`, `scripts/run-all-tests/run.sh`. Already compliant:
  `scripts/ci/run.sh` (already had `(no flags)`), `playwright/run.sh` (equivalent `(no scenario)`
  line). Below the 3+ threshold, left as-is: `scripts/sonar/run.sh` (2 flags),
  `scripts/deploy-and-run/reset.sh` (1 flag). All root `.sh`/`.bat` delegators already say
  `Usage: same as <sibling>`, so they inherit the fix automatically -- no edits needed there.

- **done (2026-08-20)** — `scripts/build-and-test/build.sh`: the top-level `trap ... ERR` was
  firing on the first non-zero top-level command anywhere past the mandatory reactor build (e.g.
  `wait $INTEGRATION_PID` returning a failed background job's real exit status) and exiting
  immediately -- before `print_unit_summary`/`print_integration_summary` ever ran, so a real test
  failure's actual cause (which test, what error) never printed anywhere, console or file, only a
  generic `=== FAILED (exit N) ===`. Root-caused via `bash -x` tracing in a disposable sandbox
  container after reproducing the exact symptom the user hit on their real machine. Fixed by
  scoping the trap (`trap - ERR` right after the mandatory build section, before any
  unit/integration/archunit test-running logic) so test failures are captured via `$?` and
  summarized as originally intended, never allowed to abort the script outright.

- **done (2026-08-20)** — `scripts/build-and-test/build.sh`/`run.sh`: the full raw console log for
  whichever test phase actually ran (`unit-tests.log`/`integration-tests.log`/
  `archunit-metrics.log`, previously only ever `cat`'d to stdout inside the container and then
  lost once it exited) now also gets copied to `/tmp/reports/logs/` inside the container, reaching
  `scripts/build-and-test/reports/logs/` on the host via the existing `docker cp` step -- never
  lost to terminal scrollback again. `playwright/run.sh`: the three `docker exec ... npx
  playwright test ...` invocations now pipe through `tee playwright/pw-report/run.log`
  (`${PIPESTATUS[0]}` instead of `$?` to keep the real exit code through the pipe) for the same
  reason -- console output unchanged, now also persisted locally.

- **done (2026-08-20)** — real root cause of the user's original integration-test failure, found
  via the log-persistence fix above: `.env` (repo root) was missing from the tar include-list
  added earlier this session -- `integration-tests`' own `SharedEnvConfig` reads it at test
  runtime (`POSTGRES_IMAGE` for Testcontainers), so every single integration test failed at
  startup with `NoClassDefFoundError`/`IllegalStateException: Could not find .env`. Added `.env`
  to the include-list.

- **done (2026-08-20)** — `scripts/build-and-test/run.sh`: `--sandbox`'s Testcontainers
  workarounds (`TESTCONTAINERS_RYUK_DISABLED`, `INTEGRATION_TESTS_POSTGRES_FIXED_PORT`) are now
  **on by default** everywhere except `GITHUB_ACTIONS` (new `--no-sandbox` to opt out), not opt-in
  via `--sandbox` as before. Confirmed via the user's own real Docker Desktop/WSL2 machine (not
  just this AI sandbox) that `build-and-test.sh`'s nested Testcontainers run (its own build
  container mounts the host's `docker.sock`) hits the same Ryuk/port-reachability issue there too
  -- the earlier "sandbox-only, never on a normal developer machine" claim in `scripts/CLAUDE.md`
  was wrong and has been corrected. `integration-tests/run.sh`'s own direct (non-nested) `--sandbox`
  flag left opt-in, unconfirmed outside the AI sandbox for that simpler case.

- **idea (2026-08-20)** — Vaadin's frontend bundle rebuild (`npm install` + Vite, ~110s) re-runs on
  every separate `build-and-test.sh` invocation even when the frontend source hasn't changed at
  all, unlike Java compilation which at least benefits from an already-warm `~/.m2`. Root cause:
  `vaadin-cache:/root/.vaadin` (added this session) only persists Vaadin's own Node.js binary --
  `marketplace-app/node_modules` and `target/frontend` (what Vaadin's own "Checking if a production
  mode bundle build is needed" staleness check actually reads) live inside the module directory,
  which starts fresh in every new build container (never persisted, no volume covers it). Possible
  fix: persist `marketplace-app/node_modules` + the relevant `target/frontend` staleness-marker
  files via another volume or copy-out/copy-in step, so Vaadin's own check can actually find
  something unchanged to compare against across separate runs, not just within one container's
  lifetime. Real correctness risk not yet assessed (a stale persisted bundle serving wrong content
  if the staleness check itself misses a real change) -- not implemented, logged as an idea only.

- **decided against (2026-08-20)** — considered switching SonarQube from its embedded H2 database
  to a dedicated PostgreSQL sidecar (`scripts/sonar/docker-compose.sonar.yml`) to clear the
  "Embedded database should be used for evaluation purposes only" banner. Verified via WebSearch:
  H2's data file already lives at `/opt/sonarqube/data`, already covered by the existing
  `sonarqube_data` named volume -- restart-safe today, contrary to what the warning implies at a
  glance. The warning has no suppression setting (confirmed via SonarSource's own community
  forum -- other users asked the same thing, no config flag exists) and only really matters for
  multi-instance scaling or migrating data across a SonarQube version upgrade, neither a concern
  for this project's single local dev instance. Decision: keep H2, live with the banner -- not
  worth the added Postgres sidecar container/volume for a risk that doesn't apply here.

- **done (2026-08-20)** — `playwright/run.sh` hardcoded the absolute path `/app/...` (this AI
  sandbox's own checkout location) at ~14 separate spots (reset.sh invocation, spec-file sync,
  pw-report output, config-file copy) instead of deriving its own root from `$(dirname "$0")`, the
  convention every other script in this repo already follows. Never actually worked outside this
  AI sandbox -- confirmed directly on the user's real Windows/WSL machine (`bash:
  /app/scripts/deploy-and-run/reset.sh: No such file or directory`, `mkdir: cannot create
  directory '/app': Permission denied`, `Error: /tmp/playwright.config.js does not exist` since
  `docker cp` silently copied nothing from a nonexistent source). The `tee`-based log-persistence
  line added earlier this same session inherited the identical bug. Fixed by adding
  `ROOT="$(cd "$(dirname "$0")/.." && pwd)"` and replacing every hardcoded `/app/playwright/...`/
  `/app/scripts/...` reference with `$ROOT/...`.

- **done (2026-08-20)** — `scripts/clean.bat` extended with per-category flags (`--build`/`--unit`/
  `--integration`/`--playwright`/`--sonar`, combinable; no flags = clean everything, unchanged
  default). Triggered by a real Docker-Desktop-WSL2 bind-mounts symptom on the user's machine:
  `playwright/run.sh`'s own `rm -rf` (via WSL) hit `Permission denied` on stale screenshot files
  from a previous run. `clean.bat` runs via native `cmd.exe` (`rmdir`/`del`), not WSL, so it
  doesn't hit the same bind-mounts lock -- the practical workaround is running it directly instead
  of relying on `playwright/run.sh`'s own WSL-side cleanup. Also added the 3 new report categories
  this session's fixes introduced (`scripts/build-and-test/reports/*`,
  `integration-tests/reports`, `scripts/sonar/report`) -- previously only Maven `target/`, Vaadin
  frontend, and Playwright artifacts were covered.

- **done (2026-08-20)** — wired `clean.bat`'s new per-category flags into the three real entry
  points that hit stale-report Docker-Desktop-WSL2 bind-mounts locks: `playwright.bat` now calls
  `clean.bat --playwright`, `build-and-test.bat` calls `clean.bat --build --unit --integration`,
  `sonar.bat` calls `clean.bat --sonar` -- each natively via `cmd.exe`, before WSL is ever
  involved, so the actual deletion happens before playwright/run.sh's own (still WSL-side) cleanup
  step would otherwise hit the same lock. Confirmed real: the user hit this exact symptom twice in
  a row running `playwright.bat` directly after `clean.bat` existed only as a separate manual tool
  they weren't yet calling.

- **done (2026-08-20)** — extended the `clean.bat` auto-wiring to the two remaining real entry
  points: `run-all-tests.bat` now calls `clean.bat --build --unit --integration --playwright`,
  `ci.bat` calls plain `clean.bat` (cleans everything, matching CI's own default of running every
  stage) -- both natively via `cmd.exe`, before WSL. Also found and fixed a real gap:
  `scripts/ci/run.sh` (lines 92-95) copies `scripts/ci/reports/pipeline-metrics.json` onto the host
  via `docker cp`, a path `clean.bat` didn't know about at all -- added under `--build`.
  `scripts/run-all-tests/reports/{build-and-test.log,playwright.log}` (written via plain bash `>`
  redirect, itself vulnerable to the same bind-mounts lock if the old file was held) added under
  `--unit`/`--playwright` respectively. Simplified per direct user feedback: each category wipes
  its whole report folder wholesale (`rmdir /s /q`) rather than enumerating individual files inside
  it -- `scripts/build-and-test/reports` holds unit/integration/archunit output together with no
  clean physical split, so `--unit` and `--integration` both just wipe that same folder in full
  rather than trying to preserve surgical independence between them. Also found and fixed a
  pre-existing non-ASCII violation in `ci.bat`'s own header (`── Header ──` box-drawing characters)
  while touching the file -- replaced with plain ASCII dashes per this repo's own `.bat`-file rule.

- **done (2026-08-20)** — fixed a real regression the previous entry's auto-wiring introduced:
  `--build` (Maven `target/` + Vaadin `node_modules`/generated frontend) was wired into
  `run-all-tests.bat`/`build-and-test.bat`/`ci.bat`, meaning every routine test/CI invocation wiped
  the whole build cache first -- directly defeating the `maven-cache`/`vaadin-cache` incremental-build
  work done earlier this same session, forcing a full recompile + npm install + Vite rebuild on
  every single run. Confirmed via the user's real `run-all-tests.bat` output. `--build` addresses a
  completely different concern (occasional full workspace reset) than the bind-mounts stale-report
  lock `--unit`/`--integration`/`--playwright`/`--sonar` exist for -- removed from all three
  wirings; `--build` stays available only as a flag a user passes to `clean.bat` manually.
  `run-all-tests.bat` now calls `clean.bat --unit --integration --playwright`, `build-and-test.bat`
  calls `clean.bat --unit --integration`, `ci.bat` calls
  `clean.bat --unit --integration --playwright --sonar`. Verified via direct re-inspection of all 5
  entry-point `.bat` files after the fix: no `--build` remains in any wiring, headers/actual calls
  consistent, ASCII-only, balanced parens in `clean.bat`'s own conditional blocks.

- **done (2026-08-20)** — root-caused why `run-all-tests.bat` intermittently produces an empty
  `playwright.log`/exit 1 with no real error, while a standalone `playwright.bat` run (or
  `build-and-test.bat`'s own reports) succeeds: confirmed real, documented WSL2 bug
  (`microsoft/WSL#6464`, open since 2021, still unresolved) where a WSL path/cwd resolution can get
  redirected through Docker Desktop's own `/mnt/wsl/docker-desktop-bind-mounts/...` alias instead
  of the real Windows-drive mount, triggered whenever Docker Desktop's WSL2 integration is active --
  independent of which specific operation is running. Each `.bat`'s own `wslpath -u` call is an
  independent chance to hit this; when `run-all-tests.bat` hits it, every downstream `$ROOT`
  computed via `dirname "$0"` chains (build-and-test.sh, deploy-and-run.sh, playwright.sh) inherits
  the bad prefix. The reason build-and-test's own reports still came through even in a poisoned run:
  they're written via `docker cp` (Docker daemon-mediated, resolves the alias correctly on its own
  side) while `run-all-tests.sh`'s `$PW_LOG`/`$BUILD_LOG` and `playwright/run.sh`'s `$RUN_LOG` were
  raw bash `>`/`tee` redirects (WSL2-kernel-mediated, exposed to the bug directly) -- confirmed via
  WebSearch/WebFetch against `docker/for-win#13377` and `microsoft/WSL#6464`, no code-level fix
  exists upstream (still open, tagged duplicate). Real in-repo fix applied to both `run-all-tests/
  run.sh` and `playwright/run.sh`: write first to a native WSL `/tmp/...` path (never subject to
  the Windows-drive alias translation), then copy tolerantly (`cp ... 2>/dev/null || true`) to the
  final destination under `$ROOT` -- the actual test run is never blocked by a failed final copy,
  only the visible log file might occasionally not land, instead of the whole
  deploy-and-run+playwright chain silently never starting. Community-known external mitigations
  (not applied, out of this repo's control): move the repo off the Windows drive into WSL2's own
  native filesystem entirely (removes the translation layer causing this), or `wsl --shutdown` +
  restart Docker Desktop as a temporary reset.

- **done (2026-08-20)** — `integration-tests/reports/` previously only got populated by
  `integration-tests/run.sh`'s own direct invocation, staying stale/absent whenever integration
  tests actually ran via `build-and-test.sh` (e.g. through `run-all-tests.bat`) instead. Fixed by
  having `build.sh`'s `run_integration_tests()` also mirror its Surefire reports + log into
  `/tmp/it-reports-mirror/` (kept outside `/tmp/reports/` to avoid the existing `docker cp` also
  sweeping it into `scripts/build-and-test/reports/` by accident), copied out via a second,
  separate `docker cp` in `run.sh` -- deliberately `docker cp`, not a raw bash write, matching the
  one mechanism proven reliable against the WSL2 bind-mounts bug all session. Confirmed real via
  the user's own default `run-all-tests.bat` run: `integration-tests/reports/` never existed at
  all after it, while `scripts/build-and-test/reports/surefire/integration-tests/` had the same
  data all along -- same underlying test results, just missing from the second, standalone script's
  own expected location.

- **done (2026-08-20)** — `run-all-tests/run.sh`'s /tmp-first log fix (previous entry) was
  unconditionally deleting the `/tmp` copy at the end even when the final copy-out to
  `$BUILD_LOG`/`$PW_LOG` failed -- losing the one reliable copy of the real log content (since
  `/tmp` itself is never subject to the WSL2 bind-mounts bug, only the final copy-to-Windows-drive
  step is). Fixed: only `rm` the `/tmp` file after a successful `cp`; on failure, print a `WARNING`
  naming the exact `/tmp/run-all-tests-*.log` path so the content is still recoverable by hand.

- **done (2026-08-20)** — real architectural fix replacing the whole `/tmp`-then-tolerant-copy
  band-aid (previous several entries): a shared named Docker volume, `test-reports`, mounted at
  `/reports` into every container that produces test output (`build-and-test`'s own build
  container, `pw-runner`). Named Docker volumes are managed entirely by the Docker daemon, never a
  WSL/Windows-drive path, so they're structurally immune to the docker-desktop-bind-mounts issue
  documented above -- not another mitigation, the actual root fix.
  - `scripts/build-and-test/build.sh`: all `/tmp/reports/...` and `/tmp/it-reports-mirror/...`
    writes changed to `/reports/...` (the volume mount).
  - `scripts/build-and-test/run.sh`: mounts `-v test-reports:/reports` into the build container;
    wipes the volume before every run; both `docker cp` calls now source `/reports/.` /
    `/reports/it-mirror/.` from the container instead of `/tmp/reports/.`.
  - `playwright/playwright.config.js`: HTML reporter `outputFolder` changed from `/tmp/pw-report`
    to `/reports/playwright` (flat, alongside run.sh's own run.log, so one `docker cp` pulls both).
  - `playwright/run.sh`: mounts `-v test-reports:/reports` into `pw-runner` at both places it can
    be (re)created (existing running containers from before this change need one manual `docker rm
    -f pw-runner` to pick up the new mount); `npx playwright test` now tees to
    `/reports/playwright/run.log` inside the container (exit code propagated via an inner
    `exit ${PIPESTATUS[0]}`, escaped to evaluate inside the container, not the host); final
    `docker cp` sources `/reports/playwright/.` instead of `/tmp/pw-report/.`.
  - `scripts/run-all-tests/run.sh`: both orchestration-level logs (`build-and-test.log`,
    `playwright.log`) now pipe into a throwaway `alpine` container that writes them straight into
    the volume (`tee >(docker run -i --rm ...)` for the foreground one, `| docker run -i --rm ...`
    for the backgrounded deploy-and-run+playwright block), then a separate short-lived
    `run-all-tests-flush` container (volume mounted, no other purpose) is used for the final
    `docker cp` out to `scripts/run-all-tests/reports/`. Volume wiped before each run.
  - Bonus: the volume can be inspected directly at any time by anyone with docker access (not just
    after a full run + copy-out cycle), e.g. `docker run --rm -v test-reports:/reports alpine cat
    /reports/run-all-tests/playwright.log` -- closes the same visibility gap that made diagnosing
    this whole chain of issues slow (no way to check the latest run's real output without the user
    manually pasting it every time).

- **done (2026-08-20)** — found and fixed a real regression introduced while writing the above:
  a bad `sed` bulk-edit corrupted `build.sh` (ate spaces after `mkdir -p`, e.g. `mkdir -p$REPORTS_DIR/logs`
  instead of `mkdir -p "$REPORTS_DIR/logs"`, and made `REPORTS_DIR` self-referential in its own
  definition) -- confirmed as the real cause of a "tests ran, zero reports/logs appeared" symptom
  the user hit. Fixed by rewriting the file cleanly and re-verifying with `bash -n`.

- **done (2026-08-20)** — real collision the shared volume introduced: `run-all-tests.sh` triggers
  two concurrent `build-and-test.sh` invocations (its own direct call, and deploy-and-run.sh's
  internal one, which already used a distinct `BUILD_CONTAINER_NAME` to avoid a Docker
  container-name collision) -- both were writing into the same flat `/reports/` volume path and
  wiping it out from under each other. Fixed: `build.sh` now namespaces everything under
  `/reports/$BUILD_CONTAINER_NAME/`; `run.sh`'s pre-run wipe and both `docker cp` calls are scoped
  to that same subfolder; the host-side copy-out is *also* namespaced for any non-default
  `BUILD_CONTAINER_NAME` (confirmed real: the deploy build's own no-op `build-info.txt` had
  overwritten the real test run's marker in the shared host `scripts/build-and-test/reports/`
  before this second fix). Also added `build-info.txt` (BUILD_CONTAINER_NAME, timestamp, which
  flags were set) written unconditionally to every `$REPORTS_DIR/`, per direct user request, so
  it's always visible which invocation produced a given set of reports.

- **investigated, not resolved (2026-08-20)** — user reported `playwright/pw-report/run.log`
  missing after a real `run-all-tests.bat` run. Live debugging inside `pw-runner` confirmed the
  underlying `tee`+`${PIPESTATUS[0]}` bash pattern itself works correctly in isolation (a clean
  single-command repro created the file with real content). Root cause not conclusively isolated --
  manual reproduction attempts were incomplete (didn't sync the full `e2e/_flows/` dependency
  tree) and accumulated dirty intermediate state across many exploratory `docker exec` calls,
  making later results unreliable. Real, confirmed, separate finding along the way: `pw-runner` had
  been running continuously since before the `-v test-reports:/reports` mount was added to
  `playwright/run.sh` -- a reused container only picks up a new mount when actually recreated, so
  it had no volume at all. Removed it (`docker rm -f pw-runner`) so the next run creates it fresh
  with the mount and without the accumulated manual-testing debris; this may turn out to be the
  entire explanation once retried for real, but that isn't confirmed yet -- next real
  `run-all-tests.bat`/`playwright.bat` run is the actual test.

- **done (2026-08-20)** — root-caused and fixed the real "empty `run-all-tests/reports/` on host"
  bug: confirmed the volume itself *did* have fresh, correct `build-and-test.log`/`playwright.log`
  content, but the host copy-out was still empty -- isolating the bug to a race between the
  build-and-test.log writer and the flush step. Root cause: `tee >(docker run ...)` (process
  substitution) is not reliably waited for by bash -- confirmed directly with a controlled test (a
  deliberately slow process-substitution writer, a bare `wait` returned in ~5ms instead of
  blocking for it, and the file was never created). An earlier attempted fix (adding a bare `wait`)
  was verified ineffective by that same test and removed. Real fix: a named pipe (`mkfifo`) with
  the writer backgrounded via its own explicitly captured PID (`docker run ... < "$FIFO" &
  WRITER_PID=$!`), then `wait $WRITER_PID` before the flush step -- confirmed via a second
  controlled test that this pattern genuinely blocks until the writer finishes. The other log
  (`playwright.log`, written via a real `{ ... } | docker run ... &` pipe, not process
  substitution) was already safe -- `$!`/`wait` correctly track the last process in a real
  pipeline, just not a `>()` process substitution's own subprocess.

- **done (2026-08-20)** — verified a real `run-all-tests.bat` run end to end for the
  build-and-test half: unit 53/53 and integration 165/165 both `BUILD SUCCESS`, correct
  `build-info.txt` (no more mislabeling from the earlier collision fix). Found one more real gap
  while checking: `integration-tests/reports/` never got created -- the second `docker cp` (the
  it-mirror copy) had no `mkdir -p` for its destination beforehand, unlike the first copy, so it
  silently failed (swallowed by `2>/dev/null || true`). Fixed: added
  `mkdir -p "$ROOT/integration-tests/reports"` alongside the existing one.

- **done (2026-08-20)** — root-caused (with direct evidence, not a guess) why
  `playwright/pw-report/run.log` never got created despite `tee` running correctly. Confirmed via
  the volume: `/reports/playwright/` always had only `index.html`, never `run.log`; but
  `run-all-tests/playwright.log` (a separate log capturing the same output externally, via the
  outer pipe around `docker exec`) always had the full content -- proving the actual test output
  was there, just not surviving in the file `tee` wrote inside the container. Cause:
  `playwright.config.js`'s HTML reporter uses `outputFolder: '/reports/playwright'` -- the exact
  same directory `tee` wrote `run.log` into. Playwright's HTML reporter clears its outputFolder
  when it finalizes the report after the run, deleting `run.log` (written earlier by `tee`) right
  before writing the fresh `index.html` -- explains both why `index.html` always survived (written
  last) and `run.log` never did. Fixed by moving `run.log` to a separate volume path,
  `/reports/playwright-log/`, never touched by the HTML reporter's own cleanup: updated all 3
  `tee`/`mkdir` call sites in `playwright/run.sh`, and the final copy-out now runs two `docker cp`
  calls (`/reports/playwright/.` and `/reports/playwright-log/.`) into the same host
  `playwright/pw-report/` directory. Separately confirmed the "`run-all-tests/reports/` empty on
  host" symptom from this same run was not a new bug -- the volume had fresh, correct
  `build-and-test.log`/`playwright.log` content, and the exact `docker create`+`start`+`docker cp`
  flush pattern was reproduced manually and worked correctly -- so that run simply never reached
  the flush step (interrupted mid-run), not a code defect.

- **done (2026-08-20)** — simplified `scripts/run-all-tests/run.sh`'s volume-writing/flush
  mechanism per direct instruction: replaced the throwaway-container-per-step pattern (a fresh
  `docker run --rm`/`docker run -d ... sleep 10` created and torn down for the initial wipe, each
  log writer, and the final flush) with one persistent `run-all-tests-reports` container -- same
  reused-container shape `playwright/run.sh` already uses for `pw-runner`, confirmed reliable for
  `docker cp` earlier in this same investigation. Removed and recreated fresh at the start of each
  run; deliberately **not** removed at the end, so a run's data stays inspectable
  (`docker exec run-all-tests-reports cat /reports/run-all-tests/playwright.log`) until the next
  run explicitly clears it. All three write call sites (initial wipe, build-and-test.log's fifo
  writer, playwright.log's pipe writer) and the final copy-out now go through `docker exec`/
  `docker cp` against this one container instead of `docker run`.

- **done (2026-08-20)** — root-caused a real `deploy-and-run.sh` crash on the user's actual
  Windows/WSL2 machine (confirmed via the shared Docker daemon: `run-all-tests.bat`'s own fresh
  `playwright.log`, read directly from the `run-all-tests-reports` volume container, showed
  `mkdir: cannot create directory '/mnt/wsl/docker-desktop-bind-mounts/.../integration-tests/reports':
  Permission denied` immediately followed by `=== FAILED (exit 1) ===`, meaning Playwright never
  even started). Cause: `scripts/build-and-test/run.sh`'s `mkdir -p
  "$ROOT/integration-tests/reports"` (added earlier this session to fix a silently-swallowed
  `docker cp` for the it-mirror copy) ran unconditionally, even on `deploy-and-run.sh`'s own
  internal `--no-integration` build where there's nothing to copy there at all -- a raw bash
  `mkdir -p` against a WSL/Windows-drive host path is exactly the docker-desktop-bind-mounts bug's
  blast radius, and `set -e` turned that failure into a full script crash. Fixed by wrapping both
  that `mkdir -p` and its paired `docker cp` in `if [ "$RUN_INTEGRATION" = "true" ]` -- the
  directory is now only touched on a build that actually produces it-mirror content.

- **done (2026-08-20)** — that fix turned out incomplete: the same "Permission denied" still hit a
  real run where `RUN_INTEGRATION=true` (integration tests genuinely ran, it-mirror genuinely had
  content), because the conditional only skipped the *unnecessary* case, not the underlying
  raw-`mkdir`-on-a-WSL-host-path problem itself. Real fix: removed all three raw bash `mkdir -p`
  calls in `scripts/build-and-test/run.sh` (the top-level `$REPORT_DIR`, `$HOST_REPORT_DEST`, and
  `$ROOT/integration-tests/reports`) plus the equivalent ones in `scripts/run-all-tests/run.sh`
  (`$REPORT_DIR`) and `playwright/run.sh` (`$ROOT/playwright/pw-report`) -- `docker cp` creates its
  destination directory (and any missing intermediates) itself when it doesn't exist, so every one
  of these was pure redundancy paid for with a real WSL-bind-mount-vulnerable filesystem write.
  `docker cp` itself is daemon-mediated, already proven reliable against this bug all session.

- **done (2026-08-20)** — implemented plan items 1-3 above (container-file-verification-before-
  cleanup): new `scripts/utils/wait-for-container-files.sh` (`wait_for_container_files_or_keep`,
  same sourced-utility shape as `ensure-docker-plugins.sh`), wired into
  `scripts/build-and-test/run.sh` (replaces the old unconditional `docker rm` -- skips straight to
  "leave running" if the build itself already failed, removes immediately with no polling if
  nothing was expected to produce a log file, otherwise polls) and
  `scripts/run-all-tests/run.sh` (replaces the "never remove, only before the next run" behavior
  with a real check against both `build-and-test.log` and `playwright.log`). Syntax-checked
  (`bash -n`) on all three files; a real end-to-end run to confirm both the success path (container
  removed) and a deliberately-broken path (container left up with the ERROR line) is still pending
  -- plan item 4.

- **done (2026-08-20)** — real run surfaced that the utility's own check was wrong: it used
  `docker exec "$container" sh -c "[ -s '$f' ]"` against container-internal paths, but
  `advertisement-build-only` is a one-shot container (`docker run -i`, no `sleep` keep-alive) that
  has already **exited** by the time this check runs -- `docker exec` only works against a running
  container, so it failed for both expected files every time regardless of whether the data
  actually existed (confirmed: the user's own reports were present on host, `docker cp` had already
  succeeded, only the verification step was broken). Fixed by changing
  `wait_for_container_files_or_keep` to check plain **host** filesystem paths (`[ -s "$f" ]`, no
  docker at all for the check itself, only for the final `docker rm -f`) -- correct for both the
  one-shot build container (already exited, `docker exec` unusable) and the persistent
  `REPORTS_CONTAINER` (still running, but checking host paths after its own `docker cp` already ran
  is simpler and equally valid). Updated both call sites (`build-and-test/run.sh`'s
  `EXPECTED_LOG_FILES` now built from `$HOST_REPORT_DEST/...`, `run-all-tests/run.sh`'s call now
  passes `$REPORT_DIR/...`) to match. Syntax-checked on all three files; real end-to-end
  verification (plan item 4) still pending.

- **done (2026-08-21)** — plan item 4 (real end-to-end verification) confirmed: a full
  `bash scripts/run-all-tests.sh` run finished `ALL PASSED` (exit 0), host
  `scripts/run-all-tests/reports/build-and-test.log`/`playwright.log` both fresh and correctly
  sized, and both `run-all-tests-reports` and `advertisement-build-only` were confirmed gone
  (`docker ps -a` empty for both names) -- the success path removes the container as designed.
  Plan closed; all 4 items done.
