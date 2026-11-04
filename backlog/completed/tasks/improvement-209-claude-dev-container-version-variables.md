# improvement-209: Claude dev container -- version variables, Node 24, engine-strict install

**Type:** improvement (fixes a real bug: the dev container silently shipped an outdated Claude Code)
**Module:** `Dockerfile.claude`, `scripts/claude.bat`, `Dockerfile.opencode`, `scripts/opencode.bat`, `scripts/DECISIONS.md`
**Priority:** medium-high
**When:** independent, no blockers

## Current state

- `Dockerfile.claude` installs Node 20 (`setup_20.x`) and runs a bare `npm install -g @anthropic-ai/claude-code` (no tag).
- Claude Code 2.1.198 and later declare `engines.node >=22`. Under Node 20 a bare-name `npm install` silently resolves the newest engine-compatible release, 2.1.197 (verified with `npm install --dry-run` under Node 20.20.2, npm 10.8.2), while `latest` is 2.1.302.
- An explicit tag (`@latest`, `@2.1.302`) installs 2.1.302 under Node 20 with only an `EBADENGINE` warning; adding `--engine-strict` turns that warning into a hard failure.
- The container is started with `docker run --rm`, so the in-container auto-updater's newer binary is lost on every container exit; the next start runs the image's 2.1.197 again.
- Sonnet 5.5 needs Claude Code 2.1.284 or later, so `/model` never offered it inside the container.
- `claude.bat --update` ran a plain `docker build`, whose layer cache reused the old npm layer.
- Versions (base image tag, Node major, Claude Code) are hard-coded in the RUN text; `scripts/ci/Dockerfile` already keeps its tool version in an `ARG`.

## Why change

A rebuild (`--update`, `--recreate`) could never fix the container, and nothing failed loudly when the Node major no longer satisfied the package. Versions buried in a RUN command are also harder to bump than named variables.

## Expected benefit

`claude.bat <login> --update` always yields the newest Claude Code; a Node/package mismatch fails the build instead of silently downgrading; each version is one named `ARG` default in one file.

## Approach

Chosen (confirmed by the user 2026-10-17):

1. `Dockerfile.claude`: `ARG BASE_IMAGE` (eclipse-temurin:25-jdk-jammy), `ARG NODE_MAJOR=24`, `ARG CLAUDE_CODE_VERSION=latest`, `ARG CLAUDE_CODE_REFRESH=0`; `npm install -g --engine-strict @anthropic-ai/claude-code@${CLAUDE_CODE_VERSION}`; `claude --version` printed in the build log; header `Usage`/`Uses`/`Env` updated per `infra-doc-standards`.
2. `scripts/claude.bat`: `IMAGE` and `CONTAINER` name variables replace the repeated literals; `--update` passes a fresh `CLAUDE_CODE_REFRESH` build-arg so only the Claude Code layer rebuilds. Version defaults stay only in the Dockerfile (one source of truth).
3. ADR in `scripts/DECISIONS.md` via `/record-decision`, with sources: Docker best practices, Claude Code npm setup doc, endoflife.date Node.js schedule, OneUptime cache-busting article (third-party); regenerate `.claude/nav/adr-index.md`.

4. Same treatment for the sibling opencode container (added 2026-10-17): `Dockerfile.opencode` gets `ARG BASE_IMAGE`, `ARG NODE_MAJOR=24` (it still installs Node 20 via `setup_20.x`, whose security support ended 2026-04-30), `ARG OPENCODE_VERSION=latest`, `ARG OPENCODE_REFRESH=0`, and installs `opencode-ai@${OPENCODE_VERSION}` with `--engine-strict` (a no-op today since `opencode-ai` declares no `engines`, but it keeps the two Dockerfiles identical in shape); header `Usage`/`Uses`/`Env` updated per `infra-doc-standards`. `scripts/opencode.bat` mirrors `claude.bat`: `IMAGE`/`CONTAINER` name variables replace the repeated `opencode-j25-dev`/`opencode-dev` literals, and `--update` passes a fresh `OPENCODE_REFRESH` build-arg (its `docker build` at line 60 currently reuses the cached npm layer, the same gap `claude.bat` had). Extend the existing ADR with this scope instead of filing a new one (one decision, not one per container).

5. ADR-usage tracking and `/feature` → `/new-task` (added 2026-10-17, confirmed by the user). The `Referenced` column of `.claude/nav/adr-index.md` is 0 everywhere because `## Operational notes
- token_cost_review: 77512
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: 2 / 3
- context_loading_task_type: Local refactor, single class/package
- context_loading_consulted: yes
- context_loading_matched: yes
- flows_situation: new tracked task, ADR counting design, rename of the task-filing command
- flows_chosen: /review, /sync-docs, /record-decision
- flows_matched: yes
- haiku_decomposition_used: no
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: ADR-017 (scripts), ADR-004 (.claude)

### Agent calls
- code review (uncommitted changes, default effort, shortened procedure: no finder lenses/verifiers) | subagent_type=deep-review-orchestrator | tokens=77512 | tool_uses=8 | duration_s=96 | mode=background | batch=solo

### Script/command runs
- bash .claude/nav/scripts/generate-adr-index.sh (several runs) | duration_s=n/a | mode=foreground | result=pass
- bash .claude/nav/scripts/check-flows-completeness.sh | duration_s=n/a | mode=foreground | result=pass
- bash docs/architecture/scripts/generate-architecture-model.sh (two completed runs) | duration_s=n/a | mode=background | result=pass
