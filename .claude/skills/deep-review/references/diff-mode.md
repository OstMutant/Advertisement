# Diff mode

Source: `anthropics/claude-code`, `plugins/code-review/commands/code-review.md` —
Anthropic's own bundled PR-review command. Adapted here for local-diff review
instead of GitHub PRs, since this project reviews commits directly rather than
through a PR queue. If a specific PR *is* what's being reviewed, swap step 1's
`git diff` for `gh pr diff <n>` and step 8's file-write for
`gh pr comment`/inline comments — the rest of the procedure is unchanged.

## Scope

`$ARGUMENTS` after "diff": a git ref (review everything since that ref) or
nothing (review the last commit, `git diff HEAD~1`). Resolve this first with
`git diff <ref>` or `git diff HEAD~1` — whichever applies — and treat that as
the full scope for every step below. Do not read files outside this diff unless
a specific check requires surrounding context (e.g. confirming a method's full
signature).

## Procedure

**1. Cheap skip check.** Before spending any real budget: is the diff trivial
(formatting-only, generated files, a version bump) or empty? If so, stop and
report "nothing to review" — don't run the rest of the pipeline for a no-op diff.

**2. Summarize.** One pass to produce a short description of what changed and
why (infer intent from the diff itself, commit message, and any linked
`backlog/issues/` file the commit references).

**3. Find candidates — parallel, specialized, not generic.** The original
Anthropic command runs a generic "bugs" agent and a "CLAUDE.md compliance"
agent. This project's own history (`improvement-049`, `improvement-050`,
`improvement-090`, `improvement-106`, `improvement-107`) shows its real bugs
cluster around a few specific shapes, so use those as the lenses instead of a
generic "find bugs" prompt:

   - **Security-boundary agent** — does this diff add or touch a mutation path
     (`save`/`delete`/anything with side effects)? Is authorization checked at
     the service layer, or only assumed from the UI having hidden a button?
     (Background: `improvement-111` — UI-only enforcement is a known, accepted,
     but *conditionally* safe state; anything that changes the condition — a
     new non-UI caller — is exactly what to flag here.)
   - **Data-integrity agent** — any external side effect (S3, a future webhook,
     email) sequenced against a DB transaction? Could a failure between the two
     leave them inconsistent? Any TOCTOU gap between a check and the action it
     gates?
   - **SOLID/DRY agent** — is the same shape (not just the same text) repeated
     across this diff and something already in the codebase? Structural
     duplication (same sequence of operations, different types) counts as much
     as copy-pasted text.
   - **CLAUDE.md / ArchitectureRulesTest compliance agent** — does the diff
     violate a rule the project already encodes as prose (`CLAUDE.md`,
     `marketplace-app/DECISIONS.md`) or as an enforced ArchUnit rule
     (`ArchitectureRulesTest`)? Only flag rules that are actually scoped to the
     changed files — a rule in a different module's `CLAUDE.md` doesn't apply.

   Each agent gets: the diff, the one-line summary from step 2, and this
   instruction verbatim from the source command, which is worth keeping
   unchanged because it's the actual mechanism that keeps signal high:

   > Flag only significant issues; ignore nitpicks and likely false positives.
   > Do not flag issues that you cannot validate without looking at context
   > outside of the diff. If you are not certain an issue is real, do not flag
   > it — false positives erode trust and waste review time.

**4. Validate every candidate — separately, one subagent per candidate.** This
is the step that most matters and the one most tempting to skip. For each
issue any agent in step 3 raised, spawn a fresh subagent whose only job is to
open the real, current file and confirm the issue is actually there — not
"plausible", actually present. A candidate that fails this check is dropped
silently, not softened into a caveat. This mirrors the source command's step 5
exactly, and it's the same discipline that caught `improvement-066`/`067`
being stale — a claim that sounds right is not the same as a claim that's been
looked at.

**5. Cross-check survivors against the backlog**, per the parent skill's rule 2.

**6. Write results.** For each surviving, non-duplicate issue, create
`backlog/issues/improvement-<next-number>-<slug>.md` in this project's existing
format. For a doc/code mismatch found during cross-check, write that as its own
short entry — don't bury it inside an unrelated issue.

**7. Summarize in chat** per the parent skill's Output section.

## What NOT to flag (carried over from the source command, still applies)

- Pre-existing issues outside this diff
- Something that looks wrong but is actually correct
- Pedantic nitpicks
- Anything a linter or `ArchitectureRulesTest` would already catch
- A rule that's explicitly, deliberately not followed with a documented reason
  (check `DECISIONS.md` first)
