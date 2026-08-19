# Local CI Runner

Dagu (https://github.com/dagucloud/dagu) is a single-binary, no-database DAG/workflow engine with a
built-in web UI — clickable "Start", live per-stage status/logs, run history. This project runs it
in one persistent `ci-runner` container to orchestrate the same checks a hosted CI would (compile,
unit tests, Testcontainers integration tests, Playwright e2e, optionally SonarQube and an ArchUnit
module-coupling export), isolated from the normal dev stack and safe to run alongside it.

## Requirements

- Docker Engine with a reachable daemon (this tool mounts `/var/run/docker.sock` into its own
  container — Docker-outside-of-Docker, not Docker-in-Docker; see `docs/ai/adr-index.md`)
- Everything else (Maven, Node, Playwright browsers, Dagu itself) is already inside the built
  image or downloaded on first container start — nothing extra to install locally

## Flow

Entry points: `scripts/ci.sh` (Linux/WSL) and `scripts/ci.bat` (Windows) — both thin delegators
into `scripts/ci/run.sh`, where the real logic lives. Once `ci-runner` is running, a DAG run can
also be triggered directly from Dagu's own web UI, bypassing `run.sh` entirely for that one step.

```mermaid
flowchart TD
    A1[ci.sh] --> B[run.sh]
    A2[ci.bat] --> B
    B --> B1{flags recognized?}
    B1 -->|no| Z0[print error, exit 1]
    B1 -->|yes| C{--sync-artifacts only?}
    C -->|yes| C1[docker cp metrics files onto host] --> Z1[exit 0]
    C -->|no| D{--no-rebuild?}
    D -->|no| E[docker build Dockerfile] --> E1{build succeeded?}
    E1 -->|no| Z4[exit non-zero]
    E1 -->|yes| F[start ci-runner container]
    F --> F1[poll Dagu web UI, up to 120s]
    F1 --> F2{came up?}
    F2 -->|no| Z5[exit 1]
    F2 -->|yes| G[start ci-runner-dagu-proxy sidecar]
    D -->|yes| H[reuse already-running ci-runner]
    G --> I[dagu start ci.yaml -- params]
    H --> I
    I --> J{--foreground?}
    J -->|yes| K[stream output, block until done] --> L[sync_artifacts] --> Z2[exit: 0 if PASSED, non-zero if FAILED]
    J -->|no| M[trigger detached] --> Z3[return immediately, exit 0 -- watch progress at :8082]
```

Once `ci-runner` is up, the same DAG run can be triggered a second way, entirely independent of
`run.sh`:

```mermaid
flowchart LR
    U[open http://localhost:8082] --> S["Start" button on the ci DAG] --> P[fill in params dialog] --> T[dagu executes ci.yaml]
```

**The UI path never picks up source changes made since the last `bash scripts/ci.sh` rebuild** —
`ci-runner` has no live view of the host filesystem (a bind mount doesn't work when the caller
invoking `docker run` is itself running inside a container, confirmed directly — see
`DECISIONS.md`). Re-run `bash scripts/ci.sh` after any code change before relying on the UI's
"Start" button again.

## Running

```bash
bash scripts/ci.sh                                   # default: most extensive run (unit +
                                                       # integration + e2e + sonar +
                                                       # archunit_metrics + docs)
bash scripts/ci.sh --unit                             # one stage only
bash scripts/ci.sh --unit --integration --e2e         # chosen stages
bash scripts/ci.sh --all --sonar                       # everything, spelled out explicitly
bash scripts/ci.sh --no-archunit-metrics                 # skip ArchUnit's module-coupling
                                                          # export (on by default)
bash scripts/ci.sh --playwright-args "e2e --ux"           # override the e2e stage's Playwright args
                                                           # (default when unset: "e2e --full --ux")
bash scripts/ci.sh --no-keep-e2e-infra                        # tear down the isolated e2e stack
                                                               # after the run (on by default,
                                                               # left up for debugging)
bash scripts/ci.sh --reset-e2e-db                              # full --reset (volume wipe) before
                                                               # e2e's deploy instead of the default
                                                               # --reset-only-db -- only needed when
                                                               # the DB schema itself changed
bash scripts/ci.sh --foreground                                 # block and stream output instead
                                                                  # of the background default
bash scripts/ci.sh --no-rebuild                                   # trigger a new run against the
                                                                    # already-running container
bash scripts/ci.sh --refresh-tools                                  # force re-download of
                                                                      # buildx/compose/dagu even if
                                                                      # already cached
bash scripts/ci.sh --sync-artifacts                                   # pull metrics files onto the
                                                                        # host without a new run
```

### Windows

```bat
scripts\ci.bat
```

## Live status, logs, and run history

`http://localhost:8082` — Dagu's own web UI, reached through a small `alpine/socat` proxy sidecar
(`ci-runner-dagu-proxy`) rather than directly, since `ci-runner` runs `--network host` (needed so
its own DAG steps reach sibling `ci-*` containers at plain `localhost:PORT`), and a
`--network host` container's bound ports aren't reachable from a real browser in this sandbox —
see `DECISIONS.md`. There is no per-run `scripts/ci/reports/<timestamp>/` tree or `progress.txt`
file anymore — Dagu's UI and run history (backed by the `ci-dagu-home` named volume) replace all
of that. `scripts/ci/reports/pipeline-metrics.json` and
`scripts/build-and-test/reports/architecture-metrics.json` still exist, but as two fixed-path
metrics files (not a timestamped report tree) synced onto the host by `--sync-artifacts` — see
"Running" above.

For a scripted/automated watch instead of the browser, `python3 -u scripts/ci/watch-run.py` polls the
same API for whichever `ci` run is newest, prints one line per step-status change, and exits once
the run reaches a terminal state — see its own header for the exact output/exit-code contract.

## Isolation

The e2e stage runs its own `ci-advertisement-db`/`ci-advertisement-minio`/`ci-marketplace-app`
containers on different host ports (15432/19000-19001/18081) and a separate Docker network
(`ci-advertisement`) from the persistent dev stack — safe to run concurrently with normal dev work.
They're left running after the run by default, for debugging (`--no-keep-e2e-infra` to tear them
down instead). Since the containers (and their DB/MinIO volumes) can persist across runs, the
stage's own deploy always applies `--reset-only-db` first (fast truncate — clears leftover data
from a prior run without touching the schema/migration history) unless `--reset-e2e-db` asks for a
full `--reset` instead (only needed when the DB schema itself changed since the stack was last
brought up). The
`integration` stage always applies the Testcontainers sandbox workaround internally (not a
toggleable flag) — `ci-runner`'s own Docker-outside-of-Docker nature hits the same
dynamically-assigned-port problem regardless of host machine.

