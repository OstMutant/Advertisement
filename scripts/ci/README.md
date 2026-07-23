# Local CI Runner

One CI-runner container that runs the same checks a hosted CI would (compile, unit tests,
Testcontainers integration tests, Playwright e2e, optionally SonarQube), isolated from your normal
dev stack and safe to run alongside it.

## Requirements

- Docker Engine with a reachable daemon (this tool mounts `/var/run/docker.sock` into its own
  container — Docker-outside-of-Docker, not Docker-in-Docker; see `DECISIONS.md` ADR-001)
- Everything else (Maven, Node, Playwright browsers) is already inside the built image or in
  sibling containers this tool manages itself — nothing extra to install locally

## Running

```bash
bash scripts/ci.sh                                   # default: most extensive run (unit +
                                                       # integration + e2e + sonar; e2e uses
                                                       # "e2e --full --ux"), runs in the background
bash scripts/ci.sh --unit                             # one stage only
bash scripts/ci.sh --unit --integration --e2e         # chosen stages
bash scripts/ci.sh --all --sonar                       # everything, spelled out explicitly
bash scripts/ci.sh --playwright-args "e2e --ux"         # override the e2e stage's Playwright args
                                                         # (default when unset: "e2e --full --ux")
bash scripts/ci.sh --report-dir /some/path               # write reports somewhere else
bash scripts/ci.sh --keep-reports 5                        # keep the last N report dirs
                                                             # (default: 3; 0 = never prune)
bash scripts/ci.sh --keep-infra                              # leave the isolated e2e stack
                                                               # running after the run (debugging)
bash scripts/ci.sh --sandbox                                  # this claude-dev sandbox's
                                                                # Testcontainers workaround --
                                                                # not needed on a real dev machine
bash scripts/ci.sh --foreground                                 # block and stream output instead
                                                                  # of the background default
```

### Windows

```bat
scripts\ci.bat
scripts\ci\run.bat --all --sonar
```

## Checking on a background run

A bare `bash scripts/ci.sh` returns control within seconds (after the image build) and prints:

```
Running in background (PID 12345).
Progress:  scripts/ci/reports/20260716-144808/progress.txt
Full log:  scripts/ci/reports/20260716-144808/run.log
```

Check in anytime, no need to attach to anything:

```bash
cat scripts/ci/reports/20260716-144808/progress.txt
```

```
CI run: 20260716-144808
Started: 2026-07-16 14:48:42
Elapsed: 76s

[x] unit           38s  PASSED
[x] integration    37s  PASSED
[>] e2e             ..  RUNNING (started 14:49:58)
[ ] sonar            ..  PENDING
```

## Reports

Each run gets its own timestamped directory under `scripts/ci/reports/` (gitignored):

```
scripts/ci/reports/<timestamp>/
├── progress.txt              live status while running, final PASSED/FAILED summary after
├── run.log                   full raw output
├── unit-tests/                run.log + surefire XML/txt per test class
├── integration-tests/          run.log + surefire XML/txt per test class
├── playwright/                  index.html (open in a browser) + screenshots/error-context
└── sonar/                        report.html
```

Older report directories are pruned automatically after each run, keeping the last 3 by default
(`--keep-reports N` to change).

## Isolation

The e2e stage runs its own `ci-advertisement-db`/`ci-advertisement-minio`/`ci-marketplace-app`
containers on different host ports (15432/19000-19001/18081) and a separate Docker network
(`ci-advertisement`) from the persistent dev stack — safe to run concurrently with normal dev work.
They're torn down automatically after the run (`--keep-infra` to leave them up for debugging).
`unit`/`integration` stages need no isolation of their own: unit tests touch no Docker at all, and
Testcontainers-based integration tests already always spin up their own ephemeral Postgres per run.

## Can I develop and run the app/tests myself while this runs in the background?

Yes — genuinely, not just "probably fine":
- The `ci-runner` image is built from a **frozen snapshot** of the source tree (`COPY . .` at
  `docker build` time). Editing files on the host after a run has started has zero effect on that
  run — it's already testing the snapshot it was given. To test new edits, wait for the current run
  to finish (or `docker rm -f` it) and start a new one.
- Maven dependency caching uses `ci-m2-cache`, a Docker-managed named volume mounted only inside
  the container — completely separate from your own `~/.m2`. No shared state, no race.
  `integration-tests` always spins up its own ephemeral Postgres via Testcontainers regardless.
- The e2e stage's `ci-*` stack (see "Isolation" above) never touches the persistent dev stack's
  containers, ports, network, or database.

**The one real, observed limit is CPU/RAM contention, not data isolation.** Running the e2e stage
alongside a full dev stack (app + db + minio + `pw-runner`) *and* SonarQube simultaneously caused
genuine Playwright timeout flakiness on this project's own constrained sandbox (6.7 GB RAM total) —
confirmed directly, not theoretical. On a normal, less constrained dev machine this is much less
likely to bite, but it's a real resource limit, not a design flaw to "fix" — see `DECISIONS.md`
ADR-005 for the full writeup.

**Two `--e2e` runs at once will collide with each other** — the isolated stack's container/network
names (`ci-advertisement-db`, etc.) are fixed, not unique per run (unlike the `ci-runner-<timestamp>`
container itself). Sequential CI runs, or one CI run alongside normal dev work, are both fine; two
concurrent e2e stages are not.

See `DECISIONS.md` for the full design rationale and `../CLAUDE.md`'s "Local CI Runner" section for
the project-wide script conventions this tool follows.
