---
description: (In development) Find code and documentation this repository no longer needs to maintain, measure the saving and remove it only in approved batches -- discovery (read-only: a brief built from a fixed template, the deep-review-orchestrator with the code-reduction-reviewer lens, then the deep-researcher on Opus), report, apply <ID>, verify. Never touches marketplace-orchestrator, never commits.
argument-hint: "[module <name> | path <dir> | topic <description> | all | report | apply <ID>[,<ID>] | verify [<ID>]]"
disable-model-invocation: true
---

# /reduce-code

This command is still in development. It reduces what this repository has to write, understand, test and maintain, without losing readability, explicitness, type safety, testability or the architecture; it uses a recognised pattern only where that also reduces.

## Hard limits (every mode)

- Never touch `marketplace-orchestrator/`; refuse a scope inside it.
- Never change module boundaries, dependency direction or layers; never remove a Port, Hook or interface only for line count; never add a dependency or reflection.
- Never change behavior, contracts, authorization, validation, transaction boundaries, exception semantics or side effects; never convert a mutable Vaadin Binder DTO into a record without proof.
- By mode: discovery never edits application code; `apply` edits only the entries the user names, after a direct yes to its plan (Approval Rule of `.claude/rules.md`), and stops after every batch.
- A pattern entry is proposed only when its net change is a reduction of lines, or it removes a duplication or a coupling that the entry measures (the sites or the dependencies counted); never an abstraction for its own sake, never against a settled decision (an Accepted decision record, `docs/best-practices.md` or `.claude/rules.md`).
- Never commit.

## Scopes

The first word of `$ARGUMENTS` picks the mode.

| Argument | Mode | Effect |
|---|---|---|
| `module <name>` | discover | the scope is `path <name>/`: every file of the module (tests, `pom.xml`, `README.md`, `DECISIONS.md` included), not only `src/main` |
| `path <dir>` | discover | every file under `<dir>` |
| `topic <description>` | discover | every file outside `marketplace-orchestrator/` the description names (`Grep`/`Glob` for its symbols, tables and columns, tests and changelogs included), listed grouped by module |
| `all` | discover | every module except `marketplace-orchestrator/`; the orchestrator reads each module's `src/main` |
| `report` or none | report | prints the inventory ranked, with no edit |
| `apply <ID>[,<ID>]` | apply | applies the named entries as one batch |
| `verify [<ID>]` | verify | measures and tests the named entries; with no ID, every entry with status `applied` |

## The inventory

All findings live in one standing task in `backlog/tasks/` whose title contains `code-reduction findings`. Find it by search at the start of every run and never write its file name anywhere; when none exists, say so and create it only through `/new-task` after a direct yes; after a no, discover writes nothing and the other modes stop. Its sections are `## Coverage`, `## Findings` and `## Rejected ideas`. This command is the canonical home of the entry format:

```
### OPT-<n>. <title>
**Status:** open | needs-poc | approved | applied | verified | rejected
**Priority:** ...
**Kind:** duplication | boilerplate | forwarding | indirection | form-state | test-setup | convention | pattern
**Where:** file:start-end and symbol
**Now:** a short real excerpt or a precise reference
**Proposed:** the change
**Estimate:** -removed +added = net, files, assumptions
**Also applies to:** other sites, each counted once
**Why it stays readable:** ...
**Risks and tests:** ...
**Backlog, rules, ADRs:** what was checked and its status
**Recommendation:** implement | investigate
**Source:** the link of the pattern or practice (kind `pattern` only)
**Actual:** written only by verify
```

