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

## Decision and plan

Chosen (2026-11-10): one script, option 1 with option 3, containers by default, every other cleanup an opt-in scope named by what is lost; no `ci.sh` integration.

What exists on the host today (measured): seven cache volumes (`maven-cache`, `vaadin-cache`, `dev-shell-m2-cache`, `ci-maven-cache`, `ci-vaadin-cache`, `ci-m2-cache`, `ci-tools-cache`, about 2 GB), two jar volumes (`marketplace-app-jar`, `ci-marketplace-app-jar`), two test-report volumes, the dev and CI databases and object stores, four SonarQube volumes (about 750 MB, hold the analysis history and the issue states such as false-positive marks), `ci-dagu-home` (the CI run history), 22 unnamed volumes left by removed containers, and 14.5 GB of Docker build cache shared by the whole host.

Design:

- `scripts/remove-containers.sh` is replaced by `scripts/docker-clean.sh`, self-contained (no subdirectory, no delegation). Every scope has one array of names, defaults in the script, each overridable through an `Env` variable so the script can be tested on throw-away objects.
- Scopes, none implied by another except `--all`; no flag means containers only:
  - (default) the project containers (the dev and CI stacks, SonarQube, the runners, the build containers, the screenshot container).
  - `--caches` build caches and jar volumes that the next run rebuilds: only slower, nothing lost.
  - `--reports` the two test-report volumes: past reports lost.
  - `--data` dev and CI databases and object stores (`advertisement_*`, `app_*`, `ci_advertisement_*`): data lost, the dev data is re-seeded by Liquibase.
  - `--sonar` the four SonarQube volumes: analysis history and issue states (false-positive marks) lost.
  - `--ci-history` `ci-dagu-home`: CI run history lost.
  - `--orphans` unnamed volumes no container uses (`docker volume ls -f dangling=true`, names that are 64 hex characters); named volumes outside every list are reported, never removed.
  - `--builder-cache` the Docker build cache (`docker builder prune`): host-wide, shared with other projects, so a scope of its own and never part of `--caches`.
  - `--all` every scope above.
- Every scope that is not a pure cache (`--reports`, `--data`, `--sonar`, `--ci-history`, `--orphans`, `--builder-cache`, `--all`) prints the sizes it would free and then needs the typed word `DELETE` on a terminal, or `--yes` when there is none; a non-interactive run without `--yes` refuses. `--dry-run` prints the plan and removes nothing, whatever the scopes.
- Order: containers first (a volume in use cannot be removed), then volumes, then the builder cache. A volume still in use is reported as kept, not as an error, and a name that does not exist is skipped.
- Guards before any removal: a protected-name list whose default is the working session's container, a refusal to touch any container that mounts the Docker socket together with `/app` (the session recognises itself by what it mounts, since its own id is not visible from inside), and a refusal to remove any volume a protected container mounts.
- Output: one table per scope with name, size and verdict (removed, kept protected, kept in use, kept not a project object), then the total freed.
- The owners keep their own narrow resets (`deploy-and-run.sh --reset`, `ci.sh --reset-e2e-stack`, `ci.sh --reset-ci-cache`, `build-and-test.sh` cache reset); this script is the union for a clean host, not a replacement.
- Known cost: the volume names are listed here as well as in the scripts that create them. The "kept, not a project object" group makes a renamed or new volume visible instead of silently missed.
- Documentation in the same change: the script's own header (`infra-doc-standards`), its row in `scripts/README.md`, `INFRASTRUCTURE.md` if it lists the scripts, a row in `.claude/nav/flows.md` if its mechanism table covers project scripts.
- Verification: ShellCheck; `--dry-run --all` on the real host (nothing removed, the session container and its volumes listed as protected); real runs of every scope against throw-away containers and volumes through the name overrides, including a container that mounts the socket and `/app` (must survive) and a volume mounted by a running container (must be kept); the confirmation refusal without `--yes`. No real project container or volume is removed in testing.

Done 2026-11-10: `scripts/docker-clean.sh` written as planned, the session container is recognised by a host-bound `/app` together with the host-bound Docker socket (a volume at `/app`, as the application containers have, does not count). ShellCheck clean; `--dry-run --all` on the real host removed nothing and listed the session container as protected; the throw-away test (every scope, the refusal without `--yes`, the typed `DELETE` on a pseudo-terminal, a surviving container's volume kept) passed all checks. Not exercised on real data: the `--builder-cache` removal, which is host-wide.

Added at the user's request, 2026-11-10:

- `scripts/docker-clean.bat`, the Windows entry point: finds the path and runs the script through WSL, the same shape as `reset.bat`. Not run on a Windows shell from here; checked for ASCII-only text and CRLF line endings.
- The command `/docker-clean` (`.claude/commands/docker-clean.md`): shows the `--dry-run` plan for the flags the user named, asks a direct question naming what is lost, runs with `--yes` only after the yes. Documented in `.claude/commands/README.md`, the slash-command list of the root `CLAUDE.md` and a row of `.claude/nav/flows.md`; `.claude/rules/scripts.md` has a short section on cleaning the host; `scripts/README.md` lists the `.sh` / `.bat` pair. `INFRASTRUCTURE.md` lists no per-script rows for cleanup, so it is unchanged.
- Nothing calls the script automatically; it runs only by hand or through the command. A `ci.sh` flag for it stays out of scope.
- Still to do in this task before saving: `/sync-docs` over the uncommitted changes and `/deep-review` of the three new files, their findings handled by the user's decision; then the task closes and moves to `completed/tasks/`.

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
