# improvement-130: Rename `backlog/issues/` to a name that doesn't imply bugs-only

**Type:** improvement — organizational/documentation only, zero functional/runtime impact
**Module:** `backlog/` (the directory itself), `.claude/rules.md`, `.claude/commands/feature.md`,
`CLAUDE.md`, `backlog/BACKLOG.md`, `backlog/completed/BACKLOG-ARCHIVE.md`, and every existing file
under `backlog/issues/*.md` / `backlog/completed/issues/*.md` that cross-references another issue
via a relative `backlog/issues/...` or `backlog/completed/issues/...` path (58 files currently
match, confirmed via `grep -rl` across those two locations plus the rule/command/doc files above —
re-count at execution time since this number will drift as new issues are filed).
**Priority:** 🟢 cheap + low-impact
**When:** independent, no blockers — but should be scheduled for a moment when no other issue is
actively being filed/edited, since it touches `BACKLOG.md` and every issue file's own
cross-references; avoid running it concurrently with another in-flight issue-filing task.

## Problem

`backlog/issues/` is named as if it only tracks bugs ("issues"), which reads misleadingly given
what actually gets filed there today. Raised in conversation 2026-07-29 while discussing whether
the folder should also host "improvements" and "features" as single files, not just bug reports.

**Correction made during discussion, worth recording so it isn't re-litigated:** the folder
already supports this today — `.claude/commands/feature.md` step 1 already lets `/feature` assign
one of four prefixes (`improvement` default, `bug`, `feature`, `goal`) to a new file in the same
`backlog/issues/` directory, no subfolder split needed. So this issue is **purely a naming/rename
question**, not a missing-capability one — nothing about how issues get filed or cross-referenced
needs to change functionally.

## Suggested fix

Rename `backlog/issues/` → a more neutral name (candidates to pick between when this is actually
scheduled, not decided here): `backlog/active/`, `backlog/current/`, `backlog/backlog-items/`.
Mirror the choice for `backlog/completed/issues/` → `backlog/completed/<same-new-name>/` to keep
the open/closed pair symmetric.

Mechanical steps, in order:
1. `git mv backlog/issues backlog/<new-name>` and `git mv backlog/completed/issues
   backlog/completed/<new-name>` (preserves file history; a plain filesystem move would not).
2. Grep-and-replace every literal `backlog/issues/` and `backlog/completed/issues/` path across
   the 58 files found above — including relative "## Related" cross-references inside issue files
   themselves, not just the rule/command/doc files. A plain find-and-replace should be safe since
   `backlog/issues/` doesn't appear to have any other meaning in this codebase, but grep the
   replacement's result afterward to confirm zero remaining literal matches of the old path.
3. Re-read `.claude/rules.md`'s "Issue Lifecycle" section and `.claude/commands/feature.md` in
   full after the replace — both name the folder explicitly in multiple places and are the
   highest-risk spot for a missed reference, since they're re-read before every action per the
   rules.md header ("RE-READ ALL RULES BEFORE EVERY ACTION").
4. Spot-check a handful of issue files' "## Related" sections manually after the automated
   replace — these are free-text prose, not a consistent machine-checkable format, so an automated
   replace could miss a reference phrased differently than expected (e.g. `see improvement-045` by
   number only, with no literal path, wouldn't need changing at all; but `backlog/issues/
   improvement-045-....md` would).
5. Update this issue's own eventual "Resolution" note and move it to `backlog/completed/<new-
   name>/` (under whatever the new name turns out to be) as the very last step, since it can't
   reference its own future location before the rename happens.

**Not in scope:** changing what gets filed here, the `/feature` prefix mechanism, or the file
naming convention (`<prefix>-NNN-<slug>.md`) — only the directory name changes.

## Related

- `.claude/commands/feature.md` — already documents the multi-prefix (`improvement`/`bug`/
  `feature`/`goal`) mechanism this issue's "Problem" section clarifies is not actually missing.
- `.claude/rules.md` "Issue Lifecycle" — the section most likely to need careful re-verification
  after the rename, since it's re-read before every action.
