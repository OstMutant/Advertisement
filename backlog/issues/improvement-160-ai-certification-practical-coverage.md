# improvement-160: AI certification — practical coverage in this project

**Type:** improvement
**Module:** repo-wide — candidate touch points span `.claude/` (commands, skills, hooks, rules)
and, where a real Claude/Anthropic API integration point makes sense, `marketplace-app`.
**Priority:** Top
**When:** independent, no blockers — no fixed pace, ideas get added and picked up incrementally.

## Problem

There is a private "Claude Certified Architect" certification study document covering 5 domains
(Agentic Architecture & Orchestration, Tool Design & MCP Integration, Claude Code Configuration &
Workflows, Prompt Engineering & Structured Output, Context Management & Reliability). The goal is
to build practical, hands-on skill with the certification's material by finding real, reusable
places to apply it in this project — not one-off exercises done once and forgotten, but standing
mechanisms (hooks, rules, checklists, reusable commands/skills) that keep getting exercised as the
project evolves.

## Suggested fix

Work through the certification content incrementally. For each domain/point:
1. Check whether something equivalent already exists in this repo (many Domain 1/3 concepts —
   hooks, CLAUDE.md hierarchy, skills vs. commands — already have real examples here).
2. If not, propose a concrete, reusable implementation (not a single-use exercise) and record it
   in the companion coverage map: `backlog/issues/improvement-160-certification-coverage-map.md`.
3. Once a proposal is approved and implemented, mark it covered in the coverage map and note the
   real file/mechanism it lives in.
4. Continue until every domain/point in the certification has either a real implementation, a
   documented "already covered by X" note, or a documented "not applicable to this project" note.

No fixed batch size or order — ideas get added to the coverage map opportunistically as they come
up, then picked up for real implementation when the user chooses to.

## Related

- `backlog/issues/improvement-160-certification-coverage-map.md` — the per-point tracking file
  this issue drives.

## Investigation log (2026-08-19)

Working session, starting from `/deep-review` (`.claude/skills/deep-review`) as the first concrete
target to map against the certification. Findings recorded below; matching rows added to
`improvement-160-certification-coverage-map.md` in the same operation each time — this log is the
narrative trail of *why*, the coverage map is the terse per-point tracking table.

**`/deep-review` domain matches (initial pass):**
- Domain 1 (Orchestration): full-mode's "one subagent per module, in parallel, read-only" is a
  real hub-and-spoke pattern; each agent gets isolated context (module's own CLAUDE.md/DECISIONS.md
  only) — real subagent context isolation. The "fresh subagent validates every candidate" step
  (diff-mode step 4, full-mode step 2) is the verify-agent discipline concept, currently prose-only.
- Domain 4 (Structured Output): the `ReportFindings` tool exists in this environment with a schema
  matching exactly what review findings need (file, line, summary, failure_scenario, verdict,
  outcome) — but neither `SKILL.md` nor `diff-mode.md`/`full-mode.md` reference it; findings are
  reported as free-text chat + hand-written markdown today.
- Domain 5 (Reliability): the "never report a finding you have not personally verified" rule
  (SKILL.md rule 1) is exactly the confidence-calibration concept, also currently prose-only —
  wiring `ReportFindings`'s `verdict: CONFIRMED/PLAUSIBLE` field in would make it structural.
- Domain 2 (Tool Design): review subagents dispatch as `general-purpose` (full tool access) though
  they only ever need read-only tools — a tool-overload gap per the "4-5 tools/agent optimal" rule.

**Token-cost concern raised by the user** — real levers found by re-reading `full-mode.md`/
`diff-mode.md` against actual data from `improvement-159`'s own `## Operational notes` (7
module-classification agents, 93k-210k tokens each, avg ~135k — the closest real precedent to
full-mode's "one agent per module" shape):
- Full-mode always reads all 10 modules regardless of whether they changed since the last full-mode
  pass — an escalation-trigger gap (Domain 5). Estimated ~65-75% token reduction on a typical
  incremental run if unchanged modules are skipped or given a cheap confirm-only pass instead.
- Diff-mode always spawns all 4 specialized lenses (security-boundary, data-integrity, SOLID/DRY,
  CLAUDE.md-compliance) regardless of what the diff touches — same escalation-trigger gap, smaller
  effect (~20-35%, since only the find-stage shrinks, not validation).
- Full-mode's coordinator holds all 10-11 subagent reports directly in its own conversation context
  — a scratchpad-file gap (Domain 5): write findings to a file, read/grep from it instead of holding
  everything in-context.
