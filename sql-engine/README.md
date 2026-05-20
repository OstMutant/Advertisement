# sql-engine

A lightweight module for building and executing type-safe SQL queries via `JdbcClient`. No JPA, no reflection magic — just plain SQL assembled from typed building blocks.

---

## Package structure

```
org.ost.sqlengine                     ← public API entry points
  RepositoryCustom                    — SqlCommand-based executor (composition root for plain repos)
  FilterableRepository<T,F>           — extends RepositoryCustom; adds findByFilter / countByFilter
  SqlEntityDescriptor                 — marker interface for all descriptor classes

exec/
  SqlCommand                          — functional interface wrapping a SQL string
  SqlQueryBuilder                     — assembles SELECT / COUNT SQL strings from parts
  SqlQueryExecutor                    — executes queries via JdbcClient

filter/
  SqlFilterBuilder<F>                 — builds WHERE clause from a filter DTO
  SqlBoundFilter<F,R>                 — binds a filter DTO field to a SqlCondition
  SqlFilterBinding<F,R>               — interface: produces SqlCondition from a filter value
  SqlFilterMapping                    — interface: filterProperty + sqlExpression
  SqlCondition<R>                     — a single WHERE condition (expression, param, value, operator)
  [SqlOperator]                       — package-private; EQUALS, LIKE, IN, LESS/GREATER_OR_EQUAL

read/
  SqlSelectField<T>                   — expression + alias + extractor; extract(rs) reads the value
  SqlSelectFieldFactory               — static factory: str, longVal, bool, instant, intVal + *Col variants
  SqlEntityProjection<T>              — abstract RowMapper; owns SELECT clause and ORDER BY
  SqlFixedQuery<T>                    — for fixed/complex queries (CTEs, UNIONs); implement querySql()
  SqlField                            — interface: sqlExpression + alias
  SqlFieldReader<T>                   — @FunctionalInterface: (ResultSet, alias) → T

write/
  SqlWriteField<E>                    — sealed: SqlMappedField | SqlExpressionField
  SqlMappedField<E>                   — maps entity field → column + named param
  SqlExpressionField<E>               — maps column to a literal SQL expression
  SqlWriteFieldFactory                — static factory: field(...), fieldExpr(...)
  SqlEntityWriter<E>                  — builds UPDATE SQL and MapSqlParameterSource from entity
```

Types in `[brackets]` are package-private implementation details — do not reference them from outside sql-engine.

---

## How the pieces fit together

```
Descriptor.Read.FILTER  ──► SqlFilterBuilder  ──► WHERE clause + params
Descriptor.Read.PROJECTION ──► SqlEntityProjection ──► SELECT fields + ORDER BY
                                                  └──► RowMapper<T>
RepositoryCustom / FilterableRepository ──► SqlCommand ──► JdbcClient
```

### Filterable queries

Hold a `FilterableRepository<T, F>` via composition. Pass `Descriptor.Read.PROJECTION` and `Descriptor.Read.FILTER` to its constructor. You get `findByFilter(filter, pageable)`, `countByFilter(filter)`, and `find(customFilter, value)` for free.

```java
public class AdvertisementRepository {

    private final FilterableRepository<AdvertisementDto, AdvertisementFilterDto> query;

    public AdvertisementRepository(JdbcClient jdbcClient) {
        this.query = new FilterableRepository<>(
                jdbcClient,
                AdvertisementDescriptor.Read.PROJECTION,
                AdvertisementDescriptor.Read.FILTER);
    }

    public List<AdvertisementDto> findByFilter(AdvertisementFilterDto filter, Pageable pageable) {
        return query.findByFilter(filter, pageable);
    }
}
```

### Plain SQL execution

Hold a `RepositoryCustom` via composition. Use pre-declared `SqlCommand` constants from the descriptor.

```java
public class AdvertisementMediaChangeConsumer {

    private final RepositoryCustom repo;

    public AdvertisementMediaChangeConsumer(JdbcClient jdbcClient) {
        this.repo = new RepositoryCustom(jdbcClient);
    }

    public void onMediaChanged(Long entityId, MediaSummaryDto summary) {
        repo.execute(AdvertisementDescriptor.Write.UPDATE_MEDIA,
                AdvertisementDescriptor.Write.updateMediaParams(entityId, summary));
    }
}
```

### Complex fixed queries (CTEs, UNIONs)

Extend `SqlFixedQuery<T>` and implement `querySql()`. The developer writes the full SQL; `queryAll` executes it.

```java
public class ActivityProjection extends SqlFixedQuery<ActivityItemDto> {

    static final SqlSelectField<Long> ID = longVal("s.id", "snapshot_id");

    public ActivityProjection() {
        super(List.of(ID, ...));
    }

    @Override
    public String querySql() {
        return "WITH ... UNION ALL ...";
    }

    @Override
    public ActivityItemDto mapRow(ResultSet rs, int n) throws SQLException {
        return new ActivityItemDto(ID.extract(rs), ...);
    }
}
```

### Writing (INSERT / UPDATE)

Declare write fields once in the descriptor's `Write` namespace via `SqlEntityWriter` or `SqlCommand` constants. Call `updateWhere(where)` for the SQL and `params(entity)` for the parameter source.

```java
// Inside XxxDescriptor.Write:
static final SqlEntityWriter<MyEntity> WRITER = SqlEntityWriter.of("my_table",
        field("name",  MyEntity::getName),
        field("email", MyEntity::getEmail));

static final SqlCommand UPDATE_NAME = SqlCommand.of(
        WRITER.updateWhere("id = :id"));
```

---

## Descriptor pattern (Read / Write namespaces)

All SQL constants and param-factory methods live in a `*Descriptor` class, split into `Read` and `Write` inner namespaces:

```java
public final class MyEntityDescriptor implements SqlEntityDescriptor {
    private MyEntityDescriptor() {}

    // Shared column name constants used by both Read and Write
    static final String COL_ID   = "id";
    static final String COL_NAME = "name";

    public static final class Read {
        private Read() {}

        public static final SqlSelectField<Long>   ID   = longVal("e.id",   COL_ID);
        public static final SqlSelectField<String> NAME = str("e.name", COL_NAME);

        public static final SqlEntityProjection<MyEntityDto> PROJECTION =
                new SqlEntityProjection<>(List.of(ID, NAME), "my_entity e") {
                    @Override
                    public MyEntityDto mapRow(ResultSet rs, int n) throws SQLException {
                        return new MyEntityDto(ID.extract(rs), NAME.extract(rs));
                    }
                };

        public static final SqlFilterBuilder<MyFilterDto> FILTER =
                new SqlFilterBuilder<>(List.of(
                        SqlBoundFilter.of("name", NAME,
                                (m, f) -> SqlCondition.like(m, f.name()))
                ));
    }

    public static final class Write {
        private Write() {}

        static final SqlCommand UPDATE_NAME = SqlCommand.of(
                "UPDATE my_entity SET name = :name WHERE id = :id");

        static MapSqlParameterSource updateNameParams(Long id, String name) {
            return new MapSqlParameterSource()
                    .addValue("id",   id)
                    .addValue("name", name);
        }
    }
}
```
