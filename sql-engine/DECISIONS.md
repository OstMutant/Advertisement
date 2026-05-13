# Architecture & Technical Decisions — sql-engine

---

## Ongoing — Framework-agnostic SQL query library

**Decision:** `sql-engine` is a plain Java library with no Spring Boot autoconfiguration and no domain knowledge. It provides the query-building API (`SqlSelectField`, `SqlFixedQuery`, `SqlEntityProjection`, `SqlFilterBuilder`, `SqlCondition`, `SqlEntityWriter`) used by all repository classes across all modules.

**Why:** Keeping the query API decoupled from Spring allows unit-testing it without a Spring context (pure JUnit), and prevents domain concerns from leaking into infrastructure. Any module can depend on it without pulling in Spring Boot starters.

**Rejected:** Embedding the query API inside `advertisement-app` or `advertisement-contracts` — would prevent reuse across starters without circular dependencies.

---

## Ongoing — Two query patterns: SqlEntityProjection vs SqlFixedQuery

**Decision:** Repositories choose between two patterns depending on query complexity:
- `SqlEntityProjection` + `RepositoryCustom` — for filterable/pageable queries with dynamic WHERE clauses
- `SqlFixedQuery<T>` — for CTEs, UNION ALL, self-joins where the developer writes the full SQL

**Why:** A single abstraction for both cases either over-constrains simple queries (forcing unnecessary indirection) or under-constrains complex ones (hiding SQL behind inadequate builders). The two-pattern split keeps each use case clean.

**Rejected:** A single `SqlQuery` abstraction covering both — the impedance mismatch between dynamic filtering and structural queries made a unified API awkward.

---

## Ongoing — Null-safe conditions via applyIfPresent

**Decision:** All `SqlCondition` factory methods (`like`, `equalsTo`, `after`, `before`, `inSet`) are null-safe and applied via `.applyIfPresent()` in `SqlFilterBuilder` subclasses. A null filter value silently skips the condition rather than generating `WHERE field = NULL`.

**Why:** Filter DTOs have optional fields. Null-safety at the condition level eliminates per-field null checks in every repository and prevents accidental `IS NULL` queries.
