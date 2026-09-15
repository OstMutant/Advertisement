# improvement-187: `activity-monitor.sh --in-container` — run standalone scripts inside a dedicated container so their live state is visible regardless of which WSL2 distro/shell triggered them

**Type:** improvement (tooling/infrastructure — Claude Code's own ability to observe a standalone
script run, not a marketplace-app feature)
**Module:** scripts/activity-monitor, scripts/ci, scripts/utils
**Priority:** medium (real, recurring friction this session — every standalone `deploy-and-run.sh`/
`playwright.sh` run triggered by the user was invisible to the agent)
**When:** independent, no blockers

## Current state (investigated 2026-09-12)

`scripts/activity-monitor.sh` writes its live step checklist (`tree.txt`/`raw.log`/`exit_code`) to
`/tmp/activity-monitor/<script>/`. When the agent (this Claude Code session) runs a wrapped command
itself, this works fine. When the **user** runs the identical command in their own terminal
(PowerShell invoking `bash <script>` on Windows, which resolves through WSL2), the agent cannot see
that state at all — confirmed, not assumed, across a long real investigation this session:

- The agent's own shell is a different WSL2 distro (`docker-desktop`, Docker Desktop's own utility
  VM) than the user's real default distro. `/tmp` is local to each distro/VM — never shared.
