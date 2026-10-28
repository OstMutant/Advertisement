# improvement-220: Deploy script skips the SeaweedFS bucket setup when the container already exists

**Type:** bug — local dev stack tooling
**Module:** `scripts/deploy-and-run/run.sh`
**Priority:** low — Top, small and cheap; filed so a half-failed first start does not silently leave the stack without its bucket
**When:** independent

## Current state

Read on 2026-10-21. `start_infra` in `scripts/deploy-and-run/run.sh` sets `seaweedfs_was_running=true` when the SeaweedFS container merely exists (`docker container inspect` succeeds, not a check of its state), starts or creates the container, and then runs `configure_seaweedfs` only when that variable is false, that is only on the very first creation. `configure_seaweedfs` creates the bucket with `s3.bucket.create` and already tolerates an "already exists" answer, so it is safe to repeat. When it fails on the first run, the script exits through its error trap, the container and its volume stay, and every later run skips the setup: the stack comes up without the bucket and uploads fail at runtime. `--restart-infra` and `--reset` remove the container first, so those paths do run the setup again.

## Why change

The condition decides on whether the container exists, not on whether the bucket exists, and the variable name says something else than what it tests. A failed first start is a narrow case, but it is exactly the kind a deploy script must recover from by itself.

## Expected benefit

A stack whose first start was interrupted heals on the next run instead of failing later in the application.

## Approach

1. Run `configure_seaweedfs` on every start, with no condition. It is idempotent and costs one short `docker run`; remove the `seaweedfs_was_running` variable.
2. Alternative: ask SeaweedFS whether the bucket exists (`s3.bucket.list`) and create it only when missing. More code for the same outcome, so not preferred.

## Related

- The deploy documentation in `.claude/rules/scripts.md` (infrastructure step).

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