- `<n>` is the highest existing OPT number plus one. A finding from the review becomes an entry (`OPT-<n>`) when it is written; a rejected idea is one line under `## Rejected ideas` with its reason; the status `rejected` marks an existing entry that the user rejects.
- The kind `pattern` comes from the research of step 6 of Discover, not from the lens (the lens keeps its own kinds); its entries follow the status rule of step 5 (`open` when the researcher's confidence is high and the recommendation is `implement`, otherwise `needs-poc`) and are ranked like any entry in the report.
- A coverage line, one per discovery run: date, scope, files read out of files in scope, files not read (count and reasons), excluded paths, findings added.
- A finding already tracked elsewhere in the backlog is linked, not copied; a real finding that is too large for a batch follows the deferred-findings rule of `.claude/rules.md`.

## Discover (`module`, `path`, `topic`, `all`)

1. For `marketplace-app` as a whole (`module marketplace-app`, `path marketplace-app/`, and `all`, which includes it), recommend `path` scopes of its packages and go on only after a direct yes to keeping the scope as given.
2. Research topic name: `reduce-code-<scope slug>` (for the `topic` scope, the slug comes from the description), lower-case letters, digits and hyphens, at most 40 characters. Run `bash backlog/research/tools/scripts/list-topics.sh`; when the research topic exists, run `show-status.sh <research-topic>`, print what is there instead of starting over, say that the user can resume it as `.claude/commands/deep-research.md` does or remove it after a direct yes, and stop.
3. Run `bash backlog/research/tools/scripts/start-topic.sh <research-topic>` and write `backlog/research/<research-topic>/brief.md` from the brief template with `<scope>` filled in (for `topic`, the description and the file list).
4. Print the research topic and the brief in at most five lines, say that the researcher is Opus with no token or time limit, and ask whether to start the orchestrator run and the researcher. Stop unless the answer is a direct yes.
5. In the background run `Agent({subagent_type: "deep-review-orchestrator", prompt: "lenses: code-reduction-reviewer\nexclude: marketplace-orchestrator/**\noutput: json\n<orchestrator scope>"})`, where `<orchestrator scope>` is `path <name>/` for `module`, `path <dir>` for `path`, `paths <file>[,<file>]` (comma-separated, no spaces) for `topic`, and `all` for `all`. If it fails or returns no JSON block, print what happened and stop. Parse its JSON block: findings with `confidence: high` and `recommendation: implement` get status `open`, all others `needs-poc`. Write the findings, the rejected ideas and the `inspected` block into the research topic's `notes.md` with `add-note.sh`.
6. Then, after the notes of step 5 are written, start the researcher in the background: `Agent({description: "Deep research <research-topic>", subagent_type: "deep-researcher", prompt: "Topic: <research-topic>", run_in_background: true})`; watch it with `show-status.sh` as `.claude/commands/deep-research.md` does; on `PLAN BLOCKED` show the reason, leave the research topic and stop.
7. On `PLAN READY` print `plan-human.md`, its pattern entries with their source links, and the `inspected` block: files read out of files in scope, every file not read with its reason, and every file not accounted for (a lens neither read it nor said why).
8. Ask once whether to add the findings, the pattern entries of the plan, the rejected ideas and the coverage line to the inventory; a pattern entry without a source link, a net estimate or a decision check, or whose estimate is not a reduction and measures no duplication or coupling, is not written. On a direct yes write them, re-read the written lines against the notes and the plan, fix any mismatch, then run `remove-topic.sh <research-topic> --yes`; on any other answer write nothing and ask whether to remove the research topic; remove it only after a direct yes. Print the final report and stop.

A failed script or agent in any step stops the run, and its error is printed.

Brief template (`<scope>` is the only part that changes between runs):

```
## Goal
Find how to reduce what has to be written, understood, tested and maintained in <scope>, without losing readability, explicitness, type safety, testability or the architecture.
Java code is optimized as code, documentation (README, DECISIONS, ADRs) as documentation, pom.xml as build configuration.
Where a recognised pattern (GoF, GRASP) or a best practice of the stack (Java 25, Spring Boot, JdbcClient, Vaadin) gives a net reduction, or removes a measured duplication or coupling, in <scope>, name the pattern, the place, the change and the estimate.

## Where to look in the project
<scope>; docs/best-practices.md; .claude/rules.md and the relevant .claude/rules/<module>.md; .claude/nav/adr-index.md and the DECISIONS.md files (duplicate or similar decisions); backlog/BACKLOG.md and the findings inventory (work already planned); the review result in notes.md; for documentation in <scope>, the checklist and the Documentation Rules of .claude/commands/sync-docs.md (--module mode); for a pattern or a practice, first the Accepted decision records, docs/best-practices.md and .claude/rules.md for a settled decision, then its primary sources (the pattern catalogue, the framework's own documentation) found on the web, with links.

## Completion criterion
Every file in <scope> is read, or listed as not read with the reason. Findings are concrete (file, symbol, excerpt, estimate, risks, tests), ranked by net benefit and risk; rejected ideas are listed with reasons. A pattern entry has the fields of the entry format (kind `pattern`, Where, Now, Proposed, Estimate, Source, Risks and tests, Recommendation) and names the project rule or decision it was checked against.

## Decisions already taken
Application code is never edited during discovery; applying is a separate approved phase; nothing is committed; marketplace-orchestrator/ is out of scope.
A pattern entry is proposed only when its net change is a reduction of lines, or it removes a duplication or a coupling that the entry measures; never against a settled decision.
Never change module boundaries, dependency direction or layers; never remove a port, hook or interface only for line count; never add a dependency or reflection; never change behavior, contracts, authorization, validation, transaction boundaries, exception semantics or side effects; never convert mutable Vaadin Binder DTOs to records without proof.

## What the researcher may decide
Nothing beyond the goal; conflicts with best practice or with the project's practice are highlighted, the decision is left to the user; a pattern against a settled decision is not proposed but listed as a rejected idea with that decision.
```

## Report (`report` or no argument)

1. Read the inventory. For every `open` entry check that its `Where` still matches the file (`Grep` for the symbol); an entry that no longer matches is named stale in the output and not edited.
2. Print one table ranked by estimated net reduction, then risk: ID, title, estimate, actual, priority, status. Totals count overlapping line ranges once. Print the coverage lines.
3. Print the final report. With no argument, end by asking which scope to search. Stop.

## Apply (`apply <ID>[,<ID>]`)

1. Take only entries with status `open` or `approved`, and one kind of change per batch, the kind of the first named ID; refuse by ID every entry with another status or another kind of change, naming the reason; the batch runs with the entries left, and when none is left, stop.
2. Present the plan in the Approval Rule format of `.claude/rules.md` with the exact files and edits, and ask whether to apply it; apply only after a direct yes (hard limits), and on it set the status `approved`.
3. Apply with the per-unit Haiku dispatch that step 2 of `.claude/commands/autopilot.md` defines; an entry of kind `pattern` goes to Opus instead. Review the diff, then set the status `applied`.
4. Run Verify steps 1 to 3 for the same IDs, without printing their report.
5. Print the final report and stop after the batch (hard limits). Never start the next batch yourself.

## Verify (`verify [<ID>]`)

1. Run `bash .claude/commands/reduce-code/check-batch.sh <the entry's files>`. Any `VIOLATION` stops the run and is reported.
2. Run the smallest relevant tests first (`build-and-test.sh --unit-test <module>` or `--integration-test <class>`), then the suite the Definition of Done of `.claude/rules.md` names, backgrounded through the activity monitor as its Scripts section requires. A failure stops the run and is reported.
3. Write `**Actual:**` on the entry: the `TOTAL` line (physical lines, comments and blank lines included), the test counts and the date; a `pattern` entry whose `TOTAL` is not a reduction must show the duplication or coupling it removed, or it does not pass; when the checks pass for an entry with status `applied`, set the status `verified`. On its own, print the final report.

## Models

- Haiku: everything mechanical and fully specified -- the per-finding verifiers of the review, counting and listing files, and an `apply` edit whose old and new text are stated exactly.
- Sonnet: reading a file set and judging it (the `code-reduction-reviewer` lens), and an `apply` edit that needs the exact behavior and constraints but no ready-made code.
- Opus: the `deep-researcher`, a step that needs design judgment, and an `apply` edit of a `pattern` entry.
- No model: counting lines and the rule checks (`check-batch.sh`), the topic bookkeeping scripts.

## Final report

Every mode ends with this template; write "none" for an empty part and "n/a" for a field the mode does not have.

```
## /reduce-code <mode> -- <scope or IDs>
Scope:          <scope>; files in scope <n>; read <n>; excluded <paths>
Not read:       <file -- reason>
Not accounted:  <files no lens read or explained>
Unreachable:    <what a reviewer could not reach>
Inventory:      <IDs added or changed, with status>
Stopped at:     <step and the question asked, or "done">
```
