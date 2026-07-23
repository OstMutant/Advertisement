# query-lib

A lightweight SQL filter/sort/pagination helper library. No Spring Boot autoconfiguration, no
Vaadin dependency, no domain knowledge.

---

## Package structure

```
org.ost.query.filter
  SqlFilterBuilder<F>        — translates a filter DTO into a WHERE clause + named params
  SqlBoundFilter<F, R>       — binds a filter DTO field to a SQL expression and a condition factory
  SqlFilterBinding<F, R>     — @FunctionalInterface: getCondition(F filter) → SqlCondition<R>
  SqlFilterMapping           — interface: filterProperty() + sqlExpression()
  SqlCondition<R>            — a single resolved WHERE condition (expression, param, value, operator)
  SqlOperator                — EQUALS, LIKE_IGNORE_CASE, IN, GREATER_OR_EQUAL, LESS_OR_EQUAL

org.ost.query.sort
  OrderByBuilder             — converts Spring Sort into an ORDER BY clause via an alias→expression map
  PaginationSqlBuilder       — converts a Pageable into a LIMIT :limit OFFSET :offset clause + named params
```

That's the entire module — 8 classes, two packages. No UI code lives here; Vaadin query-bar
components live in `marketplace-app`.

---

## SQL Usage

### 1. Declare a filter

```java
private static final SqlFilterBuilder<AdvertisementFilterDto> FILTER = new SqlFilterBuilder<>(List.of(
        SqlBoundFilter.of("title",          "a.title",      (m, v) -> like(m, v.getTitle())),
        SqlBoundFilter.of("createdAtStart", "a.created_at", (m, v) -> after(m, v.getCreatedAtStart())),
        SqlBoundFilter.of("createdAtEnd",   "a.created_at", (m, v) -> before(m, v.getCreatedAtEnd()))
));
```

### 2. Use in a JdbcClient query

```java
public List<AdvertisementInfoDto> findByFilter(AdvertisementFilterDto filter, Pageable pageable) {
    var params = new MapSqlParameterSource();
    String where   = FILTER.build(params, filter, "WHERE ");
    String orderBy = OrderByBuilder.build(pageable.getSort(), SORT_MAP);
    String limit   = PaginationSqlBuilder.pageLimit(params, pageable);
    return jdbcClient.sql("SELECT ... FROM advertisement a " + where + orderBy + limit)
                     .paramSource(params)
                     .query(ROW_MAPPER).list();
}
```

---

## SqlCondition factory methods

All are null-safe: return `null` when the filter value is absent; `SqlFilterBuilder` skips null conditions automatically.

| Method | SQL |
|---|---|
| `like(mapping, value)` | `col ILIKE '%value%'` |
| `equalsTo(mapping, value)` | `col = :param` |
| `after(mapping, instant)` | `col >= :param` |
| `before(mapping, instant)` | `col <= :param` |
| `after(mapping, long)` | `col >= :param` |
| `before(mapping, long)` | `col <= :param` |
| `inSet(mapping, enumSet)` | `col IN (:param)` |

---

## OrderByBuilder

```java
private static final Map<String, String> SORT_MAP = Map.of(
        "created_at", "a.created_at",
        "title",      "a.title"
);

OrderByBuilder.build(sort, SORT_MAP)
// → " ORDER BY a.created_at DESC NULLS LAST"  or  ""
```

Looks up each `Sort.Order`'s property directly in the alias map — no case conversion happens.
Map keys must be the exact camelCase DTO field name (e.g. via `SomeDto.Fields.xyz`), not a
hand-converted snake_case string. Unknown sort properties are silently skipped.

---

## PaginationSqlBuilder

```java
PaginationSqlBuilder.pageLimit(params, pageable)
// → " LIMIT :limit OFFSET :offset"  (adds "limit"/"offset" named params), or "" if pageable is unpaged
```
