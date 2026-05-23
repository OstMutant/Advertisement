## sql-engine API

A lightweight filter/sort library. No projection classes, no base repositories — just utilities for building WHERE clauses and ORDER BY from filter DTOs.

### Classes

| Class | Role |
|---|---|
| `SqlFilterBuilder<F>` | Translates a filter DTO into a SQL WHERE fragment + named params |
| `SqlBoundFilter<F, R>` | Binds one filter DTO field to a column expression and a `SqlCondition` factory |
| `SqlCondition<R>` | A single resolved WHERE condition (expression, param name, value, operator) |
| `SqlFilterMapping` | Interface: `filterProperty()` + `sqlExpression()` — implemented by `SqlBoundFilter` |
| `SqlFilterBinding<F, R>` | Functional interface: `getCondition(F filter) → SqlCondition<R>` |
| `OrderByBuilder` | Converts `Spring Sort` into an `ORDER BY` clause via an alias→expression map |

### Defining a filter

```java
private static final SqlFilterBuilder<AdvertisementFilterDto> FILTER = new SqlFilterBuilder<>(List.of(
        SqlBoundFilter.of("title",          "a.title",      (m, v) -> like(m, v.getTitle())),
        SqlBoundFilter.of("createdAtStart", "a.created_at", (m, v) -> after(m, v.getCreatedAtStart())),
        SqlBoundFilter.of("createdAtEnd",   "a.created_at", (m, v) -> before(m, v.getCreatedAtEnd()))
));
```

### Using in a repository

```java
public List<AdvertisementInfoDto> findByFilter(AdvertisementFilterDto filter, Pageable pageable) {
    var params = new MapSqlParameterSource();
    String where  = FILTER.build(params, filter, "WHERE ");
    String orderBy = OrderByBuilder.build(pageable.getSort(), Map.of("created_at", "a.created_at"));
    return jdbcClient.sql("SELECT ... FROM advertisements a " + where + orderBy + " LIMIT :limit OFFSET :offset")
                     .paramSource(params.addValue("limit", pageable.getPageSize())
                                        .addValue("offset", pageable.getOffset()))
                     .query(ROW_MAPPER).list();
}
```

### `SqlCondition` factory methods

All are null-safe — return `null` when the value is absent; `SqlFilterBuilder` skips null conditions silently.

| Method | SQL operator |
|---|---|
| `like(mapping, value)` | `ILIKE '%value%'` |
| `equalsTo(mapping, value)` | `= :param` |
| `after(mapping, instant/long)` | `>= :param` |
| `before(mapping, instant/long)` | `<= :param` |
| `inSet(mapping, enumSet)` | `IN (:param)` |

### `OrderByBuilder`

```java
OrderByBuilder.build(sort, Map.of(
    "created_at", "a.created_at",
    "title",      "a.title"
))
// returns " ORDER BY a.created_at DESC NULLS LAST" or "" if sort is empty
```

Converts camelCase property names to snake_case before lookup. Unknown properties are silently skipped.
