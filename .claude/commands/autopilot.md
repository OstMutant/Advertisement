Plan once, approve once, then execute the whole task end-to-end without further check-ins —
implementation, all relevant tests, docs/ADR, issue lifecycle — reporting back only when
genuinely done (or genuinely blocked).

Usage: /autopilot <task description>
Example: /autopilot implement the tracked city dictionary + geo filter feature

This exists because the normal flow (plan → approve → implement → ask again before testing →
ask again before docs → ...) is the right default for most work, but is needless friction for a
task that's already been scoped into a clear, concrete plan and just needs someone to go build
it. `/autopilot` is that explicit, durable authorization for one task: the user is opting out of
per-step check-ins for this run, not for the project in general — the standing Approval Rule in
`.claude/rules.md` still applies to the *next* task unless `/autopilot` is invoked again.

Steps:

1. **Plan, once.** If $ARGUMENTS references a plan already fully hashed out earlier in this
   conversation (or a backlog issue file with a complete `## Suggested fix`/`## Approach`),
   synthesize it into the plan instead of re-deriving it from scratch; otherwise research the
   codebase first (read the relevant files, find the pattern to mirror, check for prior art).
   Either way, write the complete plan into the relevant `backlog/issues/<n>.md` file per the
   Approval Rule (plain-language layer first, technical layer second), then present a short
   summary from that file in chat, ending with a literal question. Send a `PushNotification` at
   this point too — the plan is ready and the user may have stepped away while it was being
   drafted. **Then stop and wait for an explicit answer to that exact question.** This is the one
   and only gate — do not skip it even if the plan feels obvious, and do not infer approval from a
   directive-sounding reply that isn't a literal answer to the question asked.

