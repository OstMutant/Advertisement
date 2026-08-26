# improvement-162: `docs/architecture/` directory reorganization — architecture-doc.sh relocation + data/ split

**Type:** improvement
**Module:** `docs/architecture/architecture-doc.sh`/`.bat` (moved from `scripts/`),
  `docs/architecture/*`, `docs/architecture/scripts/generate-architecture-model.sh`,
  `docs/architecture/scripts/check-architecture-model-freshness.sh`,
  `.claude/nav/scripts/check-hardcoded-counts.sh`, `.claude/commands/sync-docs.md`, `scripts/README.md`
**Priority:** Top 🟡
**When:** independent, no blockers

## Background

Filed as a scope-TBD placeholder with two candidate directions. Both are now resolved:

1. ~~Root `scripts/*.sh`/`*.bat` `infra-doc-standards` rollout~~ — **done**, confirmed via repeated
   `infra-doc-standards` skill runs over `scripts/` this same session (all 20 root files' headers
   verified accurate by 3 independent fresh-agent reviews; `scripts/README.md` fully rewritten;
   real bugs found and fixed along the way — stale paths in `collect-code.bat`, hardcoded
   sandbox-only `/app/...` paths in `playwright.bat`/`architecture-doc.bat`). Not this issue's
   concern going forward.
2. The generator-refactor direction narrowed, through direct discussion, into a concrete,
   scoped structural reorganization (below) — not the originally-speculated full heredoc HTML/CSS
   rewrite of `generate-architecture-model.sh`, which stays out of scope here.

## Problem

`scripts/architecture-doc.sh`/`.bat` is a root-level `scripts/` entry point whose entire job is
delegating into `docs/architecture/scripts/generate-architecture-model.sh` — a different directory
tree, structurally unrelated to `scripts/`'s own operational (build/deploy/test) family. Unlike
`playwright.sh` (a genuine root-level entry point for an operational sibling directory),
`docs/architecture/scripts/` is already its own separate, established documentation tree (its own
`SCRIPT_GROUP_DIRS` card in the architecture-map generator, alongside `.claude/nav/scripts/`) — so
keeping its entry point artificially in `scripts/` doesn't fit the same pattern. Confirmed the real,
documented invocation path (`.claude/commands/sync-docs.md`) already calls
`bash docs/architecture/scripts/generate-architecture-model.sh` directly, bypassing
`scripts/architecture-doc.sh` entirely — the root-level wrapper is not the actual canonical
entry point in practice.

Separately, `docs/architecture/` itself mixes its one real human-facing output
(`architecture-map.html`) at the top level with 4 generator-internal/input files
(`architecture-model.json`, `README.md`, `runtime-notes.md`, `arch-embed-index.md`) — no separation
between "the deliverable" and "the data that produces it."

## Fix — applied

1. **Moved** `scripts/architecture-doc.sh` → `docs/architecture/architecture-doc.sh`,
   `scripts/architecture-doc.bat` → `docs/architecture/architecture-doc.bat` — **one level above**
   `docs/architecture/scripts/`, alongside `architecture-map.html` (the two things a human directly
   interacts with: the deliverable and the "regenerate it" entry point), not inside `scripts/`
   alongside the generator's own internal logic. Internal calls to `scripts/generate-architecture-model.sh`/
   `scripts/check-architecture-model-freshness.sh`/`scripts/screenshot-architecture-map.sh` resolve
   via `dirname "$0"/scripts/...`.
2. **Created** `docs/architecture/data/`, moved `arch-embed-index.md`, `architecture-model.json`,
   `README.md`, `runtime-notes.md` into it. `architecture-map.html` and `architecture-doc.sh`/`.bat`
   are now the only two top-level entries in `docs/architecture/`; `scripts/` (the generator's own
   logic) stays at `docs/architecture/scripts/`, unmoved.