- `/app` (the git repo) **is** shared — both sides mount the same Windows `D:\Ost\dev\Advertisement`
  drive (confirmed via matching `git remote`), so code edits sync both ways. But it's mounted via
  9p/DrvFs, which has a real, documented WSL2 kernel-level caching bug (`statx()` "path cache
  poisoning" — the exact same class of issue already filed against Claude Code itself,
  [anthropics/claude-code#28015](https://github.com/anthropics/claude-code/tasks/28015)): content
  written by a process on one side can read back stale on the other side, sometimes indefinitely.
  Reproduced directly: `scripts/logs/playwright/run.log`, itself already under the shared `/app`,
  stayed frozen at an old run's content for over an hour while a real, currently-running Playwright
  suite on the user's side was actively writing to it.
- `mkdir` for a **new** directory on the DrvFs-mounted path can additionally fail outright
  (`Permission denied`) unless the WSL2 distro's own `/etc/wsl.conf` has `[automount] options =
  "metadata"` set — confirmed directly (the first attempt at this fix, moving
  `activity-monitor.sh`'s own base directory from `/tmp` to `scripts/logs/activity-monitor/` under
  `/app`, broke the user's real `deploy-and-run.sh` run with exactly this `mkdir` error, and was
  reverted).

**Dead ends, tried for real, not assumed:**
- Docker Desktop bind-mount of the host path from a throwaway container (`docker run -v
  "D:\...:/host" alpine cat ...`) — does **not** bypass the cache, because the agent's own shell
  already runs *inside* the `docker-desktop` VM (the same one hosting the Docker Engine), so a
  container's bind-mount resolves through the exact same DrvFs mount the agent's shell already has
  — there is no genuinely separate access path available from this vantage point.
- `dd iflag=direct` (O_DIRECT, bypassing the ordinary Linux page cache) — read back the identical
  stale bytes; the staleness lives in the 9p client/protocol layer, not the page cache O_DIRECT
  addresses.
- A targeted `umount`/`mount -o remount` of just `/app` (9p cache is documented to flush on
  unmount) — blocked: `must be superuser to unmount`, even as root, since the agent's own sandbox
  lacks the `CAP_SYS_ADMIN` capability needed for any mount-table change, independent of the WSL2/
  DrvFs question entirely.
- Disabling glibc's `statx()` in favor of the older `stat()` syscall (the specific syscall
  implicated in the poisoning bug) — no documented environment-variable or tunable found for this.

The one fix that would genuinely close the gap (`/etc/wsl.conf`'s `metadata` option +
`wsl --shutdown`) is entirely client-side, on the user's own machine — nothing on the repo/agent
side can apply or verify it.

## Why change

Every time the user runs a script standalone (outside `bash scripts/ci.sh`) to show the agent a
real failure, the agent is currently blind to it — the user must manually paste log content, which
is exactly the friction `activity-monitor.sh` exists to remove for the *agent's own* triggered runs
already.

## Expected benefit

Live visibility into a standalone run's progress and result, regardless of which shell/distro the
human actually typed the command in — closing the one gap `activity-monitor.sh` doesn't already
cover.

## Approach (decided, being implemented this session)

Docker daemon state (containers, `docker exec`, volumes) is the one channel proven fully reliable
all session, regardless of which side of the WSL2 boundary triggered it — every `ci-runner`/Dagu
check this session, including ones the user triggered independently, read back live and accurate.
The fix routes standalone runs through that same channel instead of the host filesystem:

1. **`scripts/utils/sync-source.sh`** (done) — the `git ls-files | tar` working-tree-into-container
   pipe, extracted out of `scripts/ci/run.sh` into a shared, reusable function
   (`sync_source_into <container>`), so both `ci.sh` and this new mode share one implementation
   instead of two copies. `scripts/ci/run.sh` refactored to call it — behavior unchanged, verified
   below.
2. **`scripts/utils/ensure-dev-shell.sh`** (done) — starts a **separate** "dev-shell" container,
   deliberately not a second use of `ci-runner` itself: an ad-hoc exec sharing `ci-runner`'s own
   container would race a real `ci.sh` DAG run's own container lifecycle/working-tree sync,
   colliding on the same `/app` and the same fixed-name e2e stack — the exact "concurrent runs
   collide" failure mode `ci.sh` itself already had to fix once (see `.claude/nav/adr-index.md`).
   `dev-shell` reuses the `ci-runner` image tag (same Java/Maven/Docker-CLI toolset, no second
   Dockerfile to maintain), overrides the image's own `ENTRYPOINT` (which would otherwise start a
   second competing Dagu server), and mounts its own dedicated `dev-shell-m2-cache` volume.
   - **Idle cleanup (done, verified for real):** `dev-shell` runs with `--rm` plus an internal
     watchdog loop (checks a last-used timestamp file inside the container every 60s, self-exits
     past a ~1h idle default, container is then auto-removed by `--rm`); every real use touches
     that timestamp file so an active session's idle clock keeps resetting. Verified directly with
     a short override (`DEV_SHELL_IDLE_SECONDS=15` against a throwaway container name): the
     container self-terminated and was auto-removed by Docker roughly 65s later (one watchdog poll
     past the idle threshold) — confirmed via repeated `docker inspect`, not assumed.
3. **`scripts/activity-monitor.sh --in-container`** (done) — new flag on the common entry
   point (not `ci.sh`-specific), e.g.:
   ```
   bash scripts/activity-monitor.sh --in-container -- bash scripts/deploy-and-run.sh --reset-only-db
   ```
   Sources (1)+(2), syncs the working tree into `dev-shell`, then `docker exec dev-shell bash
   scripts/activity-monitor.sh -- <original command>` — **not** detached, so the human's own
   terminal still redraws the live checklist exactly as today; the only change is that `tree.txt`/
   `raw.log` now physically live inside the container's own filesystem (pure Docker-daemon state,
   zero DrvFs/9p involvement), readable via `docker exec dev-shell cat ...` regardless of which
   shell/distro triggered the run. Default (no flag) behavior is unchanged.
4. Verified `scripts/ci/run.sh`'s own sync-source refactor introduced no regression: a
   `--docs-only --foreground` smoke run passed end to end, and every regenerated docs artifact
   landed fresh on the host with a timestamp matching that run. Found and fixed one adjacent,
   pre-existing bug while verifying this: `sync_artifacts()` unconditionally tried to copy Sonar's
   `report.html` even when the sonar stage never ran (e.g. `--docs-only`), misreporting the whole
   run as a failed artifact sync and misattributing the cause to the unrelated docs trio — fixed by
   tracking that copy separately and only counting it as a real failure when this run's own sonar
   stage actually executed.
5. Documented the new mode (`scripts/activity-monitor/README.md`'s new "Running in a container"
   section, `.claude/rules.md`'s script-running step, `.claude/commands/activity-monitor.md`).
6. Checked for leftover test containers/volumes from development — none found; the only
   container/volume the work created (`dev-shell`, `dev-shell-m2-cache`) are the intended,
   persistent, self-cleaning resources this feature adds, not test debris.
7. **Validation table** (in `scripts/activity-monitor/README.md`) — every wrapped script run both
   locally (no flag) and via `--in-container`, real results, not assumed: `deploy-and-run.sh`,
   `build-and-test.sh`, `playwright.sh`, `sonar.sh`, `reset.sh`, `run-all-tests.sh` all verified
   working both ways; `ci.sh` marked N/A for the distinction since it already always runs its own
   work inside its own container regardless of this flag.

**Real bug found and fixed during validation, not in the original plan:** `run-all-tests.sh`
mirrored `build-and-test.sh`'s and the deploy+playwright branch's live output into a shared
reports container over a long-held `docker exec -i ... < fifo` connection kept open for the whole
run (minutes). Run enough concurrent `--in-container` attempts (three parallel heavy Docker
workloads sharing one socket) and that connection occasionally drops mid-stream — killing the
process piping into it with `SIGPIPE` (exit 141) and failing the *real* test run, not just its log
mirror; confirmed by direct comparison (identical scope passed cleanly with no flag, failed roughly
1 run in 4 with `--in-container`) and by isolating the exact failure point in the raw log. Fixed by
having both branches write to a plain local file first (`tee`, never able to SIGPIPE upstream) and
mirroring the *completed* file into the shared container afterward as a single best-effort copy —
so a mirror hiccup can no longer touch the real result. Verified with 4 consecutive clean
`--in-container` runs post-fix (plus a local-mode regression check, still passing).

## Noted, not in this item's scope (2026-09-12)

User flagged that `activity-monitor`'s live tree rendering should stay human-readable/informative
for every wrapped script, not just `ci.sh` (already considered fine) — no concrete example of a
confusing render given yet. Deferred: needs a specific case to act on, not folded into this item's
own scope (which is about *where* a script executes, not the tree-rendering format itself).

## Related

- [anthropics/claude-code#28015](https://github.com/anthropics/claude-code/tasks/28015) — the
  same statx/9P cache-poisoning bug class, filed against Claude Code's own Write/Edit tools.
- `scripts/ci/DECISIONS.md` — existing ADRs on the DooD design and the "concurrent runs collide"
  fix `dev-shell`'s own separateness deliberately mirrors.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: standalone script run invisible to the agent when triggered from a different shell/WSL2 distro
- flows_chosen: direct implementation (no applicable command/skill covers a new activity-monitor run mode)
- flows_matched: n/a

### Script/command runs
- scripts/ci.sh --docs-only --foreground | duration_s=217 | mode=foreground | result=pass
- scripts/activity-monitor.sh --in-container -- scripts/build-and-test.sh | mode=background | result=pass
- scripts/activity-monitor.sh --in-container -- scripts/deploy-and-run.sh --reset-only-db | mode=background | result=pass
- scripts/activity-monitor.sh --in-container -- scripts/playwright.sh 01-marketplace-empty-flow --ux | mode=background | result=pass
- scripts/activity-monitor.sh --in-container -- scripts/sonar.sh --no-gate | mode=background | result=pass
- scripts/activity-monitor.sh --in-container -- scripts/reset.sh | mode=background | result=pass
- scripts/activity-monitor.sh --in-container -- scripts/run-all-tests.sh (narrow scope) | duration_s=254 | mode=background | result=fail (pre-fix, SIGPIPE)
- scripts/activity-monitor.sh -- scripts/run-all-tests.sh (narrow scope, local) | duration_s=254 | mode=background | result=pass
- scripts/activity-monitor.sh --in-container -- scripts/run-all-tests.sh (narrow scope) x4 post-fix | mode=background | result=pass (all 4)
- scripts/activity-monitor.sh -- scripts/run-all-tests.sh (narrow scope, local, post-fix regression check) | duration_s=247 | mode=background | result=pass
- dev-shell idle-watchdog end-to-end check (DEV_SHELL_IDLE_SECONDS=15 override) | duration_s=~65 | mode=foreground | result=pass
