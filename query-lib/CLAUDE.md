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
