# improvement-225: code-reduction findings (running inventory for /optimize)

**Type:** improvement — running inventory, not a single fix.
**Module:** cross-cutting — whichever module each finding actually touches.
**Priority:** 🟡 Top — first in the backlog.
**When:** ongoing; findings are added by discovery runs of the `/optimize` skill and worked off in approved batches, not on a fixed schedule.

## Purpose

This file holds every finding of the `/optimize` skill: its status, its estimate and, once a batch is verified, its actual saving. The entry format is defined in the skill's own file and is not repeated here. A finding already tracked elsewhere in the backlog is linked, not copied.

## Coverage

- 2026-10-24 | path query-lib | read 10 of 14 files in scope (all main) | not read: 4 test files (no production finding, so test-setup duplication not assessed), plus `pom.xml`, `README.md`, `DECISIONS.md` outside the 14 | excluded: none | findings added: 0
- 2026-10-24 | path query-lib (re-run, lens fixed to read every file) | read 18 of 18 files in scope (the 17 under `query-lib/` plus `.claude/rules/query-lib.md`) | not read: none | excluded: none | findings added: 1 (OPT-1); the deep plan of the same run also read all 17 files, every consumer in 6 starters and 6 web sources, and proposed further reductions F1-F6 and D3 that await decisions

## Findings

### OPT-1. Stale `OrderByBuilder` Map overload and wrong statements in the query-lib README and rule file
**Status:** applied
**Priority:** low
**Kind:** convention
**Where:** `query-lib/README.md` (package list, usage example, `OrderByBuilder` section, dependencies) and `.claude/rules/query-lib.md` ("Defining a sort-alias map")
**Now:** both documents describe `OrderByBuilder.build(sort, Map<String,String>)` and a `SORT_MAP` example; the only public method is `build(Sort, List<SortField>)`. The README also called `SqlFilterBinding` a `@FunctionalInterface`, which it is not.
**Proposed:** one `SortField` description per document; the alias-map subsection merged into the `SortField` one; the wrong statements corrected.
**Estimate:** -28 +8 = net -20, 2 files, from the sections read.
**Also applies to:** `integration-tests/README.md` still mentions the removed sort map (not touched).
**Why it stays readable:** the documents now match the one real API.
**Risks and tests:** docs only, no tests needed.
**Backlog, rules, ADRs:** no overlap found; a documented-fact duplication across README and rule file reduced.
**Recommendation:** implement
**Actual:** README +15 -17, rule file +20 -32, net -14 in 2 files (`check-batch.sh`, physical lines, 2026-10-24); no tests run (docs only).

## Rejected ideas

- Remove the `SqlFilterBinding`/`SqlFilterMapping`/`SqlBoundFilter` layering or merge `SqlFilterBinding` into `SqlBoundFilter` — removes a public interface only for line count; `SqlFilterMapping` is also the `SqlCondition` factories' parameter type.
- Merge `applyIfPresent` and `applyIfNotEmpty` in `SqlCondition` — needs a `Predicate` parameter, adds code, saves under 5 lines.
- Collapse the `equalsTo`/`after`/`before` overloads into generics — changes the public API and overload resolution; call sites rely on the typed return.
- Remove the manual `OffsetPageable` overrides — a record implementing `Pageable`; accessor names differ from the interface, Lombok cannot generate them on a record.
- Remove the `PaginationSqlBuilder` null/unpaged guard — callers may pass null; behavior change.
- Remove the `SortField.of` overloads or the `OrderByBuilder` null-sort branch — documented public API used by every repository. (The later deep plan proposes dropping only the 2-argument `SortField.of`, because the varargs `of` takes the same calls; decision pending.)
- Drop the `SqlBoundFilter.of` factory for the record constructor — gives type inference at every repository and is prescribed by the README and rule file (second run).
- Remove `SqlFilterMapping`/`SqlFilterBinding` as thin interfaces — real extension seams, public API (second run).
- Tidy the duplicated test helpers (second run) — a few lines at most; the deep plan keeps it as F5/F6, decision pending.
- Drop the explicit `spring-jdbc` in `query-lib/pom.xml` as redundant — `MapSqlParameterSource` is imported directly in `SqlFilterBuilder` and `PaginationSqlBuilder` (second run).

## Operational notes
- token_cost_review: 251385 (orchestrator runs as reported: 55981, 55255, 65010, plus the lens 75139 reported in the third run's hand-back; the lens may already be counted inside the orchestrator figure, so the total can be overstated)
- token_cost_research: 161534 (deep-planner, topic optimize-query-lib)
- token_cost_verification: 143277 (Haiku verifiers of the second run)
- review_signal_ratio: 1 / 2
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
