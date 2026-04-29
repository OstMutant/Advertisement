# sql-engine

A lightweight module for building and executing type-safe SQL queries via `NamedParameterJdbcTemplate`. No JPA, no reflection magic — just plain SQL assembled from typed building blocks.

---

## Package structure

```
exec/
  SqlQueryBuilder       — assembles SELECT / COUNT SQL strings from parts
  RepositoryExecutor    — executes queries: findAll, findOne, count

filter/
  FilterMapping         — interface: filterProperty + sqlExpression
  FilterBinding<F,R>    — extends FilterMapping; produces SqlCondition from a filter object F
  DefaultFilterBinding  — record implementation of FilterBinding
  FilterBuilder<F>      — abstract; holds List<FilterBinding>; builds WHERE clause + params
  SqlCondition<R>       — a single WHERE condition (expression, param name, value, operator)
    SqlOperator         — EQUALS, LIKE_IGNORE_CASE, IN, LESS_OR_EQUAL, GREATER_OR_EQUAL

projection/
  SqlFieldProjection    — interface: sqlExpression + alias
  SqlFieldDefinition<T> — record: expression + alias + SqlFieldReader extractor
  SqlFieldReader<T>     — @FunctionalInterface (ResultSet, alias) → T
  SqlFieldBuilder       — static factory: str, id, longVal, bool, instant, intVal, strArray
  SqlProjection<T>      — abstract RowMapper; owns SELECT clause and ORDER BY from Sort
  SqlFixedProjection<T> — extends SqlProjection; for fixed/complex queries (CTEs, UNIONs);
                          subclass provides querySql()

writer/
  SqlColumnDefinition<E> — maps one entity field to a named SQL parameter
  SqlEntityWriter<E>     — builds INSERT / UPDATE SQL and MapSqlParameterSource from entity

RepositoryCustom<T,F>   — base class for repositories; wires projection + filter + executor;
                          provides findByFilter(filter, pageable) + countByFilter(filter) + find(...)
```

---

## How the pieces fit together

```
FilterBuilder ──► WHERE clause + params
SqlProjection ──► SELECT fields + ORDER BY
SqlQueryBuilder ──► full SQL string
RepositoryExecutor ──► executes via NamedParameterJdbcTemplate
RepositoryCustom ──► orchestrates all of the above
```

### Reading (SELECT)

Extend `RepositoryCustom<T, F>` and pass a `SqlProjection<T>` and `FilterBuilder<F>` to its constructor. You get `findByFilter`, `countByFilter`, and `find` for free.

For complex fixed queries (CTEs, UNION ALL), extend `SqlFixedProjection<T>` instead and implement `querySql()`.

### Writing (INSERT / UPDATE)

Use `SqlEntityWriter.of(col(...), col(...))` to declare columns once. Call `insertInto(table)` or `updateWhere(table, where)` to get the SQL, and `params(entity)` for the parameter source.

---

## Usage example

```java
// Projection — declare fields once
public class AdProjection extends SqlProjection<AdDto> {
    static final SqlFieldDefinition<Long>   ID    = id("a.id", "id");
    static final SqlFieldDefinition<String> TITLE = str("a.title", "title");

    public AdProjection() {
        super(List.of(ID, TITLE), "advertisement a");
    }

    @Override
    public AdDto mapRow(ResultSet rs, int n) throws SQLException {
        return new AdDto(ID.extract(rs), TITLE.extract(rs));
    }
}

// Filter — bind DTO fields to SQL conditions
public class AdFilterBuilder extends FilterBuilder<AdFilterDto> {
    public AdFilterBuilder() {
        super(List.of(
            DefaultFilterBinding.of("title", AdProjection.TITLE,
                (m, f) -> SqlCondition.like(m, f.title()))
        ));
    }
}

// Repository — combine and execute
public class AdRepository extends RepositoryCustom<AdDto, AdFilterDto> {
    public AdRepository(NamedParameterJdbcTemplate jdbc) {
        super(jdbc, new AdProjection(), new AdFilterBuilder());
    }
}
```
