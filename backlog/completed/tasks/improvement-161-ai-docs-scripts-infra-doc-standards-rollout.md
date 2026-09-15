# improvement-161: .claude/nav/scripts/ — infra-doc-standards rollout

**Type:** improvement
**Module:** `.claude/nav/scripts/` (`check-adr-index-freshness.sh`, `check-flows-completeness.sh`,
  `check-hardcoded-counts.sh`, `generate-adr-index.sh`), `.claude/skills/infra-doc-standards/SKILL.md`
**Priority:** Top 🟡
**When:** independent, no blockers

Split out of `improvement-155`'s "Not yet started — the actual repo-wide rollout" list (that
issue's own design/mechanism work is done; this is one of the still-untouched directories from
its list).

## Problem

None of the 4 scripts in `.claude/nav/scripts/` carry the structured 7-field header
(`Description`/`Usage`/`Uses`/`Env`/`Input`/`Outputs`/`Returns`) `infra-doc-standards/SKILL.md`
defines — confirmed by reading all 4 files directly, each has only a short prose comment block.
There is also no `README.md` in that directory documenting the flow between these scripts (e.g.
`check-adr-index-freshness.sh` calls `generate-adr-index.sh` internally).

## Suggested fix

Apply `infra-doc-standards/SKILL.md`'s convention: add the 7-field header to all 4 scripts, write
a `.claude/nav/scripts/README.md` with a `## Flow` section covering how the check scripts relate to
`generate-adr-index.sh` and to `scripts/ci.sh`'s `docs` stage. Details to be filled in when this
issue is picked up.

## Related

- `improvement-155` — the issue this was split from; design/mechanism for the whole convention.
- `.claude/skills/infra-doc-standards/SKILL.md`
- `improvement-170` — landed the actual work as a byproduct of its own item 1/9 (the `docs/ai` →
  `.claude/nav` rename and AI Tooling tree rebuild): all 4 scripts now carry the 7-field header and
  `.claude/nav/scripts/README.md` exists with a `## Flow` section and mermaid diagram. Verified
  directly against the current files before closing this issue — no separate implementation pass
  was needed here.

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
