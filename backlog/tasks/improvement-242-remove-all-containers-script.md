# improvement-242: A script that removes every project container and never the container of the working session

**Type:** improvement — a manual step done by hand with raw `docker` commands has no script
**Module:** `scripts/` (a new script group, name open), `scripts/deploy-and-run/`, `scripts/ci/`
**Priority:** 🟡 high, at the top at the user's request
**When:** independent, no blockers

## Current state

Checked on 2026-11-08 in the Docker host of this repository:

- No project script removes the running containers of the dev stack, the CI stack and SonarQube in one go. `deploy-and-run.sh --prune-all` removes stopped containers and unused volumes host-wide and stops no running one; `deploy-and-run.sh --reset` wipes the dev DB and SeaweedFS volumes; `ci.sh --reset-e2e-stack` wipes the isolated e2e stack's volumes; `ci.sh --reset-ci-cache` wipes the CI build caches.
- To start the CI on fresh containers the ten containers `marketplace-app`, `ci-marketplace-app`, `arch-map-shot`, `advertisement-seaweedfs`, `advertisement-db`, `ci-advertisement-seaweedfs`, `ci-advertisement-db`, `ci-runner-dagu-proxy`, `ci-runner` and `sonarqube` were removed by hand with `docker rm -f` and their names typed out; volumes stayed.
- The container `claude-dev` runs next to them. It mounts `/app`, `/root/.claude` and `/var/run/docker.sock` and its entrypoint is `claude`, so it is the working session itself; removing it ends the session. Nothing in the repository marks it as protected, and `hostname` and `/proc/self/cgroup` inside the session do not show its container id, so the session cannot recognize its own container that way.

## Why change

- A list of names typed by hand goes stale as soon as a container is renamed or added, and a `docker rm -f $(docker ps -aq)` removes the session's own container.
- The project rule says to use project scripts and not raw `docker` commands; for this step there is none.

## Expected benefit

- One repeatable command to bring the host to a clean state before a full CI run, with the session's container protected by construction and a printed list of what is removed and what is kept.

## Approach

Options, the choice is open:
1. A script in `scripts/` that removes containers named in a list read from the places that already define them (the compose files, the CI and e2e container names), keeping a protected list whose default is `claude-dev`.
2. The same script selecting by label or compose project instead of by name; needs labels on containers that today have none or are started by several scripts.
3. A control mode (`--dry-run`) that only prints the containers it would remove and the ones it would keep, so the result can be checked before anything is removed; useful with either option.

Open questions to settle before writing: how the script learns which container is the working session (a name, a label, or the mount of `/var/run/docker.sock` together with `/app`), whether volumes stay untouched as in this manual run (the current manual run kept them), and whether `ci.sh` should call it behind a flag.

## Related

- `deploy-and-run.sh --prune-all`, `--reset` and `ci.sh --reset-e2e-stack`, `--reset-ci-cache`: the existing, narrower cleanups listed above.
- [improvement-241](../completed/tasks/improvement-241-database-refactoring.md): its test runs and the full CI run that prompted this task.

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
