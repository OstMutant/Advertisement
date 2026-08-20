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
isolate a skill's whole execution from the main conversation. Checked against
[code.claude.com/docs](https://code.claude.com/docs), the official Claude Code documentation, and
found **no such field exists** — isolation is only available by wrapping
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

**Second verification round** (three more certification claims checked against
[code.claude.com/docs](https://code.claude.com/docs) before logging, after the `context: fork`
false-claim taught the lesson to verify before writing):
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

**SOLID/DRY-specific review tools** (user question: are there tools specifically for SOLID/DRY,
beyond SonarQube?) — verified via WebSearch:
- **Semgrep MCP** (`github.com/semgrep/mcp`) — official Semgrep MCP server, confirmed real. Local
  install, no account (`uvx semgrep-mcp`). Semgrep is a semantic static analyzer (understands code
  structure/AST, not just text) with 5000+ built-in rules across languages including Java, plus the
  ability to write **custom rules** — real value for SOLID specifically, since a custom rule could
  target this project's own patterns (e.g. a repository class with too many SQL methods, a
  structural duplication shape specific to this codebase) rather than generic smell detection. A
  third real MCP-server candidate alongside the already-logged SonarQube/Dagu ones.
- **DRY is already partially covered** — SonarQube (already running in this project) computes a
  duplication metric itself (a real observed figure, ~4.97% duplication, from a past
  `scripts/ci.sh` run) — a dedicated DRY tool (PMD CPD, jscpd) would duplicate what Sonar already
  reports, so not a priority addition.
- **DesigniteJava** — a Java-specific design-smell detector explicitly targeting SOLID-style
  violations (God Class, Feature Envy, cyclic dependencies) rather than generic lint rules. Lower
  confidence — only found via academic-paper references (arxiv), no confirmed GitHub/maintenance
  status checked yet, no known MCP integration. Worth a closer look before relying on it, not yet
  verified to the same bar as Semgrep/SonarQube/Dagu.

Not yet added to the coverage map — user asked to log the investigation in the issue first, revisit
coverage-map placement once DesigniteJava's real status is clearer.

**Semgrep runtime — no separate container required** (verified via WebFetch on
`github.com/semgrep/mcp`'s README): three real install/run methods documented —
`uvx semgrep-mcp` (local Python process, no account), `pipx install semgrep-mcp`, or
`docker run ... ghcr.io/semgrep/mcp` (Docker offered only as an optional alternative, not a
requirement). Semgrep itself ships inside the `semgrep-mcp` package, no separate install. Three
transport modes: stdio (default), streamable-http (`127.0.0.1:8000/mcp`), sse. Simpler to stand up
than SonarQube/Dagu, which already run as their own containers in this project.

**Semgrep → SonarQube integration is real** (verified via WebSearch against Sonar's own docs):
SonarQube supports a "Generic issue import format" via the `sonar.externalIssuesReportPaths`
analysis parameter — a comma-delimited list of report files/directories (SARIF or Sonar's own
generic JSON shape), imported into the same dashboard/quality gate as Sonar's own findings at scan
time. Semgrep's own findings could appear in the same Sonar UI instead of a separate tool/view.
Real limitation from Sonar's docs: external-tool rules aren't manageable via Sonar's Quality
Profiles UI — activation/config stays entirely on the Semgrep side.

**DesigniteJava — real status checked via GitHub API** (`api.github.com/repos/tushartushar/DesigniteJava`):
real repo, not archived, Apache-2.0 license, 194 stars, 18 open issues, created 2016. **Last real
push: 2025-03-21** — roughly 17 months stale as of this session, i.e. not actively developed
(though not abandoned either). Output format undocumented on the repo page. No MCP integration
found. Verdict: usable as a one-off manual CLI scan if ever needed, but too low-confidence/low-
maintenance to build a standing, reusable mechanism around, unlike Semgrep/SonarQube/Dagu.

**DesigniteJava alternative search** — no clearly superior, actively-maintained direct analog
found. Turned up **CK** (Chidamber & Kemerer OO metrics tool — computes WMC/CBO/LCOM etc., metrics
that map onto SRP/coupling concerns) as a possible complementary option, but its maintenance status
was not checked yet. PMD/SpotBugs/SonarQube itself came up again but are already accounted for
elsewhere in this investigation (already in the project, or already noted as weak on SOLID
specifically).

**Ready-made Claude Code subagent definitions exist** (user's own idea — "may there already be
skills/agents for this?"), verified via WebSearch + WebFetch:
`github.com/VoltAgent/awesome-claude-code-subagents` — a community collection of 158+ subagent
definitions across 10 categories, in exactly the `.claude/agents/*.md` format this repo currently
has zero of (the same gap already logged under Domain 1's "real hub-and-spoke orchestration" row).
Fetched `categories/04-quality-security/architect-reviewer.md` directly — real frontmatter:
```yaml
name: architect-reviewer
description: "Use this agent when you need to evaluate system design decisions, architectural patterns, and technology choices at the macro level."
tools: Read, Write, Edit, Bash, Glob, Grep
model: inherit
```
Its instructions explicitly list "Separation of concerns, Single responsibility, Interface
segregation, Dependency inversion, Open/closed principle, Don't repeat yourself, Keep it simple,
You aren't gonna need it" as review criteria — i.e. SOLID + DRY + KISS/YAGNI by name. Caveat found
directly in the fetched content: it gives qualitative/judgment-based review, not granular
duplication counts or quantified SOLID-violation metrics — complements Semgrep/Sonar's measured
findings rather than replacing them. A sibling `code-reviewer.md` in the same collection covers
security/performance/maintainability more generally. Practical implication: adopting/adapting
these ready-made definitions into this repo's own `.claude/agents/` is a faster path than writing
`verify-agent.md`/`security-boundary-reviewer.md` from scratch, as originally proposed under
Domain 1.

**`docs/ai/` and `docs/architecture/` already-covered pass** (user request: check whether this
project's own architecture documentation matches the certification): mapped `docs/ai/README.md`'s
three files against Domain 5/3/4 concepts — `context-loading.md` and `adr-index.md` are real,
working implementations of "route context by task type, don't load speculatively" and "generate an
index instead of hand-duplicating source content"; the "Staying correct" section's
`check-adr-index-freshness.sh` CI gate is a real drift-detection/information-provenance mechanism;
`flows.md` is a real command/skill routing table (Domain 3); `architecture-model.json` vs.
`architecture-map.html` is a real machine-structured-output vs. human-readable-view split
(Domain 4). All five added to the coverage map as `already covered` (not `idea`) — this project
already has genuine, working examples of these certification concepts, not just gaps to fill.
Also used this pass to promote several ideas that had only existed as prose in this log into real
coverage-map rows: Semgrep MCP, the Semgrep→SonarQube `externalIssuesReportPaths` integration,
DesigniteJava (kept low-priority per its verified stale status), and a concrete-resource update to
the Domain 1 "named subagents" row citing the VoltAgent collection directly.

**Architecture-visualization tooling weight check** (user question: is the current tooling fine, or
are there lighter alternatives?) — verified via WebSearch against real current data:
- `docs/architecture/architecture-map.html` (472K) loads both **Mermaid.js** (for the embedded
  bounded-contexts/database-ERD diagrams) and **Cytoscape.js + cytoscape-dagre + dagre** (for the
  interactive module drill-down graph) from unpkg, confirmed via grep on the real file.
- Mermaid.js is ~158KB minified+gzipped today (down from 2MB+ uncompressed in 2023, per
  Sidharth Vinod's "Shrinking Mermaid" writeup) — real weight, but avoidable here: this project's
  Mermaid diagrams are static (generated once from markdown, never live-edited in the browser), so
  a **build-time pre-render to static SVG** (via `mermaid-cli`, which wraps headless
  Chrome/Puppeteer) embedded directly into the HTML would let the browser skip downloading/running
  the Mermaid JS engine entirely — a concrete, real optimization for the Mermaid portion.
- Cytoscape.js: the "2026 rule" from research (PkgPulse's comparison guide) is "Cytoscape for
  graph analysis, vis-network for interactive diagrams, Sigma.js for large WebGL graphs" — given
  this project's graph is small (10 modules, not thousands of nodes) and genuinely needs
  interactivity (click-to-drill-down), Cytoscape.js stays a reasonable choice; no clear win from
  switching to a lighter library at this scale.
- Not a direct match to any single certification domain — flagged as a general engineering finding
  worth keeping on record, not filed under a specific domain row.

**Is our overall approach a recognized standard, or bespoke?** (user question) — verified via
WebSearch, split answer:
- **`docs/architecture/`'s generated-model approach (JSON + visual, from a single source of truth,
  CI-freshness-gated) matches a real, recognized industry standard**: "Architecture as Code" /
  "Living Documentation" — the C4 Model (Simon Brown), with a real tool ecosystem (Structurizr,
  PlantUML, Mermaid). Same core idea confirmed in sources: diagrams generated from a
  version-controlled source of truth, kept automatically in sync with code, to avoid
  "documentation rot". This project's approach isn't a novel invention here — it's following an
  established, named best practice.
- **`docs/ai/`'s AI-navigation layer (context-loading.md/flows.md/adr-index.md) has no established
  standard yet.** Closest named candidate: `llms.txt` (proposed by Jeremy Howard, Sept 2024;
  adopted by Anthropic/Stripe/Vercel/Cloudflare among others) — but it solves a different problem
  (a public website's root-level summary file for external LLM crawlers, not an internal repo's
  session-context routing for a coding agent already working inside it), and even within its own
  niche, verified as of Q1 2026: no major AI company (including Anthropic itself) has publicly
  committed to reading/acting on it in production, adoption stays ~10% even among tech-forward
  publishers, and a 300k-domain analysis found no measurable effect. Conclusion: this project's
  `docs/ai/` layer is a genuinely bespoke solution to a problem space without a settled industry
  standard yet — worth knowing explicitly, not assumed to be "reinventing something established".

**Portfolio implication flagged (not yet actioned):** the user noted this project's second goal is
a portfolio piece, and this finding — the architecture-documentation approach follows a real,
named industry standard (C4 Model / Living Documentation / Architecture as Code), while the AI
navigation layer is original work in an unsettled space — is worth surfacing somewhere visible
(the repo's `README.md` is the confirmed portfolio-facing entry point, per its own recent commit
history — "restructure top of README for portfolio presentation"). No README edit made yet; this
is a follow-up to revisit, not done in this session.

**`infra-doc-standards` skill — is it a recognized standard, or bespoke?** (user request: check
this skill against real-world precedent) — verified via WebSearch, same split pattern as the
architecture-docs question above:
- **Bash-script header docs: real precedent on both sides.** The skill's own base
  (Google Shell Style Guide) is confirmed real. Additionally found a whole real tool ecosystem —
  **shdoc / bashdoc / shdocport** ("Javadoc for shell scripts") — annotation-based
  (`@description`/`@arg`/`@example`/`@exitcode`) parsers that auto-generate Markdown docs from
  script comments. Same core idea as this skill's structured header (`Description`/`Usage`/`Uses`/
  `Env`/`Input`/`Outputs`/`Returns`), different tag syntax, and a real difference: shdoc/bashdoc
  actually **parse and auto-generate** output docs, while this skill's convention is manually
  applied with no generator/parser tool built around it.
- **Docker/docker-compose header docs: no comparable formal standard found.** Search results only
  surfaced generic advice ("add a header comment", "explain why, not what") — no field-structured
  schema comparable to this skill's own Dockerfile/`.properties` field-meaning tables. This part of
  the skill is more original — no direct industry precedent found for this level of structure.

**shdoc vs. this project's own approach — pros/cons** (user request, verified via WebFetch on
`github.com/vargiuscuola/shdoc`'s README for exact tag list/scope/limitations):
- **shdoc pros:** auto-**generates** a separate Markdown doc from annotations (real parse+generate
  tool, not just a manual convention); richer per-element tag vocabulary (documents constants/
  settings/globals individually, not just 7 fixed fields).
- **shdoc cons (vs. this project):** shell scripts only — no Dockerfile/docker-compose/`.properties`
  support at all, unlike this skill's deliberate extension to those three file types; focused on
  **function-level** docs, but this skill's own finding is that most of this project's scripts are
  standalone CLI entry points, not function libraries — shdoc's core value doesn't map well onto
  that shape; produces a **separate** generated doc file (a second artifact that can drift from the
  script), vs. this skill's header living directly in the file it describes; no `Uses`/`Env`
  fields (docker/mvn/node orchestration, caller-set vs. user-set variables) — real fields this
  project needed that shdoc's generic tag set doesn't address; no explicit visual delimiter for the
  header block, unlike this skill's `# ── Header ──` bounding (added specifically after two real
  parsing bugs this session traced back to that ambiguity).
- **This project's approach pros (vs. shdoc):** covers 3 file types vs. shell-only; inline in the
  file itself (harder to silently go stale than a separate generated doc); fields tailored to real
  discovered project needs, not a generic vocabulary; explicit delimiter bug-fix built in.
- **This project's approach cons (vs. shdoc):** no automated generation/parsing/CI-lint tool —
  enforcement is manual application plus the skill's own "Independent review" fresh-agent check,
  not a build-breaking script; no separate browsable all-scripts-in-one-place doc artifact.
- **Verdict:** not directly interchangeable — shdoc is stronger for a "browsable docs site from all
  scripts" use case; this project's approach is stronger for keeping docs inseparable from the file
  and covering non-shell infra files.

**Documentation-standard coverage gaps beyond bash/Docker: YAML and JavaScript** (user question —
does this project have other file types with real content but no doc-standard coverage yet?)
Checked real files first, then researched precedent for each:

- **Java source itself: already covered, no gap.** Javadoc is the universally established standard
  and this project already applies it (`CLAUDE.md`'s rule requiring a Javadoc purpose paragraph on
  every `*.spi` interface). No research needed, no action item here.
- **YAML — real gap confirmed.** Found 4 real non-docker-compose YAML files with no doc-standard
  coverage: `scripts/ci/dagu/ci.yaml`, `marketplace-app/src/main/resources/application.yml`,
  `application-dev.yml`, `application-prod.yml` (`docker-compose*.yml` is already covered by
  `infra-doc-standards`). Verified via WebSearch: no formal field-structured standard exists for
  Spring Boot `application.yml` specifically (only generic "use comments to separate feature
  sections" advice) — but a real, recognized general YAML convention does exist: a top-of-file
  comment block stating purpose/owner/critical context, standard practice in Kubernetes manifests
  and Ansible playbooks. Real technical constraint confirmed: **YAML has no block-comment syntax**
  (no `/* ... */` equivalent) — every line of a multi-line header must be individually
  `#`-prefixed, same mechanical shape as this project's existing bash headers.
- **JavaScript — real gap confirmed, and the strongest-precedent case found so far.** Checked
  actual files directly (`playwright/e2e/_helpers.js`, a `.spec.js` file): zero header convention
  applied today, files start straight into `require()` calls. Verified via WebSearch: JSDoc's
  `@fileoverview` tag is a real, established file-level header standard, specified by the **same
  authority already trusted in this project** — the Google JavaScript Style Guide (same family as
  the Google Shell Style Guide `infra-doc-standards` is already grounded in), explicitly recommended
  "whenever a file consists of more than a single class definition". This is a stronger precedent
  than the Docker/YAML cases — same authority, not just an analogous idea. No Playwright-specific
  header-comment convention found (search only surfaced general test-organization/`test.step()`
  advice, which this project already follows per `playwright/CLAUDE.md`). Real files with zero
  coverage: `playwright/e2e/*.spec.js`, `playwright/e2e/_flows/*.flow.js`,
  `playwright/e2e/_helpers.js`.

Neither YAML nor JavaScript coverage added to the coverage map yet — logged here first, per the
established "gather in the issue, structure into the map later" pattern for this session.

**Cross-referenced with `improvement-155`** (2026-08-19): that issue's own "Skill improvements
raised while applying it to `scripts/ci/`" section already flagged this exact YAML/JS (and Python)
gap on 2026-08-18, unresearched at the time. Added a pointer there to this investigation's real
findings, so that issue's open item now has a concrete answer instead of a bare "needs research"
note. The two Domain 3 coverage-map rows above for extending `infra-doc-standards` to YAML/JS
partially overlap with `improvement-155`'s own scope — actual implementation of either should
happen through `improvement-155` (the issue that owns this skill's rollout), not as a separate
`improvement-160` deliverable; this issue's role stays limited to having supplied the research.

**"Apply the skill fully, then have a fresh agent independently verify" — is this a recognized
methodology?** (user's clarified question, after an ambiguous first phrasing — confirmed via
`AskUserQuestion` to mean: is `infra-doc-standards`'s own apply-then-independently-review process a
known approach?) Verified via WebSearch — yes, real and named:
- **"Verifier Pattern" in multi-agent AI systems.** A dedicated verifier agent independently
  reviews a generator agent's output **with no access to the generator's context, reasoning, or
  intermediate steps** — evaluates the artifact cold, against the original stated requirements, not
  against what it assumes the requirements meant.
- **Why it works, confirmed by cited research:** LLMs exhibit a "context consistency" bias — they
  favor interpretations of new information that align with prior context rather than contradicting
  it. This is the exact mechanism `infra-doc-standards/SKILL.md`'s own "Independent review" section
  already states as its reasoning ("Catches the class of gap a same-context self-review misses...
  precisely because the reviewer starts cold") — written independently of this research, but now
  confirmed to match it.
- Described in the cited sources as "core infrastructure for high-stakes... AI", not a niche
  technique — this project's two real instances of the pattern (`infra-doc-standards`'s
  "Independent review" step, and `/deep-review`'s verify-agent discipline, already logged under
  Domain 1 in the coverage map) both already follow a recognized, industry-validated design, not a
  bespoke invention.

**Idea, deferred to a later step — not fully formed yet:** the `improvement-155` skill-writing work
this session (YAML/JavaScript/Python sections, each grounded in a real cited external standard
checked before writing, plus one caught-and-corrected false claim about an issue-number citation)
was itself a live demonstration of the Verifier Pattern/confidence-calibration discipline above —
worth documenting *in the skill itself* eventually (a short, citation-backed note on why this
project's skills verify against real sources before writing claims, possibly wrapped in similar
tags to what commands use, aimed at eventually being pulled into generated documentation). User
explicitly said: do this as a **last step**, not now — there are still unresolved nuances (exact
tag shape, where in `SKILL.md` it belongs, whether it duplicates the "Independent review" section's
existing reasoning) to work through first.

Status at end of this session: **still gathering ideas, nothing implemented yet.** All rows in
`improvement-160-certification-coverage-map.md` are `idea`/`already covered` status. Next step is
the user's call — either continue surveying more of the certification content, or pick specific
ideas to move to `approved` and implement.
