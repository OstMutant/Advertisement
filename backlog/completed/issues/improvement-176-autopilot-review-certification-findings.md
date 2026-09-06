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
Five findings: two plain bugs, two certification-informed gaps, one stale reference to a
now-deleted project mechanism.

### 1. Duplicate step numbering — plain bug — ✅ FIXED (2026-08-28)

The file has two steps both numbered "4.": *"4. Verify like it's going into the final report, not
like a checkbox"* (line 103) and *"4. Document as you go, not as an afterthought"* (line 113),
followed immediately by *"5. One final report..."* (line 119). The numbering is broken — should be
sequential 4/5/6.

**Fixed:** renumbered to 1/2/3/4/5/6 sequential.

### 2. Stale/false claim about the git-commit hook — plain bug, contradicts this repo's own `rules.md` — ✅ FIXED (2026-08-28)

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

**Fixed:** the paragraph now describes the real `PreToolUse:Bash` hook and the
`/tmp/commit-approved` marker mechanism, cross-referencing `.claude/rules.md`'s "Two-call rule"
section instead of claiming no hook exists.

### 3. Certification-verified gap — destructive-action safety net is prompt-only, not hook-enforced, in exactly the highest-risk scenario for that — ❌ DECIDED AGAINST (2026-08-28)

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

**Decision (2026-08-28), user's explicit call:** do not hook-enforce these actions. A hard
`PreToolUse` block, unlike the git-commit case, has no equivalent "explicit approval, then proceed"
release valve here — the commit hook's marker-file mechanism works because a plain "зроби коміт"
naturally produces one, but a force-push/`reset --hard`/schema-change approval doesn't map onto the
same shape without real added complexity. Blocking these commands outright would mean `/autopilot`
could never complete a run that genuinely, legitimately needs one — defeating the point of
`/autopilot` being able to finish a task end-to-end. The user's own framing: these operations
should stay something `/autopilot` does **only in genuine extreme necessity**, still gated by
stop-and-ask (already how `autopilot.md` step 2 reads today — no wording change needed), not by a
hard technical block. The certification's own prompt-vs-hook gap stands as a documented finding,
correctly identified, but not acted on here — a real, considered trade-off, not an oversight.

### 4. Re-surfaced known gap — Plan Mode vs. the hand-rolled Approval Rule, now concretely observed in `autopilot.md` step 1 — ✅ FIXED, then REVERTED (2026-08-28)

**Reverted, same day, explicit user request.** The `EnterPlanMode`/`ExitPlanMode` migration
described below was implemented, then undone: `ExitPlanMode`'s plan file is harness-assigned (no
way to point it at a chosen path), which fought this project's own standing rule that a plan lives
in its tracked `backlog/issues/<n>.md` file. `autopilot.md` step 1 is back to the issue-file +
chat-question gate. `improvement-177` (which was unblocked on the assumption this fix was a stable,
working precedent to generalize from) needs re-checking — its own "precondition met" framing no
longer holds.

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

**Scope decision (2026-08-28), user's explicit call:** implement this **only for `autopilot.md`
step 1** in this issue — not the standing project-wide Approval Rule in `.claude/rules.md`, which
governs every normal conversation, not just `/autopilot` runs. The user wants to try the
project-wide version too, eventually, but as its own separate, deliberately scoped-larger issue —
filed as [`improvement-177`](improvement-177-plan-mode-approval-rule-rollout.md) — so a working,
narrow instance lands first and can inform whether the broader rollout is worth doing.

