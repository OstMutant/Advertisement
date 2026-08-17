# improvement-154: `deploy-and-run` reuses `build-and-test.sh`'s shared-volume jar instead of rebuilding from scratch; Playwright picks up the same fresh build

**Type:** improvement — implemented, all phases done (not yet committed).
**Module:** `scripts/deploy-and-run/` (new), root `Dockerfile`, `scripts/build-and-test/`,
`scripts/run-all-tests/`, `playwright/run.sh`, `docs/architecture/scripts/generate-architecture-model.sh`.
**Priority:** Top.
**When:** done — Phase 1, 1b, 2, 2c, 3 all implemented and verified with real Docker runs this
session.

## Problem

Today `scripts/deploy.sh` independently rebuilds the application image from scratch via its own
multi-stage `Dockerfile` (`mvn package` runs again inside the Docker build, BuildKit-cached but
still a fully separate build path) — duplicating work `scripts/build-and-test.sh` already does
(full-reactor `mvn install`, tests, and its own `marketplace-app.jar` refreshed at a fixed path
inside the shared `maven-cache` volume), and disconnected from it entirely.

This is the concrete cause of an already-documented, unfixed gap (`improvement-152` Part A, "Real
gap found, not fixed: Playwright in `run-all-tests.sh` never sees `build-and-test.sh`'s fresh
build"): Playwright only checks whether `marketplace-app` is already running and tests whatever
that container currently serves — which could be arbitrarily stale relative to current source,
since nothing connects a `build-and-test.sh` run to what's actually deployed.

**A prior version of this idea was explicitly rejected on principle** (`improvement-152` Part A,
"Explicitly out of scope, investigated and rejected"): *"Making `deploy.sh`'s Docker image build
reuse host-compiled classes — rejected on principle: the deploy image must build reproducibly from
source in an isolated context, not from potentially-stale/uncommitted host artifacts."* That
rejection assumed the shared-volume jar could be from an arbitrary, unrelated prior run.

## Suggested fix

Revisit the rejected idea under a condition that actually resolves its stated concern:
reproducibility holds as long as `build-and-test.sh` runs **fresh, as the first step of the same
invocation that deploys** — never reusing whatever happened to be left in the shared volume from
an unrelated earlier run.

- `scripts/deploy-and-run.sh` (the top-level entry point) calls
  `bash scripts/build-and-test.sh [test flags]` first, producing a fresh `marketplace-app.jar` in
  the shared `maven-cache` volume for the current source tree exactly as it stands right now.
- Only after that succeeds does `scripts/deploy-and-run/run.sh` build/start the app container.
- **Composition happens only at the entry-point-script level** — `deploy-and-run.sh` calls
  `build-and-test.sh` as a sibling script, the same way `scripts/ci/entrypoint.sh` already composes
  `build-and-test.sh`/`playwright/run.sh`/`sonar.sh` without any of them reaching into each other's
  directory. `scripts/deploy-and-run/run.sh` and its Dockerfile never read anything from
  `scripts/build-and-test/` directly — no folder-level coupling.
- The runtime image build step becomes much thinner: `COPY` the already-built jar out of the
  shared volume instead of running `mvn package` inside its own Dockerfile builder stage.
- **Playwright also switches to consuming the same freshly-built jar/volume**, instead of only
  checking whether `marketplace-app` happens to already be running — this is what actually closes
  the previously-documented freshness gap, not just the deploy side alone.

## Open design questions (closed — resolved during implementation, see Phase 2/2c below)

- Default behavior, not opt-in — `--from-scratch` is the opt-out fallback to the old fully-isolated
  multi-stage build.
- New, separate thin Dockerfile (`scripts/deploy-and-run/Dockerfile`), not a conditional stage in
  the root `Dockerfile`.
- Neither playwright/run.sh nor deploy-and-run.sh reused build-and-test.sh's jar for Playwright
  specifically — the fix landed one level up, in `run-all-tests.sh`, which now runs
  `deploy-and-run.sh` sequentially before `playwright.sh` (see Phase 2c).
- `build-and-test.sh` skips tests by default when invoked from `deploy-and-run.sh` (`--no-unit
  --no-integration`); `--with-tests` opts in explicitly.

## Plan (2026-08-17 — supersedes the "blocked, restructure in progress" note above)

Verified directly: no `scripts/deploy-and-run/` restructure exists anywhere in this repo — not in
git log, not in any stash, not in any branch. The earlier note that it was "in progress in the
same session this issue was filed from" was wrong; that work was never actually done. This issue
now carries that restructure as its own first phase, since no other issue tracks it.

### Phase 1 — restructure (done)

Implemented and verified: `bash scripts/deploy-and-run.sh` runs end to end identically to the old
`scripts/deploy.sh` (all 3 steps, app reaches `Started Application`, HTTP 200 on port 8081).
Legacy `scripts/deploy.sh`/`.bat`, `scripts/infra/`, `scripts/database/` removed after verification.
External references updated in every current-state doc/functional script found by repo-wide grep
(`playwright/run.sh`, `scripts/ci/entrypoint.sh`, `docs/architecture/scripts/generate-architecture-model.sh`,
root `CLAUDE.md`, `README.md`, `scripts/CLAUDE.md`, `scripts/README.md`, `playwright/CLAUDE.md`,
`playwright/README.md`, `integration-tests/CLAUDE.md`, `.claude/rules.md`,
`.claude/commands/build.md`, `.claude/commands/autopilot.md`, `.claude/skills/infra-doc-standards/SKILL.md`,
`docs/ai/flows.md`) — `DECISIONS.md` files and `backlog/` issue files deliberately left untouched
(append-only historical record, per `.claude/rules.md`). Phase 1b/2/2c/3 below remain undecided/
unimplemented.

### Phase 1 — restructure, original scope note

- New `scripts/deploy-and-run/` directory.
- `scripts/deploy.sh`'s body moves into `scripts/deploy-and-run/run.sh`.
- The **contents** of `scripts/infra/` (`docker-compose.db.yml`, `docker-compose.minio.yml`,
  `docker-compose.app.yml`) move directly into `scripts/deploy-and-run/` — flat, no nested
  `infra/` subfolder.
- The **contents** of `scripts/database/` (`reset.sh`, `reset.bat`, `reset-clean.sql`) move
  directly into `scripts/deploy-and-run/` — flat, no nested `database/` subfolder.
- `run.sh` (deploy logic, moved from `deploy.sh`) is reused by `scripts/deploy-and-run.sh` and
  `scripts/deploy-and-run.bat` — both call into it rather than duplicating logic.

Side effects of this restructure (paths in ~15 dependent files: `playwright/run.sh`,
`scripts/database/reset.sh`'s own self-reference, README.md, various `CLAUDE.md`/`DECISIONS.md`
files, `docs/architecture/scripts/generate-architecture-model.sh`'s hardcoded compose-file entry)
identified during discussion — to be gone through separately, not decided yet.

### Phase 1b — database cleanup script unification (done)

Implemented and verified: `bash scripts/deploy-and-run.sh --reset-only-db` runs end to end — infra
up, reset via the shared `scripts/deploy-and-run/reset.sh` ("Database reset complete — all data
truncated."), build, app started, HTTP 200. The 4 previously-open items below were resolved during
implementation: connection mechanism converged on `docker exec <container> psql` (dropping
standalone `reset.sh`'s old host-side `psql -h/-p` path); "skip if already empty" stayed
`playwright/run.sh`-side logic (it already needs the check result to decide whether to
stop/restart the app, so `reset.sh` doesn't also need the flag); stop/restart around the truncate
stays the caller's responsibility, not inside `reset.sh` (`reset.sh` only does the truncate itself,
never orchestrates app-container lifecycle); DB container name reaches `reset.sh` via a
`--container <name>` flag (falls back to `$DB_CONTAINER` env var, then a dynamic `docker ps`
lookup if neither is set) — `deploy-and-run/run.sh` and `playwright/run.sh` both pass their own
already-resolved container name explicitly.

### Phase 1b — database cleanup script unification, original scope note

Today the same "truncate all app tables via `reset-clean.sql`" action is duplicated in three
places, found during discussion:
- `scripts/database/reset.sh` (standalone) — connects with host-side `psql -h/-p`, auto-starts the
  DB container if none is running.
- `scripts/deploy.sh`'s `--reset-db` flag — reimplements the same truncate inline
  (`docker cp` + `docker exec ... psql`), runs before the app container starts.
- `playwright/run.sh`'s own inline reset block — same truncate inline via `docker exec ... psql`,
  plus a "skip if `user_information` already has 0 rows" check, and stops/restarts the app
  container around the truncate.

**Decided:**
- After the Phase 1 move, both `scripts/deploy-and-run/run.sh` and `playwright/run.sh` call the one
  relocated `scripts/deploy-and-run/reset.sh` instead of each keeping its own inline duplicate.
- The flag exposed for this on `deploy-and-run.sh`/`.bat` (forwarded down to `run.sh`) is renamed
  `--reset-db` → `--reset-only-db`, to read distinctly from `--reset` (the separate, unrelated,
  full DB/MinIO-volume-wipe flag, which keeps its existing name and behavior unchanged).
- Parameters are propagated upward through the call chain: `deploy-and-run.sh`/`.bat` (top-level
  entry point) → `run.sh` → the shared `reset.sh` — the same forwarding shape `run.sh` already uses
  for its other flags today.

**Not yet decided (open items from this discussion):**
- Which connection mechanism the unified `reset.sh` uses — `docker exec <container> psql` (what
  `deploy.sh`/`playwright/run.sh` use today) vs. host-side `psql -h/-p` (what standalone `reset.sh`
  uses today). These need to converge on one.
- Whether the "skip if already empty" check (currently only in `playwright/run.sh`) becomes a flag
  on the shared `reset.sh`, or stays playwright-side logic wrapping the call.
- Whether stopping/restarting the app container around the truncate (currently only in
  `playwright/run.sh`) belongs inside `reset.sh` itself or stays the caller's own responsibility.
- How the DB container name reaches `reset.sh` — today `deploy.sh`/`playwright/run.sh` each resolve
  `DB_CONTAINER` themselves (fixed default vs. dynamic `docker ps` lookup); `reset.sh` doesn't take
  it as a parameter today since it doesn't use `docker exec` at all.

### Phase 2 — the actual improvement-154 behavior (done)

Implemented and verified: `bash scripts/deploy-and-run.sh` (no flags) now runs
`bash scripts/build-and-test.sh --no-unit --no-integration` first (Step 1.5), extracts
`marketplace-app.jar` from the `maven-cache` volume via a short-lived helper container
(`jar-extract-tmp`, removed after `docker cp`) into `scripts/deploy-and-run/marketplace-app.jar`
(gitignored), then builds the image from a new thin `scripts/deploy-and-run/Dockerfile`
(`FROM eclipse-temurin:25-jre` + `COPY marketplace-app.jar app.jar` — no `mvn` invocation inside
this image at all). `--with-tests` forwards `--unit --integration` to the `build-and-test.sh` call;
`--from-scratch` skips Step 1.5 entirely and falls back to the original full multi-stage root
`Dockerfile` build — the mount-mechanism question resolved to "extract via a helper container",
and the opt-in-vs-default question resolved to "default, `--from-scratch` is the opt-out."
Real run: infra up → build-and-test.sh build → jar extracted (69MB) → thin image built (~8s,
`COPY` only) → app started → HTTP 200 on port 8081.

Confirmed facts from this session's discussion (verified against `scripts/build-and-test/build.sh`
and `scripts/build-and-test/run.sh`):

- `build-and-test.sh` is already parameterized (`--unit`/`--no-unit`, `--integration`/
  `--no-integration`, `--sandbox`, `--reset-cache`, `--rebuild-image`) and **always** refreshes
  `marketplace-app.jar` at `/root/.m2/artifacts/marketplace-app.jar` inside the shared `maven-cache`
  Docker volume on every call, regardless of which test flags are passed — the test flags only
  control whether tests *additionally* run, not whether the jar gets refreshed. No new
  parameterization is needed on `build-and-test.sh`'s own side for this.
- `deploy-and-run.sh` calls `bash scripts/build-and-test.sh [flags]` as a plain sibling-script call
  (same composition style `scripts/ci/entrypoint.sh` already uses) — by default just a build (no
  test flags), with the option to additionally pass `--unit`/`--integration` when a caller wants
  tests run in the same invocation.
- `deploy-and-run/run.sh`'s Docker image build step needs to mount/connect to the same
  `maven-cache` volume and `COPY` the already-built jar out of
  `/root/.m2/artifacts/marketplace-app.jar`, instead of running `mvn package` inside its own
  Dockerfile builder stage. Exact mount mechanism (build-context mount vs. a build step that reads
  from the volume) — not decided yet.

What this does and does not achieve, confirmed in discussion:
- **Does:** eliminates the duplicate full-project compile (today `deploy.sh` compiles a second time
  inside its own Dockerfile even though `build-and-test.sh` already compiled everything) — one
  shared jar, one source of truth, faster deploy image build.
- **Does not, by itself:** close the Playwright-freshness gap — see Phase 2c below for how that
  actually gets closed.

### Phase 2c — Playwright freshness gap: where the fix actually belongs (done)

Implemented and verified: `bash scripts/run-all-tests.sh --sandbox` — unit tests PASSED,
integration tests PASSED (165/165), `deploy-and-run.sh --reset-only-db` (default; `--reset` when
`--reset` is passed to `run-all-tests.sh` itself, for when the schema changed) ran sequentially
before `playwright.sh`, full e2e suite passed (exit 0, "ALL PASSED"). One real bug found and fixed
during testing, not anticipated in the design below: `scripts/build-and-test/run.sh` runs its build
container under a **fixed** name (`advertisement-build-only`) — the first real-world case of two
concurrent `build-and-test.sh` invocations from one `run-all-tests.sh` call (this script's own
direct one, and the one `deploy-and-run.sh` now triggers internally) hit a genuine Docker
container-name conflict (`docker run --name` fails outright on a duplicate name, independent of and
before the shared-volume `flock` ever gets a chance to serialize anything — the "safe... via flock"
claim below was wrong, an unverified assumption caught by actually running the test). Fixed by
adding `BUILD_CONTAINER_NAME` (env var, default unchanged) to `scripts/build-and-test/run.sh`;
`deploy-and-run/run.sh`'s Step 1.5 passes `BUILD_CONTAINER_NAME=advertisement-build-only-deploy` for
its own internal call. Also added defensive `docker rm -f "$BUILD_CONTAINER_NAME"` before container
creation in `build-and-test/run.sh` (an interrupted/failed prior run left a stale container behind,
blocking the next run with the same conflict) — audited every other `docker run --name` in
`scripts/` for the same gap; all others already self-heal (unique per-invocation name, a
check-then-create helper, or an existing defensive `rm -f`).

Traced the real call graph for every place Playwright runs today, confirmed against the actual
scripts:

- `playwright/run.sh` itself never reads the shared jar/volume — it only makes HTTP calls against
  whichever `marketplace-app` container already happens to be running. **Its own code does not need
  to change.**
- `scripts/ci/entrypoint.sh`'s e2e stage already calls `deploy.sh` sequentially, then
  `playwright/run.sh` (lines 185/191) — once `deploy-and-run.sh` reuses `build-and-test.sh`'s jar
  (Phase 2 above), this call path gets a fresh container automatically, no further change needed
  here.
- `scripts/run-all-tests/run.sh` is the actual gap: it launches `build-and-test.sh --unit
  --integration` and `playwright.sh` **in parallel**, with no deploy step in between at all —
  Playwright there tests whatever container already happens to be running, completely disconnected
  from the parallel build.
- Standalone `bash playwright/run.sh` already requires a manual rebuild+redeploy first (documented
  in `playwright/CLAUDE.md`) — unaffected by any of this.

**Decided:** `run-all-tests.sh` keeps `build-and-test.sh --unit --integration` and `playwright.sh`
running in parallel as today (unit/integration test reporting is unaffected). A `deploy-and-run`
call is inserted sequentially, immediately before `playwright.sh` starts (not before the parallel
`build-and-test.sh` call) — this ensures `marketplace-app` is rebuilt from a fresh jar before
Playwright tests it. The two resulting `build-and-test.sh` invocations from one `run-all-tests.sh`
call (the existing parallel one, and the one `deploy-and-run` triggers internally) are safe against
each other via the existing `flock`-based lock (see Phase 2 above) — redundant but not conflicting.
The database-cleanup flag (`--reset-only-db`, see Phase 1b above) is forwarded upward through this
same new call: `run-all-tests.sh` → `deploy-and-run.sh` → `run.sh` → `reset.sh`.

### Phase 3 — docs for the new `scripts/deploy-and-run/` folder (final phase) (done)

Implemented and verified: ran the `infra-doc-standards` skill against `scripts/deploy-and-run/` —
Google-style headers added to `run.sh`, `reset.sh`, `reset.bat`, `Dockerfile`, and all 3
`docker-compose*.yml` files; new `scripts/deploy-and-run/README.md` with a "What is it" opener and
a `## Flow` section (two Mermaid diagrams: the deploy pipeline, and standalone `reset.sh`). An
independent review agent (per the skill's own "spawn a fresh agent" step) caught 3 real gaps, all
fixed: `run.sh`'s header falsely claimed `.env`'s `POSTGRES_IMAGE` is honored (it isn't — hardcoded
`postgres:15-alpine`, unlike the compose files); `docker-compose.minio.yml`'s `Usage` line used
stale hyphenated `docker-compose` syntax and was missing `--project-directory .`; the README's
Mermaid diagram didn't show `--with-tests` as a real branch. Also caught during verification (not
by the review agent): `docker-compose.app.yml` still had `version: '3.8'` above its header block,
which the generator's header parser can't see past (breaks at the first non-comment line) — fixed
by moving `version:` below the header, matching `db.yml`/`minio.yml`.

Architecture-map card added: `scripts/deploy-and-run` registered in
`docs/architecture/scripts/generate-architecture-model.sh`'s `SCRIPT_GROUP_CATEGORY`/
`SCRIPT_GROUP_DIRS`/`SCRIPT_GROUP_FILE_ORDER` (bash side, JSON data) and `PIPELINE_GROUPS`/
`PIPELINE_GROUP_ORDER` (JS side, actual card *display order* — a separate mechanism from the bash
arrays, confirmed by reading `renderPipelines()`), positioned immediately after `build` (Build and
Test) in `PIPELINE_GROUP_ORDER`. Regenerated for real
(`bash docs/architecture/scripts/generate-architecture-model.sh`) and verified in the output JSON:
node `scripts/deploy-and-run` present, category "Deploy and Run", all 6 files' headers parsed
correctly, README embedded.

## Related

- `improvement-152` Part A, "Explicitly out of scope, investigated and rejected" — the earlier
  rejection this issue revisits under the different (fresh-build-same-invocation) condition above.
- `improvement-152` Part A, "Real gap found, not fixed: Playwright ... never sees
  `build-and-test.sh`'s fresh build" — the gap this issue closes.
- `scripts/ci/DECISIONS.md` — the entry-point-composition precedent (`scripts/ci/entrypoint.sh`
  composing sibling scripts without internal folder coupling) this issue's own composition model
  follows.
- The `scripts/deploy.sh` → `scripts/deploy-and-run/` restructure (in-progress, same session) —
  this issue is explicitly sequenced to start immediately after that work lands.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: 110471 (deploy-and-run docs-coverage review agent)
- review_signal_ratio: n/a (no /code-review ran this task)
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a

### Agent calls
- deploy-and-run docs coverage verification | subagent_type=general-purpose | tokens=110471 | tool_uses=21 | duration_s=90 | mode=foreground | batch=solo

### Script/command runs
- bash scripts/deploy-and-run.sh (Phase 1 restructure verification) | duration_s=~50 | mode=foreground | result=pass
- bash scripts/deploy-and-run.sh --reset-only-db (Phase 1b unification verification) | duration_s=~60 | mode=foreground | result=pass
- bash scripts/deploy-and-run.sh (Phase 2 build-and-test jar reuse verification) | duration_s=~130 | mode=foreground | result=pass
- bash scripts/run-all-tests.sh --sandbox (Phase 2c, 1st attempt) | duration_s=~150 | mode=foreground | result=fail (found: build-and-test container-name collision)
- bash scripts/run-all-tests.sh --sandbox (Phase 2c, after container-name fix) | duration_s=~230 | mode=foreground | result=fail (playwright: stale DB state from this session's own earlier manual tests, not a Phase 2c regression)
- bash scripts/run-all-tests.sh --sandbox (Phase 2c, with unconditional --reset, interrupted by user) | duration_s=n/a | mode=foreground | result=n/a
- bash scripts/run-all-tests.sh --sandbox (Phase 2c, final, --reset-only-db default) | duration_s=~330 | mode=foreground | result=pass (unit 53/53, integration 165/165, e2e ALL PASSED)
- bash docs/architecture/scripts/generate-architecture-model.sh (Phase 3, multiple regenerations while fixing header-parsing gaps) | duration_s=~15 each | mode=foreground | result=pass (final run)
