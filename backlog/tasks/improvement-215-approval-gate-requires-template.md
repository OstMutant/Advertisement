# improvement-215: Approval gate must require the plan template, not only an affirmative reply

**Type:** improvement — working process and safety of changes
**Module:** `.claude/settings.json` (hooks `UserPromptSubmit` and `PreToolUse`), `.claude/rules.md` (Approval Rule)
**Priority:** high — Top, first: every other task's changes pass through this gate
**When:** independent; do before further feature phases

## Current state

Verified against `.claude/settings.json`, 2026-10-18. The `UserPromptSubmit` hook creates `/tmp/claude-approved/<session>` when the user's message begins with an affirmative word ("так", "давай", "ок", "yes", "go ahead" and similar) and removes it on any other message. The `PreToolUse` hook on `Write|Edit|NotebookEdit|Agent|Skill` only tests whether that marker exists and then allows the call. It does not see the assistant's message, so it cannot check that a plan was shown, that it used the template the Approval Rule in `.claude/rules.md` requires (plain-language layer, then **Summary**, **Agent**, **Model**, **Input**, **Result**, ending with a direct question), or that the "yes" answers that plan. File changes made through `Bash` (Python scripts, `sed -i`, `cp`, `cat >`) are not covered by the approval hook at all; the Bash hooks only guard commits, direct docker/mvn use and script invocations.

In practice a plain "так починаємо" was accepted as approval for a whole implementation phase although the plan had been written in the task file in another structure and summarised in chat without the template.

## Why change

An approval that attaches to nothing in particular is not an approval. The assistant's own discipline has failed several times in one session, and the hook cannot catch it, so the rule is enforced by nothing.

## Expected benefit

A change can start only after the user has been shown the plan in the required template and has answered it; "yes" given to anything else does not open the gate.

## Approach

1. **Find out what a hook can see** (read-only, from the Claude Code hooks documentation): whether `UserPromptSubmit` or `PreToolUse` receives the conversation (for example a transcript path) so that the previous assistant message can be read. If it can, set the approval marker only when that message contains all five template fields and a question, and clear it after use.
2. **If hooks cannot see the conversation:** make the template visible and mechanical on the assistant's side — every approval request starts with the line `ШАБЛОН НА ЗАТВЕРДЖЕННЯ` and carries the five fields; a reply that follows a message without that line approves nothing. Record this in `.claude/rules.md`.
3. **Close the Bash gap:** a hook for Bash commands that write to files, with careful command recognition, or an agreed rule that file changes go only through Write/Edit. Needs design before building.
4. Check the result with a deliberate test: a "yes" after a message without the template must not open the gate.

## Related

- `.claude/rules.md` — Approval Rule and its hook-level mechanic
- [improvement-213](improvement-213-rest-api-revision-parity-with-ui.md) — phase 2 was started on a plain "yes" without the template

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none
