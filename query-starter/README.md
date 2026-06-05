# query-starter

A lightweight SQL filter/sort library + Vaadin UI query components. Two layers in one starter.

---

## Package structure

```
org.ost.query

filter/
  SqlFilterBuilder<F>        — translates a filter DTO into a WHERE clause + named params
  SqlBoundFilter<F, R>       — binds a filter DTO field to a SQL expression and a condition factory
  SqlFilterBinding<F, R>     — @FunctionalInterface: getCondition(F filter) → SqlCondition<R>
  SqlFilterMapping            — interface: filterProperty() + sqlExpression()
  SqlCondition<R>            — a single resolved WHERE condition (expression, param, value, operator)
  [SqlOperator]              — package-private: EQUALS, LIKE_IGNORE_CASE, IN, GREATER_OR_EQUAL, LESS_OR_EQUAL

sort/
  OrderByBuilder             — converts Spring Sort into an ORDER BY clause via an alias→expression map

org.ost.query.ui

ui/
  QueryBlock<T>              — abstract VerticalLayout base for filter+sort panels
  QueryStatusBar<T>          — status bar showing active filters and sort, toggles QueryBlock visibility
  QueryMessages              — TranslationKey enum for sort icon and action tooltips

ui/filter/
  FilterProcessor<F>         — manages filter state and field highlight lifecycle
  FilterFieldMeta<I,F,R>     — metadata binding: getter, setter, validation, display name
  FilterMapper<F>            — copy/update contract for filter DTOs
  ValidationService<F>       — validates filter DTOs via jakarta.validation
  ValidationPredicates       — common BiPredicate builders for FilterFieldMeta

ui/sort/
  SortProcessor              — manages sort state and SortIcon color lifecycle
  SortFieldMeta              — metadata for a sort field: property + i18n key
  CustomSort                 — mutable sort state wrapper (wraps Spring Sort)

ui/elements/
  SortIcon                   — clickable sort direction icon (cycles NEUTRAL → ASC → DESC)
  SvgIcon                    — inline SVG icon component

ui/elements/action/
  QueryActionBlock           — Apply / Clear button row
  QueryActionButton          — single action button with SVG icon and tooltip
  QueryActionBlockHandler    — interface for dirty-state updates

ui/elements/fields/
  QueryTextField, QueryComboField, QueryDateTimeField,
  QueryMultiSelectComboField, QueryNumberField — pre-styled query input fields

ui/elements/rows/
  QueryInlineRow             — horizontal row with label+sort icon on the left, fields on the right

ui/utils/
  HighlighterUtil            — CSS highlight class management for changed/dirty/invalid states
  TimeZoneUtil               — client-side timezone detection and Instant formatting
  SvgUtil                    — SVG resource loading from classpath
```

Types in `[brackets]` are package-private — do not reference them from outside query-starter.

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
    return jdbcClient.sql("SELECT ... FROM advertisements a " + where + orderBy + " LIMIT :limit OFFSET :offset")
                     .paramSource(params.addValue("limit", pageable.getPageSize())
                                        .addValue("offset", pageable.getOffset()))
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

Converts camelCase property names to snake_case before lookup. Unknown sort properties are silently skipped.
