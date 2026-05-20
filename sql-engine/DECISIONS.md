# Architecture & Technical Decisions — sql-engine

---

## Ongoing — Framework-agnostic SQL query library

**Decision:** `sql-engine` is a plain Java library with no Spring Boot autoconfiguration and no domain knowledge. It provides the query-building API (`SqlSelectField`, `SqlFixedQuery`, `SqlEntityProjection`, `SqlFilterBuilder`, `SqlCondition`, `SqlEntityWriter`, `SqlParams`) used by all repository classes across all modules.

**Why:** Keeping the query API decoupled from Spring allows unit-testing it without a Spring context (pure JUnit), and prevents domain concerns from leaking into infrastructure. Any module can depend on it without pulling in Spring Boot starters.

**Rejected:** Embedding the query API inside `marketplace-app` or `platform-contracts` — would prevent reuse across starters without circular dependencies.

---

## Ongoing — Two query patterns: SqlEntityProjection vs SqlFixedQuery

**Decision:** Repositories choose between two patterns depending on query complexity:
- `SqlEntityProjection` + `FilterableRepository` — for filterable/pageable queries with dynamic WHERE clauses
- `SqlFixedQuery<T>` — for CTEs, UNION ALL, self-joins where the developer writes the full SQL

**Why:** A single abstraction for both cases either over-constrains simple queries or under-constrains complex ones. The two-pattern split keeps each use case clean.

**Rejected:** A single `SqlQuery` abstraction covering both — the impedance mismatch between dynamic filtering and structural queries made a unified API awkward.

---

## 2026-05-20 — RepositoryCustom / FilterableRepository split (current state)

**Decision:** `RepositoryCustom` wraps `JdbcClient` via a composed `SqlQueryExecutor` and exposes: `executeUpdate(SqlCommand, params): int`, `findOne`, `findAll`, `queryAll(SqlFixedQuery, params)`. No `jdbcClient()` accessor — internal JDBC access does not leak to callers. `FilterableRepository<T, F>` extends it and adds projection-based methods (`findByFilter`, `countByFilter`, `find`, `findOneWhere`). Repositories hold either via composition — never extend either class. CrudRepository constructors are package-private to avoid leaking the package-private interface into the public API.

`SqlParams.Builder extends MapSqlParameterSource` — the builder IS a `MapSqlParameterSource`, so `with(...).add(...).add(...)` chains need no terminal `.build()` call.

**Why:** Null fields, guards, and a leaking `jdbcClient()` accessor were removed. The split makes the two use cases structurally distinct. `executeUpdate` returning `int` makes the affected-row count available without a separate call. `queryAll` on `RepositoryCustom` delegates to `SqlFixedQuery.queryAll(jdbcClient, params)` — callers never touch raw JdbcClient.

**How to apply:** Repositories that do `executeUpdate`/`findOne`/`findAll` with raw SQL → `new RepositoryCustom(jdbcClient)`. Repositories that do `findByFilter`/`countByFilter` → `new FilterableRepository<>(jdbcClient, projection, filterBuilder)`. Never extend either class.

---

## Hard limit — Do not extend the sql-engine DSL beyond its current scope

**Decision:** The sql-engine API is frozen at its current abstraction level. The following are explicitly out of scope and must never be added:
- JOIN DSL (fluent join builders, relationship traversal)
- Expression AST (composable expressions, operator trees)
- Automatic query rewriting or optimization
- Generic pagination abstractions beyond what already exists
- Dirty tracking, change detection, or relationship persistence in `SqlEntityWriter`
- Conditional param helpers in `SqlParams` (`.ifNotNull()`, `.json()`, `.enumVal()`, `.array()`) — type conversion belongs in descriptor param-factory methods

**Why:** The current API covers exactly two cases: simple filterable queries and complex structural queries with raw SQL. Adding a JOIN DSL or expression layer would recreate the problems of JPA/QueryDSL. `SqlParams` minimalism is intentional — it is a thin factory, not a DSL.

**Rule:** If you feel the urge to add a new abstraction to sql-engine, write raw SQL in `SqlFixedQuery` instead.

---

## Ongoing — Null-safe conditions via applyIfPresent

**Decision:** All `SqlCondition` factory methods (`like`, `equalsTo`, `after`, `before`, `inSet`) are null-safe. A null filter value silently skips the condition rather than generating `WHERE field = NULL`.

**Why:** Filter DTOs have optional fields. Null-safety at the condition level eliminates per-field null checks in every repository and prevents accidental `IS NULL` queries.

---

## Ongoing — Architecture guardrails

Five boundaries to enforce as the codebase grows:

1. **Projection = mapping concern only.** `SqlFixedQuery` subclasses map `ResultSet` → DTO. They must not resolve names, parse JSON, or contain business logic. `ObjectMapper` and `List<EntityDisplayNameResolver>` inside projections are an accepted current state — do not add more. If a projection starts doing service-layer work, extract a post-query transformation step in the service instead.

2. **JSON parsing belongs on the service layer.** `parseChanges(...)` living in a projection is a known smell. A future `SnapshotCodec<T>` (or similar) centralizes `ObjectMapper.readValue` calls and removes the leak. Until then: do not add more JSON parsing inside projections.

3. **Descriptor size.** When a single descriptor exceeds ~200 lines or gains a third `Read.*` sub-namespace, consider splitting into `*Descriptor` (columns + shared constants) + `*Queries` (SELECT SQL) + `*Mappings` (projections). `AuditLogDescriptor` is approaching this boundary.

4. **`SqlParams` stays minimal.** `with().add()` is the complete API. Any call site that needs conditional params, JSON encoding, or array conversion handles it in the descriptor's param-factory method — not in `SqlParams`.

5. **`RepositoryCustom` is not a God Service.** It must never acquire: transactions (use `@Transactional` on the service), caching, retry logic, metrics, auditing, or authorization. It is a thin `JdbcClient` wrapper. The day it grows past its current method count is the day it starts becoming infrastructure middleware.

---

## Convention — Two accepted patterns for Write column names

**Decision:** Descriptors must never use raw string literals for column names when a typed constant exists. Two patterns are accepted:

- **`SqlEntityWriter` path** (`field()` / `fieldExpr()`): pass `@FieldNameConstants`-generated `Fields.*` constants — `SqlWriteFieldFactory.toSnakeCase()` converts camelCase to the DB column name automatically (`updatedAt` → `updated_at`). Reference: `UserDescriptor.Write`.
- **Raw SQL path** (`SqlCommand` strings): reference column names via `READ_FIELD.columnName()` — e.g. `DELETED_AT.columnName()`. Reference: `AdvertisementDescriptor.Write`, `AttachmentDescriptor.Write`, `AuditLogDescriptor.Write`.

**Why:** Eliminates string literals scattered across Write descriptors. Column name changes in the DB (via Liquibase) only need to be updated in the `SqlSelectField` declaration — Write side follows automatically via `columnName()` or `Fields.*`.
