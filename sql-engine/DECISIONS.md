# Architecture & Technical Decisions — sql-engine

---

## Ongoing — Framework-agnostic SQL query library

**Decision:** `sql-engine` is a plain Java library with no Spring Boot autoconfiguration and no domain knowledge. It provides the query-building API (`SqlSelectField`, `SqlFixedQuery`, `SqlEntityProjection`, `SqlFilterBuilder`, `SqlCondition`, `SqlEntityWriter`) used by all repository classes across all modules.

**Why:** Keeping the query API decoupled from Spring allows unit-testing it without a Spring context (pure JUnit), and prevents domain concerns from leaking into infrastructure. Any module can depend on it without pulling in Spring Boot starters.

**Rejected:** Embedding the query API inside `marketplace-app` or `platform-contracts` — would prevent reuse across starters without circular dependencies.

---

## Ongoing — Two query patterns: SqlEntityProjection vs SqlFixedQuery

**Decision:** Repositories choose between two patterns depending on query complexity:
- `SqlEntityProjection` + `FilterableRepository` — for filterable/pageable queries with dynamic WHERE clauses
- `SqlFixedQuery<T>` — for CTEs, UNION ALL, self-joins where the developer writes the full SQL

**Why:** A single abstraction for both cases either over-constrains simple queries (forcing unnecessary indirection) or under-constrains complex ones (hiding SQL behind inadequate builders). The two-pattern split keeps each use case clean.

**Rejected:** A single `SqlQuery` abstraction covering both — the impedance mismatch between dynamic filtering and structural queries made a unified API awkward.

---

## 2026-05-20 — RepositoryCustom / FilterableRepository split

**Decision:** `RepositoryCustom` is a non-generic class that wraps `JdbcClient` and exposes only `SqlCommand`-based methods (`execute`, `executeUpdate`, `findOne`, `findAll`, `jdbcClient()`). `FilterableRepository<T, F>` extends it and adds `sqlProjection`, `filterBuilder`, `SqlQueryBuilder`, and the projection-based methods (`findByFilter`, `countByFilter`, `find`, `findOne(where, params)`). `SqlQueryExecutor` lost its vestigial `<T>` type parameter (none of its methods used it). Repositories that only execute hand-written SQL hold `private final RepositoryCustom repo` via composition; repositories that need filtering hold `private final FilterableRepository<T, F> query` via composition. No repository extends either class.

**Why:** The old `RepositoryCustom<T, F>` carried nullable `sqlProjection`/`filterBuilder` fields and a `requireProjection()` guard, making the constructor choice invisible at call sites and both type parameters meaningless for pure-SQL repositories. The split removes all null fields, eliminates the guard, and makes the two use cases structurally distinct.

**How to apply:** Repositories that do `execute`/`findOne` with raw SQL → `new RepositoryCustom(jdbcClient)`. Repositories that do `findByFilter`/`countByFilter` → `new FilterableRepository<>(jdbcClient, projection, filterBuilder)`. Never extend either class.

---

## Hard limit — Do not extend the sql-engine DSL beyond its current scope

**Decision:** The sql-engine API is frozen at its current abstraction level. The following are explicitly out of scope and must never be added:
- JOIN DSL (fluent join builders, relationship traversal)
- Expression AST (composable expressions, operator trees)
- Automatic query rewriting or optimization
- Generic pagination abstractions beyond what already exists
- Dirty tracking, change detection, or relationship persistence in `SqlEntityWriter`

**Why:** The current API covers exactly two cases: simple filterable queries (`SqlEntityProjection`) and complex structural queries (`SqlFixedQuery` with raw SQL). Any query that does not fit the first case belongs in the second — the developer writes SQL directly. Adding a JOIN DSL or expression layer would recreate the problems of JPA/QueryDSL: hidden complexity, leaky abstractions, and SQL that is harder to read than the raw version.

**Rule:** If you feel the urge to add a new abstraction to sql-engine, write raw SQL in `SqlFixedQuery` instead.

---

## Ongoing — Null-safe conditions via applyIfPresent

**Decision:** All `SqlCondition` factory methods (`like`, `equalsTo`, `after`, `before`, `inSet`) are null-safe and applied via `.applyIfPresent()` in `SqlFilterBuilder` subclasses. A null filter value silently skips the condition rather than generating `WHERE field = NULL`.

**Why:** Filter DTOs have optional fields. Null-safety at the condition level eliminates per-field null checks in every repository and prevents accidental `IS NULL` queries.
