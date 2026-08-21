# improvement-165: Investigate linters/analyzers for the .claude/ AI-instruction layer (agnix, AgentLint, AgentLinter)

**Type:** investigation/tooling — read-only, not a full integration commitment
**Module:** cross-cutting — `.claude/` (root `CLAUDE.md`, 11 module `CLAUDE.md` files,
`.claude/skills/`, `.claude/commands/`, `.claude/rules.md`, `.claude/settings.json`);
`docs/architecture/scripts/generate-architecture-model.sh` / `architecture-map.html` only as a
possible later follow-up, not in this issue's own scope
**Priority:** Top — explicit placement, ranked first (per explicit user request, 2026-08-21)
**When:** independent, no blockers

## Problem

The repo has mechanical verification for code (ArchUnit — `ArchitectureRulesTest`) and for docs
(`doc-standards` skill, `check-hardcoded-counts.sh`, `check-adr-index-freshness.sh`,
`check-flows-completeness.sh`), but nothing mechanically validates the `.claude/` AI-instruction
layer itself — 11+ `CLAUDE.md` files, `.claude/skills/*/SKILL.md`, `.claude/commands/*.md`, and
`settings.json` hooks have only ever been reviewed by eye.

`agnix` (github.com/agent-sh/agnix) is a real, existing open-source linter purpose-built for
exactly this: 443 rules across 19 categories (Skills, Hooks, Agents, Memory/CLAUDE.md, MCP, XML
tag balance, `@import` safety), static rule validation (semantic schema checks + cross-file
consistency, not regex/AST), JSON/SARIF/text/GitHub-Actions output, confidence-tiered autofix
(`--fix`/`--fix-safe`/`--dry-run`), installable via npm/cargo/brew. Confirmed real via direct web
search and a fetch of its `SPEC.md` — not assumed from the name alone.

## Suggested fix

Read-only investigation only — not a design or implementation task yet:

1. `npm install -g agnix` (fall back to `cargo install agnix-cli` if npm fails) and confirm
   `agnix --version` actually runs in this sandboxed environment. If it can't be installed here
   (network restriction, missing toolchain), stop and report that plainly — don't fabricate what
   its output would look like.
2. Run `agnix --format json .` against the real repo and capture the baseline output.
3. Triage findings by rule category. Verify each one against the actual file and the relevant
   module's `DECISIONS.md`/`CLAUDE.md` before treating it as valid — `agnix` is a linter, not
   infallible, and a rule can false-positive against a deliberate, already-documented choice. Use
   `agnix explain <rule-id>` for anything whose rationale isn't obvious.
4. Report what real findings survive verification. Do not run `--fix`/`--fix-unsafe` in this pass
   — auto-fixing is out of scope here.
5. Decide, as a separate follow-up issue (not this one), whether folding surviving `agnix`
   findings into `architecture-model.json`'s existing "Tooling & Pipelines" subgraph
   (`improvement-138` Track A — already built, no dependency on Track B's not-yet-started
   VIOLATION/red-outline visual encoding) is worth doing, based on what Step 3 actually finds.

## First-pass execution plan (2026-08-21, not yet run) — narrower than the full "Suggested fix"

Scoped to only Steps 1-2 of "Suggested fix" above (install + capture), explicitly deferring
Step 3's per-finding verification and Steps 4-5's reporting/follow-up-issue decision to a later
pass once this first pass's raw output is actually seen:

1. `npm install -g agnix` (fallback `cargo install agnix-cli` if npm fails) — `npm`/`node` already
   confirmed present in this container (`npm 10.8.2`, `node v20.20.2`); outbound network access to
   the npm registry from this sandbox is unconfirmed until actually attempted.
2. `agnix --version` — confirm the binary actually runs.
3. `agnix --format json . > <scratchpad>/agnix-baseline.json`, run from the repo root — read-only,
   writes nothing inside the repo itself.
4. Read the raw JSON output and relay it as-is (finding counts by category/severity, a few
   representative entries) — no per-finding verification against real files/`DECISIONS.md` yet,
   no `--fix`/`--fix-safe`, no repo file changes.

**Output of this pass:** one JSON file of raw, unverified findings (rule id, file, line, severity,
message) written to the session scratchpad directory, plus a summary relayed in chat — not written
back into this issue file until the findings are actually triaged in a later pass.

## First-pass results (agnix, 2026-08-21) — raw counts only, not yet triaged

`npm install -g agnix` succeeded (network reachable from this sandbox), `agnix 0.49.0`. Ran
`agnix --format json .` from repo root — `228 files_checked`, exit code 1 (standard for a linter
with findings, not a run failure).

**417 diagnostics total: 37 errors, 378 warnings, 2 info.**

| Category | Count | Dominant rule(s) |
|---|---|---|
| cross-platform | 283 | `XP-003` (283, warning) — hard-coded `.claude/` path |
| claude-memory | 45 | `CC-MEM-006` (23), `CC-MEM-008` (14), `CC-MEM-009` (7), `CC-MEM-014` (1) |
| xml | 35 | `XML-001` (35, **all error**) — unclosed XML tag |
| prompt-engineering | 31 | `PE-001` (26), `PE-006` (4), `PE-004` (1) |
| agent-skills | 12 | `AS-013` (11), `AS-012` (1) |
| claude-hooks | 5 | `CC-HK-010` (4), `CC-HK-009` (1) |
| references | 4 | `REF-001` (2), `REF-004` (2) |
| claude-skills | 1 | `CC-SK-007` |
| version-awareness | 1 | `VER-001` |

