# improvement-043: `OrderByBuilder` sort-alias maps use raw string keys — no compile-time check against DTO field renames

**Type:** improvement — consistency/type-safety. Found while discussing improvement-041, after
that issue's fix required deleting three sort-alias entries that had silently gone dead (no
compiler warning, no runtime error — just quietly unreachable).
**Module:** `query-lib` (`OrderByBuilder`), `advertisement-spring-boot-starter`,
`user-spring-boot-starter`, `taxon-spring-boot-starter`
**Priority:** low-medium — not a bug, no behavior change; closes a silent-failure class before it
bites again on the next DTO field rename
**When:** independent, no blockers
**Scope:** this is a `query-lib`-level (shared) fix, not an Advertisement-specific one — the same
raw-string-key pattern exists in every repository that supports sorting today
(`AdvertisementRepository`, `UserRepository`, `TaxonRepository`), since they all call the same
`OrderByBuilder.build()`. Fixing only the module this issue was found in (Advertisement, during
improvement-041) and leaving User/Taxon on the old pattern would defeat the point — the fix must
land in `query-lib` itself plus all three call sites in the same change, and apply automatically
to any future repository that adds sorting.

## Problem

`SqlFilterBuilder`/`SqlBoundFilter.of()` (the filter side of `query-lib`) is already
type-safe: `SqlBoundFilter.of(title, "a.title", (m, v) -> like(m, v.getTitle()))`, where `title`
is a static import of `AdvertisementFilterDto.Fields.title` — a Lombok `@FieldNameConstants`
constant. Renaming the DTO field is a compile error at every call site that still references the
old name.

`OrderByBuilder` (the sort side) has no equivalent protection. Every repository's `ORDER BY`
alias map is written with raw string literals:

```java
// AdvertisementRepository.findByFilter()
Map.entry("created_at", "a.created_at")

// UserRepository.findByFilter() — same pattern
Map.entry("created_at", "u.created_at")

// TaxonRepository — same pattern (SORT_ALIASES constant)
```

`OrderByBuilder.build(Sort sort, Map<String, String> aliasToExpression)` takes
`Sort.Order.getProperty()` (a camelCase DTO field name, e.g. `"createdAt"`, populated via
`SortFieldMeta.of(AdvertisementInfoDto.Fields.createdAt, ...)` in the UI-layer `*SortMeta`
classes), converts it to snake_case internally, and looks it up in the map. Nothing checks that
the map's string keys actually correspond to a real DTO field. If a DTO field is renamed and a
map key is not updated to match, that sort option silently stops resolving — `OrderByBuilder`
just returns `null` for that `Sort.Order` and drops it from the `ORDER BY` clause with no error,
no warning, no test failure unless a test specifically asserts sort *order* (most don't; they
assert presence/absence of rows).

This is exactly the failure mode improvement-041 found and fixed by hand: three alias entries
(`created_by_user_id`/`created_by_user_name`/`created_by_user_email`) had gone dead because no UI
path ever built a `Sort` reaching them, and nothing would have caught it if they *had* been
reachable and someone renamed the underlying field without updating the map.

## Suggested fix

1. **Remove the `toSnakeCase()` conversion from `OrderByBuilder.build()`** (`query-lib`,
   `org.ost.query.sort`). Since `Sort.Order.getProperty()` already carries the exact camelCase DTO
   field name (populated via `SortFieldMeta.of(SomeDto.Fields.xyz, ...)` in every `*SortMeta`
   class), the snake_case conversion step is unnecessary if the alias map is keyed the same way.
2. **Re-key every alias map to `Fields.*` constants** instead of raw strings, mirroring
   `SqlBoundFilter.of()`'s existing pattern exactly:
   ```java
   // before
   Map.entry("created_at", "a.created_at")
   // after
   Map.entry(AdvertisementInfoDto.Fields.createdAt, "a.created_at")
   ```
   Apply to all three call sites: `AdvertisementRepository.findByFilter()`,
   `UserRepository.findByFilter()`, `TaxonRepository`'s `SORT_ALIASES` constant. Static-import
   `Fields.*` in each repository the same way `SqlFilterBuilder` definitions already do (see
   `import static org.ost.platform.advertisement.dto.AdvertisementFilterDto.Fields.*;` at the top
   of `AdvertisementRepository.java`).
3. No SQL or behavior change — the generated `ORDER BY` clauses stay identical. This is a pure
   compile-time-safety refactor: a future DTO field rename now fails the build in every repository
   that still references the old field name in its sort-alias map, instead of silently dropping a
   sort option at runtime.

## Resolution notes (2026-07-14)

A fourth call site was found during implementation, missed by the original scope check:
`AuditLogRepository.SORT_ALIASES` (`audit-spring-boot-starter`) — `Map.of("created_at",
"al.created_at")`, re-keyed to `AuditTimelineItemDto.Fields.createdAt`. All four repositories
(`Advertisement`, `User`, `Taxon`, `Audit`) now share the same `Fields.*`-keyed pattern.

A real, pre-existing instance of the exact bug this issue describes was also found and fixed
while re-keying: `TaxonRepository.SORT_ALIASES` had `"createdAt"`/`"updatedAt"` keys already in
camelCase — inconsistent with Advertisement/User's snake_case keys, and never actually matched by
`OrderByBuilder`'s (now-removed) `toSnakeCase()` conversion, which always produced `"created_at"`/
`"updated_at"`. This was silently dead code, harmless only because `DefaultTaxonPort` always
hardcodes `Sort.by("id")` and never exposes `createdAt`/`updatedAt` as a user-selectable Taxon
sort option.

## Required test coverage

None new — this is a refactor with no behavior change. Existing e2e sort tests (spec 05:
`advertisements — title, date and category filters, column sort, pagination`, `users — email,
role and date filters, column sort, pagination`) must stay green as the regression check that the
generated `ORDER BY` clauses are unchanged.

## Related

- `features/completed/issues/improvement-041-advertisement-user-sql-join-and-column-naming.md` —
  where the dead-alias problem this issue generalizes from was first found and manually fixed.
- `.claude/rules.md` "Query Layer Pattern — FilterMeta and SortMeta — Fields.* constants only" —
  the existing rule this issue extends one layer deeper, from the UI-facing `*SortMeta` classes
  down into the repository-level `OrderByBuilder` alias maps themselves.
- `query-lib/CLAUDE.md` — `OrderByBuilder` API reference, needs a note added once this ships.
