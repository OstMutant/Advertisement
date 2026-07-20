# improvement-094: AdvertisementService.resolveCategoryFilter() — `null` vs empty-set sentinel contradicts the project's Optional style

**Type:** improvement — code style / API clarity, minor. Found via pattern-focused code review
(2026-07-19).
**Module:** `advertisement-spring-boot-starter` (`services/AdvertisementService.java`,
`repository/AdvertisementRepository.java`)
**Priority:** low — private helper, both callers handle it correctly today; purely a readability/
safety-margin cleanup
**When:** Batch D (advertisement service & port consistency, with improvement-092/062) — see `backlog/BACKLOG.md` "Execution batches" (2026-07-19)

## Problem

`resolveCategoryFilter()` encodes three states into a nullable `Set<Long>`:

- `null` → "no category filter requested" (or taxon starter absent) → no SQL clause;
- empty set → "filter requested but matches nothing" → short-circuit to empty result;
- non-empty → `AND a.id = ANY(:allowedIds)`.

Both `getFiltered()` and `count()` must then repeat the two-step dance
(`if (allowedIds != null && allowedIds.isEmpty()) return ...`), and
`AdvertisementRepository.buildIdClause()` has its own `if (ids == null) return ""` — the meaning
of `null` is documented nowhere and lives only in the callers' heads. The project's own rules
("Functional `Optional` style", "no `null` sentinels crossing method boundaries") point the other
way.

## Suggested fix

Make the tri-state explicit — e.g. return `Optional<Set<Long>>` (`empty()` = no filter,
`of(ids)` = filter, possibly empty) or a tiny
`sealed interface CategoryFilter { NoFilter / MatchNone / Ids(Set<Long>) }` record family; the
short-circuit then reads as `filter instanceof MatchNone` instead of a null-plus-isEmpty
incantation. Keep the repository's array-binding exactly as is (improvement-050 item 2).

## Related

- Root `CLAUDE.md` "Functional `Optional` style" / "No `Optional` parameters" — the boundary rules
  this helper skirts.
