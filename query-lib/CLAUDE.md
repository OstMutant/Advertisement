## query-lib API

Plain Java SQL helper library. No Spring Boot autoconfiguration, no domain knowledge.

---

### SQL Layer (`org.ost.query.filter`, `org.ost.query.sort`)

| Class | Role |
|---|---|
| `SqlFilterBuilder<F>` | Translates a filter DTO into a SQL WHERE fragment + named params |
| `SqlBoundFilter<F, R>` | Binds one filter DTO field to a column expression and a `SqlCondition` factory |
| `SqlCondition<R>` | A single resolved WHERE condition (expression, param name, value, operator) |
| `SqlFilterMapping` | Interface: `filterProperty()` + `sqlExpression()` |
| `SqlFilterBinding<F, R>` | Functional interface: `getCondition(F filter) → SqlCondition<R>` |
| `OrderByBuilder` | Converts `Spring Sort` into an `ORDER BY` clause via an alias→expression map |
| `PaginationSqlBuilder` | Converts `Pageable` into a `LIMIT :limit OFFSET :offset` clause + named params |

#### Defining a filter

```java
private static final SqlFilterBuilder<AdvertisementFilterDto> FILTER = new SqlFilterBuilder<>(List.of(
        SqlBoundFilter.of("title",          "a.title",      (m, v) -> like(m, v.getTitle())),
        SqlBoundFilter.of("createdAtStart", "a.created_at", (m, v) -> after(m, v.getCreatedAtStart())),
        SqlBoundFilter.of("createdAtEnd",   "a.created_at", (m, v) -> before(m, v.getCreatedAtEnd()))
));
```

#### SqlCondition factory methods

| Method | SQL operator |
|---|---|
| `like(mapping, value)` | `ILIKE '%value%'` |
| `equalsTo(mapping, value)` | `= :param` |
| `after(mapping, instant/long)` | `>= :param` |
| `before(mapping, instant/long)` | `<= :param` |
| `inSet(mapping, enumSet)` | `IN (:param)` |

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
