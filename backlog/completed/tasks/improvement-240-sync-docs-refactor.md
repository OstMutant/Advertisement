# improvement-240: Refactor of the AI commands and documentation checks: /sync-docs, comment rule cleanup, a full documentation sync, /optimize renamed to /reduce-code, and a new /tighten-ai

**Type:** improvement — the command does not fit the way it is used (a sync before closing a change)
**Module:** `.claude/commands/` (`sync-docs.md`, `optimize.md`, new `tighten-ai.md`), `.claude/agents/review/`, `.claude/nav/`, `CLAUDE.md`, `.claude/DECISIONS.md`, `integration-tests`, `docs/architecture/scripts/generate-architecture-model.sh`
**Priority:** 🟡 high, at the very top of the backlog at the user's request
**When:** independent, no blockers

## Current state

Observed in one run, checked against the command text:

- The default mode lists `git diff --name-only origin/main...HEAD`, committed work only; the change being synced was uncommitted and absent from it.
- Its last step runs `generate-architecture-model.sh`, which the CI docs stage runs again.
- A document that mentions a changed thing but is not itself in the diff is never checked, so its lists and names stay stale (found on 2026-11-08: the module table of the root `README.md`).

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
- Done 2026-11-09: `/deep-review <a>..<b>` takes the changes between two refs (`git diff <a>..<b>`, nothing uncommitted) in `.claude/commands/deep-review.md` and `.claude/agents/review/deep-review-orchestrator.md`; the regeneration step that the CI docs stage repeats stays in `/sync-docs` by the decision 2 of the tighten proposal (the union of the lists), so that a local run shows a fresh map; the ERD marker check is below.

## Report of the commands

Wanted: `/deep-review` and `/sync-docs` end with a report of which files were handled and against what: the files in scope and what each was verified against, what was changed or found, and what was not checked.

Done 2026-11-08: the report is part of the orchestrator's final result, which `/deep-review` shows in full, and of `/sync-docs` Step 5. Not done: the report of Full Audit Mode.

## The ERD no-FK marker is not enforced

- The Database ERD draws a relationship without a FK only from a column `remarks=` that carries `References <table>(<column>), no FK`; the skill says a missing or misworded marker silently drops the relationship, and no test or script checks it.
- `promotion_placement.entity_id` says `provider_profile(id) for PROVIDER_PROFILE, no FK`, without `References`, so the ERD shows no arrow to `provider_profile`; the feedback tables carry the marker and show one.
- The skill is applied by hand only; `sync-docs.md` triggers on `**/db/changelog/**` while the real directories are `db/<module>-changelog/`; the hook on a changelog edit reminds about `CLAUDE.md`'s Schema section, not the marker.

Done 2026-11-09: option 1 and the glob of option 2 (the `sync-docs` text uses `**/db/*changelog/**`). The generator fails, naming the column, when a column named `entity_id`, `author_id`, `actor_id`, `created_by`, `*_actor_id` or `*_taxon_id` has no foreign key and no `References <table>(<column>), no FK` marker in its `remarks=`; the failure reaches the exit status of the generator through the two command substitutions (a first version swallowed it, checked with a small script). The check was run on the current schema (32 derived relationships, no error) and on a copy of the model with the marker of `promotion_placement.entity_id` removed (error naming that column, exit 1). Also done 2026-11-09: the two hooks on a changelog edit in `.claude/settings.json` now also remind that a column referencing another table without a foreign key needs the marker, and `module-doc-standards` has the step of option 3 (before closing a task that changes a changelog, check those columns).

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

## Comments of two test classes and the map generator, and a full documentation sync (from the former improvement-245)

### Current state

Found on 2026-11-08 by the Javadoc check of `/sync-docs` (default scope, the uncommitted changes), each row confirmed against the file:

- `TaxonServiceTest`, lines 91 to 96: a six-line comment above `update_returnsAuditingRefreshedUpdatedAt_notTheStalePreSaveValue()`, the history of a bug the test caught.
- `TaxonServiceTest`, lines 108 to 111: a four-line comment about sub-microsecond differences between two reads of a timestamp column.
- `ProviderProfilePaginationScenarioTest`, lines 26 to 31: a four-line class Javadoc with the whole scenario and the one-profile-per-actor reason.
- `ProviderProfilePaginationScenarioTest`, lines 191 and 192: a two-line comment about one profile per actor and the separate reader.
- `ProviderProfilePaginationScenarioTest`, lines 205 to 208: a four-line comment about `createdAt` without a unique tiebreaker and the effect of `size=999`.

