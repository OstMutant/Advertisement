# improvement-176: `/autopilot` — fix real bugs, close a certification-verified destructive-action gate

**Type:** bug + improvement — `.claude/commands/autopilot.md` review findings, cross-checked against
the private "Claude Certified Architect" certification document
(`/app/private/AICertificationAndAudit/AICertification.txt`)
**Module:** `.claude/commands/autopilot.md`, `.claude/settings.json` (hooks)
**Priority:** 🔴 top — moved to the top of the backlog per explicit user request, 2026-08-28
**When:** independent, no blockers

## Current state

Reviewed `.claude/commands/autopilot.md` directly and verified its claims against real repo state
and the real certification text (`grep`, not from memory — same discipline
`improvement-160-certification-coverage-map.md`/`certification-matching.md` already established).
Four findings, two are plain bugs, two are certification-informed gaps.

### 1. Duplicate step numbering — plain bug

The file has two steps both numbered "4.": *"4. Verify like it's going into the final report, not
like a checkbox"* (line 103) and *"4. Document as you go, not as an afterthought"* (line 113),
followed immediately by *"5. One final report..."* (line 119). The numbering is broken — should be
sequential 4/5/6.

### 2. Stale/false claim about the git-commit hook — plain bug, contradicts this repo's own `rules.md`

Lines 53-57 state: *"`git commit` remains off-limits without the user's explicit
'зроби коміт'/'commit' — this is a separately hard-enforced rule in `.claude/rules.md` that
`/autopilot` does not and cannot override (**this repo has no actual git commit hook enforcing
it** — the enforcement is Claude's own compliance with that rule, every session, not a mechanism
outside Claude's control)."*

**Confirmed false, directly, in this session:** `.claude/settings.json` has a real
`PreToolUse:Bash` hook (line 20) that inspects every Bash command for `git commit` and blocks it
(exit 2, `"git commit BLOCKED — say \"зроби коміт\" first"`) unless a recently-touched
`/tmp/commit-approved` marker file exists. `.claude/rules.md` itself already documents this exact
hook under "Two-call rule for any hook-gated commit step." `autopilot.md`'s claim directly
contradicts its own project's `rules.md` — either it predates the hook being added, or was never
checked against real repo state. Needs correcting to describe the real mechanism (hook + marker
file), not "no hook exists."

### 3. Certification-verified gap — destructive-action safety net is prompt-only, not hook-enforced, in exactly the highest-risk scenario for that

Verified via direct `grep` against the real certification text (Domain 1, "Refund threshold
enforcement" example): the document explicitly contrasts prompt-based vs. hook-based enforcement
for business-risk operations —

> Prompt approach: "For refunds above $500, escalate to a human agent." Works most of the time. A
> single failure means a large refund processed without approval.
>
> Hook approach: Intercept `process_refund`, check the amount, block if above $500 and route to
> human escalation. Works 100% of the time.

`autopilot.md`'s own step 2 states: *"DO still stop and ask before anything the plan didn't cover
and that is genuinely destructive/hard-to-reverse — a schema change beyond what was scoped, a
force-push, deleting data outside a disposable dev volume..."* — this is **prompt-only**
enforcement (Claude's own compliance mid-run), unlike the git-commit case (finding 2), which
already has a real hook. `/autopilot`'s entire premise is an unattended run — *"no one is watching
until the final report"* (step 2's own wording) — which is precisely the scenario where a
prompt-only safety net is weakest per the certification's own reasoning: there's no human present
in real time to catch a single failure before it lands.

### 4. Re-surfaced known gap — Plan Mode vs. the hand-rolled Approval Rule, now concretely observed in `autopilot.md` step 1

Already logged (status `idea`, unimplemented) in the now-closed `improvement-160`'s coverage map,
row `D3-6`: *"this repo's hand-written 'Approval Rule' in `.claude/rules.md` reimplements in prose
what Claude Code's built-in Plan Mode (`EnterPlanMode`/`ExitPlanMode`) already provides natively."*
`autopilot.md`'s own step 1 ("Plan, once... restate it... plain-language layer first, technical
layer second... Then stop and wait") is the sharpest concrete instance of this general finding —
verified directly against the certification's real "Plan Mode vs Direct Execution" section (lines
1782-1840): *"Plan mode is for complex tasks where you need to explore the codebase, evaluate
multiple approaches, and design a strategy before making changes... Plan mode enables safe
exploration and design. Claude reads the codebase, analyses dependencies, and proposes an approach
— all without modifying any files."* This is structurally the same thing `autopilot.md` step 1
does via prose instruction, not the real tool.

## Why change

Findings 1-2 are real correctness/documentation bugs a reader (human or Claude, mid-`/autopilot`-run)
would trust at face value — finding 2 specifically understates the real safety net already in
place, which could lead to unnecessary caution or, worse, confusion about what's actually enforced.
Finding 3 is a genuine risk gap in exactly the run mode designed to go unsupervised the longest.
Finding 4 is a known design duplication, now with a concrete, high-value example.

## Approach

1. Fix the numbering (1-6 sequential).
2. Correct the git-commit-hook paragraph to describe the real mechanism (`PreToolUse:Bash` hook +
   `/tmp/commit-approved` marker, per `.claude/rules.md`'s "Two-call rule").
3. Evaluate hook-enforcing at least the clearest destructive actions autopilot's step 2 currently
   only asks Claude to self-police: `git push --force`, `git reset --hard`, `git clean -f`,
   `git branch -D` (the same list root `CLAUDE.md`'s "Git Safety Protocol" already names) — a
   `PreToolUse:Bash` hook mirroring the existing commit-blocking one's shape. Schema changes and
   "deleting data outside a disposable dev volume" are harder to pattern-match mechanically
   (no fixed command signature); scope the hook to what's actually a fixed, matchable command
   pattern, and keep the rest prompt-enforced with an explicit note that it's a lower-reliability
   tier, per the certification's own honesty about prompt-approach reliability.
4. Decide (design discussion, not implementation): whether real Plan Mode
   (`EnterPlanMode`/`ExitPlanMode`) should replace or reinforce `autopilot.md` step 1's hand-rolled
   plan-then-wait gate, and whether the same applies to the standing Approval Rule in
   `.claude/rules.md` more broadly (bigger, cross-cutting decision — may need to stay scoped to
   `autopilot.md` alone here, or get split into its own issue once a direction is picked).

## Testing strategy

Steps 1-2 are documentation fixes — no test needed, re-read the file for correctness. Step 3 (new
hook) — verify directly the same way the existing commit-blocking hook was verified: trigger the
blocked command, confirm it's rejected; trigger it with the required marker/confirmation, confirm
it proceeds. Step 4 is a design decision, not code — no test.

## Related

- `improvement-160` (closed, investigation archived in `completed/issues/`) — original source of
  the Plan Mode vs. Approval Rule idea (`D3-6`), re-surfaced here with a concrete example.
- `/app/private/AICertificationAndAudit/certification-matching.md` — the established format/
  discipline for certification-vs-repo matching this issue's findings 3-4 follow (real quote,
  real repo file, explicit reasoning — never assumed).
- `.claude/rules.md` — "Two-call rule for any hook-gated commit step" already documents the real
  git-commit hook `autopilot.md` incorrectly claims doesn't exist.
