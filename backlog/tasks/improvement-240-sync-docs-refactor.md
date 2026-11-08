# improvement-240: Refactor /sync-docs — its default scope, cost and duplication do not behave correctly

**Type:** improvement — the command does not fit the way it is used (a sync before closing a change)
**Module:** `.claude/commands/sync-docs.md`
**Priority:** 🟡 high, at the top at the user's request
**When:** independent, no blockers

## Current state

Observed in one run, checked against the command text:

- The default mode lists `git diff --name-only origin/main...HEAD`, committed work only; the change being synced was uncommitted and absent from it.
- Its last step runs `generate-architecture-model.sh`, which the CI docs stage runs again.
- What exactly went wrong in the user's own view is not stated yet.

## Expected scope of the command

The user's expectation: `/sync-docs` and `/deep-review` work over what exists. Each takes a diff, and can also cover everything. Not seeing uncommitted work is wrong.

Wanted scopes, for both commands:

1. Uncommitted changes only.
2. Committed changes only (a diff against a ref).
3. Uncommitted changes plus a number of committed ones, when asked.

Done 2026-11-08, in `.claude/commands/sync-docs.md`, `.claude/commands/deep-review.md` and `.claude/agents/review/deep-review-orchestrator.md`:
- `/sync-docs` without an argument takes the uncommitted changes (`git diff --name-only HEAD`); `/sync-docs <ref>` takes the committed ones (`<ref>...HEAD`); `/sync-docs task <id>` takes every commit whose message names the task plus the uncommitted changes.
- `/deep-review` without an argument takes the uncommitted changes (as before); `/deep-review <ref>` is that one commit; `/deep-review task <id>` is every commit whose message names the task plus the uncommitted changes.
- The default of `/sync-docs` changed from the committed range against `origin/main` to the uncommitted changes; the descriptions of it in `CLAUDE.md`, `.claude/commands/autopilot.md` and `.claude/nav/flows.md` follow. Commits are matched to a task by its id in the commit message.
- Not done: the other two points of this task (the regeneration step the CI docs stage repeats, the ERD marker check), and a committed-range form (`<ref>..<ref>`) for `/deep-review`.

## Report of the commands

Wanted: `/deep-review` and `/sync-docs` end with a report of which files were handled and against what: the files in scope and what each was verified against, what was changed or found, and what was not checked.

Done 2026-11-08: the report is part of the orchestrator's final result, which `/deep-review` shows in full, and of `/sync-docs` Step 5. Not done: the report of Full Audit Mode.

## The ERD no-FK marker is not enforced

- The Database ERD draws a relationship without a FK only from a column `remarks=` that carries `References <table>(<column>), no FK`; the skill says a missing or misworded marker silently drops the relationship, and no test or script checks it.
- `promotion_placement.entity_id` says `provider_profile(id) for PROVIDER_PROFILE, no FK`, without `References`, so the ERD shows no arrow to `provider_profile`; the feedback tables carry the marker and show one.
- The skill is applied by hand only; `sync-docs.md` triggers on `**/db/changelog/**` while the real directories are `db/<module>-changelog/`; the hook on a changelog edit reminds about `CLAUDE.md`'s Schema section, not the marker.

Options, the order is open:
1. A generator check: a column without a FK whose name matches a pattern (`entity_id`, `author_id`, `actor_id`, `*_actor_id`, `created_by`, `*_taxon_id`) and whose `remarks=` lacks the marker fails the generator with the column named; the CI docs stage already runs it. A deliberately no-FK column with another name is not seen.
2. Fix the `sync-docs.md` glob to `**/db/*-changelog/**` and make the changelog-edit hook mention the marker. Raises the odds of noticing, forces nothing.
3. A step in the skill: before closing a task that changes a changelog, go through the marker checklist. Depends on someone doing it.
4. Fix the one known case: `promotion_placement.entity_id` gets `References provider_profile(id), no FK`.

## Related documents of a change (plan, 2026-11-08; steps 1 to 5 done 2026-11-08, step 6 needs nothing)

Found on 2026-11-08: `/sync-docs` checks only the documents its mapping table ties to a changed file. A list in a document that was not itself in the diff is invisible to it: the root `README.md` "Per-module documentation" table lacks `contact-spring-boot-starter`, `feedback-spring-boot-starter`, `promotion-spring-boot-starter` and `marketplace-mcp` (files exist for all four), and nobody saw it. `.claude/nav/scripts/check-module-lists.sh` already guards the same README, but only the "Module Layout" diagram, not that table. A name search is not enough: `README.md` names all 18 modules somewhere, so completeness has to be checked per list.

Goal (user): when a change is synced, every related document is found and corrected, and each correction is made against the code.

Plan (done: the script failed on the four missing rows before the README edit and passes after it):

1. `.claude/commands/sync-docs.md`, new Step 2b "Related documents": for every name added, removed or renamed by the diff (class, method, table, column, enum value, module, endpoint, script flag), search all current documents (`*.md`, script headers, `*.js`, `*.yml`; not `backlog/` and not generated files) and list each hit as a related document; for a changed set of modules, list every document that enumerates modules and check each list or table for completeness against the real set. Related documents join the Step 2 targets.
2. Same file, Step 4: a corrected document is read again after the edit; each corrected claim names the place in the code that confirms it (file and line); replacing a word without that check does not count.
3. Same file, Step 5 report: a table with one row per related document (why it is related, what it was verified against, result) and a separate list "related, not checked".
4. `.claude/nav/scripts/check-module-lists.sh`: also check that the "Per-module documentation" table of the root README has a row for every module of `pom.xml`; update its header (Description, Input, Outputs) and `.claude/nav/scripts/README.md` if it describes the script. It already runs in the CI docs stage.
5. `/app/README.md`: add the four missing rows to the table (the script of step 4 fails before this and passes after it).
6. `.claude/nav/flows.md` row and `CLAUDE.md` line for `/sync-docs` stay valid; no new command file, so no new row.

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