- `docs/architecture/scripts/generate-architecture-model.sh` (4816 lines) has runs of several comment lines: an earlier audit by a Haiku agent counted 184 (the number was not reproduced); a count made on 2026-11-08 of runs of two or more consecutive `#` lines gives 93, one of them the 38-line file header. Condensing them without losing their rationale needs a decision where each rationale goes.

The rule in `.claude/rules.md` is one line or none, and a rationale that is a real fact goes first to its canonical home (task, decision record, per `module-doc-standards`), and only a one-line reference stays in the code. The comments were not changed by `/sync-docs`, because deciding where each rationale goes is a decision, not a mechanical edit.

The full check `/sync-docs --full-audit` (every `README.md`, `DECISIONS.md`, `CLAUDE.md`, `.claude/rules/*.md`, every `*.java` Javadoc and every infrastructure file header, against the current code) was not run in this work; the date of the last full audit was not checked.

### Why change

The five comments break the comment rule. The default mode of `/sync-docs` only checks documents tied to the files of a change, so drift in other documents stays unseen until a full check is run.

### Expected benefit

The two test classes follow the comment rule and their rationale lives in one canonical place. A full check of the documentation against the code, with a report of what was checked and what was not.

### Approach

1. For each of the five comments: decide where its rationale goes (task, decision record, or nowhere if it is only local context) and replace the comment by one line; the decision is made with the user, not alone.
2. The multi-line comments of `docs/architecture/scripts/generate-architecture-model.sh`: the same decision for each (where the rationale goes), then one line each; the count (184 or 93) is checked first.
3. Run `/sync-docs --full-audit` and fix what it finds, following its report format.

### Related

- [improvement-241](../completed/tasks/improvement-241-database-refactoring.md): the work in which the comments were found.

## Rename of /optimize to /reduce-code and the new /tighten-ai (from the former improvement-247)

### Current state

Checked on 2026-11-08:

- `/optimize` finds code and documentation the repository no longer needs to maintain, measures the saving and removes it in approved batches (modes `module`, `path`, `topic`, `all`, `report`, `apply`, `verify`; the `topic` mode is an uncommitted change). Its files: `.claude/commands/optimize.md` and the helper `.claude/commands/optimize/check-batch.sh`.
- The name says "make faster", the command makes the repository smaller. The agent lens used by it is already called `code-reduction-reviewer`, and its findings task has `code reduction findings` in the title.
- Where the name `optimize` appears (outside completed tasks and generated files): `CLAUDE.md` (line 184), `.claude/DECISIONS.md` (the decision about `/optimize` reusing the review orchestrator, its text and title), `.claude/agents/review/code-reduction-reviewer.md`, `.claude/agents/review/README.md`, `.claude/commands/README.md` (lines 19 and 21), `.claude/commands/optimize.md`, `.claude/commands/optimize/check-batch.sh` (the description, the usage line and one message), `.claude/nav/flows.md` (line 14), `.claude/nav/adr-index.md` (generated from the decision title), `backlog/BACKLOG.md` (improvement-226 row), the open tasks improvement-223 and improvement-226.
- The AI instructions are large: `.claude/rules.md` has 919 lines and 60,170 characters, the four standards skills 81,469 bytes together, plus the root `CLAUDE.md`, the rules of each module, the commands and the agent definitions.

### Why change

The user wants the code command renamed to say what it does, marked as still in development, and a second command whose goal is to rewrite the AI instructions so that they are more concise, precise and strict.

### Expected benefit

Two commands with names that match what they do. A command that makes the instructions shorter without losing what they make the AI do.

### Approach

#### Part A. Rename `/optimize` to `/reduce-code` (done 2026-11-08)

Done: the command and its folder are renamed with `git mv`; the title, the description (marked "In development"), `argument-hint` path, the research topic prefix (`reduce-code-<scope slug>`), the helper script texts, `CLAUDE.md`, `.claude/commands/README.md`, `.claude/nav/flows.md`, both agent files, the decision in `.claude/DECISIONS.md` (title and text), the ADR index, the open tasks 223 and 226 and the 226 row of `BACKLOG.md` use the new name. Kept: the history row of the first run in `backlog/research/tools/observations.md`, the completed tasks, and the file name of task 226.

