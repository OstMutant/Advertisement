# improvement-169: Hybrid Agentic Review Factory — Semgrep/ArchUnit/Sonar-MCP mechanical layer + formalized `.claude/agents` review specialists

**Type:** improvement — AI tooling/process infra, design pending
**Module:** `.claude/skills/deep-review/`, `scripts/sonar/`, `scripts/ci/dagu/ci.yaml`,
  `marketplace-app/src/test/java/org/ost/marketplace/architecture/ArchitectureRulesTest.java`,
  candidate new `.claude/agents/`
**Priority:** high (Top)
**When:** independent, no blockers — scope not yet decided, see "Open decision" below

## Problem

A multi-revision mission prompt proposed a "Hybrid Agentic Review Factory": a mechanical
detection layer (Semgrep + ArchUnit + SonarQube, SARIF-aggregated, exposed to Claude via an MCP
server) feeding a reasoning layer of four specialized subagents (security-boundary,
data-integrity, SOLID/DRY, CLAUDE.md compliance), replacing the existing `/deep-review` skill's
full-file reads with coordinate-only "sniper" reads for a claimed 80-90% token saving.

Investigated against the real project state before deciding scope:

**Already exists, re-derived by the mission's own later revision (not new):**
`.claude/skills/deep-review/references/diff-mode.md` already implements the exact 4-lens
parallel-agent structure the mission's final revision proposes (security-boundary /
data-integrity / SOLID-DRY / CLAUDE.md-ArchUnit-compliance), each with its own per-candidate
verification subagent and a backlog cross-check step — just as inline prompts, not persisted
`.claude/agents/*.md` files.

**Verified true (via a `claude-code-guide` agent check against official docs):**
- `.claude/agents/*.md` custom subagents (YAML frontmatter + markdown body as system prompt) are
  a real Claude Code feature — this project has none yet (`.claude/agents/` doesn't exist).
- A real, official SonarQube MCP server exists — but under `sonarqube-mcp-server`
  (SonarSource, npm/GitHub), **not** `@sonarsource/mcp-server-sonarqube` as the mission specified
  (that exact name 404s on the npm registry, confirmed directly).
- No built-in concurrency safety exists for multiple parallel subagents writing to one shared
  file — the mission's proposed `scripts/runtime/review_ledger.json` (all 4 specialists appending
  verdicts to one JSON file) risks lost writes under real parallel execution; the existing
  `diff-mode.md` design already avoids this by having each agent return its findings directly
  through the Agent tool's own result channel instead of a shared file.

**Verified false — two proposed mechanical rules directly contradict this project's own
deliberate, documented architecture, not just generically risky:**
1. The proposed ArchUnit rule ("every `*Service.java` method must have `@PreAuthorize`")
   contradicts the project's explicit, tested rule: *"Never put `@PreAuthorize` at class level on
   service beans"* (`marketplace-app/CLAUDE.md` — Vaadin initializes view beans before
   authentication; a class-level `@PreAuthorize` breaks view wiring). Grep-confirmed: zero
   `@PreAuthorize` usages exist in any starter's source today. Adding this rule as specified would
   fail immediately against the entire codebase.
2. The proposed Semgrep rule ("forbid `AttachmentPort`/S3 calls inside `@Transactional` methods")
   contradicts `AdvertisementSaveService`'s deliberate design
   (`marketplace-orchestrator/CLAUDE.md`): it intentionally bundles the DB write, category/city
   assignment, attachment gallery commit, and audit capture into one `TransactionTemplate`-bounded
   atomic unit. It also wouldn't technically match this codebase at all — the project uses
   `TransactionTemplate` (programmatic), not the `@Transactional` annotation the rule pattern
   targets — so as literally specified it would silently miss the exact code it claims to guard.

**The central "token economy" pitch is in tension with the skill's own existing discipline:** the
proposed "No Blind Reads" rule (read only ±20 lines around a scanner-flagged coordinate) conflicts
with `deep-review/SKILL.md`'s existing "Verify, don't relay" rule, which exists specifically
because narrow-context findings previously produced stale/false claims in this project. Given how
many deliberate, documented exceptions this codebase already carries (the two contradictions
above are existence proof, not the only ones), a coordinate-only read is more likely to miss the
`DECISIONS.md`/`CLAUDE.md` context that turns a apparent violation into a known, intentional
design choice.

## Suggested fix

**Open decision — not yet made.** Filed to capture the analysis and keep the idea visible; scope
has not been chosen. Candidates identified so far, narrowest to broadest:

1. **Narrowest:** formalize `diff-mode.md`'s already-proven 4-lens parallel-agent + per-candidate
   verification pattern as real, named `.claude/agents/*.md` files — no Semgrep/Sonar-MCP/ArchUnit
   expansion. Low risk, since it codifies a pattern already working, using a real but currently
   unused Claude Code feature.
2. **Add SARIF/Semgrep as an extra signal source**, but only after every individual custom rule
   is validated against this project's actual `DECISIONS.md`/`CLAUDE.md` exceptions — the "quick,
   low-token automation" framing undersells how much per-rule validation this specific,
   exception-heavy codebase needs (both example rules the mission proposed failed this check).
3. **Do nothing:** the existing `/deep-review` skill already implements the reasoning-layer
   structure this mission's final revision converged on; no demonstrated token-cost problem has
   been measured to justify the mechanical layer's added complexity and maintenance burden.

## Related

- `.claude/skills/deep-review/references/diff-mode.md` — the existing, already-working 4-lens
  parallel-review structure the mission's final revision re-derived.
- `marketplace-app/CLAUDE.md` "Security: @PreAuthorize and Vaadin" — the documented rule the
  mission's proposed ArchUnit rule would have violated.
- `marketplace-orchestrator/CLAUDE.md` `AdvertisementSaveService` description — the documented
  design the mission's proposed Semgrep rule would have violated.
- `backlog/issues/improvement-167-dag-aware-agent-friendly-script-execution-contract.md` and
  `backlog/issues/improvement-168-ai-guidance-memory-vs-canonical-rules.md` — same pattern of a
  large speculative-infrastructure mission investigated against real project state before
  committing to a scope, filed the same session.
