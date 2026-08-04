---
name: deep-review
description: >
  Deep, evidence-verified code review for this project — either the recent diff
  or a full-codebase sweep. Use this whenever the user asks to "review changes",
  "check for bugs", "audit the code", "look for SOLID/DRY issues", or wants a
  periodic deep pass across the whole codebase rather than a quick glance. Always
  invoke explicitly with /deep-review — do not run it on your own initiative.
disable-model-invocation: true
---

# Deep Review

Two modes, one shared discipline: **never report a finding you have not personally
verified against the current file content.** That discipline — not the volume of
findings — is the entire point of this skill. It exists because in the plain-chat
version of this workflow, findings were sometimes taken from a backlog issue's own
description instead of the real file, and twice that produced a stale claim
(`improvement-066`, `improvement-067` — both already fixed in code while their own
"suggested fix" section still described the old, broken version). This skill makes
the check-before-claim step structural instead of optional.

## Pick a mode

`$ARGUMENTS` selects the mode. Parse it before doing anything else:

- **No arguments, or starts with a git ref/`diff`** → **diff mode**. Read
  `references/diff-mode.md`. This is the cheap, frequent check — run it after any
  batch of changes, the way you'd want a second pair of eyes on every commit.
- **Starts with `full`** → **full mode**. Read `references/full-mode.md`. This is
  the expensive, periodic sweep — run it "час від часу" (from time to time), not
  on every change. An optional second word scopes it to one module
  (`/deep-review full platform-commons`); with no second word, cover every module.

Read only the reference file the mode requires — don't load both.

## Why two modes instead of one

These come from two different real precedents in this project, not from a generic
template:

- **Diff mode** adapts Anthropic's own official code-review command
  (`anthropics/claude-code`, `plugins/code-review/commands/code-review.md`) —
  the exact skill that ships with Claude Code for PR review. The structure (parallel
  find → parallel validate → high-signal-only filter) is proven; only the
  GitHub-PR-specific parts (`gh pr diff`, inline PR comments) are swapped for this
  project's actual workflow (local git, `backlog/issues/`).
- **Full mode** adapts this project's own `improvement-121` — the 11-agent,
  one-per-module SOLID/DRY/KISS pass run on 2026-07-25. That pass found real,
  correctly-calibrated issues across every module. This skill formalizes it as a
  repeatable procedure instead of a one-off.

## Non-negotiable rules for both modes

1. **Verify, don't relay.** Every finding must be checked against the actual
   current file content before it's reported, no matter how it was found —
   whether by grep, by reading a diff, or by another subagent's claim. If you
   can't verify something (e.g. it depends on runtime behavior you can't observe),
   say so explicitly rather than reporting it as confirmed.
2. **Cross-check the backlog before writing a new issue.** Search
   `backlog/issues/` and `backlog/completed/issues/` for the same root cause
   first. Three outcomes, handle each differently:
   - Genuinely new → write a new `backlog/issues/improvement-<next-number>-<slug>.md`
     following the format defined in `.claude/commands/feature.md` step 3.
   - Same as an existing open issue → don't duplicate it; note the overlap in your
     summary instead.
   - Contradicts a `backlog/completed/issues/` doc (the doc says fixed, the code
     says otherwise, or vice versa) → this is itself a finding, and an important
     one. Report the doc/code mismatch explicitly rather than silently trusting
     either side.
3. **High signal only.** Do not flag: pre-existing issues unrelated to what's in
   scope, style nitpicks a senior engineer would let go, anything a linter or
   ArchUnit test (`ArchitectureRulesTest`) would already catch, or a deliberate,
   documented exception (e.g. `PaginationBar` staying a Spring bean per ADR-054 —
   check `marketplace-app/DECISIONS.md` before flagging something that looks like
   an inconsistency but is actually a recorded decision).
4. **Never write code changes in this skill.** Both modes are read-only and
   findings-only — they produce or update markdown in `backlog/`, never `.java`/
   `.xml`/etc. This is deliberate: `improvement-121` itself (a findings-only pass)
   worked cleanly, but a separate, later attempt to have autopilot *execute* all
   8 of its batches automatically was aborted before landing anything. Whatever
   caused that abort hasn't been root-caused. Until it is, keep review and
   execution as two separate steps with a human in between.

## Output

End with a summary in chat: what was checked, what's new, what overlapped with
existing issues, and any doc/code mismatch found. Don't just say "wrote N issue
files" — name them and give the one-line reason each is real.