1. `git mv .claude/commands/optimize.md .claude/commands/reduce-code.md` and `.claude/commands/optimize/` to `.claude/commands/reduce-code/`.
2. In `reduce-code.md`: the title `# /reduce-code`, the `description` and `argument-hint`, the path `.claude/commands/reduce-code/check-batch.sh`, and a first line stating that the command is still in development.
3. In `check-batch.sh`: the description, the usage line and the message that names the command.
4. `CLAUDE.md` line 184, `.claude/commands/README.md` (the row and the link, with the in-development mark), `.claude/nav/flows.md` line 14 (with the in-development mark), `.claude/agents/review/README.md` and `code-reduction-reviewer.md` (the command name), `.claude/DECISIONS.md` (the title and text of the decision, annotated), then `bash .claude/nav/scripts/generate-adr-index.sh`.
5. Open tasks improvement-223 and improvement-226 and their `BACKLOG.md` row: the command name in the text of the open ones; completed tasks are history and stay as they are.
6. `bash .claude/nav/scripts/check-flows-completeness.sh`, regenerate the architecture map, `/check-architecture-map`.

#### Part B. New command `/tighten-ai` (command file written 2026-11-08)

Done: `.claude/commands/tighten-ai.md` with the seven steps (scope, goal, research, proposal, check, apply, summary), its rows in `.claude/nav/flows.md` and `.claude/commands/README.md` and its line in `CLAUDE.md`. Not done: a first run on `.claude/commands/` (the goal for it is still to be written by the user).

The goal as the user stated it: the command first does a deep research on the best practices for working with a given AI structure (a rules file, a skill, a command, an agent definition), then optimizes that structure so that it still makes the AI work well, while its text is concise, precise, minimal and covers as much as possible.

Decided by the user on 2026-11-08: the command is flexible. It takes the path of any file or folder of AI instructions; the first run is on the commands (`.claude/commands/`), later runs on the rules, the `.claude/` folder and other structures. The other three points are proposed here and wait for the user's approval:

Added by the user on 2026-11-08:

- Goal first: before any research or rewrite the command needs the goal written exactly (what the instructions must achieve and what shortening is for); the user states or confirms it, the command records it in the task in whose scope it runs, and does not start without it. The research and every rewrite are judged against that goal and the best practices found for the structure.
- Summary: for each file and at the end the command shows on screen, and writes into the task of its scope, a summary of what was changed into what and what benefit that brings with respect to the goal; the command takes the task id (or file) as its scope argument.

- Research: once per kind of structure (command files, rules files, skills, agents) and for the stated goal, concentrating on Claude's own practices found on the web, through the `/deep-research` mechanism: topic `tighten-ai-<kind>`, the `deep-researcher` agent on Opus, a human plan and a machine plan, the topic folder removed after the user's decision.
- Rewrite: for each file a proposal with the text before and after, the size before and after, a coverage table (every requirement of the old text and the line of the new text that keeps it) and the benefit against the goal.
- Check and apply: a read-only reviewer agent checks the coverage table against both texts (a lost requirement is a finding); the rewrite is applied file by file after approval, with a stop after each file, and never committed.

Hard limits, as in `/reduce-code`: never change what an instruction requires, never delete a rule that is not stated elsewhere, never touch the files outside the given path.

Files after approval: `.claude/commands/tighten-ai.md`, its row in `.claude/nav/flows.md` and `.claude/commands/README.md`, its line in `CLAUDE.md`, the architecture map regenerated.

### Related

- [improvement-246](improvement-246-technical-debt-investigation.md): its hypothesis about the size of `.claude/rules.md` and the duplication in the skills.
- [improvement-244](improvement-244-research-wiki-or-keep-architecture-control-plane.md).

## Tighten goal

Confirmed by the user on 2026-11-08, scope `.claude/commands/sync-docs.md` (400 lines, 30,401 characters, a command file):

`/sync-docs` has to make the AI find, every time, all the documents related to a change (by the list of changed files and by the content of the change), correct them against the code, and show a report of what was checked and what was not. The shortening is needed so that the command reads fast and has no duplicates and no ambiguity, but no requirement is lost: the scopes `<ref>` and `task <id>`, Step 2b, the re-reading after an edit, the report, and the `--full-audit` mode.

## Tighten goal, second run: reduce-code.md

Confirmed by the user on 2026-11-09, scope `.claude/commands/reduce-code.md` (160 lines, 9,816 characters, a command file):

