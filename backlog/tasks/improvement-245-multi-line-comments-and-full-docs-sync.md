# improvement-245: Five multi-line comments in two test classes, and a documentation sync of the whole project

**Type:** improvement — a comment rule violation found by `/sync-docs`, and a full documentation check that has not been run in this work
**Module:** `integration-tests` (`TaxonServiceTest`, `ProviderProfilePaginationScenarioTest`), `docs/architecture/scripts/generate-architecture-model.sh`; all documentation of the repository for the full sync
**Priority:** 🟡 medium, at the top of the backlog at the user's request
**When:** independent, no blockers

## Current state

Found on 2026-11-08 by the Javadoc check of `/sync-docs` (default scope, the uncommitted changes), each row confirmed against the file:

- `TaxonServiceTest`, lines 91 to 96: a six-line comment above `update_returnsAuditingRefreshedUpdatedAt_notTheStalePreSaveValue()`, the history of a bug the test caught.
- `TaxonServiceTest`, lines 108 to 111: a four-line comment about sub-microsecond differences between two reads of a timestamp column.
- `ProviderProfilePaginationScenarioTest`, lines 26 to 31: a four-line class Javadoc with the whole scenario and the one-profile-per-actor reason.
- `ProviderProfilePaginationScenarioTest`, lines 191 and 192: a two-line comment about one profile per actor and the separate reader.
- `ProviderProfilePaginationScenarioTest`, lines 205 to 208: a four-line comment about `createdAt` without a unique tiebreaker and the effect of `size=999`.

- `docs/architecture/scripts/generate-architecture-model.sh` (4816 lines) has runs of several comment lines: an earlier audit by a Haiku agent counted 184 (the number was not reproduced); a count made on 2026-11-08 of runs of two or more consecutive `#` lines gives 93, one of them the 38-line file header. Condensing them without losing their rationale needs a decision where each rationale goes.

The rule in `.claude/rules.md` is one line or none, and a rationale that is a real fact goes first to its canonical home (task, decision record, per `module-doc-standards`), and only a one-line reference stays in the code. The comments were not changed by `/sync-docs`, because deciding where each rationale goes is a decision, not a mechanical edit.

The full check `/sync-docs --full-audit` (every `README.md`, `DECISIONS.md`, `CLAUDE.md`, `.claude/rules/*.md`, every `*.java` Javadoc and every infrastructure file header, against the current code) was not run in this work; the date of the last full audit was not checked.

## Why change

The five comments break the comment rule. The default mode of `/sync-docs` only checks documents tied to the files of a change, so drift in other documents stays unseen until a full check is run.

## Expected benefit

The two test classes follow the comment rule and their rationale lives in one canonical place. A full check of the documentation against the code, with a report of what was checked and what was not.

## Approach

1. For each of the five comments: decide where its rationale goes (task, decision record, or nowhere if it is only local context) and replace the comment by one line; the decision is made with the user, not alone.
2. The multi-line comments of `docs/architecture/scripts/generate-architecture-model.sh`: the same decision for each (where the rationale goes), then one line each; the count (184 or 93) is checked first.
3. Run `/sync-docs --full-audit` and fix what it finds, following its report format.

## Related

- [improvement-240](improvement-240-sync-docs-refactor.md): the `/sync-docs` command and its report.
- [improvement-241](improvement-241-database-refactoring.md): the work in which the comments were found.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: new improvement request, not yet tracked
- flows_chosen: /new-task
- flows_matched: yes
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none