**Fixed:** `autopilot.md` step 1 now calls `EnterPlanMode`, writes the plan (plain-language layer
first, technical layer second, same structure the Approval Rule always used) into Plan Mode's own
plan file, then calls `ExitPlanMode` instead of ending a chat message with a question and manually
waiting. Real mechanics verified directly against the actual tool schemas before writing this
(not assumed from the certification's abstract description) — two details that matter for
`improvement-177`'s own broader rollout too:
- `ExitPlanMode` **does not take the plan text as a parameter** — it reads the plan from the file
  the harness already told Claude to write it to (named in Plan Mode's own system message), then
  surfaces that file's content to the user for approval. The plain-language/technical two-layer
  structure is preserved by writing the plan file in that shape, not by anything the tool call
  itself carries.
- `ExitPlanMode` **already requests user approval on its own** — its own docs explicitly warn
  against also using `AskUserQuestion` to ask "does this look OK?" afterward, since that would
  duplicate what the tool call itself does. `autopilot.md`'s old "end the message with... then stop
  and wait" framing is gone for this reason, not just replaced with new wording.

### 5. Step 3 describes a review process that doesn't match either of this project's real review mechanisms — likely a stale reference to the now-deleted `deep-review` skill — ✅ FIXED (2026-08-28)

Step 3 says to run *"`/code-review --fix`... **Always run `/code-review`'s full documented process
end to end, exactly as that skill specifies it**"* and then describes a specific two-phase shape:
*"Phase 1: all 8 finder angles, each launched as its own Agent-tool call... Phase 2: every
surviving candidate gets its own 1-vote verifier Agent-tool call."*

This project has two real, distinct review mechanisms today, and neither matches that description:

- **`/code-review`** — a built-in, global Claude Code skill (not a file in this repo, so its actual
  internal shape can't be verified by reading source here). Has effort levels
  (low/medium/high/xhigh/max/ultra) and a real `--fix` capability (applies findings directly to the
  working tree) per its own description — this part of step 3 is plausible.
- **`/review`** — this project's own command, dispatching the `deep-review-orchestrator` agent
  (`.claude/agents/review/deep-review-orchestrator.md`). Real, named, reusable subagent definition
  (exactly what `improvement-160`'s `D1-2` certification finding said this repo was missing).
  Dispatches **2** finder lenses (`dry-kiss-yagni-reviewer` + `solid-reviewer`), not 8, each
  candidate verified by its own fresh subagent (matches step 3's "1-vote verifier" idea) — but is
  **read-only by design** (`tools: Agent, Read, Bash, Grep, Glob`, no `Write`, explicit "Never write
  anything" rule). It can only return a `ReportFindings` JSON payload and prepared-but-unwritten
  issue-file content — never applies a fix itself.

The "8 finder angles" shape matches neither. It's the closest in spirit to this project's own
**now-deleted** `.claude/skills/deep-review/references/diff-mode.md`/`full-mode.md` (removed per
`improvement-171`, replaced by `deep-review-orchestrator`) — though even that had 4 lenses in
diff-mode, not 8, so the exact number was never verified as accurate for any real mechanism in this
repo, past or present. Whatever step 3 originally described, it's stale relative to current repo
state either way.

**Practical implication:** `/review`/`deep-review-orchestrator` cannot directly replace
`/code-review --fix`'s described role, since it never writes anything — but it *can* be used as the
find-and-verify half of the same job: call `/review`, receive its verified findings + prepared
`ReportFindings` JSON, then have `/autopilot` itself (which has `Write`/`Edit`) apply each finding —
the same "apply directly unless genuinely destructive" judgment step 3's own text already describes
for `/code-review`'s findings. This also more cleanly matches the certification's own "Session
Context Isolation" pattern (an isolated subagent with zero access to the generation session's
reasoning) than an unverifiable reference to a global skill's internals.

## Why change

Findings 1-2 are real correctness/documentation bugs a reader (human or Claude, mid-`/autopilot`-run)
would trust at face value — finding 2 specifically understates the real safety net already in
place, which could lead to unnecessary caution or, worse, confusion about what's actually enforced.
Finding 3 was a genuine risk gap in exactly the run mode designed to go unsupervised the longest —
raised and considered, decided against per the trade-off recorded above.
Finding 4 was a known design duplication, now fixed with a concrete, high-value example — see
"Fixed" note above for the two real mechanics (no plan-text parameter, built-in approval request)
that had to be verified before rewriting rather than assumed. Finding 5 means
`/autopilot`'s self-review step is currently specified against a mechanism that may not exist in
this exact shape — risking either a confused execution mid-run (trying to follow "8 finder angles"
against a skill that doesn't work that way) or, worse, silently falling back to something weaker.

## Approach

1. ✅ DONE — Fix the numbering (1-6 sequential).
2. ✅ DONE — Correct the git-commit-hook paragraph to describe the real mechanism
   (`PreToolUse:Bash` hook + `/tmp/commit-approved` marker, per `.claude/rules.md`'s "Two-call
   rule").
3. ❌ DECIDED AGAINST — no hook. A hard block on these commands would prevent `/autopilot` from
   ever completing a run that genuinely needs one, with no clean "explicit approval, then proceed"
   release valve the way the commit hook's marker file provides. Stays prompt-enforced,
   stop-and-ask, done only in genuine extreme necessity — no change to `autopilot.md`'s existing
   wording for this needed.
4. ✅ DONE — Replaced `autopilot.md` step 1's hand-rolled plan-then-wait gate with real Plan Mode
   (`EnterPlanMode`/`ExitPlanMode`), scoped to `autopilot.md` only per the decision above. Verified
   the real tool mechanics directly (schema, not assumption) before rewriting — see "Fixed" note
   above. The broader, project-wide Approval Rule question is out of scope here — see
   `improvement-177`.
5. ✅ DONE — Rewrote step 3 (now numbered 3, per finding 1's fix) to dispatch this project's own
   `/review` (`deep-review-orchestrator`) as the find-and-verify stage instead of the stale
   "8 finder angles" description. `/autopilot` now explicitly: dispatches
   `deep-review-orchestrator`, reads its result, calls `ReportFindings` with the returned payload,
   applies every auto-report-bucket finding directly, presents any prepared-but-unwritten issue
   file for approval before writing it, and carries "needs human review" findings into the final
   report. Step 6 (final report)'s own two mentions of `/code-review`'s "8 finder-angle agents"
   were updated to reference `deep-review-orchestrator`'s real shape too, for consistency. The
   built-in `/code-review --fix` is no longer referenced in this step at all — decided in favor of
   this project's own, verifiable, already-Session-Context-Isolated mechanism rather than keeping
   an unverifiable fallback to a tool whose actual internals can't be inspected from this repo.

## Testing strategy

Steps 1-2 are documentation fixes — no test needed, re-read the file for correctness. Step 3 (new
hook) — verify directly the same way the existing commit-blocking hook was verified: trigger the
blocked command, confirm it's rejected; trigger it with the required marker/confirmation, confirm
it proceeds. Step 4 is a design decision, not code — no test. Step 5 — dry-run the rewritten step 3
against a small real diff in this session (dispatch `/review`, confirm the returned JSON/prepared
content is actually usable as described) before trusting it in an unattended `/autopilot` run.

## Related

- `improvement-160` (closed, investigation archived in `completed/issues/`) — original source of
  the Plan Mode vs. Approval Rule idea (`D3-6`), re-surfaced here with a concrete example.
- `improvement-177` — the project-wide Approval Rule → Plan Mode question, carved out as its own
  issue since it's a bigger, cross-cutting decision affecting every conversation, not just
  `/autopilot` runs; this issue's finding 4 stays scoped to `autopilot.md` alone.
- `/app/private/AICertificationAndAudit/certification-matching.md` — the established format/
  discipline for certification-vs-repo matching this issue's findings 3-4 follow (real quote,
  real repo file, explicit reasoning — never assumed).
- `.claude/rules.md` — "Two-call rule for any hook-gated commit step" already documents the real
  git-commit hook `autopilot.md` incorrectly claims doesn't exist.
- `.claude/commands/review.md` / `.claude/agents/review/deep-review-orchestrator.md` — this
  project's real, current review mechanism finding 5 proposes wiring into step 3 instead of the
  unverifiable/stale reference to `/code-review`'s internals.
- `improvement-171` — deleted `.claude/skills/deep-review` and replaced it with
  `deep-review-orchestrator`; finding 5's "8 finder angles" description most likely traces back to
  that now-removed skill's process, never updated in `autopilot.md` after the replacement.

## Status — closed (2026-08-28)

All 5 findings resolved: 1, 2, 4, 5 fixed directly in `.claude/commands/autopilot.md`; 3 decided
against, by explicit user call, with the trade-off recorded above. A follow-up full-document
certification audit of `autopilot.md` (user request, same session) found one confirmed existing
match (parallel Agent/Task spawning, Domain 1 "latency awareness" — already correctly implemented,
no change needed) and one new candidate finding (whether `.claude/rules.md`'s `@import` content
survives `/compact` the same way root `CLAUDE.md` itself does, per the certification's "What
Survives Compaction" section) — the user decided to treat the latter as an accepted precaution
(the existing "re-read rules.md" discipline) rather than open a new issue for it. Nothing
still-open remains in this issue.