`/reduce-code` has to make the AI find, on every run, code and documentation the repository does not need to maintain, measure the saving, and remove it only in approved batches, without changing behaviour, contracts or architecture. The shortening is needed so that the command reads fast and has no duplicates and no ambiguity, but no requirement is lost: the four modes (discover, report, apply, verify), the entry format of the inventory, the hard limits, and the `topic` scope.

The research on improving code by recognised patterns (GoF, GRASP) and best practices is a separate step after this one, with its own plan.

## Tighten goals, third run: the review, research and analyst agents

Confirmed by the user on 2026-11-09 (order: 1, 2, 3):

1. The review family (`.claude/commands/deep-review.md`, `.claude/agents/review/deep-review-orchestrator.md` and the four lenses `dry-kiss-yagni-reviewer.md`, `solid-reviewer.md`, `precedent-reviewer.md`, `code-reduction-reviewer.md`; 6 files, 584 lines, about 38,500 characters): `/deep-review` has to make the orchestrator and the lenses find, every time, only confirmed problems of SOLID, DRY, KISS, YAGNI and of the project's own precedents, each verified against the code, and report what was checked and what was not; it writes no code. The shortening is needed so that the files read fast and have no duplicates between the orchestrator and the lenses and no ambiguity, but no requirement is lost: every scope form (current changes, one commit, `<a>..<b>`, `task <id>`, `module`, `path`, `all`), the separate verification of each finding, the `ReportFindings` format and the report.
2. The research pair (`.claude/commands/deep-research.md`, `.claude/agents/research/deep-researcher.md`; 2 files, 164 lines, about 11,700 characters): `/deep-research` has to make the researcher do a deep research from primary sources and the project's practice, driven by the brief, and write the two plans, one for people and one for AI, changing nothing else in the project. The shortening loses no requirement: the brief, the stop rule, the loop signals, the formats of the plans.
3. The analysts (`.claude/agents/sonar/sonar-analyst.md`, `.claude/agents/dagu/dagu-analyst.md`; 2 files, 96 lines, about 5,800 characters): each has to give structured data from its server, read-only; the shortening loses no requirement: what it calls, what it returns, what it forbids.

The research of best practices for each kind reuses the checklist of `.claude/commands/tighten-ai.md`; no new research is run.

### Third run, group 1 (the review family): applied 2026-11-09, files staged

- Applied after one yes per file: `.claude/agents/review/deep-review-orchestrator.md`, `.claude/commands/deep-review.md` and the four lenses (`dry-kiss-yagni-reviewer.md`, `solid-reviewer.md`, `precedent-reviewer.md`, `code-reduction-reviewer.md`); `name`, `tools`, `model` and `description` of the orchestrator and the four lenses are identical to before (checked by `diff`). The texts were rewritten from the goal (the earlier scratchpad was gone) and went through four rounds of two read-only Sonnet checks (orchestrator with command, four lenses) until both said READY TO APPLY.
- Size, lines and characters before and after: orchestrator 275 and 19404 to 247 and 16796; command 24 and 1583 to 61 and 3530; dry-kiss-yagni 74 and 4026 to 77 and 4391; solid 69 and 3745 to 78 and 4139; precedent 73 and 4194 to 78 and 4599; code-reduction 94 and 5751 to 120 and 6543; total 609 and 38703 to 661 and 36998 (4 percent fewer characters, 8 percent more lines). The size is not the point: the lenses grew because the findings asked for rules the old text left open.
- What changed into what: the orchestrator opens with one list of hard limits and has two tables (options, scopes) and one fixed final-result template; the command has an argument table, numbered steps with their stops and a fixed final report, and passes only the scope; the lenses state their input (a file list, or a diff whose changed files are read in full while only what the diff introduces is reported), what `inspected.read` lists and one shared paragraph on what to flag.
- Benefit against the goal: every finding is checked against the current file by a separate verifier before it is reported; a dropped candidate appears only under "Rejected"; the report names what was checked and what was not; the contradictions of the old text are closed (parallel against sequential modules, one response against waves of three, silent drops against the rejected list, a task id matching inside a longer id).
- Added requirements, each disclosed in the coverage tables: the three default lenses return `inspected`; `task <id>` uses a regex with a boundary on both sides and the short id of the commit messages; a doc/code mismatch is a second finding listed only under "Doc/code mismatches"; the orchestrator defines the scope `paths <file>[,<file>]`; json mode returns `agent_calls`; the command asks two approval questions (task file, then its ranked backlog row).
- Choices made by the main thread, not by the user: the hard-limit-4 filter does not run with `output: json` (as in the old text, so `/reduce-code` keeps pre-existing findings); the lenses may read outside the scope to confirm a finding but `locations[0]` must be inside it; duplicated wording across the three default lenses stays because each lens must work alone with only its own file; the frontmatter of the command is kept; a file deleted by a diff is named under "Not checked" and not reviewed.
- Also applied: `.claude/agents/review/README.md` (scope pointer, verification wording, the precedent lens), `.claude/nav/flows.md` (the `all` scope row), and the architecture map regenerated and checked fresh.
- Left open, inherited from the old text: the lens paragraph "do not flag what needs context outside the scope" now has an explicit exception, but a lens still has no rule for a very long single file beyond the offset and limit reads.
- Groups 2 (the research pair) and 3 (the two analysts) are not started.

