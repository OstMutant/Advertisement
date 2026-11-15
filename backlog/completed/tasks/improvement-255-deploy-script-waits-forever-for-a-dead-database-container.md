# improvement-255: The deploy scripts wait forever for a database container that already died, and a Postgres volume of another major version is not detected

**Type:** bug (an unbounded wait with no output) and a missing early check
**Module:** `scripts/deploy-and-run/run.sh` (`wait_for_db`, `ensure_running`), `scripts/deploy-and-run/reset.sh` (two `pg_isready` loops), `scripts/deploy-and-run/README.md` and the headers of both scripts
**Priority:** 🟡 high — placed at the very top by the user; a run hangs silently for as long as nobody notices, with the cause one `docker logs` away.
**When:** independent, no blockers.

## Current state

Checked against the sources on 2026-11-13:

- `wait_for_db` (`scripts/deploy-and-run/run.sh:183-189`) loops `until docker exec "$DB_CONTAINER" pg_isready ...; do sleep 1; done`: no time limit and no check that the container exists or runs. `wait_for_seaweedfs` has a 60 s limit; `wait_for_app` has a 180 s limit and returns early when the container stopped.
- `ensure_running` (`run.sh:163-181`) starts or creates the container and does not look at it afterwards.
- `scripts/deploy-and-run/reset.sh` lines 67 and 78 repeat the same unbounded loop.
- Observed: `advertisement-db` (`postgres:18-alpine`) was created, started and died with exit code 1 within a second; the volume `advertisement_postgres_data` held PostgreSQL 15 data (`PG_VERSION` 15 at its root, a stale `postmaster.pid`). The run showed the infra step with no output for 15 minutes and never ended. The headers of `run.sh` and `docker-compose.db.yml` already say such a volume needs `--reset`; nothing checks it.
- The CI e2e stack calls the same `run.sh` with its own volume, so it has the same failure mode after any major upgrade.

## Why change

A script that waits on something that can no longer happen must stop and say why. The silent hang hides a one-line cause and costs the user the time until they look.

## Expected benefit

A dead database container ends the run within seconds with the container's last log lines and, for a volume of another major version, the instruction to run with `--reset`; no unbounded wait remains in the deploy scripts.

## Approach

1. In `wait_for_db` and the two loops of `reset.sh`: stop with an error when the container is not running (print `docker logs --tail 30` of it) and when a time limit passes, the same shape as `wait_for_app`. One helper in `scripts/utils/` shared by both scripts. Done 2026-11-13: `wait_for_db_ready` in `scripts/utils/wait-for-db.sh` (120 s limit, log tail, `--reset` hint), used by `run.sh` and both loops of `reset.sh`; `reset.sh` now also finds a container that already exited after `docker compose up`. Tested against a missing container, one that exits at once, a running one that never answers and a ready one (0 s, 0 s, 5 s with limit 4 s, 1 s); ShellCheck at error severity passes.
2. Optional early check of the volume before creating the container: read `PG_VERSION` from the volume with a throw-away container and compare its major version with the one of `POSTGRES_IMAGE`; on a mismatch stop with the cause and `--reset`. Trade-off: one more tiny container per start and a layout assumption (PostgreSQL 18 and later keep the version one directory deeper); the log tail of step 1 already names the cause without it. Done 2026-11-13: `check_db_volume_version` in `run.sh`, called from `start_infra` only when the volume exists and the DB container does not; tested on volumes with the pre-18 layout (stops), the 18 layout, an empty volume, the real volume and an image tag without a number (all pass or are skipped).
3. Update the headers and the group `README.md` (`infra-doc-standards`); test with a container that exits at once, a missing container and a running one.

## Closing note

Both approach items are done. `wait_for_db_ready` (`scripts/utils/wait-for-db.sh`) ends a run within seconds when the database container is not running or not ready within 120 s, printing its last log lines and the `--reset` hint, in `run.sh` and in both loops of `reset.sh`; `check_db_volume_version` stops a run before the container is created when the volume holds another PostgreSQL major version than the image. A real `deploy-and-run.sh --reset --dev-tools` and a full `ci.sh --foreground` run passed afterwards.

## Related

- The decision on the deploy startup detection (find it through `.claude/nav/adr-index.md`): `wait_for_app` is the pattern to copy.
- [improvement-086](../completed/tasks/improvement-086-postgres-major-version-bump.md) (completed): moved PostgreSQL to 18 and recorded that a volume of another major version is unusable.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none

### Script/command runs
- scripts/deploy-and-run.sh --reset --dev-tools | duration_s=n/a | mode=background | result=pass
- scripts/ci.sh --in-container --foreground | duration_s=4 | mode=background | result=fail
- scripts/ci.sh --foreground | duration_s=1444 | mode=background | result=pass

### Commands used
- /new-task | uses=1 | failed=0 | note=filed the task at the very top of the backlog after the template and the sources were checked
