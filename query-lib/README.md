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
  SqlOperator                — EQUALS, LIKE_IGNORE_CASE, IN, ANY_OF, GREATER_OR_EQUAL, LESS_OR_EQUAL

org.ost.query.sort
  OrderByBuilder             — converts Spring Sort into an ORDER BY clause via an alias→expression map
  SortField                  — record consumed by OrderByBuilder's tiebreaker-aware overload
  PaginationSqlBuilder       — converts a Pageable into a LIMIT :limit OFFSET :offset clause + named params
  OffsetPageable             — a Pageable carrying an arbitrary row offset (not page*size-derived),
                               for callers (e.g. Vaadin's CallbackDataProvider) that already have a
                               raw, possibly non-page-aligned offset
```

That's the entire module — 10 classes, two packages. No UI code lives here; Vaadin query-bar
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
| `like(mapping, value)` | `col ILIKE '%value%' ESCAPE '\'` — `%`/`_`/`\` in `value` are escaped first (backslash escaped before the wildcard characters, so the escaping itself can't be re-escaped into a wrong pattern), so literal wildcard characters in search terms match literally, not as SQL wildcards |
| `equalsTo(mapping, value)` | `col = :param` |
| `after(mapping, instant)` | `col >= :param` |
| `before(mapping, instant)` | `col <= :param` |
| `after(mapping, long)` | `col >= :param` |
| `before(mapping, long)` | `col <= :param` |
| `inSet(mapping, enumSet)` | `col IN (:param)` |
| `anyOf(mapping, longSet)` | `col = ANY(:param)` — for unbounded-cardinality `Set<Long>` id filters, not `inSet`'s small fixed-cardinality enum sets |

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

A second `OrderByBuilder.build(sort, List<SortField>)` overload adds stable tiebreakers (e.g. a
row's own `id`) so paginated results stay deterministic when rows tie on the caller's sort field —
see `.claude/rules/query-lib.md` for usage.

---

## PaginationSqlBuilder

```java
PaginationSqlBuilder.pageLimit(params, pageable)
// → " LIMIT :limit OFFSET :offset"  (adds "limit"/"offset" named params), or "" if pageable is unpaged
```

---

## Dependencies

- `spring-boot-starter-data-jdbc` / `spring-jdbc` — `Sort`, `Pageable`, `MapSqlParameterSource`;
  every public method signature in this module is built directly on these Spring Data JDBC types.
- `lombok` — `@NonNull`/`@RequiredArgsConstructor`/`@NoArgsConstructor` on the builder classes.
- No dependency on any domain module — nothing in `org.ost.platform.*` (`platform-commons`) is
  imported anywhere in this module's own source, matching this module's own "no domain knowledge"
  goal (see `.claude/nav/adr-index.md`).
