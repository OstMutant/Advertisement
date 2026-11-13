---
description: Run a full code review via the `deep-review-orchestrator` agent -- SOLID, DRY, KISS, YAGNI and project-precedent findings, each re-verified against the real file before reported. Never writes code.
argument-hint: "[current | <ref> | <a>..<b> | task <id> | module <name> | path <dir> | paths <file>[,<file>] | all]"
---

# /deep-review

Runs `deep-review-orchestrator` on one scope, shows its Report, files its confirmed findings with
`ReportFindings`, and writes a prepared backlog task only after a direct yes. Never writes code and
never re-verifies findings itself: verification is the orchestrator's job.

## Arguments

`$ARGUMENTS` is the scope and is passed to the orchestrator unchanged; its Input section owns the
exact git commands. This command passes only the scope; the orchestrator's option lines are for
commands that dispatch it directly.

| Argument | Reviewed |
|---|---|
| none, `current` | the uncommitted working-tree changes (default) |
| `<ref>` (e.g. `HEAD~3`) | that one commit's own changes (committed changes only) |
| `<a>..<b>` (e.g. `HEAD~5..HEAD~2`) | the changes between two refs, nothing uncommitted |
| `task <id>` | every commit of that task plus the uncommitted changes; `<id>` is the short id of the commit messages (`<prefix>-NNN`), not the task file name with its slug |
| `module <name>` | every file in that module's `src/main` |
| `path <dir>` | every file under `<dir>`, tests included |
| `paths <file>[,<file>]` | exactly those files |
| `all` | the whole repo, one module's `src/main` at a time, modules one after another |

## Steps

1. **Dispatch** `Agent({description: "Code review", subagent_type: "deep-review-orchestrator", prompt: "$ARGUMENTS"})`.
   The orchestrator is read-only by its own `tools:` and runs on its own `model:`; no override.
   An empty `$ARGUMENTS` reviews the uncommitted changes.
2. **Read the result in full and check it.** If the result is "nothing to review", print that and
   stop. If the call failed, or the result has no `## Report` with its five sections, print the
   error or the result as received and stop.
3. **Show** the Report section in full, and the "Overlaps", "Doc/code mismatches" and "Needs human
   review" lists when they are not `none`.
4. **File findings.** If the result has a `ReportFindings` JSON block, call `ReportFindings` with
   it unchanged, an empty `[]` included; if the block does not parse as JSON, print the parse
   error and do not call it.
5. **Prepared tasks.** For each prepared `backlog/tasks/*.md` file in the result, ask two
   questions in the Approval Rule format of `.claude/rules.md`, each written only after its own
   direct yes, nothing on any other answer:
   a. the filename and content: write the file with `Write`?
   b. only after a yes to a, read `backlog/BACKLOG.md` for the position, then its ranked row for
      the Priority order table (the Task Lifecycle section of `.claude/rules.md`), with the exact
      position and text: write it? If declined, the written task file stays and the final report
      lists its row as pending.
6. **Print the final report** below and stop.

## Final report

```
## /deep-review <scope>
Report:            shown above
Needs human review: <count>, or none
ReportFindings:    called with <n> findings | not called -- <reason>
Prepared tasks:    <filename -- file: written | declined | awaiting answer; BACKLOG.md row: written | pending (declined) | awaiting answer>, or none
Stopped at:        <step and the question asked, or "done">
```