2. **On approval, go dark on routine questions.** For the rest of this run:
   - `.claude/rules.md` itself says "re-read before every action" — a rule that's easy to drift
     from precisely in a long, unattended autopilot run where context fills with implementation
     detail. Re-`Read` `.claude/rules.md` fresh at the start of this step, and again before each
     later major phase (verification, documentation, final report) — not just once at the top of
     the run. Named example of what drifts first during a long run: the comment-brevity rule
     (one line or none, ever) — every code comment written during this run gets checked against it
     before moving on, not just left for step 3's `/code-review` pass to catch.
   - Resolve implementation questions yourself: grep/read the codebase for the existing pattern,
     mirror it, note the decision in the final report rather than asking mid-flight. This is the
     entire point of `/autopilot` — per the user's own framing, "questions that come up along the
     way, resolve them yourself, having studied the problem in detail and looked at similar things
     in the code."
   - Do NOT stop to ask about routine follow-up steps that were implicitly part of the approved
     plan: running tests, redeploying, rerunning Playwright after a fix, writing the ADR, moving
     the issue to `completed/`. Chain them straight through.
   - DO still stop and ask before anything the plan didn't cover and that is genuinely
     destructive/hard-to-reverse — a schema change beyond what was scoped, a force-push, deleting
     data outside a disposable dev volume, or anything matching "Executing actions with care" in
     the top-level system instructions. Autopilot suspends check-ins for *implementation*
     decisions, not for irreversible actions outside the approved scope.
   - DO still surface a genuinely serious discovery immediately if it changes the plan's scope or
     reveals a real bug unrelated to the task (per the standing "report issues immediately"
     preference) — but keep moving once flagged; don't stall waiting for a reply unless the
     discovery actually blocks progress.
   - `git commit` remains off-limits without the user's explicit "зроби коміт"/"commit" — this is
     a separately hard-enforced rule in `.claude/rules.md`, backed by a real `PreToolUse:Bash` hook
     in `.claude/settings.json` that blocks any `git commit` invocation lacking a fresh
     `/tmp/commit-approved` marker (see `.claude/rules.md`'s "Two-call rule for any hook-gated
     commit step"). `/autopilot` does not and cannot override it. Land the run in a "ready to
     commit" state and say so in the final report; do not commit automatically.
   - **Every long-running script call in an autopilot run uses `run_in_background: true` by
     default** — this overrides `scripts/CLAUDE.md`'s normal "run synchronously so the user sees
     streaming output" guidance, which exists for interactive sessions where someone is watching;
     autopilot's whole premise is that no one is watching until the final report. Pair each
     backgrounded call with a Monitor watching its log for the same success/failure markers
     `scripts/CLAUDE.md` specifies, so a real failure is still caught immediately rather than only
     discovered when the task-completion notification arrives.
   - Never sit idle waiting on a backgrounded step. The moment something is backgrounded, start
     the next independent unit of work in the same turn (write the Playwright test, the ADR, a
     docs update, read files for the next step) — always through this project's existing scripts
     (`./mvnw`, `scripts/build-and-test.sh`, `scripts/deploy-and-run.sh`, `scripts/playwright.sh`, etc.), never
     raw substitutes. Only wait/block when every remaining unit of work in the plan has a real
     dependency on the thing currently running (e.g. deploying before compile is confirmed clean,
     or running Playwright before deploy finishes) — in that case say so briefly and wait for the
     notification rather than polling.

3. **Self-review before spending a full test cycle on it.** Once the implementation is written
   (before running unit/integration/Playwright), dispatch this project's own `/review` command
   (the `deep-review-orchestrator` agent) against the diff so far, then apply its findings
   directly. This catches obvious SOLID/DRY/KISS/YAGNI bugs cheaply, before burning a full test
   cycle on code that would need to change anyway — running the whole suite first and then
   discovering a review finding forces a second full run for nothing. Only proceed to step 4 once
   `/review` has run and its findings (if any) are applied. This applies to **any** review run
   during an autopilot session — not just this scheduled step — including one the user asks for ad
   hoc mid-run or after a commit already landed: default to applying findings directly, do not stop
   to ask whether to apply a finding that is itself low-risk (a rename, a dedup extraction, a
   straightforward null-guard). Only stop and ask when a finding's fix would itself be
   destructive/hard-to-reverse per the standing "Executing actions with care" bar — the same
   exception step 2 already carves out.
   **`/review`'s `deep-review-orchestrator` never writes anything itself** (no `Write` tool, by
   design) — it independently dispatches and verifies two finder lenses (`dry-kiss-yagni-reviewer`
   + `solid-reviewer`), confirms every surviving candidate with its own fresh verifier subagent, and
   returns a `ReportFindings` JSON payload plus prepared-but-unwritten issue-file content for
   anything genuinely new. `/autopilot` — not `/review` — is what applies findings and writes
   files here:
   1. `Agent({description: "Code review", subagent_type: "deep-review-orchestrator", prompt:
      "<diff scope, or empty for uncommitted changes>"})`.
   2. Read its final result in full — do not re-derive or second-guess what it already verified;
      that is not this step's job (per `.claude/commands/review.md`'s own step 2).
   3. Call `ReportFindings` with the JSON payload it returns, then apply every auto-report-bucket
      finding directly (per this step's own default-to-applying rule above).
   4. Any prepared-but-unwritten `backlog/issues/*.md` content: present it and, only after
      explicit approval, write it via `Write` — never automatically, per the standing Approval
      Rule (same rule `deep-review-orchestrator` itself is built to respect by having no `Write`
      tool at all).
   5. Carry every "needs human review" (medium/low-confidence) finding into step 6's final report —
      do not silently drop it just because it wasn't auto-applied.
   "The diff looks small enough to skip this step" is exactly the judgment call this process exists
   to remove — it was violated three times in a row in one session before being caught and
   corrected (against an earlier review mechanism, but the same discipline applies here). Do not
   repeat it, and do not repeat a partial version of it either (dispatching `/review` but skipping
   the apply step is the same violation in a different shape).

4. **Verify like it's going into the final report, not like a checkbox.** Run every test layer
   the change actually touches — unit tests always; integration tests when a repository/schema/
   port contract changed; a full Playwright `e2e --full --ux` pass when anything UI-visible
   changed — using this project's normal Monitor+tee patterns (see `scripts/CLAUDE.md`). Before
   that Playwright run, always `bash scripts/deploy-and-run.sh --reset` first — never reuse whatever DB
   state happens to be sitting around from an earlier run this session (see `playwright/CLAUDE.md`);
   this is unconditional, not something to reach for reactively after a failure looks suspicious.
   If a test fails, root-cause and fix it in the same run rather than reporting a partial result and
   stopping — that's still "implementation," not a new decision point.

5. **Document as you go, not as an afterthought.** Update the relevant module's `DECISIONS.md`
   with a new ADR before the run ends if the change is architectural — same bar as
   `.claude/rules.md`'s "Definition of Done." If the task closes a backlog issue, move it to
   `backlog/completed/issues/`, drop its `BACKLOG.md` row, and add the one-line archive entry —
   same operation, not a follow-up.

6. **One final report, comprehensive but human — no file-by-file diff table.** When the whole
   chain above is done (or genuinely blocked on something outside the plan), report once: what was
   implemented in plain terms (not an enumerated per-file diff description — the user reads the
   actual diff themselves, see `.claude/rules.md` "Final reports"), what was verified and its
   actual result (test counts, not just "passed"), which
   scripts ran in the background over the course of the run (command + what it was for — the
   compile/test/deploy/Playwright calls step 2 backgrounded by default), which Agent-tool calls ran
   (how many, which subagent type, foreground or `run_in_background`, and whether they ran in
   parallel in one message or sequentially — e.g. step 3's `deep-review-orchestrator` dispatch and
   its own internal finder/verifier subagents) and what each batch of agents was for — **and
   explicitly what step 3's `/review` run found and what happened to each finding** (fixed /
   skipped with why / routed to "needs human review" / "no findings survived verification"), not
   just that it ran, what got documented, and
   the concrete git status (what's staged, ready for "зроби коміт"). This
   report replaces every intermediate status update this run would otherwise have produced — don't
   also send a running commentary while steps 2-4 are in progress; the user asked specifically not
   to be interrupted until there's something finished to look at. Send a `PushNotification` with
   this report too, since a "dark" run is exactly the case where the user may be away when it
   finishes. If the user manually raised the permission mode for this run (e.g. to
   `bypassPermissions`) before invoking `/autopilot`, remind them in the report to switch it back —
   there is no tool available to change permission mode programmatically, so this step is always
   manual on their end, both to raise and to lower it.