- Overall estimate given to the user: ~40-60% blended reduction, wide range (20-80%) since the real
  number depends on how many modules/lenses are actually skippable in a given run — not measured,
  extrapolated from `improvement-159`'s real per-agent token data.

**"No orchestrator" observation (user):** confirmed — no `.claude/agents/*.md` custom subagent
definitions exist anywhere in this repo. Every "specialized agent" any skill uses is a
`general-purpose`/`Explore` dispatch with the role described inline in that skill's own prompt
text — not reusable outside the skill that wrote it.

**`context: fork` — corrected after verification.** Initially claimed (incorrectly, from the
certification material without checking) that SKILL.md frontmatter supports `context: fork` to
isolate a skill's whole execution from the main conversation. A `claude-code-guide` subagent
checked official docs and found **no such field exists** — isolation is only available by wrapping
a skill's entire procedure inside one top-level `Agent` call (subagent nesting), not a frontmatter
option. Coverage-map row corrected in place with the verified mechanism and a note on the false
claim, dated.

**SonarQube / Dagu — do they have review-relevant tools?** (user question) Verified via WebSearch:
- SonarQube: official `SonarSource/sonarqube-mcp-server` on GitHub — self-hosted config via
  `SONARQUBE_URL`/`SONARQUBE_TOKEN`, works against the local instance this project already runs at
  `localhost:9099`. Practical uses discussed: point-query findings on a single file instead of a
  full scan, programmatic quality-gate status check, cross-check against `/deep-review` findings
  before filing a duplicate, feed the "Definition of Done" gate check.
- Dagu: has a **built-in** MCP server (no separate package) at its own HTTP server's `/mcp`
  endpoint — works against the already-running `ci-runner` container (`localhost:8082` proxy).
  Exposes `dagu_read`/`dagu_change`/`dagu_execute`. Practical uses discussed: `dagu_execute` could
  replace triggering via `bash scripts/ci.sh`; `dagu_read` could **replace this project's own
  `scripts/ci/watch-run.py` polling script entirely** — the most concrete "real value" find so far,
  since it removes custom code rather than just adding a new integration; `dagu_change` for
  previewing DAG edits before applying them to `scripts/ci/dagu/ci.yaml`.

**Second verification round** (three more certification claims checked via `claude-code-guide`
before logging, after the `context: fork` false-claim taught the lesson to verify before writing):
1. `.claude/rules/<name>.md` with `paths:` YAML frontmatter — **confirmed real**, zero files exist
   under it in this repo today; every rule lives in the single always-loaded `.claude/rules.md`.
2. `argument-hint`/`allowed-tools` command/skill frontmatter fields — **confirmed to exist**, exact
   YAML syntax not yet pinned down (source docs too large to extract a working example in one
   fetch). Confirmed by direct grep: none of this repo's 12 `.claude/commands/*.md` files or 3
   skills declare either field; `deep-review/SKILL.md` uses `$ARGUMENTS` with no `argument-hint`.
3. `.mcp.json` project-scope (repo root, git-checked-in, shared with the team) vs. user-scope config
   — **confirmed real and distinct**. Relevant to the Sonar/Dagu MCP rows above: `.mcp.json` at the
   repo root is the right scope so every clone gets both servers, not just one developer's machine.

**New ideas from the second round:**
- Domain 1: a `SubagentStop` hook (this repo already has real `PreToolUse`/`PostToolUse`/
  `UserPromptSubmit` hooks in `.claude/settings.json`, but no `SubagentStop`) that auto-appends
  every finished Agent-tool call's token/tool-use/duration stats to a persistent ledger file —
  would remove the manual-recall step currently needed to compile every completed issue's mandatory
  `## Operational notes` block.
- Domain 4: few-shot prompting audit — `feature.md` has exactly 1 usage example, no reasoning
  attached, no other command/skill does better. Proposed a standing checklist rule (reusable, not
  one-off) requiring 2-4 concrete input→output examples with reasoning for every command/skill.
- Domain 3: noted (lower confidence, not yet verified) that this repo's hand-written "Approval
  Rule" in `.claude/rules.md` reimplements in prose what Claude Code's built-in Plan Mode
  (`EnterPlanMode`/`ExitPlanMode`) already provides natively — worth evaluating later whether real
  Plan Mode usage could reinforce or replace the hand-rolled version for multi-step tasks.

Status at end of this session: **still gathering ideas, nothing implemented yet.** All rows in
`improvement-160-certification-coverage-map.md` are `idea` status. Next step is the user's call —
either continue surveying more of the certification content, or pick specific ideas to move to
`approved` and implement.
