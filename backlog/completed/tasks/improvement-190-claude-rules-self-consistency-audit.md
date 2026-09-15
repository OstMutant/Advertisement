# improvement-190: `.claude/` rule system self-consistency audit

**Type:** improvement
**Module:** .claude/rules.md, .claude/rules/*.md, CLAUDE.md, .claude/DECISIONS.md
**Priority:** medium-high
**When:** independent, no blockers

## Current state
`.claude/rules.md` + per-module `.claude/rules/*.md` + root `CLAUDE.md` form a large, hand-maintained
governance layer. `.claude/rules/README.md` already documents one confirmed instance of a
`paths:` glob matching unintended locations (`scripts/**` also matching
`docs/architecture/scripts/**`). No mechanism currently checks the rule system itself for
contradictions, dead/stale path references, duplicated facts across these files, or other
overly-broad path triggers beyond the one already found by hand.

## Why change
A 27-section external "forensic audit" prompt was submitted this session asking for a combined
governance + architecture + security + context-bloat audit of the whole repo in one pass. Evaluated
as disproportionate and largely duplicative of existing mechanisms (ADR index, `/review` →
`deep-review-orchestrator`, the "one fact → one canonical home" rule). The narrower, genuinely
open gap is real: nothing currently re-verifies the rule system's own internal consistency the way
`generate-adr-index.sh` re-verifies `DECISIONS.md` structure.

## Expected benefit
Catches real contradictions/duplication/dead rules/overly-broad `paths:` globs in the governance
layer itself before they cause wrong AI behavior or wasted context — same rigor the rule system
already demands of application code and docs, applied to itself.

## Approach
1. **Mechanical glob check — done (2026-09-15).** Built
   `.claude/nav/scripts/check-rule-path-globs.sh` (one `find` pass over the repo tree, matched
   in-memory against each rule file's `paths:` base directory — a first per-rule-file `find`-per-loop
   version timed out at 120s due to a real bug, see below). Ran it: **8 of 16 rule files have at
   least one unintended match** — `scripts.md` also matches `.claude/nav/scripts/` (a
   previously-undocumented second instance, alongside the already-known
   `docs/architecture/scripts/` one) plus `html-sanitizer-lib.md`/`integration-tests.md`/
   `marketplace-app.md`/`marketplace-orchestrator.md`/`marketplace-rest-api.md`/`playwright.md`/
   `query-lib.md` all matching their own module-named Surefire-report/log-artifact subdirectories
   under `scripts/build-and-test/reports/**`/`scripts/logs/**`. Checked whether an anchored-glob
   syntax exists to avoid this (real docs search, not assumption) — none found; the official docs'
   own examples imply root-relative intent but the observed behavior here matches anywhere in the
   tree, with no documented anchor prefix. Documented all of this directly in
   `.claude/rules/README.md`'s existing "Important" section (canonical home for this fact,
   per this repo's own "one fact → one canonical home" rule) rather than inventing an unverified
   glob-syntax fix — severity is low across the board (generated report/log artifact directories,
   not real source, so the cost is unneeded context load, not a correctness bug).

   **Two real bugs found and fixed while building the script itself, not just assumed correct:**
   real-world testing is what caught both, not a code-review pass. (1) The first version ran one
   `find` per rule file (16 full-tree walks) and used a bash array for prune conditions without
   `\( ... \)` grouping — `find`'s own operator precedence meant `-prune` only bound to the last
   `-o`-joined condition, so it silently failed to prune `.claude/worktrees/` (a stray, full
   duplicate-repo leftover from a prior agent dispatch) and walked into it — that's what caused the
   120s timeout, not the tree size itself. Fixed by switching to one shared `find` pass with
   explicit `\( ... \)` grouping, matched in-memory per rule file afterward (2.3s total, down from
   timing out). (2) After that fix, the script still crashed (exit 1, no output past the 8th rule
   file) under `set -e`: `.claude/rules/README.md` has no `paths:` frontmatter line (it's the index,
   not a scoped rule), so `grep -m1 '^paths:'` against it returns exit 1 (grep's "no match" signal)
   inside a `$(...)` substitution — `errexit` kills the whole script on that, silently, with no
   error message pointing at the real cause. Fixed with an explicit `grep -q '^paths:' ... || continue`
   guard before attempting the extraction.

   **Also discovered, documented separately in `.claude/rules/README.md`, not part of the glob
   question but directly relevant to this rule system's reliability:** Claude Code's own docs state
   path-scoped rules trigger "when Claude reads files matching the pattern, not on every tool use"
   — confirmed via direct doc fetch, not memory. A brand-new file created via `Write` in a module
   with no prior `Read` in the current session does not load that module's rules first — only
   `Read` does. Low practical impact for edits to existing files (`Edit` already requires a prior
   `Read`), but a real gap for a genuinely first file in a new module/package.

2. **Semantic review pass — done (2026-09-15).** Read all 15 `.claude/rules/*.md` module files in
   full (plus `.claude/rules/README.md`, `.claude/rules.md`, and root `CLAUDE.md`, already in
   context) and checked for contradictions, dead rules, and duplication directly — a dedicated
   direct pass rather than `deep-review-orchestrator`, whose three lenses (DRY/KISS/YAGNI, SOLID,
   precedent) are built for Java code, not markdown rule-consistency questions this task is
   actually about.

   **No confirmed contradictions or dead rules found.** This repo's own prior consolidation
   (`improvement-168`, 55→16 memory files) already left the rule set unusually self-consistent —
   apparent tensions are pre-resolved explicitly rather than left implicit: e.g.
   `integration-tests.md` quotes `.claude/rules.md`'s "Module Import Rules" directly and explains
   why it doesn't violate it; `marketplace-orchestrator.md` names every exception to its own "≤2
   domain `*Port` types per class" rule individually, allow-listed by class name.

   **One pattern that looks like duplication but isn't, on closer inspection:** negative facts like
   "`AttachmentMediaChangeHook` does not exist" repeat verbatim across `platform-commons.md`,
   `attachment-spring-boot-starter.md`, and `advertisement-spring-boot-starter.md` (same shape for
   "`AuditActivityFieldsHook` does not exist" across `audit-spring-boot-starter.md`,
   `marketplace-orchestrator.md`, `platform-commons.md`). This is not a violation of "one fact → one
   canonical home" once the loading mechanism is accounted for: these files load independently and
   conditionally (path-scoped, one module's rules do not imply another's are also in context), so a
   file has to restate a fact its own reader needs even when a sibling file states the same fact —
   the alternative (a bare cross-reference with no local restatement) would silently lose the fact
   for anyone touching only the module that doesn't get platform-commons.md loaded alongside it.
   Deliberate, load-bearing design choice given how this specific mechanism works, not a defect.

## Related
See `.claude/rules/README.md`'s "Important — glob matching is not anchored to the repo root"
section for the one already-confirmed instance of this class of bug. See `.claude/nav/adr-index.md`
for ADR-001/002 (`.claude`) — the rules-split and `/review`-agent decisions this task builds on.