## Tighten proposal: sync-docs.md

### Decisions

The user left the choice of the 15 decisions of the research (`plan-human.md`, "Decisions waiting for you") to the main thread on 2026-11-08 ("choose the highest-quality approaches"); each line is the chosen option.

1. Early stop of diff mode: c -- stop only when no table row matches the changed files and Step 2b finds no related document.
2. When the generator runs: a -- the union of the lists of Step 2 and Step 4.
3. Changelog glob: b -- `**/db/*changelog/**`, which matches both forms of the folder name.
4. A script folder's `README.md` in diff mode: a -- checked in diff mode, for accuracy only.
5. "Since the last full audit" in Step A5: c -- all `## Operational notes` blocks, with the sentence that no audit date is recorded.
6. ADR edits in Step 4: a, b and c -- no replacement of text with task links; new ADRs only through `/record-decision`; entries whose pattern is gone are annotated as superseded, not deleted.
7. Approval and commits in the text: b -- one hard-limit line ("never commits") and one approval point before the edits.
8. Header: b -- `description` and `argument-hint`, no `disable-model-invocation: true`.
9. Emphasis: b -- plain wording ("Use X when ...") in this file.
10. A hook for "generator last" or "re-read": a -- none.
11. Full Audit Mode in its own file: a -- one file.
12. Replay of both versions on one real change before applying: b -- yes.
13. The built-in skills table check named by `.claude/nav/flows.md`: a -- added to Full Audit Mode.
14. Where the standard lives after the topic folder is removed: c -- a new skill, in the way of the documentation-standards skills.
15. Step labels: a -- renumber: Update becomes Step 3 and Report Step 4; the A labels stay.

### Proposal (shown and approved 2026-11-08)

- Size: 400 lines and 30,401 characters before, 252 lines and 18,064 characters after.
- Coverage: 84 rows of the requirement inventory of the old text; about 46 kept, 20 reworded, 8 changed by a decision, 3 merged, 1 pointer, 5 dropped as repeats, 1 dropped as a story; none lost. Three read-only checks by Sonnet found and closed a lost line, two weakened lines, a change without a decision, and several conflicts.
- What changed into what: the argument forms are one scopes table; the hard limits are one list at the top; the two generator trigger lists are one; the early stop of diff mode waits for Step 2b; ADR edits no longer replace text with task links, add ADRs by hand or delete entries; a script group's `README.md` is checked in diff mode; the reports are fixed templates; every dispatched agent has its prompt contents stated; Full Audit Mode checks the built-in skills table of `.claude/nav/flows.md`; the dated stories and stale counts are gone.
- Benefit against the goal: Step 2 and Step 2b together decide whether anything is related, so a change without Java, SQL or `pom.xml` still reaches its documents; each target's rule is stated once; the re-read and the file-and-line check are a numbered step; the report names what was not checked; the hard limits sit in the first 20 lines, which survive compaction.
- Left open, inherited from the old text: the grain "per file or per module" of the Haiku dispatch; who decides the "intended scope" of a parent package; which of Steps A0 and A5 run for `--module` and `--package`; no `pom.xml` check in Full Audit Mode although `--module` includes the file; "default section shape" for a script group's `README.md`; whether the report is printed after "No architectural changes detected."; Steps 1, 2 and A1 to A3 do not say what they print.

### Result

