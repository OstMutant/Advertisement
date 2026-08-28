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