3. **Updated every real reference** to the 4 relocated data files' old paths:
   - `docs/architecture/scripts/generate-architecture-model.sh` (13 occurrences + the now-obsolete
     "architecture-doc.sh has no matching subfolder" counterexample comment's stale path + removed
     `architecture-doc.sh`/`.bat` from `SCRIPT_GROUP_FILE_ORDER["scripts"]` — they were briefly
     added to `["docs/architecture/scripts"]` during an intermediate wrong-location attempt, then
     removed again once the correct one-level-up location was clarified)
   - `docs/architecture/scripts/check-architecture-model-freshness.sh` (8 occurrences)
   - `.claude/nav/scripts/check-hardcoded-counts.sh` (1 occurrence, an exclusion pattern)
   - `.claude/commands/sync-docs.md` (3 mentions)
   - `scripts/README.md` (removed the `architecture-doc.sh` / `.bat` Entry points row entirely —
     no longer a `scripts/` entry point at all, at either location)
4. **Not touched**: `docs/architecture/scripts/DECISIONS.md` (append-only historical record, old
   paths stay as written) and every `backlog/completed/` mention (same reason).
5. Regenerate `architecture-model.json`/`architecture-map.html` via the relocated
   `architecture-doc.sh` afterward, to verify the new layout end to end.

**Known gap, not fixed here (flagged, needs a design decision):** `docs/architecture/` itself is
not a `SCRIPT_GROUP_DIRS` entry in `generate-architecture-model.sh` (only
`docs/architecture/scripts` and `.claude/nav/scripts` are) — so `architecture-doc.sh`/`.bat`'s own
headers, now living at `docs/architecture/` directly, render nowhere in `architecture-map.html`'s
self-documentation. Whether `docs/architecture/` should become its own tracked card (and if so,
whether `architecture-map.html`/the `data/` files belong alongside it) is a follow-up design
question, out of scope for this mechanical move.

## `scripts/claude.bat` — reuse-or-create the dev container (2026-08-20)

Separate finding from the same session, tracked here per explicit user direction rather than a new
issue. `scripts/claude.bat` always did `docker rm -f claude-dev` then `docker run` unconditionally
on every invocation — destroying and recreating the dev container every time, contradicting this
project's own "self-healing" convention (`scripts/README.md`: reuse a running container, only
create when genuinely missing).

