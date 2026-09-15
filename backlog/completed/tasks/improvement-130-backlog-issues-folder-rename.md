# improvement-130: Rename `backlog/issues/` to a name that doesn't imply bugs-only — ✅ Done (2026-09-15)

**Type:** improvement — organizational/documentation only, zero functional/runtime impact
**Module:** `backlog/` (the directory itself), `.claude/rules.md`, `.claude/commands/feature.md`,
`CLAUDE.md`, `backlog/BACKLOG.md`, `backlog/completed/BACKLOG-ARCHIVE.md`, and every existing file
under `backlog/issues/*.md` / `backlog/completed/issues/*.md` that cross-referenced another issue
via a relative `backlog/issues/...` or `backlog/completed/issues/...` path (91 files at execution
time — the original "58" filed with this issue had already gone stale).
**Priority:** 🟢 cheap + low-impact
**When:** done.

## Problem

`backlog/issues/` was named as if it only tracked bugs ("issues"), which read misleadingly given
what actually got filed there. Raised in conversation 2026-07-29 while discussing whether the
folder should also host "improvements" and "features" as single files, not just bug reports.

**Correction made during discussion, worth recording so it isn't re-litigated:** the folder
already supported this — `.claude/commands/feature.md` step 1 already let `/feature` assign one
of four prefixes (`improvement` default, `bug`, `feature`, `goal`) to a new file in the same
directory, no subfolder split needed. So this task was **purely a naming/rename question**, not a
missing-capability one — nothing about how tasks get filed or cross-referenced changed
functionally.

## Decision (2026-09-15) — name chosen, scope expanded to include terminology

