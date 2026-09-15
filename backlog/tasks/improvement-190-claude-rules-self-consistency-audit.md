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
1. **Mechanical glob check** — a small script validating each `.claude/rules/*.md`'s `paths:`
   glob against the actual repo file tree, flagging unintended matches (cheap, deterministic,
   catches the same class of bug `README.md` already found by hand).
2. **Semantic review pass** — scoped read-only review of `.claude/**` + `CLAUDE.md` content only
   (via `deep-review-orchestrator` or a dedicated pass) for contradictions/duplication/dead rules —
   explicitly excludes Java architecture, security, and Provider Profile domain scope, which
   already have their own coverage via `/review`.
3. Both, sequenced: mechanical check first (cheap), semantic pass only if it surfaces real
   findings or is separately requested — avoids paying for a full semantic pass on a hunch.

## Related
See `.claude/rules/README.md`'s "Important — glob matching is not anchored to the repo root"
section for the one already-confirmed instance of this class of bug. See `.claude/nav/adr-index.md`
for ADR-001/002 (`.claude`) — the rules-split and `/review`-agent decisions this task builds on.
