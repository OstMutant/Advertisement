# improvement-248: Remaining work of the AI-instruction refactor: tighten the research pair and the analysts, the multi-line comments, a full documentation sync

**Type:** improvement — the rest of the work opened in improvement-240, split off when that task was closed
**Module:** `.claude/commands/`, `.claude/agents/research/`, `.claude/agents/sonar/`, `.claude/agents/dagu/`, `integration-tests`, `docs/architecture/scripts/generate-architecture-model.sh`, `.claude/rules/`, `.claude/skills/`, `CLAUDE.md`
**Priority:** 🟡 high, right after improvement-242
**When:** independent, no blockers

## Current state

Done in [improvement-240](../completed/tasks/improvement-240-sync-docs-refactor.md), saved in the history:

- `/sync-docs` rewritten (400 lines to 264) with a scopes table, Step 2b "related documents", a re-read with a file-and-line check, fixed report templates; its default scope is the uncommitted changes, `<ref>` takes committed changes, `task <id>` takes the task's commits plus the uncommitted ones.
- `/deep-review` and the review family (`deep-review-orchestrator`, the command and the four lenses) rewritten from the goal, with a scope `<a>..<b>`, a scope `paths <file>[,<file>]`, one fixed final result, a doc/code mismatch finding and an `inspected` list from every lens; every file passed four rounds of two read-only checks until both said ready.
- `/optimize` renamed `/reduce-code` (in development), tightened, and extended with a research of patterns and best practices (kind `pattern`, field `Source`, its hard limit); new command `/tighten-ai` with its checklist.
- The map generator fails on a reference column without the `References <table>(<column>), no FK` marker; two changelog hooks and the documentation skill remind about it; the module table of the root `README.md` is checked by `check-module-lists.sh`.

## Why change

What is not done keeps the AI instructions uneven (some files tightened against a checklist, others not), leaves five comments and many generator comments against the one-line comment rule, and leaves the documentation as a whole unchecked against the code since no full audit has run.

## Expected benefit

The AI instruction files that remain read fast, have no duplicates or ambiguity and lose no requirement; the comment rule holds in the test classes and the generator; a full audit shows what is stale and what was not checked.

## Approach

Each item below keeps the process used for the finished ones: the goal written and confirmed first, the checklist of `.claude/commands/tighten-ai.md` (no new research for a kind already covered), a coverage table, read-only checks until none finds a lost requirement, one file written per direct yes, never committed without being asked.

1. **Group 2 of the tighten run, the research pair** (`.claude/commands/deep-research.md`, `.claude/agents/research/deep-researcher.md`; 164 lines, 11,738 characters). Goal, confirmed 2026-11-09: `/deep-research` makes the researcher do a deep research from primary sources and the project's practice, driven by the brief, and write the two plans (people and AI), changing nothing else in the project; no requirement is lost: the brief, the stop rule, the loop signals, the formats of the plans. The agent has `Write`, so its limit to `backlog/research/<topic>/` is checked first.
2. **Group 3, the analysts** (`.claude/agents/sonar/sonar-analyst.md`, `.claude/agents/dagu/dagu-analyst.md`; 96 lines, about 5,800 characters). Goal, confirmed 2026-11-09: each gives structured data from its server, read-only; no requirement is lost: what it calls, what it returns, what it forbids.
3. **Five multi-line comments in two test classes:** `TaxonServiceTest` lines 91 to 96 and 108 to 111, `ProviderProfilePaginationScenarioTest` lines 26 to 31, 191 to 192 and 205 to 208. For each: where the rationale goes (task, decision record, or nowhere when it is local context) and one line in its place; decided with the user, not alone.
4. **Multi-line comments of `docs/architecture/scripts/generate-architecture-model.sh`:** count the runs first (an earlier count said 184, a later one said 93), then the same decision for each, one line each.
5. **A full documentation sync:** `/sync-docs --full-audit` (every `README.md`, `DECISIONS.md`, `CLAUDE.md`, `.claude/rules/*.md`, every `*.java` Javadoc and every infrastructure file header, against the current code), fix what it finds, follow its report format.
6. **`/tighten-ai` on the rest:** the other commands, the rules files, the skills and the `.claude/` folder; each run with its own confirmed goal.
7. **A first real run of the new `/sync-docs` text with edits** on a real change, and a `/deep-review` of the pattern-research edit of `/reduce-code` (it was checked by two read-only passes only).
8. **Known leftovers in the review family:** the paragraph "do not flag what needs context outside the scope" now allows confirming reads outside the scope but a lens still has no rule for a very long single file beyond the offset and limit reads; a file deleted by a diff is named under "Not checked" and not reviewed.

## Related

- [improvement-240](../completed/tasks/improvement-240-sync-docs-refactor.md): the task this was split from.
- [improvement-246](../completed/tasks/improvement-246-technical-debt-investigation.md): its hypothesis about the size of `.claude/rules.md` and the duplication in the skills.
- [improvement-244](../completed/tasks/improvement-244-research-wiki-or-keep-architecture-control-plane.md).

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
