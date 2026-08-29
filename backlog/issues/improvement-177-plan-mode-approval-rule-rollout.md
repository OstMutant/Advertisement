# improvement-177: Project-wide Approval Rule → real Plan Mode rollout

**Type:** improvement — architectural/process, carved out of `improvement-176` finding 4
**Module:** `.claude/rules.md` (the "Approval Rule" section)
**Priority:** low — real doubt now attached, see "Reverted precondition" below; not urgent
**When:** independent, no hard blocker, but its own working precondition no longer holds — see
below before picking this up

## Reverted precondition (2026-08-28)

`improvement-176` finding 4's `autopilot.md`-scoped `EnterPlanMode`/`ExitPlanMode` migration —
the "real, working narrow instance" this issue was meant to generalize from — was implemented,
then **reverted the same day**, by explicit user request: `ExitPlanMode`'s plan file is
harness-assigned, with no way to point it at a chosen path, which directly conflicts with this
project's own standing rule that a plan lives in its tracked `backlog/issues/<n>.md` file, not a
side file. `autopilot.md` step 1 is back to the issue-file + chat-question gate.

**This is not just a lost example — the same conflict applies to this issue's own goal.** If the
project-wide Approval Rule moved to `EnterPlanMode`/`ExitPlanMode`, every plan for every task would
land in a harness-assigned file instead of the relevant `backlog/issues/<n>.md` file — exactly the
mismatch that got the narrower version reverted. This issue does not proceed until that conflict
itself has a real answer (e.g. confirming whether the plan can still be duplicated into the issue
file post-approval in a way that's actually acceptable, since that was tried at the `autopilot.md`
scope and rejected) — not assumed away a second time.

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

1. ❌ Precondition REVERTED — see "Reverted precondition" above. `improvement-176`'s
   `autopilot.md`-scoped Plan Mode migration was implemented then undone the same day; its
   `EnterPlanMode`/`ExitPlanMode` mechanics remain real and verified (no plan-text parameter, the
   plan is written to a harness-named file, `ExitPlanMode` already requests approval on its own),
   but that harness-named-file behavior is exactly what conflicted with this project's
   plan-lives-in-the-issue-file rule and got the narrower version reverted. Do not treat this step
   as satisfied.
2. Verify directly (not assumed) that the Approval Rule's specific requirements — the two-layer
   plain-language/technical structure, ending with a literal question, per-sub-change granularity
   ("scope-level approval is not a blanket pass"), the distinction between "confirms understanding"
   vs. "confirms approval" — can all still be expressed through `ExitPlanMode`'s own plan payload
   and the surrounding conversation. If any of these genuinely can't be preserved, that's a real
   finding to report, not something to silently drop.
3. ✅ DECIDED (2026-08-28), user-approved. The "replace vs. reinforce" framing was a false binary —
   the current Approval Rule text actually does two separate jobs that Plan Mode doesn't equally
   affect:
   - **The gate mechanism** ("present a plan, then stop and wait for explicit approval") — Plan
     Mode **replaces** this outright. Right now nothing mechanically stops an edit from happening
     before approval, only Claude's own discipline; `EnterPlanMode`/`ExitPlanMode` makes it a real,
     harness-enforced block. This also resolves the "ambiguous yes" failure mode by construction —
     approval becomes a distinct structured action (approving the plan file via `ExitPlanMode`),
     not an interpretation of whether a chat reply "sounds like" a yes.
   - **The plan's content spec** (two-layer plain-language/technical structure, ending with a
     literal question, per-sub-change granularity) — Plan Mode **doesn't replace this**, because it
     never dictated plan content in the first place; it only gates on when the plan is complete.
     This content spec **stays** in `.claude/rules.md`, but reframed as "what to write into the
     Plan Mode plan file," not as its own separate enforcement mechanism.
   - **Open sub-question, explicitly not resolved by this decision:** whether every sub-change
     within an already-approved larger task needs its own `EnterPlanMode`/`ExitPlanMode` round, or
     one approval covers the whole task with sub-changes as implementation detail. Left for the
     implementation step to work out concretely (per-sub-change re-entry is likely impractical for
     a long task; document whatever the actual answer turns out to be, don't assume).
4. Rewrite `.claude/rules.md`'s "Approval Rule" section per the decision above: replace the
   "present plan... then stop and wait" gate language with instructions to use
   `EnterPlanMode`/`ExitPlanMode`; keep the content-spec requirements (two-layer structure, literal
   question, sub-change granularity) reframed as what goes into the plan file. Check every other
   `.claude/commands/*.md`/`.claude/skills/*` file that currently references or assumes the
   prose-only version's specific mechanics, and update each one found.
5. ✅ DECIDED (2026-08-28), user-approved. `EnterPlanMode`'s plan file is harness-assigned — Claude
   cannot point it at a chosen path like `backlog/issues/<n>.md` directly. So the rule becomes:
   after `ExitPlanMode` is approved, **duplicate that same plan text into the relevant
   `backlog/issues/<n>.md` file** (when the task is tracked by one) — satisfies both the real,
   harness-enforced approval gate (Plan Mode) and this project's existing rule that every
   multi-step plan lives in its issue file, not only in chat or a transient harness file. Add this
   sequencing explicitly to the rewritten Approval Rule text in step 4 above.

**Implemented (2026-08-28):** `.claude/commands/autopilot.md` step 1 now includes this sequencing
directly — after `ExitPlanMode` is approved, duplicate the plan text into the relevant
`backlog/issues/<n>.md` file when `/autopilot` was invoked for a tracked issue. The equivalent
rewrite of `.claude/rules.md`'s project-wide "Approval Rule" (step 4 above) is not yet done.

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
