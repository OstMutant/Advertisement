# improvement-139: `deep-review` full-mode scope missing `provider-profile-spring-boot-starter`

**Type:** bug — a review tool's scope list silently omits a module.
**Module:** `.claude/skills/deep-review/references/full-mode.md`
**Priority:** 🟢 low — cheap, mechanical, no live production impact, but every full-mode run since
the module was added has silently skipped it.
**When:** independent, no blockers — carved out of `improvement-137` (found during that issue's
planning, 2026-08-04) since it's small and self-contained.

## Problem

`full-mode.md`'s Scope section hard-coded its module list at 9 entries:
`marketplace-app`, `platform-commons`, `query-lib`, `advertisement-spring-boot-starter`,
`attachment-spring-boot-starter`, `audit-spring-boot-starter`, `taxon-spring-boot-starter`,
`user-spring-boot-starter`, `integration-tests`. `provider-profile-spring-boot-starter` (added in
`improvement-124` Batch B, 2026-08-01) was never added to this list, so every `deep-review full`
run (no module argument) since then has silently under-covered the repo — one whole module never
gets a SOLID/DRY/KISS pass.

## Suggested fix

Add `provider-profile-spring-boot-starter` to the module list in
`.claude/skills/deep-review/references/full-mode.md`'s Scope section (fixed as part of
`improvement-137`'s Step 4 Pass 4, in the same change that files this issue).

## Related

- `improvement-137` — the dedup-cleanup issue whose planning surfaced this gap.
- `improvement-124` — added `provider-profile-spring-boot-starter`, the module this list missed.

## Operational notes
- token_cost_review: n/a (fix applied inline as part of improvement-137's shared `/code-review --fix` pass)
- token_cost_research: n/a (fix confirmed by direct file read during improvement-137 planning)
- token_cost_verification: n/a
- context_loading_task_type: n/a (one-line fix, no context-loading lookup needed)
- context_loading_consulted: no
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a
