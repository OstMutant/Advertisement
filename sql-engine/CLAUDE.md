## sql-engine API

Two patterns for repositories depending on query complexity:

### Simple/filterable queries — `SqlEntityProjection` + `RepositoryCustom`

Define `SqlSelectField<T>` constants, an `SqlEntityProjection`, a `SqlFilterBuilder`, then extend `RepositoryCustom<T, F>`:

```java
// 1. Define fields
static final SqlSelectField<Long> ID    = longVal("a.id", "id");
static final SqlSelectField<String> TITLE = str("a.title", "title");

// 2. Projection (FROM source)
SqlEntityProjection<AdvertisementDto> projection =
    new SqlEntityProjection<>(List.of(ID, TITLE, ...), "advertisements a");

// 3. RowMapper lives in RepositoryCustom subclass
@Override
protected AdvertisementDto mapRow(ResultSet rs, int rowNum) {
    return new AdvertisementDto(ID.extract(rs), TITLE.extract(rs), ...);
}

// 4. Inherited methods
findByFilter(filter, pageable)   // SELECT ... WHERE ... ORDER BY ... LIMIT/OFFSET
countByFilter(filter)            // SELECT COUNT(*) FROM ...
```

### Complex/structural queries — `SqlFixedQuery<T>`

For CTEs, UNION ALL, self-joins — the developer writes the full SQL:

```java
public class ActivityProjection extends SqlFixedQuery<ActivityItemDto> {
    static final SqlSelectField<Long> ID = longVal("s.id", "snapshot_id");

    public ActivityProjection(ObjectMapper om) {
        super(List.of(ID, ...));
    }

    @Override public String querySql() { return "WITH ... UNION ALL ..."; }

    @Override public ActivityItemDto mapRow(ResultSet rs, int n) {
        return new ActivityItemDto(ID.extract(rs), ...);
    }
}
```

### Conditions (`SqlCondition` factory methods)
`SqlCondition.like()`, `.equalsTo()`, `.after()`, `.before()`, `.inSet()` — all null-safe via `.applyIfPresent()`. Conditions are composed in a `SqlFilterBuilder` subclass that adds named parameters to `MapSqlParameterSource`.
