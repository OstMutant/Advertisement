# improvement-160 — certification coverage map

Tracking file for `improvement-160`. One row per certification point. `Status` is one of:
`idea` (proposed, not yet approved), `approved` (approved, not yet implemented), `done`
(implemented — cite the real file/mechanism), `already covered` (an existing repo mechanism
already satisfies this, cite it), `n/a` (doesn't apply to this project, note why).

## Domain 1 — Agentic Architecture & Orchestration

| Point | Possible solution | Status |
|---|---|---|
| Verify-agent discipline (a fresh subagent must confirm every finding before it's reported) | `.claude/skills/deep-review` already has this as a prose rule (SKILL.md rule 1) but nothing enforces it mechanically — wiring the review's final step through the `ReportFindings` tool's `verdict: CONFIRMED/PLAUSIBLE` field would make the verify-step structurally required instead of relying on the agent following an instruction | idea |
| Real hub-and-spoke orchestration with named, reusable specialized subagents (not just a generic type + inline prompt text) | No `.claude/agents/*.md` custom subagent definitions exist anywhere in this repo — every "specialized agent" (security-boundary, data-integrity, verify-agent, etc.) is just a `general-purpose`/`Explore` dispatch with the role described inline in that one skill's prompt text, so the specialization can't be reused outside the skill that wrote it. Define real project-level subagents in `.claude/agents/` (e.g. `verify-agent.md`, `security-boundary-reviewer.md`) so `/deep-review`, `/autopilot`, and future skills can all reference the same named roles instead of redefining them per call site | idea |
| Agent SDK hooks — `SubagentStop` (this repo already has real `PreToolUse`/`PostToolUse`/`UserPromptSubmit` hooks in `.claude/settings.json`, but no `SubagentStop`) | Every completed issue's mandatory `## Operational notes` block (`.claude/rules.md`) requires manually recalling each Agent-tool call's token/tool-use/duration stats from task-notifications. A `SubagentStop` hook could auto-append every finished agent's stats to a persistent ledger file, so compiling `Operational notes` becomes "read the ledger" instead of "remember to note it" — reusable for every future task, not just reviews | idea |

## Domain 2 — Tool Design & MCP Integration

| Point | Possible solution | Status |
|---|---|---|
| Tool overload threshold (4-5 tools/agent optimal, more hurts tool-selection accuracy) | `/deep-review`'s find/verify subagents currently spawn as `general-purpose` with full tool access, though they only ever need read-only tools (Read/Grep/Glob/Bash for git) — scope them to a minimal read-only toolset in `SKILL.md`'s subagent dispatch instructions | idea |
| Real MCP server integration — SonarQube | This repo has zero `.mcp.json`/MCP servers configured. `SonarSource/sonarqube-mcp-server` (verified 2026-08-19 via WebSearch) is the official MCP server for self-hosted SonarQube — configure via `SONARQUBE_URL`/`SONARQUBE_TOKEN` against the local instance already running at `localhost:9099` (`scripts/sonar.sh`). Gives direct tool-call access to findings/quality-gate state instead of parsing scan output or the HTML dashboard | idea |
| Real MCP server integration — Dagu | Dagu (`scripts/ci`) has a **built-in** MCP server (verified 2026-08-19 via WebSearch) at its HTTP server's `/mcp` endpoint — no separate package, works against the already-running `ci-runner` container (proxied at `localhost:8082`). Exposes `dagu_read`/`dagu_change`/`dagu_execute` tools to inspect run state, preview/apply DAG changes, and trigger runs | idea |
| MCP server scoping — project vs user config | Verified 2026-08-19 via claude-code-guide: project-scoped `.mcp.json` in the repo root (checked into git, shared with the whole team) is distinct from user-scoped config. For the SonarQube/Dagu MCP servers above, `.mcp.json` at the repo root is the right scope — every clone of this repo gets both servers automatically, not just one developer's machine | idea |

## Domain 3 — Claude Code Configuration & Workflows

| Point | Possible solution | Status |
|---|---|---|
| Isolate a whole skill's execution from the main conversation, only the final result returns (verified 2026-08-19 via claude-code-guide: no `context: fork` or equivalent SKILL.md frontmatter field exists — isolation is only available at the subagent level, not the skill level) | Invoking `/deep-review` today runs the coordinator's own reasoning and orchestration directly in the main chat thread (not just the subagents' work), which grows the main conversation's context on every review run. The real, existing mechanism to isolate this: dispatch the whole `/deep-review` procedure into one top-level `Agent` call (a subagent that reads `SKILL.md` and runs the entire coordinator role itself, including spawning its own sub-subagents) instead of running the skill inline in the main thread — same token-saving goal as the Domain 5 escalation-trigger/scratchpad rows, achieved via subagent nesting instead of a nonexistent frontmatter option | idea |
| `.claude/rules/<name>.md` path-scoped rules with `paths:` YAML frontmatter | Verified 2026-08-19 via claude-code-guide: this directory/mechanism is real and exists in Claude Code, but this repo has zero files under it — every rule lives in the one global `.claude/rules.md`, always loaded regardless of which files are touched. Candidate: extract e.g. the Playwright-specific or Liquibase-changelog-specific rules already in `rules.md` into their own path-scoped files so they only load when relevant files are touched | idea |
| Command/skill frontmatter `argument-hint`/`allowed-tools` | Verified 2026-08-19 via claude-code-guide: both fields are real (exact YAML syntax not yet confirmed — needs a working example before implementing). Confirmed by direct check: none of this repo's 12 `.claude/commands/*.md` files or 3 skills declare either field — `deep-review/SKILL.md` uses `$ARGUMENTS` for mode selection with no `argument-hint` describing the expected syntax to a user typing `/deep-review` | idea |
| Plan mode vs. direct execution | This repo's `.claude/rules.md` "Approval Rule" hand-implements present-plan-then-wait entirely via prose instruction (plain-language + technical layers, then stop) — the same behavior Claude Code's built-in Plan Mode (`EnterPlanMode`/`ExitPlanMode` tools, confirmed available in this environment) provides natively. Worth evaluating whether real Plan Mode usage could replace or reinforce the hand-rolled rule for multi-step tasks | idea |

## Domain 4 — Prompt Engineering & Structured Output

| Point | Possible solution | Status |
|---|---|---|
| Structured output via tool_use with a JSON schema, instead of free-text | `/deep-review` (both diff and full mode) ends with a free-text chat summary + hand-written markdown issue files — `ReportFindings` tool already exists in this environment with exactly the right schema (file, line, summary, failure_scenario, verdict, outcome) but neither `SKILL.md` nor `diff-mode.md`/`full-mode.md` reference it at all — wire the final reporting step through it | idea |
| Few-shot prompting (2-4 concrete examples with reasoning, not just one bare example) | Checked `.claude/commands/feature.md`: exactly 1 example (`/feature UserPickerField pagination bug...`), no reasoning attached, no other examples. Add a standing checklist rule (e.g. to `doc-standards`/`infra-doc-standards`) that every command/skill's usage instructions include 2-4 concrete input→output examples with a one-line reasoning each — checked every time a new command/skill is authored, same reusable-checklist shape as the Domain 2 tool-description-quality row | idea |

## Domain 5 — Context Management & Reliability

| Point | Possible solution | Status |
|---|---|---|
| Confidence calibration / anti-hallucination discipline (don't report unverified claims as confirmed) | Same `ReportFindings` wiring as the Domain 1/4 rows above — its `verdict` field is the exact mechanism this concept describes, currently only enforced by prose ("never report a finding you have not personally verified") | idea |
| Escalation triggers (cheap check first, only pay for the expensive path when it's actually warranted) | `full-mode.md` always dispatches one subagent per module across all 10 modules, each reading most of that module's files, regardless of whether the module changed since its last full-mode pass — add a cheap per-module staleness check (e.g. commits since the last full-mode run) before deciding whether to spawn a full read-agent or a lightweight "confirm nothing changed" pass | idea |
| Escalation triggers, same concept applied to diff mode | `diff-mode.md` step 3 always spawns all 4 specialized agents (security-boundary, data-integrity, SOLID/DRY, CLAUDE.md-compliance) regardless of what the diff actually touches — add a cheap classification of the diff before step 3 (e.g. does it touch a mutation/side-effect path at all) to skip agents that can't find anything relevant | idea |
| Scratchpad files to mitigate context degradation on large multi-agent fan-in | Full-mode's coordinator accumulates raw reports from 10-11 subagents directly in its own conversation context — write each subagent's findings to a scratchpad file instead, and have the coordinator read/grep from the file for grouping/prioritization rather than holding every report in-context | idea |
