---
description: Find code this repository no longer needs to maintain, measure the saving and remove it only in approved batches -- discovery (read-only, via deep-review-orchestrator with the code-reduction-reviewer lens), report, apply <ID>, verify. Never touches marketplace-orchestrator, never commits.
argument-hint: "[module <name> | path <dir> | all | report | apply <ID>[,<ID>] | verify [<ID>]]"
disable-model-invocation: true
---

# /optimize

Reduce the total amount of code this repository has to write, understand, test and maintain, without
losing readability, explicitness, type safety, testability or the architecture. Four modes, chosen by
the first word of `$ARGUMENTS`. Discovery never edits application code; applying changes only the
findings the user names, after an approved plan, and stops after every batch.

Hard limits, for every mode: never touch `marketplace-orchestrator/`; never change module boundaries,
dependency direction or layers; never remove a Port, Hook or interface only for line count; never add
a dependency or reflection; never change behavior, contracts, authorization, validation, transaction
boundaries, exception semantics or side effects; never convert a mutable Vaadin Binder DTO into a record
without proof; never commit.

## The inventory

All findings live in one standing task in `backlog/tasks/` whose title contains `code-reduction
findings`. Find it by search on every run; do not write its file name anywhere. Its sections are
`## Coverage`, `## Findings` and `## Rejected ideas`. This command is the canonical home of the entry
format:

```
### OPT-<n>. <title>
**Status:** open | needs-poc | approved | applied | verified | rejected
**Priority:** ...
**Kind:** duplication | boilerplate | forwarding | indirection | form-state | test-setup | convention
**Where:** file:start-end and symbol
**Now:** a short real excerpt or a precise reference
**Proposed:** the change
**Estimate:** -removed +added = net, files, assumptions
**Also applies to:** other sites, each counted once
**Why it stays readable:** ...
**Risks and tests:** ...
**Backlog, rules, ADRs:** what was checked and its status
**Recommendation:** implement | investigate
**Actual:** written only by verify
```

`<n>` is the highest existing OPT number plus one. A rejected idea is one line under
`## Rejected ideas` with its reason. Each discovery run adds one line under `## Coverage`: date, scope,
files read, excluded paths, findings added.

## Modes

### `module <name>`, `path <dir>`, `all` -- discover

1. Refuse a scope inside `marketplace-orchestrator`. For `marketplace-app` as a whole, recommend
   `path` scopes of its packages first and go on only after a direct yes.
2. Start in the background:
   `Agent({subagent_type: "deep-review-orchestrator", prompt: "lenses: code-reduction-reviewer\nexclude: marketplace-orchestrator/**\noutput: json\n<scope>"})`.
3. Parse its JSON block. Findings with `confidence: high` and `recommendation: implement` get status
   `open`, all others `needs-poc`.
4. Show the summary: counts per status, overlaps with their backlog entries, rejected ideas and the
   `inspected` block. Ask once whether to add the entries, the rejected ideas and the coverage line to
   the inventory, and write only after a direct yes.
5. Stop. Application code is never edited in this mode.

### `report` (also an empty argument)

1. Read the inventory. For every open entry re-check that `Where` still matches the file (`Grep` for the
   symbol); name an entry that no longer matches as stale in the output and do not edit it.
2. Print one table ranked by estimated net reduction, then risk: ID, title, estimate, actual, priority,
   status. Totals count overlapping line ranges once. Print the coverage lines.
3. With an empty argument, end by asking which scope to search. Stop.

### `apply <ID>[,<ID>]`

1. Take only entries with status `open` or `approved`. One kind of change per batch.
2. Present the plan in the Approval Rule format (`.claude/rules.md`) with the exact files and edits,
   and ask. On a direct yes set the status `approved`.
3. Apply with the per-unit Haiku dispatch that step 2 of `.claude/commands/autopilot.md` defines
   (reference it, do not restate it). Review the diff.
4. Run `verify` for the same IDs. Set `applied`, then `verified` when verify passes.
5. Stop and wait for the user. Never go on to the next batch by yourself.

### `verify [<ID>]`

1. Run `bash .claude/commands/optimize/check-batch.sh <the entry's files>`. Any `VIOLATION` stops the run and
   is reported.
2. Run the smallest relevant tests first (`build-and-test.sh --unit-test <module>` or
   `--integration-test <class>`), then the suite the Definition of Done names, backgrounded through the
   activity monitor as the Scripts section of `.claude/rules.md` requires.
3. Write `**Actual:**` on the entry: the `TOTAL` line, the test counts and the date. The numbers are
   physical lines, comments and blank lines included.

## Rules by reference

The Approval Rule, never committing, the standing bucket for out-of-scope findings and the Definition
of Done are in `.claude/rules.md`; this command does not copy them. A finding already tracked elsewhere in
the backlog is linked, not copied.

## Disclosure

Every mode ends by saying what was inspected and what was not: the scope, the number of files read,
the paths excluded, and anything a reviewer could not reach.
