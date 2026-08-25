# Full mode

Source: a real one-off 11-agent, one-per-module SOLID/DRY/KISS pass this project
ran across every module. That pass is the best precedent available for this mode
because it's not a generic methodology, it's this exact codebase's own proven
approach, already shown to find real, correctly-calibrated issues. Search
`backlog/` for that pass's own findings file before assuming everything it
flagged is still relevant — priorities and status can have moved on since it ran.

Run this "час від часу" — after a batch of feature work lands, not after every
commit. It's expensive: one subagent per module, each reading most of that
module's files.

## Scope

`$ARGUMENTS` after "full": an optional module name. With one, scope to just that
module. With none, cover all of:

`marketplace-app`, `platform-commons`, `query-lib`, `advertisement-spring-boot-starter`,
`attachment-spring-boot-starter`, `audit-spring-boot-starter`,
`taxon-spring-boot-starter`, `user-spring-boot-starter`, `provider-profile-spring-boot-starter`,
`integration-tests`

## Procedure

**1. One subagent per module, in parallel, read-only.** Each agent gets:

   - The module's own `CLAUDE.md`/`DECISIONS.md` if present (its stated
     conventions — a violation only counts if it contradicts what the module
     itself claims to do)
   - Instruction to review for, in priority order: SOLID violations (especially
     SRP — a class that's grown multiple unrelated concerns), DRY violations
     that are *structural* (same sequence of operations repeated with different
     types), and KISS violations (abstraction that costs more than the problem
     it solves — the `PaginationBar` exception, see `.claude/nav/adr-index.md`,
     shows this isn't automatic; over-abstraction is as much a finding
     as under-abstraction)
   - Explicit instruction to report, for every file it actually opened, one of:
     a finding with file+line evidence, or "checked, no issue" — an agent that
     reports zero findings for a whole module is not automatically wrong, and
     silence isn't the same as "didn't look."

**2. Validate every candidate — same as diff mode's step 4.** A separate,
fresh subagent opens the real current file for each candidate and confirms it.
Full-repo passes produce more candidates than diff reviews, which means more
temptation to skip this step for the "obvious" ones — don't. The kind of
false positive this skill exists to prevent came from exactly that kind of
confidence.

**3. Cross-check every survivor against the backlog**, per the parent skill's
rule 2. This step matters more here than in diff mode: a full sweep is far more
likely to re-discover something already tracked, and re-filing a known issue as
new wastes the next reader's time figuring out it's a duplicate.

**4. Group and prioritize before writing.** Unlike diff mode, a full sweep can
surface a dozen+ candidates in one pass. Group by module, and inside each group
order worst-first. Give each finding a priority consistent with this project's existing scale
(critical/high/medium/low) — calibrate against recent examples already in
`backlog/completed/issues/` (e.g. a real, currently-exploitable auth gap is
high+; a missing `@NonNull` annotation for consistency is low) rather than
inventing a new scale.

**5. Decide: one issue file per finding, or one batch file?** If more than
~5 findings survive validation in a single run, prefer one consolidated
`backlog/issues/improvement-<n>-<short-batch-name>.md` (grouped by module,
worst-first within each group) over a dozen separate single-line issue files —
a single well-organized batch file stays far more readable than scattered
one-line issues. For 1-4 findings, separate files are fine.

**6. Summarize in chat** per the parent skill's Output section — and note
explicitly if this run's findings substantially overlap with an existing open
full-sweep issue, since that means the earlier one may be worth revisiting
rather than leaving at its current priority.

## The one thing this mode must never do

Findings only. Never let this mode's own momentum turn into "and now let me
also just fix a few of these while I'm in here" — that blurs review and
execution back together, which is exactly what happened the one time this
project tried having autopilot execute a full sweep's batches automatically,
aborted before landing anything. If a finding is a five-minute fix, write it
up as an issue anyway and let a separate, explicit step decide to act on it.
