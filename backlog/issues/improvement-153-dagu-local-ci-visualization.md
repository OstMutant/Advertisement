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

**Dagu** (https://github.com/dagu-org/dagu) — a single-binary, no-database DAG/workflow engine
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

## Open design questions (not decided yet)

- Whether Dagu runs as an always-on persistent container (started once, left running) or is
  started on demand alongside `ci-runner`.
- Exact YAML DAG shape for the existing stages and how `--sandbox`/other current `scripts/ci.sh`
  flags map to DAG-level parameters.
- Whether Dagu still needs `/var/run/docker.sock` mounted directly (Docker-outside-of-Docker, same
  as `ci-runner` today, see ADR-001) to spin up the same isolated `ci-*` sibling containers, or
  whether it wraps the existing `scripts/ci.sh` as one opaque DAG step instead of decomposing into
  individual per-stage DAG nodes.
- How Dagu's own state/run history persists across sandbox restarts — likely needs its own named
  Docker volume, same pattern already used for `maven-cache`.

## Related

- `scripts/ci/DECISIONS.md` ADR-001 (Docker-outside-of-Docker design this would need to preserve),
  ADR-002 (the `progress.txt` mechanism this issue proposes replacing), ADR-008 (the
  `build_and_test` stage merge this issue's DAG would need to reflect).
- `improvement-152` Part A's "Grounding" section — the CI-vocabulary research (GitLab
  CI/Azure Pipelines/GitHub Actions stage/job terminology) this issue's own tool comparison
  continues.