- Applied to `.claude/commands/sync-docs.md`: 400 lines and 30,401 characters before, 252 lines and 18,064 characters after (the file is identical to the approved text); the checklist that judged it now lives in `.claude/commands/tighten-ai.md`.
- Requirements of the old text (84 inventory rows): about 46 kept, 20 reworded, 8 changed by a decision, 3 merged, 1 pointer, 5 dropped as repeats, 1 dropped as a story; none lost.
- Benefit against the goal: related documents are found by changed file and by content before the run may stop, each target's rule is written once, the re-read and the file-and-line check are a numbered step, and the report names what was not checked.
- Checker rounds: 3 (the first found a lost line, two weakened lines, a change without a decision, three new lines and two conflicts; the second and third found conflicts of wording, all closed except the ambiguities inherited from the old text listed under "Proposal").
- Replay of both versions (decision 12, 2026-11-09, read-only, Sonnet agents, argument `HEAD~1`, the change that closed the database refactoring: 30 files, no Java, SQL or `pom.xml`): the old text stopped at Step 1 with "No architectural changes detected." and checked nothing (0 related documents, 0 statements checked); the new text went on, found 4 related documents (`.claude/nav/flows.md`, `.claude/commands/README.md`, `backlog/research/tools/README.md`, `.claude/commands/reduce-code.md`), checked the `Applied` counts of the two decisions, the backlog rows and the moved task against the files, and found 0 wrong statements. Two incomplete points were noted: `backlog/research/tools/README.md` and a backlog row say a topic is removed only after its plans are filed, not that it may be removed after the user's approval without a task; a line added to `.claude/commands/deep-research.md` is indented four spaces against three in its item.
- Left for the user from the replay of the new text (the text is silent): how deleted or renamed files count in the scope, whether "No architectural changes detected." applies when only the generated-map row matches, whether stale names inside `backlog/` count for Step 2b, a threshold for a name as generic as a command name, and what "Verified against" says for a deleted file.
- Ambiguities settled on 2026-11-09 by the user's instruction to take the highest-quality path, written into `.claude/commands/sync-docs.md` (264 lines, 19,135 characters): deleted and renamed files count as changed, their old path is matched against the table and verified against a search showing no current document names it; the generated-map row counts as a match for the early stop, and the stop still prints the `Files in scope` lines; Step 2b also searches the open tasks in `backlog/tasks/` (not `backlog/completed/`, not `backlog/research/`), a name counts when it identifies something the diff changes and a hit counts when the document states or depends on it; the Haiku dispatch is one agent per module with its changed files, plus one for a file outside any module; `--package` takes sub-packages only when the directory holds no `*.java` of its own; Step A0 always runs and Step A5 only with `--full-audit`; Full Audit Mode checks `pom.xml` headers and comments; a script group's `README.md` uses the section shape its standards skill defines; Steps 1, 2, A1 print their results, Steps A2 and A3 print nothing and write output files.
- Not yet done: the first run of the new `/sync-docs` text with edits on a real change. The research topic was removed on the user's yes.

## Tighten proposal: reduce-code.md

### Proposal and result (shown and approved 2026-11-09)

- Applied to `.claude/commands/reduce-code.md`: 160 lines and 9,816 characters before, 130 lines and 11,872 characters after (identical to the approved text). The lines fell by 19 percent and the characters rose by 21 percent: the text gained the scopes table, a fixed final-report template and explicit answers to what the old text left ambiguous. The research step of `/tighten-ai` reused the checklist of `.claude/commands/tighten-ai.md` (the kind of file is the same command file) and did not run a new research.
- Requirements of the old text (75 inventory rows): 59 kept, 5 reworded, 2 merged, 3 pointers, the rest changed by a recorded choice; none lost. 21 choices are recorded; three read-only rounds by Sonnet closed the weakened sequencing of the researcher start, the check of `Where`, "for every mode", two final reports from one `apply`, and several ambiguities.
- Choices made by the main thread, not by the user: a failed script or agent stops the run; an existing research topic is resumed or removed only after a direct yes; `verify <ID>` sets `verified` only for an entry with status `applied`; the kind of change of a batch is that of the first named ID; the research folder is called `<research-topic>` to separate it from the `topic` scope.
- Defects found in the system, not fixed in this file: the `topic` scope sends the orchestrator `paths <file list>`, which `.claude/agents/review/deep-review-orchestrator.md` does not define; the decision record of this command says the command carries `disable-model-invocation: true`, while the file has `false`.
- Accepted against the checklist: the in-development mark is a today fact because the user asked for it; Discover steps 5 and 8 stay dense.
- The research on improving code by recognised patterns (GoF, GRASP) and best practices is a separate step, not started.