All 37 errors = 35× `XML-001` + 2× `REF-001`. Unverified first read: most `XML-001` hits look like
Java generics in markdown prose (`<Long>`, `<AdKind>`, `<AttachmentPort>`) misread as XML tags by
the parser, not real unclosed tags in `.claude/commands/*.md` — a hypothesis, not yet confirmed
against the real files. Raw JSON kept in the session scratchpad only, not committed to the repo.

## Second-pass: candidate alternative/complementary tools found via research (2026-08-21)

User asked for better options after seeing agnix's first-pass output was mostly low-signal noise
(283 of 417 findings are one repeated warning). Web research (search + fetch of each tool's own
site) found two more real, existing, open-source (MIT) tools with a different approach — scoring
overall "harness health" against fewer, evidence-backed checks, rather than agnix's ~443 narrow
syntax rules:

- **AgentLint** (agentlint.app, `npm install -g agentlint-ai`) — 33 checks across 5 dimensions
  (Findability, Instructions, Workability, Continuity, Safety), each citing a primary source
  (Anthropic's prompt-history data, runtime internals, or research); scored report 0-100 with a
  guided fix plan.
- **AgentLinter** (agentlinter.com, `npx agentlinter`, no install) — 8 dimensions (Structure,
  Clarity, Completeness, Security, Consistency, Memory, Runtime Config, Skill Safety), including
  cross-file contradiction detection and malicious-code scanning inside skill files; tier-graded
  (S-C) report with percentile ranking; `--local` flag avoids sending data anywhere.

Both explicitly cover security/secret-exposure and cross-file consistency, which agnix's own
`SPEC.md` does not claim to check — a genuine capability difference, not just a second opinion on
the same rules.

**Third-pass execution plan (not yet run):** same read-only pattern as the agnix first pass —
1. `npm install -g agentlint-ai` (or run via `npx` if global install has issues) → `agentlint
   --version` / equivalent → run against repo root → capture output to the session scratchpad.
2. `npx agentlinter --local` (local mode, avoids any external report upload) against repo root →
   capture output to the session scratchpad.
3. Relay raw counts/representative findings in chat, same as the agnix first pass — no triage, no
   `--fix`, no repo changes yet.

## Third-pass results (AgentLint + AgentLinter, 2026-08-21) — raw counts only, not yet triaged

**AgentLint** (`npm install -g agentlint-ai` → binary name `agentlint`, CLI reports "51 core checks
+ 7 opt-in"; ran `agentlint check --format jsonl`, needed `apt-get install jq` first — the CLI
shells out to `jq` and fails silently to a "Missing required command" message without it, not a
real scan failure). 51 checks ran (core set only): **18 scored 0, 3 partial (0.62-0.8), 30 scored
1** — simple mean ≈ **0.63 (63/100)**. Failing checks by dimension: continuity (4 — no
`HANDOFF.md`/`CHANGELOG.md`/plan directories, conventions this repo doesn't use the same way),
findability (2), instructions (2), safety (4 — `S1` flags the intentionally-committed repo-root
`.env` as a secret leak, a **known false positive**: `integration-tests/CLAUDE.md` already
documents it holds only `POSTGRES_IMAGE`, no secrets, by deliberate design; `S9` flags personal
emails in git history, real but not something a history rewrite is worth doing for), workability
(6 — includes `W5`: `architecture-model.json` is 342789B, over AgentLint's 256KB
Claude-Code-readability threshold — a real, checkable fact worth a look).

**AgentLinter** (`npx agentlinter --local`, v0.3.3) — **scanned only 2 files**: `CLAUDE.md` and
`.claude/rules.md` (no CLI flag found to force a deeper workspace walk — `--help` only offers
`--local`/`--json`, nothing to widen file discovery). This is a material limitation for this
repo's shape: agnix and AgentLint both walked the full tree (228 / all module `CLAUDE.md`+
`SKILL.md`+`.claude/commands` files); AgentLinter's 70/100 (C+) score and 23 critical / 100
warning / 44 suggestion counts are computed from 2 of those 200+ files, not the layer as a whole.
Several "critical" findings inspected are very likely tool-limitation false positives specific to
this repo's per-module convention — e.g. "Referenced file DECISIONS.md not found in workspace" /
"BACKLOG.md not found" / "BACKLOG-ARCHIVE.md not found": all three exist, just not at repo root
(`platform-commons/DECISIONS.md` et al., `backlog/BACKLOG.md`, `backlog/completed/BACKLOG-
ARCHIVE.md`) — the tool appears to expect a single root-level file per name, not this project's
multi-module split. One "contradiction: always call (line 434) vs never call (line 434)" finding
citing the same line number for both sides also looks like a detector bug, not a real
contradiction — unverified, not yet checked against the actual line.

**Comparison, not yet a recommendation:** agnix and AgentLint both cover the real `.claude/` tree
width; AgentLinter's default scan does not, for this repo's file layout, undermining a same-repo
score comparison. All three tools produced some fraction of repo-shape-specific false positives
(agnix's Java-generics-as-XML-tags, AgentLint's `.env`-is-secret, AgentLinter's per-module-file
misses) — none is a drop-in, trust-the-score tool for this project without per-finding
verification, confirming the original "verify, don't relay" plan in "Suggested fix" Step 3 above
is still the right approach for any of the three, not just agnix.

## Fourth pass — targeted spot-check of CLAUDE.md + .claude/rules.md specifically (2026-08-21)

User narrowed the question to just these two core files (the actual repo-wide rule source, not
the whole `.claude/` tree). Per-tool coverage of exactly these two files, extracted from the raw
data already captured above:

- **agnix**: 41 findings (4 error, 37 warning) across the two files — `.claude/rules.md`: 4 error
  + 9 warning; `CLAUDE.md`: 28 warning.
- **AgentLint**: scores `CLAUDE.md` only as its sole entry file (`all_files: ["CLAUDE.md"]`) — does
  **not** follow the `@.claude/rules.md` import, so `.claude/rules.md`'s ~700 lines are entirely
  outside AgentLint's analysis despite being the larger of the two files.
- **AgentLinter**: 64 findings mention `CLAUDE.md`, 108 mention `.claude/rules.md` (its only 2
  scanned files, per the third-pass results above).

**Two "error"/"critical" findings manually verified against the real file content:**

1. agnix `XML-001`, `.claude/rules.md:276-277` ("unclosed tag `<container>`/`<path>`") — actual
   content: `` `docker exec <container> sh -c '...'` `` — a shell placeholder inside inline code,
   not an XML tag. **Confirmed false positive.**
2. AgentLinter "contradiction: always call (line 434) vs never call (line 434)" — actual content:
   "Always call `switchTo()`... Never call `launchSession()`" — two *different* methods being
   contrasted (do X, not Y), not a contradiction about the same action. **Confirmed false
   positive**, and the tool's own line-attribution for one side of the "contradiction" was wrong
   too (cites line 434 for both sides instead of the actual second line).

**Conclusion, investigation paused here per explicit user request — no further verification this
session:** all three tools do reach `CLAUDE.md`/`.claude/rules.md` (AgentLint only partially, via
`CLAUDE.md` alone), but every "error"/"critical"-level finding spot-checked against the two core
rule files so far turned out to be a false positive rooted in this project's own writing
conventions (inline-code placeholders, contrastive "always X / never Y" phrasing) rather than a
real defect. **Zero confirmed real findings in `CLAUDE.md`/`.claude/rules.md` from any of the
three tools as of this session.** No tool is being adopted or wired into CI/`architecture-model.json`
based on this investigation; a full per-finding triage (Step 3 of the original "Suggested fix")
was not run to completion and would be needed before any integration decision, if this is picked
up again.

## Closed (2026-08-21) — no tool adopted

User closed this issue after the fourth-pass spot-check found zero confirmed real findings across
all three candidate tools (agnix, AgentLint, AgentLinter) in the two core rule files. None of the
three is being installed persistently, wired into CI, or folded into `architecture-model.json`.
The underlying gap this issue set out to investigate — nothing mechanically validates the
`.claude/` AI-instruction layer — remains open; this issue closes the specific "is one of these
three tools worth adopting" question with "not as evaluated," not the broader gap itself. A future
attempt (a different tool, or a from-scratch project-specific check) would start fresh rather than
resume this investigation, given how high the false-positive rate against this repo's own writing
conventions turned out to be for all three.

## Operational notes
- token_cost_review: n/a (no Agent-tool review calls this session)
- token_cost_research: n/a (research done directly by the main thread, no delegation)
- token_cost_verification: n/a (tool runs and spot-checks done directly, no Agent-tool calls)
- review_signal_ratio: n/a (no /code-review ran this task)
- context_loading_task_type: n/a (external-tool investigation, not a codebase task matching a
  context-loading.md row)
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: user-initiated exploratory investigation into third-party tooling, escalating
  through several rounds of "find something better" before converging on "check just CLAUDE.md/
  rules.md" and then closing
- flows_chosen: direct implementation (install/run/spot-check), no slash command — matched the
  task shape (ad hoc tool evaluation, not a repo change)
- flows_matched: n/a (no flows.md row covers "evaluate a third-party linter")

## Related

- [improvement-138](improvement-138-architecture-control-plane.md) — the "Tooling & Pipelines"
  subgraph a later integration would extend; confirmed Track B's VIOLATION/red-outline visual
  encoding (§5) is not built yet, so a future integration step would not depend on it
- [improvement-137](../completed/issues/improvement-137-doc-standards-skill-and-dedup-cleanup.md) /
  [improvement-140](../completed/issues/improvement-140-documentation-shrink-and-dedup-completion.md)
  — the only prior validation the `.claude/` layer has had, both manual dedup passes, not
  mechanical