**First attempt (2026-08-20, reverted the same session):** introduced a new `scripts/claude.sh` and
rewrote `claude.bat` as a thin WSL delegator, matching the pattern every other root `.bat` uses.
**This broke the script on a real machine** — confirmed directly (`wsl wslpath -u
"...\claude.sh"` resolved to a `/mnt/wsl/docker-desktop-bind-mounts/...` path instead of a normal
`/mnt/d/...` one, and `wsl bash` couldn't find the file there). Root cause: unlike every other root
`.bat`, the original `claude.bat` never depended on WSL at all — it called `docker` directly from
`cmd.exe` against Windows Docker Desktop's own CLI. Introducing a WSL hop added a whole new failure
surface (WSL distro/path resolution) that the original design deliberately had zero dependency on.
Reverted: `scripts/claude.sh` deleted, `scripts/README.md`'s Entry points row and
`generate-architecture-model.sh`'s `SCRIPT_GROUP_FILE_ORDER["scripts"]` both reverted to their
pre-this-issue state (`claude.bat` = self-contained, no delegation).

**Fix — applied for real, self-contained, no WSL:**

`scripts/claude.bat` stays a single file, still calling `docker` directly from `cmd.exe` exactly as
it always did — only the reuse-vs-recreate branching is new:
- Parses `LOGIN` (still required, first positional arg) and a new `--recreate` flag alongside the
  existing `--update`; everything else forwards to the `claude` entrypoint unchanged.
- `docker ps --filter "name=^claude-dev-test$" --filter "status=running" -q` (container temporarily
  renamed `claude-dev-test` for testing, per explicit user request, so it doesn't collide with the
  live session already running as `claude-dev` — rename back to `claude-dev` once confirmed
  working end to end).
- If running and `--recreate` was not passed: reuses it via `docker exec -it claude-dev-test claude
  <extra-args>` (a new `claude` process inside the existing container, sharing its mounts) instead
  of tearing it down.
- If not running, or `--recreate` was passed: falls through to the original `docker rm -f` +
  `docker run` sequence, unchanged (including the `//var/run/docker.sock` double-slash — that
  escaping *is* needed here, since `docker run` still executes from `cmd.exe`/MSYS, not WSL).

**Verification status:** node-fix and `architecture-doc.bat` path fixes were confirmed working on
the user's real machine; this `claude.bat` self-contained rewrite has not yet been re-tested after
reverting away from the broken WSL version.

## `architecture-doc.sh`/`.bat` — real-machine failures and the containerization fix (2026-08-20)

Verifying the relocation above on the user's actual Windows/WSL machine (not just this sandbox)
surfaced a chain of real, unrelated environment problems, in the order found:

1. **`node: command not found`** — `generate-architecture-model.sh` needs `node` unconditionally
   (Liquibase-XML-to-JSON parsing, JSON-safe multi-line file reads); the user's WSL distro has no
   `node` on the host at all. Confirmed directly (`wsl which node` / `wsl node --version` both
   empty/not-found).
2. **`wsl bash -l` login-shell attempt** — tried first, assuming a PATH-via-`.bashrc` issue;
   irrelevant once (1) confirmed `node` isn't installed at all, not just unreachable via PATH.
3. **Permission Denied writing `user-spring-boot-starter/DECISIONS.md`** (and, once the same check
   ran for all three, `advertisement-spring-boot-starter`/`provider-profile-spring-boot-starter`
   too) — `generate_pointer_decisions_md()` regenerates these 3 modules' pointer-only
   `DECISIONS.md` every run; write access to (at least) these paths is denied on the user's
   machine. `attrib` (file and folder level) showed no Windows read-only flag; a direct WSL
   write-test (`touch` a throwaway file in the same directory) also failed — ruled out
   file-specific causes, pointed at the directory itself or something environmental beyond simple
   NTFS attributes (antivirus, a live lock, or similar — never fully identified). **Fix applied**:
   `generate_pointer_decisions_md()` now catches a failed write, warns, and continues instead of
   letting `set -e` abort the whole run over one low-stakes, fully-regenerable file — this
   tolerance stays regardless of the containerization fix below, as defense in depth.
4. **`/mnt/wsl/docker-desktop-bind-mounts/Ubuntu/<hash>/...` instead of normal `/mnt/d/...` paths**
   — appeared first when testing the (later reverted) `claude.sh` WSL-delegator attempt on
   `scripts/claude.bat`, then also started appearing in `architecture-doc.bat`'s own `wsl
   wslpath -u`/`wsl bash <path>` calls. Confirmed via `wsl -l -v`: `Ubuntu` is correctly the
   default distro (not a wrong-distro-selection issue) — the bind-mounts path is a real,
   documented Docker-Desktop-WSL2 integration issue (microsoft/WSL#6464 and related docker/for-win
   issues, confirmed via WebSearch, not assumed), triggered by Docker Desktop's own WSL2
   integration once host-path bind mounts are in play; exact trigger never pinned down with
   certainty. `wsl --cd <path>` (letting wsl.exe do the translation atomically, no separate
   `wslpath` call) was tried and did **not** fully avoid it either — same symptom recurred.
5. **A "hang" that was actually just a slow section** — with `--no-check`, a 45s/180s `timeout`
   both killed the run before completion; `bash -x` tracing showed real forward progress (a
   `grep -r`-heavy SPI/Port-implementer scan across `marketplace-orchestrator`'s source, not an
   infinite loop) — just slower than expected, not stuck. Self-inflicted false alarm from
   impatient `pkill` cycles during sandbox testing, not a real bug.

**Real fix (containerization) — status: partially implemented, further changes need explicit
approval before continuing:**

Given (1)/(4) are both host-WSL-environment problems `architecture-doc.sh` cannot reliably control,
and disposable-container tar-pipe-in/`docker cp`-out is an already-proven pattern in this exact repo
(`scripts/build-and-test/run.sh`, for the identical class of Windows/WSL Permission Denied issue,
see that file's own header comment) — `architecture-doc.sh` now runs the whole generation pipeline
inside a Docker container instead of on the host, sidestepping (1) and (4) entirely (the real
generation work never touches host WSL path resolution beyond the initial tar-pipe and final
`docker cp`).

Iterations tried this session, in order:
- **Disposable container per run** (`docker run --rm`) — worked, but reinstalled `python3` via
  `apt-get` on every single invocation (needed unconditionally by `script_headers_json()`, not
  gated behind any flag despite an earlier, now-stale header claim that Python is opt-in) — slow
  and, once, visibly flaky (apt mirror speed varied 2x+ between runs in this sandbox).
- **Warm, reused container** (`docker run -d ... sleep infinity`, `docker exec` into it, install
  `python3` only if missing) — same shape as `playwright/run.sh`'s `pw-runner` and this directory's
  own `screenshot-architecture-map.sh`'s `arch-map-shot`. Verified twice in a row in this sandbox:
  first run installs `python3` (slow), second run skips it entirely (fast), both exit 0.
- **`claude-dev`-reuse hybrid** (if the Claude Code dev container from `scripts/claude.bat` is
  already running, `docker exec` directly into *it* instead of a separate container, since it
  already bundles `node`+`python3` and has a real, non-nested bind-mounted `/app` — no tar-pipe or
  `docker cp` needed at all in that branch) — **implemented, then explicitly reverted at the
  user's direction**: making a documentation-generation script depend on an unrelated AI-assistant
  container being alive is the wrong permanent shape, even though it's a legitimate fast path
  during interactive debugging. Not currently in the file.

**Next step, approved in principle but not yet implemented (awaiting explicit go-ahead before
touching any file):** replace the warm-reused-container's runtime `apt-get install python3` with a
small dedicated `docs/architecture/scripts/Dockerfile` (`FROM node:22-bookworm-slim` + install
`python3` at build time), built once and reused (same "only rebuild when the Dockerfile itself
changed" staleness check `scripts/build-and-test/Dockerfile`'s own build step already does) —
removes the `apt-get` step from every run entirely, not just deferred to "first run only."

**Verification status:** the current on-disk `architecture-doc.sh` (warm-container-only, no
`claude-dev` branch, no Dockerfile yet) was confirmed working end to end on the user's real machine
once (`Wrote 41 nodes...`, all steps completed) — one run separately showed `tar` failing to *read*
two host-side files (`architecture-map.html`, `architecture-model.json`, the very files about to be
fully overwritten anyway) during the tar-pipe-in step; generation still completed successfully
despite that, likely inconsequential since those files are wholly regenerated, not
read-modify-written — not yet root-caused, noted here in case it recurs.

## Related

- `improvement-155` — repo-wide `infra-doc-standards` rollout (its root-`scripts/` item, now done
  via this session's skill runs).
- `docs/architecture/scripts/DECISIONS.md` — generator mechanism ADRs.

## Closing verification (2026-08-25)

Re-checked every open item against current disk state before closing:
- `docs/architecture/architecture-doc.sh`/`.bat` live at `docs/architecture/`, one level above
  `docs/architecture/scripts/`; `scripts/architecture-doc.*` no longer exists.
- `docs/architecture/data/` holds all 4 relocated files (`arch-embed-index.md`,
  `architecture-model.json`, `README.md`, `runtime-notes.md`).
- The "known gap" (`docs/architecture` not tracked as its own `SCRIPT_GROUP_DIRS` entry) is
  resolved — `generate-architecture-model.sh`'s `SCRIPT_GROUP_DIRS` now lists `docs/architecture`,
  `docs/architecture/scripts`, and `docs/architecture/data`.
- The approved-in-principle-but-unimplemented next step (a dedicated
  `docs/architecture/scripts/Dockerfile` replacing runtime `apt-get install python3`) is
  implemented and in use — `architecture-doc.sh`'s own header now describes building from that
  Dockerfile once, reused across runs, no runtime `apt-get`.
- `scripts/claude.bat` has the reuse-vs-recreate logic (`--recreate` flag, `docker exec -it
  claude-dev`), and the temporary `claude-dev-test` test name is gone — no remaining references
  anywhere in the repo, confirmed renamed back to `claude-dev`.

All of this landed across `improvement-163`/`improvement-164`/`improvement-166` in the days after
this issue was filed, rather than under this issue's own number — closing as done, no further
implementation needed.

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
