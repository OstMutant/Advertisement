# improvement-131: `.claude/commands/feature.md`'s priority-emoji rubric doesn't match actual backlog practice

**Type:** improvement — process/documentation consistency, zero functional impact.
**Module:** `.claude/commands/feature.md` (step 5), `backlog/issues/*.md` (29 files as of this
writing).
**Priority:** ⚪ low — cosmetic/process consistency question, no functional impact; resolved by
backfilling the emoji rubric onto all pre-existing issues rather than dropping it (see "Resolution"
below); found via
`/deep-review` diff-mode run against commit `1852dc62`, independently confirmed by 2 of 4
specialized review lenses plus a dedicated validation subagent (see "How this was found" below).
**When:** independent, no blockers.

## Problem

`.claude/commands/feature.md` step 5 instructs, verbatim:

> Judge tier from the same rubric already used throughout this backlog: 🟢 cheap + low-impact,
> 🟡 high/medium ROI (real bug or high-value fix, proportionate effort), 🔵 larger tech-debt (no
> live bug, bigger effort or needs a design decision), ⚪ lowest (preventive/no observed impact,
> or blocked on other deprioritized work).

The phrase "already used throughout this backlog" is false as written. A fresh count against the
actual files: of 29 files in `backlog/issues/*.md`, **exactly 2** use an emoji in their own
`**Priority:**` line — and both of those are `improvement-129` and `improvement-130`, filed in the
same commit that first triggered this check, using the rubric because `/feature` told them to. Not
one pre-existing issue used this format before that commit. `improvement-121` comes closest
("lowest ⚪") but that's a plain English word with an emoji appended, not the clean emoji-first
format `feature.md` prescribes. The other 27 of 29 use plain English words/phrases exclusively
(`high`, `medium`, `low`, `low-medium`, `lowest`, `medium-high`, each usually followed by
explanatory prose after a dash).

So `/feature` currently instructs new issues to adopt a format that's inconsistent with ~93% of
this backlog's real, existing entries — the instruction describes an aspirational/one-off
convention as if it were established practice.

## How this was found

Run via the newly-created `deep-review` skill (`.claude/skills/deep-review/`), diff mode, against
commit `1852dc62` (which filed `improvement-129`/`improvement-130`). Two of the four parallel
review lenses were relevant to a docs-only diff (security-boundary and data-integrity correctly
reported no findings on a markdown diff); the SOLID/DRY lens confirmed no backlog duplication: the
CLAUDE.md-compliance lens raised this exact mismatch independently, with its own fresh grep count
(2 of 29). A dedicated, separate validation subagent then re-confirmed both the `feature.md` quote
and the count from scratch. Three independent checks, same numbers each time — this is the kind of
"verify, don't relay" chain the `deep-review` skill exists to enforce.

## Suggested fix

Pick one of two directions — this issue intentionally doesn't decide for you:

1. **Drop the emoji rubric from `feature.md` step 5**, replace it with guidance to use a plain
   English word/phrase (matching the actual prevailing convention: `low`/`medium`/`high`/
   `low-medium`/`medium-high`/`lowest`, each with a short justifying clause). Cheapest fix, zero
   retroactive work, and it stops new issues from silently drifting away from what the rest of the
   backlog looks like.
2. **Keep the emoji rubric and backfill it** onto the other 27 existing issues' `**Priority:**`
   lines so it actually becomes "the rubric already used throughout this backlog" as claimed —
   meaningfully more work (27 files to touch, each needing a tier judgment call, not just a
   mechanical find-replace since plain-word priorities don't map 1:1 onto the 4-tier emoji scale),
   for a purely cosmetic consistency gain.

Given the low stakes and that direction 1 is a two-line edit while direction 2 is a 27-file
judgment-call exercise, direction 1 is the more proportionate default — but this is explicitly the
user's call, not decided here.

If direction 1 is chosen: also decide whether to reformat `improvement-129`/`improvement-130`'s own
`**Priority:**` lines to match (they currently use the now-to-be-deprecated emoji format, having
followed `feature.md` correctly at the time they were filed).

## Related

- `backlog/issues/improvement-130-backlog-issues-folder-rename.md` — filed in the same commit that
  surfaced this finding; also a pure process/organizational proposal, same "low stakes, needs a
  decision" shape.
- `.claude/skills/deep-review/` — the skill whose diff-mode run found this.

## Resolution

Direction 2 chosen (backfill, not drop) — user's explicit call, made in chat immediately after this
issue was filed. All 27 pre-existing open issues in `backlog/issues/*.md` (plus `improvement-121`,
which already had "lowest ⚪" in word-then-emoji order, reordered to emoji-then-word to match
`improvement-129`/`improvement-130`'s convention) had their `**Priority:**` line's existing word/
phrase kept verbatim, with the appropriate tier emoji (🟢/🟡/🔵/⚪) inserted immediately after
`**Priority:**` — no wording removed or reworded, purely additive per file. `improvement-123`
(marked superseded by `improvement-124` at the top of its own file) was tiered ⚪ rather than 🟡
despite its "high" text, since blindly carrying the "high" signal forward on a superseded file
would misrepresent it as still actionable — noted inline in its own Priority line. This file's own
`**Priority:**` line was updated to match (⚪), completing the set. `feature.md`'s claim that this
is "the rubric already used throughout this backlog" is now actually true, rather than aspirational.
