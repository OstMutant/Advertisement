---
paths: ["query-lib/**"]
---

## query-lib API

Plain Java SQL helper library. No Spring Boot autoconfiguration, no domain knowledge. See
`query-lib/README.md`'s "Package structure" for the class list and one-line roles — not restated
here; this file covers only the usage constraints below.

---

### SQL Layer (`org.ost.query.filter`, `org.ost.query.sort`)

#### Defining a filter

`filterProperty` (the first argument to `SqlBoundFilter.of`) is always a typed `Fields.*`
constant from the filter DTO, static-imported — never a raw string literal, same rule as
`OrderByBuilder`'s sort-alias map below:

```java
import static org.ost.platform.advertisement.dto.AdvertisementFilterDto.Fields.*;

private static final SqlFilterBuilder<AdvertisementFilterDto> FILTER = new SqlFilterBuilder<>(List.of(
        SqlBoundFilter.of(title,          "a.title",      (m, v) -> like(m, v.getTitle())),
        SqlBoundFilter.of(createdAtStart, "a.created_at", (m, v) -> after(m, v.getCreatedAtStart())),
        SqlBoundFilter.of(createdAtEnd,   "a.created_at", (m, v) -> before(m, v.getCreatedAtEnd()))
));
```

#### SqlCondition factory methods

See `query-lib/README.md`'s "SqlCondition factory methods" table for the full operator list —
not restated here.

#### Defining a sort-alias map

`OrderByBuilder.build(sort, aliasToExpression)` looks up each `Sort.Order`'s property directly
in `aliasToExpression` — no case conversion happens inside `OrderByBuilder` itself. Map keys must
therefore be the exact camelCase DTO field name (i.e. what `Sort.Order.getProperty()` actually
carries, populated via `SortFieldMeta.of(SomeDto.Fields.xyz, ...)` upstream), sourced from the
DTO's own Lombok `@FieldNameConstants` — never a raw string literal, and never a hand-converted
snake_case string:

```java
// correct — compiler catches renames, matches Sort.Order.getProperty() exactly
Map.entry(AdvertisementInfoDto.Fields.createdAt, "a.created_at")

// wrong — a typo or a DTO field rename silently drops this sort option, no compile error
Map.entry("created_at", "a.created_at")
```

Use the DTO's `Fields.*` fully qualified (not statically imported) if the same file already
statically imports another `Fields.*` set with overlapping member names (e.g. a repository that
defines both `SqlFilterBuilder` bindings off a `*FilterDto` and an `OrderByBuilder` alias map off
the corresponding `*InfoDto`/entity — both commonly share names like `title`/`createdAt`).

#### Sorting with a stable tiebreaker

`OrderByBuilder.build(sort, List<SortField>)` is the tiebreaker-aware alternative to the plain
alias-map overload above — used by every current repository (`AdvertisementRepository`,
`ProviderProfileRepository`, `TaxonRepository`, `UserRepository`, `AuditLogRepository`) so paginated
results stay deterministic even when rows tie on the caller's own sort field. Each `SortField.of(...)`
entry pairs a `Fields.*` property with its SQL expression, same typed-constant rule as above; a
field prone to ties (`createdAt`/`updatedAt`) additionally names a nested `SortField` tiebreaker
(typically the row's own `id`), appended with a default `DESC` direction unless overridden — skipped
automatically when that tiebreaker's own property is already present elsewhere in the caller's `Sort`:

```java
private static final List<SortField> SORT_FIELDS = List.of(
        SortField.of(AdvertisementInfoDto.Fields.id,        "a.id"),
        SortField.of(AdvertisementInfoDto.Fields.title,     "a.title"),
        SortField.of(AdvertisementInfoDto.Fields.createdAt, "a.created_at",
                SortField.of(AdvertisementInfoDto.Fields.id, "a.id")));
```

An empty `Sort` falls back to the first `SortField` in the list, using that field's own explicit
`direction`, only when one was set via the 3-arg `SortField.of(property, expression, direction,
tiebreakers...)` factory — a plain `SortField.of(property, expression)` entry (no direction) keeps
the same behavior as the alias-map overload: no `ORDER BY` clause when nothing was requested.
