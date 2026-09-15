# improvement-153: Local CI visualization/click-to-trigger via Dagu, replacing `scripts/ci.sh`'s `progress.txt` polling

**Type:** improvement — investigation/design, not yet actioned.
**Module:** `scripts/ci/` (`Dockerfile`, `entrypoint.sh`, `run.sh`, `DECISIONS.md`).
**Priority:** Top (explicit placement — ranked directly after `improvement-152` in `BACKLOG.md`'s
priority table).
**When:** independent, no blockers.

## Problem

`scripts/ci.sh`'s only visualization today is a hand-rolled `progress.txt` file (per-stage
`PENDING`/`RUNNING`/`DONE`/`FAILED` with elapsed seconds, periodically synced via `docker cp` since
bind mounts don't work in this sandbox — see `scripts/ci/DECISIONS.md` ADR-002) — no live web UI,
no clickable trigger, always started from a terminal invocation
(`bash scripts/ci.sh [flags]`).

Discussed and rejected alternatives, in order:
- **Jenkins** — a heavyweight, persistent CI service; a mismatch for this project's local
  dev-sandbox scale (see `improvement-152` Part A's own CI-vocabulary grounding research).
- **GitHub Actions/GitLab CI run locally** (`act`, `gitlab-ci-local`) — would mean translating
  `entrypoint.sh`'s existing stage/job bash orchestration into a foreign YAML dialect, with
  imperfect local emulation of the real hosted runners, for zero real portability benefit — there
  is no hosted GitHub/GitLab remote this project's CI ever needs to match, since it only ever runs
  locally in this sandbox.
- **Woodpecker CI** — lighter than Jenkins, but still needs a persistent `server` (+ database for
  run history) and `agent` component running continuously, not just during an active CI run —
  containerizing it doesn't remove that stateful-service operational cost.

## Suggested fix

**Dagu** (https://github.com/dagucloud/dagu) — a single-binary, no-database DAG/workflow engine
with a built-in web UI: clickable "Start" per DAG, live per-stage status coloring, live logs, run
history. Materially lighter than Woodpecker/Jenkins (no persistent server+DB+agent architecture to
operate) and closer-fitting to this project's existing container model.

Proposed shape:
- Bake the Dagu binary into (or run alongside) the existing `scripts/ci/Dockerfile`'s `ci-runner`
  image.
- Define the current `entrypoint.sh` stage sequence (`build_and_test` → `e2e` → `sonar`/`docs`,
  including `build_and_test`'s own internal unit/integration parallelism — see
  `scripts/ci/DECISIONS.md` ADR-008) as a Dagu YAML DAG.
- Each DAG node calls the exact same existing scripts already in place today
  (`scripts/build-and-test.sh`, `scripts/playwright.sh`, `scripts/sonar.sh`) — Dagu replaces only
  the orchestration/UI layer (`entrypoint.sh`'s stage bookkeeping + `progress.txt` polling), not
  any of the actual build/test logic itself.

## Prerequisite carried over from `improvement-152` Part A — verify the current `entrypoint.sh` stage merge for real

`scripts/ci/entrypoint.sh` was already rewired this session to call `scripts/build-and-test.sh`
directly as one merged `build_and_test` stage (replacing the old separate `unit`/`integration`
stages that called the now-deleted `scripts/unit-tests.sh`/`scripts/integration-tests.sh`) —
written and syntax-checked (`scripts/ci/DECISIONS.md` ADR-008), but **never run end to end**:
`bash scripts/ci.sh --unit --foreground` was not executed, since it builds its own fresh
`ci-runner` image plus a full cold-cache `mvn install` inside a separate, not-yet-warmed
`ci-m2-cache` volume (distinct from the `maven-cache` volume `build-and-test.sh` already warms
directly) — a real verification run takes a while and was deliberately postponed.

Since this issue already touches `scripts/ci/entrypoint.sh`'s own behavior (replacing its
`progress.txt` visualization layer), verifying the current stage merge actually works for real is
a natural prerequisite here — confirm `bash scripts/ci.sh --unit --foreground` reaches a real
PASSED/FAILED result before building the Dagu layer on top of it, so any redesign starts from a
confirmed-working baseline, not an unverified one.

**Verified 2026-08-18 — `build_and_test` stage confirmed working.** `bash scripts/ci.sh --unit
--foreground` run end to end: the `build_and_test` stage (the merged one this prerequisite exists
to check) passed cleanly — 53/53 unit tests green (`query-lib`/`marketplace-orchestrator`/
`marketplace`), `BUILD SUCCESS`, exit 0, 157s. The overall script still exited 1, but from an
unrelated, independent `docs` stage (the architecture-model freshness gate, tripped by this
session's own backlog-file edits, expected and not part of what this prerequisite checks). Baseline
confirmed working — the Dagu layer can be built on top of it.

## Design decisions (2026-08-18)

- **Dagu runs always-on, baked into the existing `ci-runner` image/container** — not a separate
  container. `ci-runner` stops being a one-shot "build image, run once, exit" container and
  becomes persistent, with Dagu's web server as its long-running process. A redeploy script
  recreates it on demand (remove the previous container, rebuild/run a fresh one) so no stale
  state carries over between redeploys — the same "always fresh" discipline `deploy-and-run.sh`
  already applies to the app container.
- **This also resolves the DAG-shape question**: since Dagu runs inside the same `ci-runner`
  container that already has `/var/run/docker.sock` mounted (Docker-outside-of-Docker, ADR-001),
  it inherits that access directly — no separate mounting decision needed. This makes the
  fine-grained option viable: `entrypoint.sh`'s stage sequence (`build_and_test` → `e2e` →
  `sonar`) is decomposed into individual per-stage Dagu DAG nodes (not one opaque
  `bash scripts/ci.sh` step) — each node calls the existing stage script directly
  (`build-and-test.sh`, `deploy-and-run.sh`+`playwright.sh`, `sonar.sh`), giving Dagu's UI real
  per-stage status/coloring/logs and the ability to retry a single failed stage, which an opaque
  single-step wrap would not provide.
- **Dagu's own state/run history: a separate named Docker volume**, same pattern already used for
  `maven-cache`/`ci-m2-cache`.

## DAG shape (2026-08-18, revised — grounded by reading `build-and-test/run.sh`, `build-and-test/build.sh`, `deploy-and-run/run.sh`, `sonar/run.sh`, `run-all-tests/run.sh` directly, not assumed)

Dagu's modern schema: typed `params` (list of `{name, type, default, ...}`), steps keyed by `id`/
`run` (the `name`/`command` pair still works but is documented as legacy), `depends` (string or
list of step ids — **omitted entirely means the step starts immediately**, no implicit ordering),
`continue_on: {failure: true}` to let a dependent step run even after its dependency failed, and
`preconditions: [{condition, expected}]` to skip a step based on a param. Params are referenced as
`${params.name}` (spec pages: `docs.dagu.sh/writing-workflows/yaml-specification`, `.../data-flow`).

**No intermediate `scripts/ci/stages/*.sh` wrapper layer** — dropped after reading the actual
scripts: `build-and-test.sh`, `deploy-and-run.sh`+`playwright/run.sh`, and `sonar.sh` are already
fully parameter-driven (flags/env vars), so each Dagu step calls the real script directly, building
its flags inline from `${params.*}` in a multi-line `run: |` block. No new file layer duplicating
what these scripts already do.

**`build` runs first, alone; `unit`, `integration`, `e2e`, `sonar` branch out from it in parallel**
— chosen for two reasons: (1) it reads as a clean trunk-then-branches shape in Dagu's graph view
instead of several disconnected roots, and (2) it exposes `unit`/`integration` as their own
checkbox-able params with their own visible node/status/log each, instead of bundling both inside
one opaque `build_and_test` blob. `build` itself is just `build-and-test.sh --no-unit
--no-integration` (reactor compile + install only, refreshes the shared jar — no tests) so the four
branches start from an already-warm cache. This is safe to fan out this way because of the actual
mechanism already in the codebase: `build-and-test/build.sh:49` holds a `flock` on
`/root/.m2/.build.lock`, a file **inside the `maven-cache` volume itself** — this already
serializes any concurrent `mvn install` calls from different containers mounting that volume (a
later caller just blocks on the lock, then proceeds cheaply via Maven's incremental compilation
since `build` already primed it). `run-all-tests.sh` already runs this exact class of concurrency
in production today (its own `build-and-test.sh` call in parallel with `deploy-and-run.sh`'s
internal one), documented directly in that script's own header.

The only real constraint fanning four branches out is Docker container-name uniqueness (`docker run
--name`), not the volume — `build-and-test.sh` defaults to one fixed name
(`advertisement-build-only`) unless the caller overrides `BUILD_CONTAINER_NAME`. Today only
`deploy-and-run.sh` (used by `e2e`) does this (`advertisement-build-only-deploy`); every other
caller still defaults to the same name, which is fine sequentially but not for four branches racing
each other.

**Required supporting fixes (small, outside the DAG file itself) — each branch's own
`build-and-test.sh` call needs a distinct `BUILD_CONTAINER_NAME`:**
- `unit` step: `BUILD_CONTAINER_NAME="advertisement-build-only-unit"` (new — no caller sets this today)
- `integration` step: `BUILD_CONTAINER_NAME="advertisement-build-only-integration"` (new)
- `sonar.sh`'s own internal `build-and-test.sh` call (currently `bash "$ROOT/scripts/build-and-test.sh"
  --no-unit --no-integration`, no override): `BUILD_CONTAINER_NAME="advertisement-build-only-sonar"`
- `e2e` (via `deploy-and-run.sh`): already fine, uses `advertisement-build-only-deploy` today
- `build` itself keeps the default name — nothing else runs concurrently with it, since the other
  four all `depends: [build]` and only start once it finishes.

**`docs` runs last, as an optional step** — `depends: [unit, integration, e2e, sonar]`, gated by
its own `docs` param (default `true`, a real on/off toggle now, not just a non-blocking gate).

**Params-as-checkboxes/dropdowns — not yet confirmed.** Checked `docs.dagu.sh`'s web-UI page
directly: it documents typed `params` declaration (`type: boolean`/`enum`/etc.) but does not
describe how the manual "Start" trigger dialog actually renders each type (checkbox vs. text
input vs. dropdown) — flagged as an implementation-time check, not a promised fact.

```yaml
name: ci
params:
  - name: unit
    type: boolean
    default: true
  - name: integration
    type: boolean
    default: true
  - name: e2e
    type: boolean
    default: true
  - name: sonar
    type: boolean
    default: true
  - name: docs
    type: boolean
    default: true
  - name: sandbox
    type: boolean
    default: false
  - name: keep_infra
    type: boolean
    default: false
  - name: playwright_args
    type: string
    default: "e2e --full --ux"

steps:
  - id: build
    run: bash scripts/build-and-test.sh --no-unit --no-integration
    continue_on:
      failure: true

  - id: unit
    depends: build
    run: BUILD_CONTAINER_NAME="advertisement-build-only-unit" bash scripts/build-and-test.sh --unit --no-integration
    continue_on:
      failure: true
    preconditions:
      - condition: "${params.unit}"
        expected: "true"

  - id: integration
    depends: build
    run: |
      FLAGS="--no-unit --integration"
      [ "${params.sandbox}" = "true" ] && FLAGS="$FLAGS --sandbox"
      BUILD_CONTAINER_NAME="advertisement-build-only-integration" bash scripts/build-and-test.sh $FLAGS
    continue_on:
      failure: true
    preconditions:
      - condition: "${params.integration}"
        expected: "true"

  - id: e2e
    depends: build
    run: |
      bash scripts/deploy-and-run.sh
      bash playwright/run.sh ${params.playwright_args}
    continue_on:
      failure: true
    preconditions:
      - condition: "${params.e2e}"
        expected: "true"

  - id: sonar
    depends: build
    run: bash scripts/sonar.sh
    continue_on:
      failure: true
    preconditions:
      - condition: "${params.sonar}"
        expected: "true"

  - id: docs
    depends: [unit, integration, e2e, sonar]
    run: |
      bash docs/ai/scripts/check-adr-index-freshness.sh
      bash docs/ai/scripts/check-flows-completeness.sh
      bash docs/ai/scripts/check-hardcoded-counts.sh
      bash docs/architecture/scripts/check-architecture-model-freshness.sh
    continue_on:
      failure: true
    preconditions:
      - condition: "${params.docs}"
        expected: "true"
```

Notes:
- `e2e`'s isolated-stack env-var overrides (`NETWORK`/`DB_CONTAINER`/`CI_DB_PORT`/etc. — the same
  `CI_*`-prefixed constants `entrypoint.sh` sets today, so this run never touches the persistent
  dev stack) still need to be exported inside that step's `run:` block — omitted above for
  brevity, carried over unchanged from `entrypoint.sh`'s existing values.
- Report-directory copying (`build-and-test/`, `playwright/`, `sonar/` subfolders under a per-run
  report dir) stays inline in each step's `run:` block, same `cp -r` calls `entrypoint.sh` already
  does — Dagu's own run history covers step status/logs, not these build/test artifact
  directories, so both coexist.
- The `ci-runner` container's own `ENTRYPOINT` changes from "run `entrypoint.sh` once and exit" to
  starting Dagu's own long-running server process (exposing its web UI) — the exact Dagu CLI
  invocation for that is an implementation-time detail (see open questions), not decided here since
  it doesn't affect the DAG shape itself.

## Implementation — done (2026-08-18)

- `scripts/sonar/run.sh` — `BUILD_CONTAINER_NAME="advertisement-build-only-sonar"` override added
  to its internal `build-and-test.sh` call (required supporting fix from the DAG-shape section).
- `scripts/ci/Dockerfile` — no longer installs anything at build time beyond apt packages;
  buildx/compose/Dagu binaries move to `scripts/ci/docker-entrypoint.sh` (see below). `ENTRYPOINT`
  is that script; `DAGU_PORT=18080` (not `8080` — conflicts with a local IntelliJ dev server on a
  real dev machine); `DAGU_AUTH_MODE=none` (single-user, local-only tool, reachable only through
  the proxy sidecar below — no login wizard needed).
- `scripts/ci/docker-entrypoint.sh` — new. Downloads buildx/compose/Dagu into the `ci-tools-cache`
  named volume only if not already cached there (survives image rebuilds regardless of Docker
  layer-cache misses, which this sandbox hits often for reasons independent of this issue), then
  execs `dagu start-all`. `--refresh-tools` (`scripts/ci/run.sh`) forces a re-download of all three.
- `scripts/ci/dagu/ci.yaml` — the real DAG file (`build` → `unit`/`integration`/`e2e`/`sonar` in
  parallel → `docs`), `working_dir: /app` (see resolved design questions below).
- `scripts/ci/run.sh` — fully rewritten into a thin trigger: (re)builds the image, (re)starts the
  persistent `ci-runner` container fresh, starts a small `alpine/socat` proxy sidecar
  (`ci-runner-dagu-proxy`, bridge network + `-p 8082:8082`) forwarding to Dagu's real host-network
  port, then fires a DAG run via `docker exec ci-runner dagu start scripts/ci/dagu/ci.yaml -- ...`.
  New flags: `--no-rebuild`, `--refresh-tools`; removed: `--report-dir`, `--keep-reports` (no more
  report-copying mechanism — Dagu's own UI/history replaces it).
- `scripts/ci/entrypoint.sh` deleted; every stale reference to it swept and fixed (`scripts/CLAUDE.md`,
  `scripts/README.md`, `scripts/sonar/DECISIONS.md`, `scripts/deploy-and-run/run.sh`,
  `playwright/run.sh`, `.claude/skills/infra-doc-standards/SKILL.md` — historical mentions in
  `DECISIONS.md` files left as-is per this repo's own convention).
- `scripts/ci/DECISIONS.md` ADR-009 records the full design and the three problems only found by
  actually running it (see below).

**Verified end to end, for real, not assumed:** image build → persistent container start → Dagu
web UI reachable from a real external browser (not just `curl` inside the sandbox) → "Start"
dialog on the `ci` DAG renders a field per param (confirmed directly by the user) → `unit` stage
genuinely passing (53/53) → `marketplace-app` (the persistent dev stack) still healthy throughout.

## Open design questions — resolved

- **Exact Dagu CLI invocation:** `dagu start-all --host 0.0.0.0 --port "$DAGU_PORT" --dags
  scripts/ci/dagu` to run the server; `dagu start scripts/ci/dagu/ci.yaml -- key=value ...` (the
  DAG's *file path*, not its bare name — a bare name resolves against `$DAGU_HOME/dags` instead,
  confirmed directly) to trigger a run against it.
- **Does the "Start" dialog render params as fields?** Yes — confirmed directly by the user
  opening the real UI and clicking "Start" on the `ci` DAG. A run can be triggered entirely from
  the browser once the container is running; `scripts/ci/run.sh` is only needed to build/start the
  container itself, not for every-day triggering.

Three more problems surfaced only by running this for real (not documented anywhere in Dagu's own
docs), and one unrelated but real cost problem found along the way — full detail in
`scripts/ci/DECISIONS.md` ADR-009:
1. `dagu start <name>` resolves against `$DAGU_HOME/dags`, not the server's own `--dags` directory.
2. Each DAG step runs in its own isolated working directory by default, not the launching
   process's cwd — needs `working_dir: /app` at the DAG's top level.
3. A `--network host` container's bound ports aren't reachable from a real browser in this sandbox
   (unlike an explicit `docker run -p` publish) — fixed with a bridge-network proxy sidecar reading
   the actual gateway IP from Docker (`host.docker.internal` resolved to an address that refused
   the connection here, confirmed directly).
4. (Unrelated to Dagu) the image's build-time binary installs re-downloaded ~190MB on every rebuild
   whenever an earlier layer's cache missed — fixed by moving them to a volume-backed,
   download-if-missing check at container start instead of image build time.

## Follow-up: CI pipeline metrics + ArchUnit export as DAG steps (2026-08-18, same day)

Requested after the base migration shipped: feed real CI-run data into
`docs/architecture/scripts/generate-architecture-model.sh` (matching the existing `--with-sonar`/
`--with-archunit` opt-in-file pattern) — (1) per-step status/duration from the last Dagu run, (2)
the existing ArchUnit module-coupling export, wired as its own on-demand DAG step instead of a
separate manual command.

**Shipped:**
- `scripts/ci/dagu/ci.yaml` — new `archunit_metrics` param (default `false`) + step, parallel to
  `unit`/`integration`/`e2e`/`sonar` (`depends: build`), running
  `bash scripts/build-and-test.sh --archunit-metrics`. New `pipeline_metrics` step,
  `depends: [unit, integration, e2e, sonar, archunit_metrics]`, runs after all five and before
  `docs` (which now depends on `pipeline_metrics` instead of the four stage steps directly).
- `scripts/ci/dagu/pipeline-metrics.py` — new. Queries Dagu's own local API
  (`GET /api/v1/dag-runs/{name}/{run-id}`, `$DAG_NAME`/`$DAG_RUN_ID` auto-injected by Dagu into
  every step) for the current run's per-step name/status/duration, writes a small JSON summary.
- `docs/architecture/scripts/generate-architecture-model.sh` — new `--with-ci-metrics` flag +
  `ci_pipeline_metrics_json()` (passive file read, `null` if absent — same shape as
  `archunit_metrics_json()`, no live Dagu API call from the generator itself), embedded as
  `MODEL.ciPipelineMetrics`; rendered in the `scripts/ci` Tooling & Pipelines card as a "Last CI
  run" table (step/status/duration).
- `scripts/ci/run.sh` — new `--sync-artifacts` flag; `sync_artifacts()` now runs automatically
  after every `--foreground` run.
- `scripts/build-and-test/build.sh`/`run.sh` — new `SKIP_VAADIN` env var / `--skip-vaadin` flag
  (see the confirmed-and-fixed Vaadin cost finding below), applied to `ci.yaml`'s `unit`/
  `integration`/`archunit_metrics` steps.

**Three more real bugs found only by running this, none of them assumption — each is a case where
the first design was plausible-sounding but wrong in a way only execution caught:**

1. **Embedding multi-line Python inside a YAML `run: |` block scalar is not just risky, it's
   structurally incompatible.** First attempt wrote the metrics-parsing Python inline via
   `python3 -c '...'` inside the step's `run: |` block. Failed immediately on load:
   `could not find end character of single-quoted text`. Root cause: YAML block scalars require
   every line to be indented at least as much as the block's own base indentation; Python's own
   top-level statements must have *zero* indentation. No indentation level satisfies both rules at
   once for multi-line Python. Fixed by moving the parser to a real file
   (`scripts/ci/dagu/pipeline-metrics.py`), called as `python3 <path>` — a one-line `run:` entry,
   no embedding.
2. **`docker cp`'s "local" side resolves in the *caller's* filesystem, not the real host — even
   from a container with `docker.sock` mounted.** Original design had the `archunit_metrics`/
   `pipeline_metrics` steps `docker cp` their own output onto the host themselves, using an
   `HOST_REPO_ROOT` env var passed at container-start time. Looked correct, ran without error, and
   silently did nothing useful: the daemon-socket mount gives *control* over the real Docker
   daemon (start/stop/inspect containers, stream a container's files as a tar archive) but the
   actual local-filesystem write for `docker cp CONTAINER:SRC LOCAL_DEST` is performed by the
   *client process* issuing the command — and that client was `ci-runner`'s own shell, so
   `LOCAL_DEST` landed inside `ci-runner`'s own filesystem regardless of what path string was
   passed. Confirmed directly: the host file kept a stale timestamp from days earlier while the
   identical nominal path *inside* the container had a fresh one. Fixed by moving both `docker cp`
   calls into `scripts/ci/run.sh` (a real host-side process) as `sync_artifacts()`, called after a
   `--foreground` run and available on demand via `--sync-artifacts`.
3. **A step whose only precondition-gated dependencies were `skipped` (not `succeeded`) also gets
   skipped itself, silently, with no error.** `pipeline_metrics` (`depends: [unit, integration,
   e2e, sonar, archunit_metrics]`) came back `skipped` in a run where 3 of those 5 legitimately
   skipped via their own `${params.X}` precondition — not documented as a dependency rule anywhere
   obvious, found only by inspecting the run's real per-step JSON via Dagu's API. Fixed by adding
   `continue_on: {skipped: true}` alongside each of those five steps' existing
   `continue_on: {failure: true}` — tells Dagu their own `skipped` outcome should still satisfy a
   downstream `depends:`. Every earlier verification run in this issue happened to also skip
   `docs` for an unrelated reason (`docs=false` in every test invocation), so this exact failure
   mode was never actually exercised until now.

**Also confirmed directly (not assumed) while testing, then fixed the same session (not deferred
after all — real, quick win once the root cause was clear):** `mvn install` on the full reactor
triggers `marketplace-app`'s Vaadin `build-frontend` goal (npm install + Vite bundle, ~3-4 min)
regardless of which stage is running — `unit`, `integration`, and `archunit_metrics` all paid this
cost even though none of them touch UI code, because each uses its own `BUILD_CONTAINER_NAME` and
Maven runs the full reactor's lifecycle either way. Fixed with a new `SKIP_VAADIN` env var /
`--skip-vaadin` flag (`scripts/build-and-test/build.sh`/`run.sh`) adding `-Dvaadin.skip=true` to
the reactor install for callers that only need compiled classes — guarded so a vaadin-skipped
build never overwrites the shared `maven-cache` volume's real jar/`target-classes` (both only
refresh when `SKIP_VAADIN` is unset), since `deploy-and-run.sh`/`e2e` read that same shared volume
and need the real, fully-bundled build. Applied to `ci.yaml`'s `unit`/`integration`/
`archunit_metrics` steps. **Verified end to end:** `unit` 209s → 124s (-41%), `archunit_metrics`
298s → 132s (-56%) on an otherwise-identical warm-cache run — real numbers, not estimates.

**Two more small fixes from live UI usage, same day:** a UI-triggered `integration` run failed
because the "Start" dialog's `sandbox` checkbox defaults to unchecked and is easy to forget —
fixed by removing `sandbox` as a toggleable param entirely and always passing `--sandbox`
unconditionally inside the `integration` step (`ci-runner`'s own DooD nature hits the same
dynamically-assigned-port problem regardless of host machine, so there's no real "don't need it"
case for this one step). Separately, a UI-triggered `sonar` run failed too, but that one turned out
to be the quality gate correctly doing its job (3 real findings) — not a bug, left as-is.

## Discussed and deferred: keeping `ci-runner`'s code fresh without a full rebuild

Raised while using the shipped tool for real: clicking "Start" in Dagu's web UI never picks up
source changes made since the last `bash scripts/ci.sh` rebuild (`ci-runner` has no live view of
the host filesystem — see ADR-009's bind-mount note). Discussed a few directions:

- **A live bind mount instead of `COPY . .`** — tested directly, not assumed: a throwaway
  container with `-v <sandbox-path>:/mnt/test` saw an empty directory, confirming the same
  root cause `scripts/build-and-test/run.sh`'s own comment already documents (docker resolves the
  host-side path against the *real* host, not this sandbox's own filesystem, when the caller
  invoking `docker run` is itself running inside a container). Ruled out.
- **A background file-watcher, external to `ci-runner`**, polling file mtimes (no `inotifywait`
  dependency assumed available) and `tar | docker exec -i ci-runner tar -xzf -` pushing changes in
  whenever detected — `bash scripts/ci.sh` itself would start it (once, backgrounded) alongside
  starting the container, so a single command still covers everything. Technically sound (the
  container is only ever the *target* of a push initiated from outside it, not a place a watcher
  process itself could usefully run, since it has nothing live to watch from inside), but not
  implemented — **deliberately deferred, not built**, to avoid adding a persistent background
  process for a workflow (edit → rebuild → use the UI) the base tool already supports today,
  just without the zero-manual-step polish this would add. Revisit if the manual-rebuild step
  actually becomes a recurring friction point in practice, not preemptively.

## Follow-up: live-usage fixes found running the default pipeline end to end (2026-08-18, same day)

A full default run (`bash scripts/ci.sh`, no flags) surfaced several more real gaps, each fixed and
verified with a real run rather than assumed:

- **`archunit_metrics` flipped to on by default** — with `--skip-vaadin`, its own cost (132s
  warm-cache) is small next to `e2e`'s (~17-30 min), so keeping it opt-in bought nothing. CLI flag
  became the opt-out `--no-archunit-metrics`.
- **`run-all-tests.sh` never passed `--skip-vaadin`** to its own `--unit --integration` call, unlike
  `ci.yaml`'s equivalent steps — the daily dev-loop script was paying the full ~3-4 min Vaadin/npm
  cost on every run for no reason. Fixed.
- **`scripts/ci/watch-run.py`** — a new Monitor-backed replacement for manually polling Dagu's API
  by hand every few minutes, since `ci.sh` (backgrounded, the default) returns as soon as a run is
  triggered with no single streaming log file the Monitor+`tail -f` pattern could watch instead.
  Found and fixed two real bugs building it: Python buffers stdout when it isn't a TTY (a first
  version produced zero visible output for minutes of a real run — fixed with `python3 -u`); and a
  silent stall looked identical to a healthy long step with zero output either way — fixed with a
  ~60s heartbeat line. See `DECISIONS.md`'s addenda for the full writeup.
- **`keep_infra` renamed `keep_e2e_infra`, default flipped to `true`.** The old name didn't say what
  it kept at a glance; the new default (leave the e2e stack up after a run) is more useful for
  post-failure debugging than a clean teardown, matching every other new flag's opt-out shape
  (`--no-keep-e2e-infra`).
- **Flipping that default exposed a real, pre-existing gap it didn't cause:** the `e2e` step's own
  `deploy-and-run.sh` call passed no reset flag at all, unlike `run-all-tests.sh` (which always
  applies `--reset-only-db`, per `playwright/CLAUDE.md`'s "Always deploy with a clean database"
  rule). With the stack's containers/volumes now persisting by default across runs, a run right
  after this change failed `05-seed-filter-sort-pagination.spec.js`'s category-filter assertion
  (`expected "of 12"`, got `"1–20 of 69 records"`) — plausibly leftover data from an earlier run
  bleeding into the next one. Fixed with a new `reset_e2e_db` param (default `false`, CLI
  `--reset-e2e-db`): `e2e` always passes `--reset-only-db` by default, or the full `--reset` when
  asked. **Verified directly:** a full default run with `--reset-e2e-db` passed reached `e2e:
  succeeded` — the same category-filter assertion that failed before passed clean.
- Two genuine Docker-build stalls (not apt-get slowness — the log never even reached "sending build
  context", unlike every successful build today) hit while re-verifying these changes, one
  confirmed as a real hang (killed and retried, succeeded), one that turned out to have actually
  finished around the time it was killed (the process tree wasn't fully cleaned up, leaving a
  redundant concurrent build running briefly) — resolved by explicitly stopping `ci-runner`/
  `ci-runner-dagu-proxy` and removing the stray `advertisement-build-only` container rather than
  guessing which of several overlapping background attempts was the live one.

## Related

- `scripts/ci/DECISIONS.md` ADR-001 (Docker-outside-of-Docker design this would need to preserve),
  ADR-002 (the `progress.txt` mechanism this issue proposes replacing), ADR-008 (the
  `build_and_test` stage merge this issue's DAG would need to reflect).
- `improvement-152` Part A's "Grounding" section — the CI-vocabulary research (GitLab
  CI/Azure Pipelines/GitHub Actions stage/job terminology) this issue's own tool comparison
  continues.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a (not consulted this session)
- context_loading_consulted: no
- context_loading_matched: n/a
- flows_situation: continuing an in-progress, already-scoped infra/tooling implementation issue
- flows_chosen: direct tool use (Bash/Edit), not a specific command/skill
- flows_matched: no — flows.md's own recommendation for this shape of task is `/autopilot`; not
  used here since real mid-implementation discoveries (port conflict, browser-reachability bug,
  auth wizard, cache-cost complaint) each needed a user decision, which fit this session's
  incremental back-and-forth better than autopilot's single-approval-then-no-check-ins model

### Script/command runs
- `bash scripts/ci/run.sh --unit --no-docs --foreground` (multiple iterations while fixing the DAG
  file-path/working_dir/proxy/entrypoint issues) | duration_s=~180-400 each | mode=background+foreground | result=mix of fail→fail→pass as each real bug got fixed, final iterations pass
- `bash scripts/deploy-and-run.sh` (marketplace-app recovery after resource contention) | duration_s=~190 | mode=background | result=pass
- `bash scripts/ci/run.sh --unit --archunit-metrics --no-docs --foreground` (follow-up:
  pipeline_metrics/archunit_metrics steps, multiple iterations fixing the embedded-Python-in-YAML,
  docker-cp-from-inside-a-container, and skipped-dependency-propagation bugs) | duration_s=~400-600
  each | mode=background+foreground | result=fail (YAML parse) → fail (files not on host, silent)
  → pass (unit+archunit_metrics succeeded, pipeline_metrics skipped) → pass (all steps genuinely
  succeeded, artifacts confirmed fresh on host)
- Mid-session: user raised Docker Desktop's WSL2 memory allocation as a root cause of repeated
  resource contention (marketplace-app crash, exit-137 kills) — confirmed this sandbox's Docker
  engine shares the same memory pool as the host (`docker info`'s Total Memory matched
  `/proc/meminfo` exactly, before and after the change: 6.7GiB → 9.7GiB once the user edited
  `.wslconfig` and restarted WSL2). Real, verified fix, not a workaround — real headroom is now
  available for concurrent builds that previously required careful sequencing.
- `bash scripts/ci/run.sh --unit --archunit-metrics --no-docs --foreground` (final iteration, after
  the `dagRunDetails` JSON-key fix in `pipeline-metrics.py` and the `SKIP_VAADIN` addition) |
  duration_s=~330 total (build 108s, unit 124s, archunit_metrics 132s in parallel) | mode=background
  | result=pass — all steps genuinely succeeded (`unit`, `archunit_metrics`, `pipeline_metrics`),
  both artifact files confirmed fresh on host with real content, `marketplace-app` confirmed
  healthy (200) throughout.
- `bash scripts/ci.sh` (default, no flags — first full default run after today's fixes) |
  duration_s=~1560 (build ~130s, unit/integration/e2e/sonar/archunit_metrics in parallel, e2e the
  long pole) | mode=background+Monitor | result=partially_succeeded — build/unit/integration/
  archunit_metrics/pipeline_metrics succeeded, sonar failed (3 real pre-existing MINOR findings,
  legitimate quality-gate block, not a CI-tooling bug), e2e failed
  (`05-seed-filter-sort-pagination.spec.js` category-filter assertion — `expected "of 12"`, got
  `"1–20 of 69 records"`, motivating the `reset_e2e_db` fix above), docs failed (stale
  architecture-map, expected — not yet re-synced this session).
- `bash scripts/ci.sh --reset-e2e-db` (verification run, after the `reset_e2e_db`/`keep_e2e_infra`/
  `archunit_metrics`-default/`watch-run.py` fixes) | duration_s=~1740 (build ~150s, e2e the long
  pole at ~28 min) | mode=background+Monitor | result=partially_succeeded — build/unit/integration/
  archunit_metrics/pipeline_metrics/**e2e all succeeded** (the category-filter assertion that failed
  in the prior run passed clean against the fully-reset DB), sonar failed (same 3 pre-existing MINOR
  findings), docs failed (same stale architecture-map, still not re-synced). Two Docker image builds
  during this verification stalled at "sending build context" for well beyond normal (confirmed via
  `ps aux` CPU-time staying near-zero and no new image appearing) and were killed/retried; one
  retry raced with the original build actually finishing, briefly leaving a duplicate concurrent
  build running — cleaned up by stopping `ci-runner`/`ci-runner-dagu-proxy` and removing the stray
  `advertisement-build-only` container directly rather than guessing.
