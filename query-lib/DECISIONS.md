# Architecture & Technical Decisions — query-lib

---

## ADR-001: Renamed from query-starter to query-lib
**Status:** Accepted

**Context:** The `-starter` suffix implies Spring Boot autoconfiguration. This module provides
none — it is a static SQL helper library used directly by repositories as `private static final`
constants.

**Decision:** Module renamed to `query-lib`.

**Consequences:** Consumers import the artifact as a plain library dependency, not as a starter.
No `META-INF/spring` registration, no `@AutoConfiguration`.

---

## ADR-002: Plain Java library — no Spring autoconfiguration
**Status:** Accepted

**Context:** Repositories need dynamic filter/sort SQL without coupling to Spring context
(to allow pure JUnit testing without an application context).

**Decision:** `query-lib` provides only `SqlFilterBuilder`, `SqlBoundFilter`, `SqlCondition`,
`SqlFilterBinding`, `SqlFilterMapping`, `SqlOperator`, `OrderByBuilder`, and `PaginationSqlBuilder`
(added later — see ADR-003's amendment).
No `@AutoConfiguration`, no Spring beans, no `META-INF/spring` registration.

**Consequences:**
- Unit-testable without Spring context.
- Domain concerns cannot leak into the infrastructure layer through this library.

---

## ADR-003: API scope frozen — no DSL extensions
**Status:** Accepted

**Context:** The query DSL is sufficient for the current filter/sort patterns. Adding JOIN DSL,
expression ASTs, or generic pagination abstractions would increase complexity without benefit.

**Decision:** The API is frozen at its current abstraction level. Explicitly out of scope:
JOIN DSL, expression AST, automatic query rewriting, conditional param helpers.

**Amendment (verified 2026-07-13):** `PaginationSqlBuilder.pageLimit(params, pageable)` was added
to `org.ost.query.sort` since this ADR was written — a single-method `LIMIT`/`OFFSET` clause
builder, not a general pagination abstraction (no page-count math, no response wrapping). It was
a pragmatic exception to the "no generic pagination abstractions" line above: every repository
needed the exact same `LIMIT :limit OFFSET :offset` snippet, so extracting it avoided copy-paste,
not scope creep. The API is otherwise still frozen at this level.

**Consequences:** If you feel the urge to add a new abstraction to query-lib beyond
`PaginationSqlBuilder`'s narrow scope, write raw SQL in the repository instead.

---

## ADR-004: Null-safe conditions — null filter value skips condition
**Status:** Accepted

**Context:** Filter DTOs have optional fields. Without null-safety at the condition level,
every repository would need per-field null checks.

**Decision:** All `SqlCondition` factory methods (`like`, `equalsTo`, `after`, `before`, `inSet`)
are null-safe. A null filter value silently skips the condition.

**Consequences:** Callers do not need null guards before building filter conditions.