## Patterns and best-practice research inside /reduce-code (plan, 2026-11-09)

The user's wish: the research that `/reduce-code` already starts (the `deep-researcher` with a brief) also looks for improvements by recognised patterns (GoF, GRASP) and by the best practices of the project's stack, so that the code gets better and not only smaller; the research is part of the same command, which already carries rules for it.

Plan (a few lines in `.claude/commands/reduce-code.md`; no new command, no new agent):

1. Brief template, `## Goal`: one sentence is added: where a recognised pattern (GoF, GRASP) or a best practice of the stack (Java 25, Spring Boot, `JdbcClient`, Vaadin) gives a concrete gain in size, cohesion, coupling or readability in `<scope>`, name the pattern, the place and the change.
2. Brief template, `## Where to look in the project`: the researcher takes the primary sources of the pattern and of the stack's practice from the web with links, and checks the decision records of the project (`.claude/nav/adr-index.md`) and `docs/best-practices.md` for a settled decision first.
3. Brief template, `## Completion criterion`: every pattern entry names its source link, its place, its estimate and the project rule or decision it was checked against.
4. Entry format: `**Kind:**` gets the value `pattern`.
5. Hard limits: one line: a pattern is proposed only when it removes more than it adds or removes a measured duplication or coupling; never an abstraction for its own sake, never against a settled decision of the project.
6. The result is checked by the mechanical literal check of the tightened text and one read-only Sonnet pass for conflicts; nothing else changes (the lens `code-reduction-reviewer` keeps finding reductions).

Open for the user: whether the lens or the researcher should also propose a pattern that adds lines but lowers coupling (the plan says no: it must remove more than it adds).

