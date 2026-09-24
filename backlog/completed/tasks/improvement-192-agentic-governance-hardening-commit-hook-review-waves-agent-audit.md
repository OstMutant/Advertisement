# improvement-192: Agentic governance hardening — commit-hook fixes, review-factory wave threshold, agent tool-scope audit

**Type:** improvement
**Module:** .claude/settings.json, .claude/agents/**, wherever `/review`'s dispatch pattern is
  documented (likely `.claude/agents/review/deep-review-orchestrator.md` or the `code-review`
  command)
**Priority:** medium-high
**When:** independent, no blockers

## Current state

A third external directive ("AGENTIC GOVERNANCE HARDENING") proposed five tasks (A-E) closing gaps
in `.claude/` governance. Its own Task A premise — "locate the mechanism that gates `git
commit`/`push` today; if it's prose-only, confirm that as the baseline" — was checked against the
real repo and is **false**: `.claude/settings.json` already has a working `UserPromptSubmit` +
`PreToolUse` hook pair. `UserPromptSubmit` detects an approval phrase ("зроби коміт"/"закомі[тч]"/
the word `commit`) in the user's prompt and touches `/tmp/commit-approved`; `PreToolUse` (matcher:
`Bash`) blocks any command containing the literal substring `git commit` unless that marker exists
and is under 5 minutes old.

Direct reading of that hook's shell logic confirmed two real bypasses, independent of the source
document's assumption:

1. **Substring-match bypass.** The block only fires if the command string contains the literal
   substring `"git commit"`. `git -c user.name=x commit -m "x"` and `git --git-dir=... commit`
   contain no such substring (flags sit between the words) — the grep fails, the script falls to
   `exit 0`, and the commit runs unblocked.
2. **Unscoped, keyword-triggered approval.** `/tmp/commit-approved` is a plain file with no
   session-id binding — a concurrent Claude Code session in the same container could consume
   another session's 5-minute approval window. The trigger itself is a bare `\bcommit\b` match
   (case-insensitive), so any prompt merely containing the English word "commit" in an unrelated
   sentence sets the marker.

Task B (`plansDirectory` as a `settings.json` option) was not independently verified against the
installed Claude Code version's real docs/changelog — the source document itself requires that
check before treating it as real, and it wasn't done in this session's review.

Task C's Review Factory currently dispatches exactly 3 parallel specialists
(`dry-kiss-yagni-reviewer`, `precedent-reviewer`, `solid-reviewer`) — a ">3 → sequential waves"
threshold would not change today's behavior, only future growth.

Task D+E's real agent inventory (6 files, excluding `README.md`s): `dagu-analyst`,
`deep-review-orchestrator`, `dry-kiss-yagni-reviewer`, `precedent-reviewer`, `solid-reviewer`,
`sonar-analyst` — not yet audited for role-specificity or tool-scope justification.

## Why change

The commit-gate bypasses are real and directly actionable — same class of gap the earlier
forensic-audit directive asked to check for (`.tmp/commit-approved` inheritance across sessions was
explicitly named as a thing to verify). The wave threshold and agent audit are cheap, low-risk,
forward-looking hardening the project doesn't yet have.

## Expected benefit

- Closes two confirmed commit-gate bypasses instead of leaving them latent.
- Session-scopes commit approval so one session can no longer consume another's window.
- Prevents future context-overload from an unbounded review fan-out.
- Makes each subagent's role/tool access a deliberate, reviewed choice rather than an unaudited
  default — the two most common causes of multi-agent failures per the source document's own
  citation.

## Approach

**Task D+E — done (2026-09-15).** One read pass over all 6 real `.claude/agents/*.md` files:

| Agent | Role specificity | Tools / MCP | Tool scope | Recommended fix |
|---|---|---|---|---|
| `dagu-analyst` | CONFIRMED narrow — single purpose (inspect Dagu CI state), explicit read-only-by-default, explicit guard against mutating as a side effect | Read, Bash; MCP `dagu` (http) | JUSTIFIED in practice (Bash used for `docker ps`/`bash scripts/ci.sh`/`curl` fallback only) but the raw grant is unrestricted Bash and the MCP server exposes mutating `dagu_change`/`dagu_execute` gated only by prose ("only if explicitly asked") | Same class of gap as Task A's commit-hook finding — restriction is prose-only, not mechanical. If Claude Code's tool config supports scoping Bash to specific commands or MCP tools to a read-only subset, apply it here instead of relying on the agent's own discipline |
| `deep-review-orchestrator` | CONFIRMED narrow — 10-step documented procedure, explicit "non-negotiable rules", documented reasoning for omitting `Write` (preserves the Approval Rule) | Agent, Read, Bash, Grep, Glob | JUSTIFIED — `Agent` is central to its coordinator role, `Bash` is used only for `git diff` to resolve scope, `Write` deliberately excluded with reasoning | None — best-scoped agent of the six; the missing-`Write` reasoning is worth using as the template for other agents |
| `dry-kiss-yagni-reviewer` | CONFIRMED narrow — single lens, explicit non-overlap with `solid-reviewer`, structured JSON-only output contract | Read, Grep, Glob | JUSTIFIED — pure read-only trio, zero execution capability, exactly matches a pure-analysis role | None |
| `precedent-reviewer` | CONFIRMED narrow — explicit two-part scope (rules compliance + decision precedent), explicit non-overlap with the other two lenses | Read, Grep, Glob | JUSTIFIED — same clean read-only trio | None |
| `solid-reviewer` | CONFIRMED narrow — explicit per-principle checklist, explicit skip of OCP with reasoning, explicit non-overlap boundary | Read, Grep, Glob | JUSTIFIED — same clean read-only trio | None |
| `sonar-analyst` | CONFIRMED narrow — single external-system query role, explicit "no write access to anything" | Read; MCP `sonarqube` (stdio, toolsets restricted to `quality-gates,issues,measures`) | JUSTIFIED — tightest scope of all six; no Bash at all because its MCP server has its own launch wrapper handling startup | None — the launch-wrapper pattern is why this agent needs no Bash where `dagu-analyst` does; not applicable to `dagu-analyst` today since its MCP server has no equivalent wrapper (documented limitation in its own file) |

**Overall: 6/6 roles CONFIRMED narrow, 5/6 tool scopes cleanly JUSTIFIED.** The one recurring
pattern worth carrying into Task A's fix: `dagu-analyst`'s unrestricted `Bash` grant plus its
MCP server's unrestricted mutating tools are both scoped down to their narrow real use only by
prose in the agent's own instructions, not by the tool/MCP grant itself — the same "prose-gated,
not deterministically enforced" shape Task A's commit-hook bypasses came from. No fix applied in
this pass (Task D+E is report-only per its own scope) — carried forward as a Task A follow-on
note rather than a new finding.

**Task A — done, applied, and tested end-to-end (2026-09-15).** Both hooks in `.claude/settings.json`
updated: `PreToolUse` (Bash matcher, first sub-hook) now detects `git ... commit`/`git ... push` via
two separate `grep -E` checks (`\bgit\b` anywhere in the command, AND `\b(commit|push)\b` anywhere)
instead of a fixed-phrase `grep -q 'git commit'`, closing the `git -c user.name=x commit` /
`git --git-dir=... commit` bypass and extending coverage to `push` (previously not gated at all).
The approval marker moved from a single global `/tmp/commit-approved` to a per-session
`/tmp/claude-commit-approved/$sid`, mirroring this file's own nav-check hook pattern.
`UserPromptSubmit`'s trigger phrase narrowed from a bare `\bcommit\b` to specific imperative forms
(`please commit`, `commit this/it/now/please`, or a standalone `commit` message) plus the existing
Ukrainian phrases, and now writes the same per-session marker path.

**Testing found and fixed two more real bugs before landing (2026-09-15), not assumed correct
from the diff alone:**

1. **Position-anchored `git` check regression.** The first version of the fix anchored the `git`
   detection to command-start/after-a-separator position
   (`(^|[;&|]|\$\()[[:space:]]*(sudo[[:space:]]+)?git\b`) to feel more precise. Unit-testing 9
   synthetic command strings found this actually *broke* detection of `bash -c "git commit -m x"`
   — "git" there is preceded by a `"` character, which the anchor didn't account for, so a case the
   *original* naive substring hook caught (by accident) went undetected by the "hardened" version.
   Fixed by dropping the position anchor entirely — `\bgit\b` anywhere in the command, combined
   with the `\b(commit|push)\b` check, catches everything the anchored version caught plus the
   wrapped-command case, confirmed via an 11-case end-to-end test battery (synthetic JSON piped
   into the real extracted hook script): plain commit, `-c`/`--git-dir` bypass, `push`, `bash -c`
   wrapping (with and without a marker), non-mutating `git log`, an unrelated command, single-use
   marker consumption, stale-marker expiry, and cross-session isolation — all 11 passed.
2. **Deeper, pre-existing bug: naive JSON-field extraction truncates on an embedded quote.** Both
   the original hook and my first fix extracted the `command` field via
   `grep -o '"command":"[^"]*"'` — this does not understand JSON's `\"` escaping, so for any command
   containing an early embedded double quote (`bash -c "git commit -m x"`, `sh -c "..."`, any
   double-quoted argument appearing before "commit"/"push" in the string) the extracted value is
   silently truncated *before* reaching the word that would trigger detection — confirmed directly:
   `{"command":"bash -c \"git commit -m x\"",...}` extracted as `bash -c \` (backslash, nothing
   after). This bug already existed in the *original* hook too — it happened not to matter there
   only because `git commit -m "msg"` keeps both trigger words before the first quote. This
   project's own `.claude/settings.json` already had the correct extraction pattern one hook over
   (the nav-check hook's `sed -E 's/^.*"command":"((\\.|[^"\\])*)".*$/\1/'`, which handles escaped
   characters properly) — reused that exact, already-established pattern here instead of inventing
   a new one, per this project's own "pattern-first" principle. Re-ran the full 11-case battery
   after this fix; all still pass, plus the `bash -c` wrapping case is now genuinely caught for the
   right reason (proper extraction, not just a looser regex).

**Risk found during `UserPromptSubmit` testing — resolved by a real live test (2026-09-15).**
Synthetic-JSON testing found the Ukrainian phrases ("зроби коміт", "закоміть") only matched when
the JSON payload carried the prompt as raw UTF-8 bytes, not when encoded through Python's default
`ensure_ascii=True` `\uXXXX` escaping — the hook's `grep` does not decode JSON unicode escapes, so
whether it would work depended on which encoding Claude Code's own hook payload serializer actually
uses, which could not be observed from inside the session. Resolved for real: the user said "зроби
коміт" as an ordinary message; the resulting `git add` + `git commit` (commit `f73f222e`) went
through without the `PreToolUse` block firing, confirming Claude Code serializes the prompt as raw
UTF-8, not escaped — the Ukrainian trigger phrase works correctly in the live environment. The
English phrases remain a confirmed-reliable fallback regardless.

**Real-world regression found and fixed (2026-09-16, post-completion).** The Task A hardening pass
above narrowed the English trigger phrases from a bare `\bcommit\b` to specific imperative forms,
but left both Ukrainian phrases (`зроби +коміт`, `закомі[тч]`) completely unguarded — still matched
anywhere in a message, no position/boundary restriction, unlike the English side. This produced a
real false-positive in actual use, not a hypothetical: the message *"ну давай спочатку все
виправимо по таску а тоді **закомітимо** зараз що семантичний ревю?"* — a planning sentence about
committing *later*, after other work — contains the substring "закомі" + "т", matching
`закомі[тч]` and arming the approval marker. A subsequent real `git commit` (commit `0f96b1a1`)
went through without genuine intent to approve it right then. The user caught this directly ("чому
хук не спрацював я ж не давав апрув на коміт") rather than it being found by testing.

Fixed by applying the same whole-message-anchoring principle already used for the English
standalone-`commit` case to *every* trigger form, Ukrainian and English alike — both now require
the phrase to be essentially the whole message (small optional leading confirmation word, small
optional trailing words), not merely present anywhere in a longer sentence:
```
^[[:space:]]*(так|окей|гаразд|давай|ok)?[,!]?[[:space:]]*(зроби[[:space:]]+ком[іi]т|закоміт[ьи]|ком[іi]ть)([[:space:]]+[а-яіїєa-z]+){0,3}[.!?]?[[:space:]]*$|^[[:space:]]*(please[[:space:]]+)?commit([[:space:]]+(this|it|now|please))?([[:space:]]+[a-z]+){0,3}[.!?]?[[:space:]]*$
```
Also closed the same class of gap on the English side while at it (not yet reported as a separate
finding, found by applying the same scrutiny): `commit (this|it|now|please)` was equally
unanchored — `"please don't commit this yet"` would have matched `commit this` as a substring.

Verified via a 7-case end-to-end battery (synthetic JSON piped into the real extracted hook
script), including the exact real false-positive message above: "зроби коміт" / "так, закоміть і
це" / "please commit" still arm correctly; the real false-positive message, a negated "не треба
зроби коміт зараз, почекай", "please don't commit this yet", and an unrelated "what is a commit in
git" all correctly do not. The commit that slipped through (`0f96b1a1`) was left in place per
explicit user instruction — its actual content had already been reviewed and was not itself wrong,
only the approval process around it was skipped.

**Second real-world finding, same session (2026-09-16): hooks appear not to hot-reload mid-session.**
Immediately after committing the trigger-phrase fix above, the very next `git commit` attempt was
blocked with the *original, pre-hardening* error text — `"git commit BLOCKED — say \"зроби коміт\"
first"` (no `/push`, checking the old global `/tmp/commit-approved` path) — not the hardened
message (`"git commit/push BLOCKED for this session — ..."`, per-session path) that had been on
disk for a while at that point. This is consistent with Claude Code loading `PreToolUse`/
`UserPromptSubmit` hook definitions once at session start and not re-reading `.claude/settings.json`
for the rest of the session — every edit made to these hooks during this session may have only ever
taken effect for a *future* session, never the one making the edits. Not independently confirmed
against Claude Code's own documentation or by a controlled test (e.g. deliberately triggering the
exact same command before and after an edit with nothing else changing) — this is an observation
from two data points in live usage, not a verified root cause. If true, it has a real implication
beyond this task: **testing a hook fix within the same session that authored it (as both rounds of
hardening in this task did, via synthetic-JSON script extraction) can never confirm the live,
in-session enforcement actually changed** — only a fresh session, or an explicit statement from
Claude Code's own docs/changelog, can. Worth a real follow-up check next session: read this file's
hook definitions again at the very start and confirm the `PreToolUse` block message matches what's
currently on disk before relying on it.

**Real finding during application:** the first attempt to edit `UserPromptSubmit`'s command was
**blocked by Claude Code's own auto-mode classifier** with reason `[Self-Modification]` — the
`PreToolUse` edit (the enforcement half) went through in the same batch without issue, but the
`UserPromptSubmit` edit (the approval-granting half) was specifically refused. A retry of the exact
same edit succeeded the second time. Left uninvestigated: whether this was a one-off transient
classification or a real, reproducible distinction the harness draws between editing an
enforcement hook vs. an approval-granting hook — worth a follow-up note if it recurs, not chased
further here since it resolved on retry and this task's own scope is the hook content, not the
classifier's behavior.

Residual, documented limitation (not closed by this fix): a wrapper script whose own Bash
invocation text contains neither the word `git` nor `commit`/`push` literally (e.g.
`./my-commit-wrapper.sh`) still bypasses the gate — the hook inspects only the literal command
string passed to `Bash`, not what a script does internally.

~~1. Replace the literal `git commit` substring match with a pattern that also catches `git -c
   ... commit` / `git --git-dir=... commit` (e.g. a proper `git`-subcommand-aware match, not a
   fixed-phrase grep). Apply the same fix to any `push` detection this task adds.
2. Scope the approval marker to `session_id`, mirroring the pattern this same `settings.json`
   already uses for its nav-check hooks (`/tmp/claude-nav-check/$sid/...`) instead of a single
   global `/tmp/commit-approved`.
3. Tighten the `UserPromptSubmit` trigger phrase so an unrelated sentence containing the word
   "commit" doesn't silently arm the approval window.
4. Produce the change as a diff to `.claude/settings.json`. **Do not apply it** — this is a
   protected-path change, subject to the same Approval Rule as everywhere else.

**Task B — done, no fix applied (2026-09-15).** Verified against real sources, not memory:
`plansDirectory` is a genuine, documented `settings.json` key, settable at both user
(`~/.claude/settings.json`) and project (`.claude/settings.json`) scope — the same layering this
repo already uses. But two real blockers rule out a clean integration:

1. **Confirmed, apparently-unfixed bug.** GitHub issue #19537 ("Project-level plansDirectory
   setting ignored in favor of global default") — a project-level `.claude/settings.json` value is
   ignored, Claude Code still writes to the global `~/.claude/plans/`. Issue shows as "Closed" but
   with no maintainer fix note; checked the official changelog through the latest listed version
   (2.1.273 — our installed 2.1.272 is one patch behind) and found zero mentions of
   `plansDirectory` in any release. Cannot confirm this is actually fixed in the installed version.
2. **Directory-only control, not format control.** Even working, `plansDirectory` only relocates
   *where* Plan Mode's harness-generated plan file lands — it does not make that file match this
   project's `backlog/tasks/<n>.md` template (`**Priority:**`/`**When:**`/`## Approach` etc.). This
   is the same incompatibility that already stopped `improvement-177`'s Plan Mode migration attempt
   — `plansDirectory` does not close that gap, it only changes an unrelated path.

**No clean integration exists — reported as required by this task's own instructions, nothing
applied.** Task A (the hardened hook) remains the primary, and only, fix for the commit-gate.

**Task C — done, applied (2026-09-15).** Added the wave-threshold rule to
`.claude/agents/review/deep-review-orchestrator.md` in two places, not one:

1. **Step 3 (finder-lens dispatch)** — today exactly 3 lenses (`dry-kiss-yagni-reviewer`,
   `solid-reviewer`, `precedent-reviewer`), matching the task's own "no current behavior change"
   framing: the rule only bites once a 4th lens is added.
2. **Step 4 (per-candidate verification dispatch)** — added the same rule here too, not just step 3.
   This step dispatches one verifier *per surviving candidate finding*, which routinely exceeds 3
   in a real review with more than 3 findings — this is actually the more likely real trigger point
   for the threshold in practice, and leaving it out would have meant the hardening didn't cover the
   spot most likely to matter.

**Gap found and fixed too, after user pushback on leaving a known real risk merely flagged
(2026-09-15).** Re-reading step 1's `all`/`everything` scope during this task found it already
fans out far past 3 in practice: it loops step 3 (3 lenses) once *per top-level Maven module*,
"dispatched in parallel" — with ~14 modules in this repo's root `pom.xml`, a full-repo sweep issued
roughly 14×3 ≈ 42 parallel `Agent` calls in one response, already well past the threshold this task
added elsewhere. First pass left this as a recommended follow-up rather than fixing it (the source
document's own Task C instruction said "do not change the current agent count or structure," and a
per-module/per-lens wave-batching redesign looked like a bigger design question) — on reflection,
and per direct feedback that recording a real gap and then archiving the task anyway doesn't add up,
implemented the simplest fix that actually closes it: **modules are now processed sequentially, one
at a time**, instead of dispatching every module's step 3 together. Each module's own step 3 still
fires its 3 lenses together internally (within the wave threshold), but the next module doesn't
start until the current one's step 3/4 finish — this caps real concurrency at ≤3 parallel `Agent`
calls at any point, regardless of module count, with no new per-module/per-lens batching logic
needed. Applied directly to `.claude/agents/review/deep-review-orchestrator.md`'s step 1 `all`
bullet.

**Task D+E:** one read pass over the 6 real `.claude/agents/*.md` files, reporting per agent: stated
role/scope, role-specificity (CONFIRMED narrow / AMBIGUOUS / TOO BROAD), tools/MCP access,
tool-scope justification (JUSTIFIED / OVER-BROAD), recommended fix if any. Report only — no agent
definition is rewritten in this pass.

## Related

[improvement-177](../completed/tasks/improvement-177-plan-mode-approval-rule-rollout.md) — the
reverted Plan Mode attempt Task A was originally framed as superseding. improvement-169/-171 —
Review Factory formalization Task C extends (`backlog/completed/tasks/`). The commit-gate finding
was discovered by direct inspection of `.claude/settings.json` during this task's own review, not
assumed from the source document.
