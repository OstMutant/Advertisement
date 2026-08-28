# improvement-177: Project-wide Approval Rule → real Plan Mode rollout

**Type:** improvement — architectural/process, carved out of `improvement-176` finding 4
**Module:** `.claude/rules.md` (the "Approval Rule" section)
**Priority:** medium — a real, considered idea, not urgent
**When:** independent, no blockers — `improvement-176` finding 4 (the `autopilot.md`-scoped Plan
Mode migration) has landed and shipped (closed 2026-08-28), giving this issue a real, working
narrow instance to generalize from

## Current state

`.claude/rules.md`'s "Approval Rule" hand-implements a plan-then-wait gate entirely in prose,
applied to **every** conversation with Claude in this project, not just `/autopilot` runs:
present a plan in two layers (plain-language, then technical), end with a literal question, then
stop and wait for an explicit answer — enforced purely by Claude's own discipline re-reading
`.claude/rules.md` before every action, with no mechanical/tool-level restriction preventing an
edit from happening anyway.

Claude Code has a real, built-in Plan Mode (`EnterPlanMode`/`ExitPlanMode` tools) that provides
the same shape of behavior — explore/propose without modifying files, then an explicit exit/approval
step — as a harness-level mechanism, not a prompt convention. Verified directly against the private
certification document (`/app/private/AICertificationAndAudit/AICertification.txt`, "Plan Mode vs
Direct Execution" section): *"Plan mode enables safe exploration and design. Claude reads the
codebase, analyses dependencies, and proposes an approach — **all without modifying any files**."*

`improvement-160`'s coverage map first logged this as `D3-6` (unimplemented `idea`).
`improvement-176` finding 4 made it concrete by observing it directly in `autopilot.md` step 1, and
implements the narrow, `autopilot.md`-only version of the fix. This issue is the follow-up,
explicitly deferred: apply the same migration to the project-wide Approval Rule itself.

## Why change

The prose-only Approval Rule has a documented failure mode already: "re-read rules.md before every
action" is a discipline that degrades under real conditions — this project's own session memory
carries a standing note that this exact re-reading habit visibly lapses right after a context
compaction (earlier re-Reads of `rules.md` get summarized away, and the habit of re-reading doesn't
automatically resume on its own). A mechanical mode switch doesn't degrade the same way; the harness
enforces it regardless of how much the conversation has grown or how confident Claude feels a plan
is "obvious enough to skip."

## Expected benefit

The same benefits already reasoned through for `improvement-176` finding 4, at project scope
instead of one command's scope:
- Real tool-level restriction on file edits during the plan phase, not just a self-imposed rule.
- A structured, harness-native approval UI moment instead of free-form chat text a user could skim
  past.
- Removes an entire class of "did I actually re-read rules.md recently enough" risk — the
  mechanism doesn't rely on Claude remembering to re-check a file.
- Composes with the `Explore` subagent pattern for keeping large discovery/investigation output out
  of the main conversation during the plan phase.

## Approach

1. ✅ Precondition met — `improvement-176`'s `autopilot.md`-scoped Plan Mode migration has landed
   (closed 2026-08-28). Its real, verified `EnterPlanMode`/`ExitPlanMode` mechanics (no plan-text
   parameter — the plan is written to a file the harness names; `ExitPlanMode` already requests
   approval on its own) are recorded there and apply here directly, not just as an example to
   generalize from.
2. Verify directly (not assumed) that the Approval Rule's specific requirements — the two-layer
   plain-language/technical structure, ending with a literal question, per-sub-change granularity
   ("scope-level approval is not a blanket pass"), the distinction between "confirms understanding"
   vs. "confirms approval" — can all still be expressed through `ExitPlanMode`'s own plan payload
   and the surrounding conversation. If any of these genuinely can't be preserved, that's a real
   finding to report, not something to silently drop.
3. Decide whether Plan Mode fully *replaces* the Approval Rule's text in `.claude/rules.md`, or
   *reinforces* it (Plan Mode as the mechanical backstop, the prose rule staying as the detailed
   behavioral spec Plan Mode's own UI doesn't carry) — a real design choice, not a foregone
   conclusion either way.
4. Rewrite `.claude/rules.md`'s "Approval Rule" section accordingly, and check every other
   `.claude/commands/*.md`/`.claude/skills/*` file that currently references or assumes the
   prose-only version's specific mechanics.

## Testing strategy

Not code — a process/behavior change. Verify by dry-running a few real multi-step tasks of
different shapes (a small fix, a larger multi-file change, an ambiguous one) through the new
Plan-Mode-based flow and confirming the approval gate still behaves as intended for each, before
trusting it as the new standing default.

## Related

- `improvement-176` finding 4 — the narrower, `autopilot.md`-only version of this same migration,
  sequenced to land first.
- `improvement-160` (closed) — `D3-6`, the original unimplemented idea this issue and
  `improvement-176` finding 4 both trace back to.