Done 2026-11-09 (applied after the user's yes): `.claude/commands/reduce-code.md` is 137 lines and 14,000-odd characters; the hard limit, the kind `pattern`, the field `Source`, the brief's goal, where to look and completion criterion, the rejected-idea rule for a pattern against a settled decision, steps 7 and 8, the Apply model for a `pattern` entry and the Verify rule are in the text. Two read-only Sonnet passes found conflicts (the hard limit against the goal, no step carrying the researcher's pattern entries into the inventory, a missing source field, Haiku against Opus for a pattern edit, the status path of a `needs-poc` entry); all closed. The rule that a pattern entry must remove more than it adds (or remove a measured duplication or coupling) answers the open question of this plan: a pattern that only lowers coupling without a measured count is not proposed.

## Status of this task on 2026-11-09: closed, the rest moved to improvement-248

The task is closed; its "Not done" list below was moved to [improvement-248](../../tasks/improvement-248-ai-instructions-tighten-and-doc-sync-remaining.md).


Done and saved in the repository history (the entry `8823ab6a` and the entries before it):
- `/optimize` renamed `/reduce-code`, marked as in development; new command `/tighten-ai` with its checklist; `/sync-docs` rewritten (252 lines instead of 400) with three read-only checks and a replay of both versions, its ambiguities settled; `/deep-review <a>..<b>`; the generator fails on a reference column without the marker, the two changelog hooks and the documentation skill remind about it; `scripts/collect-code.bat` fixed (CRLF, duplicated block removed); the module table of the root `README.md` checked by `check-module-lists.sh`; the research topic `tighten-ai-commands` removed.

Done, staged, not yet in the history (the save was refused by the hook until the user says "зроби коміт" in a new message): `.claude/commands/reduce-code.md` tightened (137 lines) and extended with the research of patterns (kind `pattern`, field `Source`, the limits), and this task with the plan, the goals of the third run and the state below.

Not done:
- The review family (group 1) is applied (see the section above); its decisions and the left-open points are there.
- Groups 2 (`deep-research.md`, `deep-researcher.md`) and 3 (`sonar-analyst.md`, `dagu-analyst.md`): not started.
- Done 2026-11-09 (staged, not committed): the orchestrator defines the scope `paths <file>[,<file>]`, which the `topic` scope of `/reduce-code` sends; `reduce-code.md` carries `disable-model-invocation: true`, as its decision record says; `.claude/agents/review/README.md` says the precedent lens sends general quality concerns to the other two lenses.
- The five multi-line comments in `TaxonServiceTest` and `ProviderProfilePaginationScenarioTest`, the multi-line comments of the map generator, and `/sync-docs --full-audit` (the full documentation sync).
- `/tighten-ai` on the other commands, the rules files and the `.claude/` folder.
- A first run of the new `/sync-docs` text with edits on a real change.
- The deep-review step of the pattern research (the patterns edit of `/reduce-code` was checked by two read-only passes, not by `/deep-review`).

## Operational notes
- token_cost_review: 943379
- token_cost_research: 178670
- token_cost_verification: 713200
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: tighten a command file after the goal was confirmed
- flows_chosen: /tighten-ai
- flows_matched: yes
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 5
- haiku_decomposition_rework_needed: 0 / 5
- adrs_referenced: none

### Agent calls
- deep research of Claude's practices for command files (topic tighten-ai-commands) | subagent_type=deep-researcher (model fable) | tokens=178670 | tool_uses=36 | duration_s=556 | mode=background | batch=solo
- snapshot and mechanical inventory of the old sync-docs text | subagent_type=general-purpose (haiku) | tokens=66748 | tool_uses=2 | duration_s=17 | mode=background | batch=solo
- new text and coverage table | subagent_type=general-purpose (opus) | tokens=140652 | tool_uses=19 | duration_s=307 | mode=background | batch=solo
- coverage check, round 1 (read-only) | subagent_type=general-purpose (sonnet) | tokens=117430 | tool_uses=14 | duration_s=117 | mode=background | batch=solo
- coverage check, round 2 (read-only) | subagent_type=general-purpose (sonnet) | tokens=112788 | tool_uses=8 | duration_s=156 | mode=background | batch=solo
- coverage check, round 3 (read-only) | subagent_type=general-purpose (sonnet) | tokens=108604 | tool_uses=11 | duration_s=128 | mode=background | batch=solo
- new text and coverage table for reduce-code.md | subagent_type=general-purpose (opus) | tokens=131628 | tool_uses=27 | duration_s=337 | mode=background | batch=solo
- coverage check of reduce-code.md, round 1 (read-only) | subagent_type=general-purpose (sonnet) | tokens=111285 | tool_uses=14 | duration_s=443 | mode=background | batch=solo
- coverage check of reduce-code.md, round 2 (read-only) | subagent_type=general-purpose (sonnet) | tokens=99663 | tool_uses=7 | duration_s=112 | mode=background | batch=solo
- coverage check of reduce-code.md, round 3 (read-only) | subagent_type=general-purpose (sonnet) | tokens=96682 | tool_uses=7 | duration_s=90 | mode=background | batch=solo
- orchestrator `paths` scope, frontmatter value, README claim (three defect fixes) | subagent_type=general-purpose (haiku) | tokens=210452 | tool_uses=12 | duration_s=79 | mode=background | batch=parallel
- first drafts of the six review-family texts and coverage tables | subagent_type=general-purpose (opus) | tokens=161878 | tool_uses=35 | duration_s=552 | mode=background | batch=solo
- fixes of the review findings, rounds 1 to 3 (four calls) | subagent_type=general-purpose (opus) | tokens=684467 | tool_uses=204 | duration_s=2325 | mode=background | batch=solo
- coverage checks of the review family, rounds 1 to 4 (eight calls) | subagent_type=general-purpose (sonnet) | tokens=943379 | tool_uses=93 | duration_s=1078 | mode=background | batch=parallel pairs
- README and flows.md wording fixes | subagent_type=general-purpose (haiku) | tokens=147657 | tool_uses=13 | duration_s=68 | mode=background | batch=parallel

### Script/command runs
- the mechanical check of the new text (shell, three runs) | duration_s=n/a | mode=foreground | result=pass
- bash docs/architecture/scripts/generate-architecture-model.sh (after the command files changed; one run interrupted by mistake) | duration_s=n/a | mode=background | result=pass
- bash docs/architecture/scripts/check-architecture-model-freshness.sh | duration_s=n/a | mode=background | result=pass
- bash docs/architecture/scripts/generate-architecture-model.sh | duration_s=n/a | mode=background | result=pass
- bash docs/architecture/scripts/check-architecture-model-freshness.sh | duration_s=n/a | mode=background | result=pass
- bash .claude/nav/scripts/check-flows-completeness.sh | duration_s=n/a | mode=foreground | result=pass