## Can I develop and run the app/tests myself while this runs?

Yes — genuinely, not just "probably fine":
- Maven dependency caching uses `ci-m2-cache`, a Docker-managed named volume mounted only inside
  the container — completely separate from your own `~/.m2`. No shared state, no race.
  `integration-tests` always spins up its own ephemeral Postgres via Testcontainers regardless.
- The e2e stage's `ci-*` stack (see "Isolation" above) never touches the persistent dev stack's
  containers, ports, network, or database.

**The one real, observed limit is CPU/RAM contention, not data isolation.** Running the e2e stage
alongside a full dev stack (app + db + minio + `pw-runner`) *and* SonarQube simultaneously caused
genuine Playwright timeout flakiness on this project's own constrained sandbox — confirmed
directly, not theoretical. On a normal, less constrained dev machine this is much less likely to
bite, but it's a real resource limit, not a design flaw to "fix" — see `DECISIONS.md` for the full
writeup.

**Two `--e2e` runs at once will collide with each other** — the isolated stack's container/network
names (`ci-advertisement-db`, etc.) are fixed, not unique per run. Sequential CI runs, or one CI
run alongside normal dev work, are both fine; two concurrent e2e stages are not.

See `DECISIONS.md` for the full design rationale and `../CLAUDE.md`'s "Local CI Runner" section for
the project-wide script conventions this tool follows.
