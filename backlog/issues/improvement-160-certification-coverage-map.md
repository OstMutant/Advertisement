# improvement-160 — certification coverage map

Tracking file for `improvement-160`. One row per certification point. `Status` is one of:
`idea` (proposed, not yet approved), `approved` (approved, not yet implemented), `done`
(implemented — cite the real file/mechanism), `already covered` (an existing repo mechanism
already satisfies this, cite it), `n/a` (doesn't apply to this project, note why), `not
certification` (checked directly against `/app/private/AICertificationAndAudit/AICertification.txt`
— this row's concept does not actually appear in the private certification document; kept as a
real, useful repo-quality finding, but it does not count as certification coverage).

## Domain 1 — Agentic Architecture & Orchestration

| ID | Point | Possible solution | Status |
|---|---|---|---|
| D1-1 | Verify-agent discipline (a fresh subagent must confirm every finding before it's reported) | `.claude/skills/deep-review` already has this as a prose rule (SKILL.md rule 1) but nothing enforces it mechanically — wiring the review's final step through the `ReportFindings` tool's `verdict: CONFIRMED/PLAUSIBLE` field would make the verify-step structurally required instead of relying on the agent following an instruction. **Correction (2026-08-20):** the original entry cited "Verifier Pattern"/"context consistency bias" as if sourced from the private certification document — verified false by reading the real file (`/app/private/AICertificationAndAudit/AICertification.txt`): neither term appears anywhere in it, and this concept actually lives under **Domain 3** ("CI/CD Integration" task statement, "Session Context Isolation" subsection), not Domain 1. The document's own real text: "The same Claude session that generated code is less effective at reviewing its own changes... When Claude generates code in a session, it builds up reasoning context... It's less likely to question decisions it already justified to itself. The fix: independent review instances — use a separate Claude Code invocation for review — one that has no access to the generation session's reasoning context." This project already has two real instances of that fix — this row's own `/deep-review` and `infra-doc-standards/SKILL.md`'s "Independent review" step — both written independently of the certification material, now confirmed to match it | idea |
| D1-2 | Real hub-and-spoke orchestration with named, reusable specialized subagents (not just a generic type + inline prompt text) | No `.claude/agents/*.md` custom subagent definitions exist anywhere in this repo — every "specialized agent" (security-boundary, data-integrity, verify-agent, etc.) is just a `general-purpose`/`Explore` dispatch with the role described inline in that one skill's prompt text, so the specialization can't be reused outside the skill that wrote it. **Concrete candidate found** (verified 2026-08-19 via WebFetch): `github.com/VoltAgent/awesome-claude-code-subagents` — 158+ community `.claude/agents/*.md`-format definitions; `architect-reviewer.md` fetched directly, real frontmatter (`name`/`description`/`tools`/`model`), instructions explicitly list SOLID+DRY+KISS/YAGNI as review criteria. Adapting this (and its sibling `code-reviewer.md`) into this repo's own `.claude/agents/` is faster than writing `verify-agent.md`/`security-boundary-reviewer.md` from scratch | idea |
| D1-3 | Agent SDK hooks — `SubagentStop` (this repo already has real `PreToolUse`/`PostToolUse`/`UserPromptSubmit` hooks in `.claude/settings.json`, but no `SubagentStop`) | Every completed issue's mandatory `## Operational notes` block (`.claude/rules.md`) requires manually recalling each Agent-tool call's token/tool-use/duration stats from task-notifications. A `SubagentStop` hook could auto-append every finished agent's stats to a persistent ledger file, so compiling `Operational notes` becomes "read the ledger" instead of "remember to note it" — reusable for every future task, not just reviews | idea |

## Domain 2 — Tool Design & MCP Integration

| ID | Point | Possible solution | Status |
|---|---|---|---|
| D2-1 | Tool overload threshold (4-5 tools/agent optimal, more hurts tool-selection accuracy) | `/deep-review`'s find/verify subagents currently spawn as `general-purpose` with full tool access, though they only ever need read-only tools (Read/Grep/Glob/Bash for git) — scope them to a minimal read-only toolset in `SKILL.md`'s subagent dispatch instructions | idea |
| D2-2 | Real MCP server integration — SonarQube | This repo has zero `.mcp.json`/MCP servers configured. `SonarSource/sonarqube-mcp-server` (verified 2026-08-19 via WebSearch) is the official MCP server for self-hosted SonarQube — configure via `SONARQUBE_URL`/`SONARQUBE_TOKEN` against the local instance already running at `localhost:9099` (`scripts/sonar.sh`). Gives direct tool-call access to findings/quality-gate state instead of parsing scan output or the HTML dashboard | idea |
| D2-3 | Real MCP server integration — Dagu | Dagu (`scripts/ci`) has a **built-in** MCP server (verified 2026-08-19 via WebSearch) at its HTTP server's `/mcp` endpoint — no separate package, works against the already-running `ci-runner` container (proxied at `localhost:8082`). Exposes `dagu_read`/`dagu_change`/`dagu_execute` tools to inspect run state, preview/apply DAG changes, and trigger runs | idea |
| D2-4 | MCP server scoping — project vs user config | Verified 2026-08-19 against [code.claude.com/docs](https://code.claude.com/docs): project-scoped `.mcp.json` in the repo root (checked into git, shared with the whole team) is distinct from user-scoped config. For the SonarQube/Dagu MCP servers above, `.mcp.json` at the repo root is the right scope — every clone of this repo gets both servers automatically, not just one developer's machine | idea |
| D2-5 | Real MCP server integration — Semgrep (SOLID/DRY-focused static analysis) | Official `github.com/semgrep/mcp` (verified 2026-08-19 via WebFetch), no separate container needed — `uvx semgrep-mcp` runs as a local process (Docker offered only as an optional alternative). Semgrep is a semantic static analyzer with 5000+ built-in rules plus **custom rules**, real value for SOLID specifically since a custom rule can target this project's own structural patterns (e.g. a repository class with too many SQL methods) rather than generic smells. A fourth real MCP-server candidate alongside SonarQube/Dagu | idea |
| D2-6 | Feed Semgrep findings into the existing SonarQube dashboard instead of a separate view | Verified 2026-08-19 via WebSearch against Sonar's own docs: `sonar.externalIssuesReportPaths` analysis parameter imports a generic-issue-format or SARIF report from an external tool into the same Sonar dashboard/quality gate at scan time. Semgrep's findings could appear alongside Sonar's own instead of a separate tool/view — real limitation: external rules aren't manageable via Sonar's Quality Profiles UI, only via Semgrep's own config | idea |
| D2-7 | SOLID-specific design-smell detector — DesigniteJava | Checked real status via GitHub API 2026-08-19 (`api.github.com/repos/tushartushar/DesigniteJava`): real repo, Apache-2.0, not archived, 194 stars, but **last push 2025-03-21** — ~17 months stale, not actively developed, output format undocumented, no MCP integration. Usable as a one-off manual CLI scan if ever needed, but too low-confidence/low-maintenance to build a standing reusable mechanism around, unlike Semgrep/SonarQube/Dagu — kept at low priority deliberately | idea |

## Domain 3 — Claude Code Configuration & Workflows

| ID | Point | Possible solution | Status |
|---|---|---|---|
| D3-1 | Isolate a whole skill's execution from the main conversation, only the final result returns (verified 2026-08-19 against [code.claude.com/docs](https://code.claude.com/docs): no `context: fork` or equivalent SKILL.md frontmatter field exists — isolation is only available at the subagent level, not the skill level) | Invoking `/deep-review` today runs the coordinator's own reasoning and orchestration directly in the main chat thread (not just the subagents' work), which grows the main conversation's context on every review run. The real, existing mechanism to isolate this: dispatch the whole `/deep-review` procedure into one top-level `Agent` call (a subagent that reads `SKILL.md` and runs the entire coordinator role itself, including spawning its own sub-subagents) instead of running the skill inline in the main thread — same token-saving goal as the Domain 5 escalation-trigger/scratchpad rows, achieved via subagent nesting instead of a nonexistent frontmatter option | idea |
| D3-2 | `.claude/rules/<name>.md` path-scoped rules with `paths:` YAML frontmatter | Verified 2026-08-19 against [code.claude.com/docs](https://code.claude.com/docs): this directory/mechanism is real and exists in Claude Code, but this repo has zero files under it — every rule lives in the one global `.claude/rules.md`, always loaded regardless of which files are touched. Candidate: extract e.g. the Playwright-specific or Liquibase-changelog-specific rules already in `rules.md` into their own path-scoped files so they only load when relevant files are touched | idea |
| D3-3 | Command/skill frontmatter `argument-hint`/`allowed-tools` | Exact YAML syntax confirmed 2026-08-19 via the real frontmatter reference table at [code.claude.com/docs/en/skills](https://code.claude.com/docs/en/skills): `allowed-tools` accepts a space- or comma-separated string or a YAML list; `argument-hint` is valid for project skills (the "unexpected key" error only applies when packaging for the external Agent Skills spec). done — `infra-doc-standards`/`doc-standards` (verified 2026-08-19: neither had any frontmatter at all, not just these two fields) now have `name`/`description`/`allowed-tools`. No `argument-hint` added to either — confirmed neither uses `$ARGUMENTS`, so the field would be moot. `deep-review/SKILL.md` (which does use `$ARGUMENTS`) and the remaining 12 `.claude/commands/*.md` files still lack it — not yet done | done (partial) |
| D3-4 | Extend `infra-doc-standards` skill to cover YAML config files | **Correction (2026-08-20): not sourced from the private certification document** — checked directly (`grep` for `.js`/documentation-standard/header-comment/docstring terms against `/app/private/AICertificationAndAudit/AICertification.txt`), zero matches. This row is a general documentation-quality gap this project found on its own, researched via WebSearch, and tracked in this file alongside real certification points — it was never actually a certification exam concept. Original finding stands as a real, useful gap (kept below), just relabeled honestly. 4 real files with zero doc-standard coverage today: `scripts/ci/dagu/ci.yaml`, `marketplace-app/src/main/resources/application.yml`/`application-dev.yml`/`application-prod.yml` (`docker-compose*.yml` is already covered). No formal field-structured standard exists for Spring Boot `application.yml` specifically (verified via WebSearch — only generic "separate sections with comments" advice), but a real general YAML convention does: a top-of-file purpose/owner/context comment block, standard in Kubernetes manifests and Ansible playbooks. done — `infra-doc-standards/SKILL.md`'s `YAML files` section. Applying it to the 4 real files listed is separate, tracked in `improvement-155` | not certification |
| D3-5 | Extend `infra-doc-standards`/`doc-standards` skill to cover JavaScript files (JSDoc `@fileoverview`) | **Correction (2026-08-20): not sourced from the private certification document**, same as the YAML row above — verified absent via direct `grep`, zero matches for `.js`/JSDoc/`@fileoverview`/documentation-header terms anywhere in the real file. A genuine, useful project finding (Google JavaScript Style Guide precedent, same authority family as the Shell Style Guide this skill already used), just not a certification-exam point — relabeled honestly rather than left implying certification coverage it doesn't have. Checked real files directly (`playwright/e2e/_helpers.js`, a `.spec.js` file): zero header convention at the time, files started straight into `require()` calls. Real files that had zero coverage: `playwright/e2e/*.spec.js`, `playwright/e2e/_flows/*.flow.js`, `playwright/e2e/_helpers.js`. done — `infra-doc-standards/SKILL.md`'s `JavaScript files` section. Applying it to the real files listed (`playwright/playwright.config.js` and 24 files under `playwright/e2e/`) is also now done | not certification |
| D3-6 | Plan mode vs. direct execution | This repo's `.claude/rules.md` "Approval Rule" hand-implements present-plan-then-wait entirely via prose instruction (plain-language + technical layers, then stop) — the same behavior Claude Code's built-in Plan Mode (`EnterPlanMode`/`ExitPlanMode` tools, confirmed available in this environment) provides natively. Worth evaluating whether real Plan Mode usage could replace or reinforce the hand-rolled rule for multi-step tasks | idea |
| D3-7 | Situation → which command/skill handles it (workflow routing) | **Correction (2026-08-20): not sourced from the private certification document** — checked directly (`grep`/`sed` against `/app/private/AICertificationAndAudit/AICertification.txt`); the document's only "Situation → X" table (Domain 3) maps a situation to a prompt-refinement *technique* (examples vs. iteration vs. interview pattern), not to a command/skill. `docs/ai/flows.md` — a real, working routing table mapping "situation" to the exact command/skill that handles it and why, kept fresh by `docs/ai/scripts/check-flows-completeness.sh` wired into `scripts/ci.sh`'s `docs` stage — is a genuine, useful repo mechanism, just not a certification-sourced point | not certification |
| D3-8 | Session Context Isolation — independent review instances counter same-session self-justification bias | Verbatim from the real document, "CI/CD Integration" task statement, "Session Context Isolation" subsection: "The same Claude session that generated code is less effective at reviewing its own changes... The fix: independent review instances — use a separate Claude Code invocation for review — one that has no access to the generation session's reasoning context." done — `.claude/skills/deep-review`'s verify-agent step (a fresh subagent confirms every finding) and `infra-doc-standards/SKILL.md`'s own "Independent review" section are the two existing repo mechanisms that already match this shape. See the "Practical evidence log" below for a live, observed occurrence of the failure this fixes | done |

## Domain 4 — Prompt Engineering & Structured Output

| ID | Point | Possible solution | Status |
|---|---|---|---|
| D4-1 | Structured output via tool_use with a JSON schema, instead of free-text | `/deep-review` (both diff and full mode) ends with a free-text chat summary + hand-written markdown issue files — `ReportFindings` tool already exists in this environment with exactly the right schema (file, line, summary, failure_scenario, verdict, outcome) but neither `SKILL.md` nor `diff-mode.md`/`full-mode.md` reference it at all — wire the final reporting step through it | idea |
| D4-2 | Few-shot prompting (2-4 concrete examples with reasoning, not just one bare example) | Checked `.claude/commands/feature.md`: exactly 1 example (`/feature UserPickerField pagination bug...`), no reasoning attached, no other examples. Add a standing checklist rule (e.g. to `doc-standards`/`infra-doc-standards`) that every command/skill's usage instructions include 2-4 concrete input→output examples with a one-line reasoning each — checked every time a new command/skill is authored, same reusable-checklist shape as the Domain 2 tool-description-quality row | idea |
| D4-3 | Structured output for machines, separate from the human-readable view | **Correction (2026-08-20): domain mismatch** — checked directly against `/app/private/AICertificationAndAudit/AICertification.txt`: the passage closest to this exact framing ("In CI, Claude Code's output has to be machine-parseable. No human is reading it" — `--output-format json`) lives in **Domain 3**'s CI/CD Integration task statement, not Domain 4. Domain 4 does have its own real "structured output" content (tool_use/JSON schema, Task Statement 4.3/4.4), but that's a different, narrower concept — guaranteeing schema-valid output via a forced tool call, not "two separate views for two audiences." `docs/architecture/architecture-model.json` (machine-readable) generated separately from `docs/architecture/architecture-map.html` (human visual) by the same `generate-architecture-model.sh` script is a real, working repo mechanism — just not accurately attributed to this domain/row as originally worded | not certification |

## Domain 5 — Context Management & Reliability

| ID | Point | Possible solution | Status |
|---|---|---|---|
| D5-1 | Confidence calibration / anti-hallucination discipline (don't report unverified claims as confirmed) | Same `ReportFindings` wiring as the Domain 1/4 rows above — its `verdict` field is the exact mechanism this concept describes, currently only enforced by prose ("never report a finding you have not personally verified") | idea |
| D5-2 | Escalation triggers (cheap check first, only pay for the expensive path when it's actually warranted) | `full-mode.md` always dispatches one subagent per module across all 10 modules, each reading most of that module's files, regardless of whether the module changed since its last full-mode pass — add a cheap per-module staleness check (e.g. commits since the last full-mode run) before deciding whether to spawn a full read-agent or a lightweight "confirm nothing changed" pass | idea |
| D5-3 | Escalation triggers, same concept applied to diff mode | `diff-mode.md` step 3 always spawns all 4 specialized agents (security-boundary, data-integrity, SOLID/DRY, CLAUDE.md-compliance) regardless of what the diff actually touches — add a cheap classification of the diff before step 3 (e.g. does it touch a mutation/side-effect path at all) to skip agents that can't find anything relevant | idea |
| D5-4 | Scratchpad files to mitigate context degradation on large multi-agent fan-in | Full-mode's coordinator accumulates raw reports from 10-11 subagents directly in its own conversation context — write each subagent's findings to a scratchpad file instead, and have the coordinator read/grep from the file for grouping/prioritization rather than holding every report in-context | idea |
| D5-5 | Task type → what context to load, instead of loading speculatively | **Correction (2026-08-20): not sourced from the private certification document** — checked directly; no passage describes a "task type → which files to open" routing table. The closest real concept (path-scoped `.claude/rules/` loading only for matching file paths, Domain 3) is a different mechanism — automatic load-by-path, not a manual task-type reading guide. `docs/ai/context-loading.md` is a real, working repo mechanism, just not a certification-sourced point | not certification |
| D5-6 | Avoid duplicating full source content into a summary/index — generate, don't hand-author | **Correction (2026-08-20): not sourced from the private certification document** — checked directly, zero matches for "duplicat"/"hand-author"/"summary/index" language describing this concept. `docs/ai/adr-index.md` — mechanically generated one-line-per-ADR index (never restates decision text) so a session doesn't have to open every full `DECISIONS.md` file speculatively; regenerated automatically on every `DECISIONS.md` edit per a standing `.claude/rules.md` rule — is a real repo mechanism, just not a certification-sourced point | not certification |
| D5-7 | Information provenance / drift detection (a claim's source must stay checkably fresh) | **Certification concept — verbatim from the real document, Domain 5:** "Information provenance — knowing where every claim comes from and how confident you should be in it — is the difference between a research system that produces trustworthy outputs and one that produces plausible-sounding fiction." The document's own context is claim attribution across a multi-agent research synthesis pipeline — a related but distinct application of the same idea to this repo's own doc-freshness problem. done — `docs/ai/scripts/check-adr-index-freshness.sh` (documented in `docs/ai/README.md`'s "Staying correct" section) is a read-only freshness check that diffs the live `docs/ai/adr-index.md` against a fresh regeneration, wired as an unconditional early stage in `scripts/ci.sh`, catching drift between `DECISIONS.md` and its generated index automatically. See the "Practical evidence log" below | done |

## Practical evidence log — real work mapped to certification domains (2026-08-20)

Rows above track proposed/approved/done *mechanisms*. This table instead logs concrete work done
in one real session against `infra-doc-standards`/`improvement-155`, kept separate for later study
review — what was actually done, where, how, and which certification concept it demonstrates in
practice (not just in theory). Full step-by-step detail (exact code diffs, verification commands,
real click-through logs) lives in
`backlog/issues/improvement-155-infra-doc-standards-repo-wide-rollout.md` — this table is the
compressed, analysis-ready summary of that record.

---

### `infra-doc-standards/SKILL.md § Independent review — verify docs actually cover the script's real capabilities` (principle applied, not the literal mechanism)

**Certification concept — verbatim from the real private document**
(`/app/private/AICertificationAndAudit/AICertification.txt`), **Domain 3, "CI/CD Integration" task
statement, "Session Context Isolation" subsection** — not Domain 1 as the coverage-map row below
originally (incorrectly) had it:
> The same Claude session that generated code is less effective at reviewing its own changes. This
> isn't a theoretical worry; it's a measurable effect... When Claude generates code in a session,
> it builds up reasoning context: why it chose this approach, what tradeoffs it considered, what
> alternatives it rejected. Ask it to review the same code in the same session and it keeps all of
> that. It's less likely to question decisions it already justified to itself.
>
> The fix: independent review instances
>
> Use a separate Claude Code invocation for review — one that has no access to the generation
> session's reasoning context. The independent reviewer evaluates the code on its own merits,
> without the bias of prior justification.

**Idea reference:** `D3-8` ("Session Context Isolation — independent review instances counter
same-session self-justification bias") — added to the Domain 3 table specifically for this concept,
since no prior row captured it. `D1-1` ("Verify-agent discipline") is adjacent but distinct — it
proposes formally wiring verify-agent discipline into `/deep-review` via `ReportFindings`, an
unimplemented idea (`status: idea`), not the same thing as the live event this entry records.

**The problem it describes:** a model checking its own output in the same session retains the
reasoning that produced it, and is measurably less likely to question its own prior decisions. The
document's own prescribed fix is a separate invocation with zero access to that reasoning context.
`/deep-review`'s verify-agent step and `infra-doc-standards/SKILL.md`'s own "Independent review"
section are two existing repo mechanisms already shaped like this fix.

**How we address it — and what this entry adds beyond those existing mechanisms:** this is a
*live, observed occurrence* of the failure the document describes, not another instance of the fix
simply working correctly. My first verification pass (a Node `vm.createContext` simulation I wrote
myself, using the same assumptions as the fix, in the same session) is exactly that failure mode —
it reported success while missing real bugs. Only switching to a genuinely independent checker (a
real headless Chromium session with zero access to my own reasoning about the fix) caught them.

- **What:** 8 real UI bugs in `architecture-map.html` found and fixed (breadcrumb tripling, dead
  back-button, stale title, `.bat`-header leak, and 4 more).
- **Where:** `generate-architecture-model.sh` (`navigate()`, `renderBreadcrumb()`, `navigateBack()`,
  `script_headers_json()`).
- **How:** First verification pass was a Node `vm.createContext` simulation written by me with the
  same assumptions as the fix itself — it reported success while missing the exact bugs the user
  then found by actually clicking the live UI. Second verification pass used a genuinely independent
  tool instead: a real headless Chromium session (`pw-runner`) clicking the actual rendered DOM, with
  zero access to the fix's own reasoning — it caught and confirmed every bug immediately.

---

### `docs/ai/scripts/check-adr-index-freshness.sh` (existing repo mechanism, not new work this session)

**Certification concept — verbatim from the real document, Domain 5:**
> Information provenance — knowing where every claim comes from and how confident you should be in
> it — is the difference between a research system that produces trustworthy outputs and one that
> produces plausible-sounding fiction.

**Idea reference:** `D5-7` ("Information provenance / drift detection").

**The problem it describes:** the document's own context is claim attribution surviving (or not)
through a multi-agent research synthesis pipeline — a different specific scenario from this repo's
own generated-index-vs-source-of-truth problem, but the same underlying idea: a claim (here, an ADR
index entry) is only trustworthy if there's a mechanical way to verify it still matches its source.

**How we address it:** `docs/ai/adr-index.md` is a generated index of every `DECISIONS.md` entry
across every module. `check-adr-index-freshness.sh` diffs the committed index against a fresh
regeneration and is wired as an unconditional early stage in `scripts/ci.sh` — so drift between a
`DECISIONS.md` edit and the index that's supposed to reflect it is caught automatically, not left to
someone noticing by eye.

- **What:** existing freshness check, not new work — cited here as a working example of the
  provenance-verification pattern, not something built this session.
- **Where:** `docs/ai/scripts/check-adr-index-freshness.sh`, `docs/ai/README.md`'s "Staying correct"
  section, `scripts/ci.sh`'s `docs` stage.
- **How:** regenerate the index in a scratch location, diff it against the committed one; any
  difference means `docs/ai/adr-index.md` is stale relative to the real `DECISIONS.md` files.

---