**Name:** `backlog/tasks/`, mirrored to `backlog/completed/tasks/`. Rationale (user's own): these
are fundamentally "tasks," with `feature`/`bug`/`improvement`/`goal` as sub-kinds already handled
by the existing `<prefix>-NNN-<slug>.md` filename convention — nothing about that convention
changes, only the container folder's name and the generic word used to describe an entry.

**Scope expanded beyond the original path-only rename:** also rename the word "issue"/"issues" to
"task"/"tasks" in prose, wherever it refers to a tracked backlog work item — not just the literal
folder path. Confirmed via `.claude/nav/adr-index.md` search: no existing ADR already decided this
question one way or the other.

**Real count as of 2026-09-15 (re-counted, the original "58" was already stale):** 91 files
reference the literal path `backlog/tasks/` or `backlog/completed/tasks/`.

### The "issue" → "task" classification rule — not every occurrence renames

Checked every occurrence in `.claude/rules.md` (41) and `.claude/commands/feature.md` by hand
before writing this plan, since a blind find-and-replace would corrupt unrelated senses of the same
word:

- **Rename** when it names a tracked backlog work item — "the issue file," "issue number," "a new
  issue," "Issue Lifecycle," "issue/ticket number," "file an issue," etc.
- **Keep as "issue"** when it means something else entirely:
  - a generic problem/defect, not a backlog entry — "quality issue," "class of issue," "not an
    issue in `.sh` files"
  - the verb "to issue" (e.g. "issue the hook-satisfying step") — grammatically unrelated
  - SonarQube's own terminology ("mark the issue false positive in SonarQube itself") — an
    external tool's vocabulary this project doesn't control; renaming it here would misdescribe
    what the code/UI actually calls it

Apply the same two-bucket judgment call per occurrence in every other file touched, below — never
a blind sed across a whole file.

### Files in scope for the terminology rename (word-level, not just path)

Live/standing-instruction files only — each read start-to-finish, "issue" occurrences classified
per the rule above, only the tracked-item-sense ones changed:

- `.claude/rules.md` — heading "Issue Lifecycle" → "Task Lifecycle"; ~24 of the 41 occurrences are
  tracked-item-sense (already classified above, the rest stay).
- `.claude/commands/feature.md` — its own opening line ("Scaffold a new tracked issue..."), step 1
  scanning logic, step 5's "Issue Lifecycle" cross-reference, template's "## Related" hint text.
- `backlog/BACKLOG.md` — "Maintenance rules" section prose + any other tracked-item-sense mentions
  outside the per-row table cells (row cells themselves are per-file content, not rewritten here).
- `.claude/commands/autopilot.md`, `sync-docs.md`, `review.md`, `README.md` — wherever they mean
  "the backlog task file," not some other sense; classify each hit individually (`grep -n`), same
  as rules.md above.
- `.claude/nav/flows.md`, `.claude/nav/context-loading.md` — routing-table prose.
- `CLAUDE.md` — 2 occurrences, check each.

### Files explicitly OUT of scope for the terminology rename (path-only fix there)

- **`backlog/completed/BACKLOG-ARCHIVE.md`** — 269 occurrences of "issue," but this file is an
  **append-only historical record** ("This file is never reordered — entries stay in the order
  they were done"), the same character `.claude/rules.md` already protects for `DECISIONS.md`
  ("`DECISIONS.md` keeps its own append-only historical character... an ADR records the decision
  and why"). Rewriting 269 historical narrative occurrences purely for word choice is disproportionate
  and against that precedent — only its literal `backlog/tasks/`/`backlog/completed/tasks/` path
  substrings get fixed (mechanical), the prose narrative text stays exactly as originally written.
- **Individual files under `backlog/tasks/*.md` / `backlog/completed/tasks/*.md` themselves** —
  same point-in-time-record reasoning. Only their literal path cross-references to *other* files
  (in "## Related" sections etc.) get mechanically fixed; a file's own body prose (e.g. "this
  issue's scope," "found via /deep-review") is left as originally written, not reworded for
  vocabulary consistency.

### Mechanical steps, in order

1. `git mv backlog/issues backlog/tasks` and `git mv backlog/completed/issues
   backlog/completed/tasks` (preserves file history).
2. Grep-and-replace every literal `backlog/issues/` → `backlog/tasks/` and
   `backlog/completed/issues/` → `backlog/completed/tasks/` across all 91 files (safe mechanical
   substitution — confirm zero remaining old-path matches afterward via `grep -rl`).
3. Apply the word-level "issue"→"task" rename to the standing-instruction files listed above only,
   occurrence-by-occurrence per the classification rule (not `BACKLOG-ARCHIVE.md`, not individual
   task files' own prose).
4. Re-read `.claude/rules.md` and `.claude/commands/feature.md` in full after both passes — highest
   -risk spot for a missed or wrongly-classified reference, since both are re-read before every
   action.
5. Spot-check a handful of task files' "## Related" sections manually — free-text prose, an
   automated path replace could miss a reference phrased differently (e.g. `see improvement-045`
   by number only needs no change; `backlog/tasks/improvement-045-....md` does).
6. Update this issue's own "Resolution" note and move it to `backlog/completed/tasks/` as the very
   last step, since it can't reference its own future location before the rename happens.

## Resolution (2026-09-15)

Implemented exactly per the plan above, all 6 mechanical steps:

1. `git mv backlog/issues backlog/tasks` + `git mv backlog/completed/issues backlog/completed/tasks`.
2. Path substitution across all 91 files — two passes were actually needed: the first pass only
   caught the literal `backlog/issues/`/`backlog/completed/issues/` form; a second, broader sweep
   found 27 more files using a bare relative form (`](issues/...)`, no `backlog/` prefix, since
   those files already live inside `backlog/`) and a third sweep found 20 more using other relative
   forms (`` `issues/` `` in prose, `../../issues/...`). Verified zero remaining `issues/` path
   segments afterward except 2 confirmed false positives (`CLAUDE.md`'s "quality-gate/issues/metrics"
   and `infra-readme-standards/SKILL.md`'s "READMEs/issues/PRs/wikis" — both slash-separated concept
   lists, not paths).
3. Terminology rename applied occurrence-by-occurrence (not blind sed) to `.claude/rules.md`,
   `.claude/commands/feature.md`, `backlog/BACKLOG.md` (prose + table headers, not row-cell
   content), `autopilot.md`, `sync-docs.md`, `README.md`, `.claude/nav/flows.md`. `review.md` and
   `.claude/nav/context-loading.md` had zero "issue" occurrences to begin with.
4. `.claude/rules.md` and `.claude/commands/feature.md` re-read in full afterward — both
   internally consistent, "Task Lifecycle" cross-referenced correctly everywhere it's named.
5. Spot-checked several task files' own cross-references, then ran a full systematic check (every
   relative `.md` link under `backlog/`, target-file-exists check) — found 82 pre-existing broken
   relative links repo-wide, all confirmed to predate this task (same broken shape already existed
   under the old `issues/` name — verified via `git show HEAD` on one sample). Root causes: links
   written "same directory" back when both files were open, later broken when one side moved to
   `completed/`; a `completed/tasks/...`-style link missing its `../` hop; a few with a redundant
   extra `tasks/` path segment. Of those 82, the 15 whose *source* file lives in the currently-open
   `backlog/tasks/` (10 files) were fixed at the user's explicit follow-up request — each corrected
   to either add the missing `../completed/tasks/` hop or drop the redundant `tasks/` segment,
   verified afterward with the same systematic check (0 remaining broken links in `backlog/tasks/`).
   The remaining ~67, all with a source file already inside `backlog/completed/`, were deliberately
   left untouched per the same append-only-historical-record reasoning this task already applied to
   `BACKLOG-ARCHIVE.md` — out of scope here.
6. This file itself moved to `backlog/completed/tasks/` as the final step.

**Self-referential correction made during execution:** the mechanical path-substitution pass
initially also rewrote this file's own historical "was named X, renamed to Y" language (e.g. the
title briefly read "Rename `backlog/tasks/`..." after the sed pass touched its own body text) —
caught and corrected by hand so the "Problem"/title sections still correctly describe the *old*
`backlog/issues/` name being renamed away from, not the new one.

**Scope gap found and closed during final verification:** the original "files in scope" list
missed `.claude/agents/review/deep-review-orchestrator.md` (a standing-instruction agent
definition, same category as `.claude/commands/*.md`, just not listed) plus two doc-standards
skill files (`infra-doc-standards/SKILL.md`, `module-doc-standards/SKILL.md`) that quoted the
renamed `rules.md` rule headings verbatim. A repo-wide sweep of every `.claude/**/*.md` file for
remaining `issue`/`issues` occurrences (not just the originally-listed files) caught all three;
each classified and fixed the same occurrence-by-occurrence way as the rest.

## Related

- `.claude/commands/feature.md` — already documented the multi-prefix (`improvement`/`bug`/
  `feature`/`goal`) mechanism this task's "Problem" section clarified was not actually missing.
- `.claude/rules.md` "Task Lifecycle" (was "Issue Lifecycle") — the section needing the most
  careful re-verification after the rename, since it's re-read before every action.
- `.claude/rules.md`'s own "No task/ticket numbers... `DECISIONS.md` keeps its own append-only
  historical character" rule — the precedent this task's `BACKLOG-ARCHIVE.md`/individual-task-file
  carve-out followed.
