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

---

## ADR-005: `SqlOperator.ANY_OF` / `SqlCondition.anyOf(Set<Long>)` — a second `IN`-shaped operator, for id sets specifically

**Status:** Accepted

**Context:** [improvement-075](../backlog/completed/issues/improvement-075-timeline-actor-filter-multi-select.md)
needed the Timeline actor filter to match "any of N selected user ids" — `Set<Long>` — against
`audit_log.actor_id`. `SqlCondition.inSet()` (ADR-004's sibling) already covers "match any of a
set," but is typed `<E extends Enum<E>>` and hardcodes `Enum::name` as its value mapper — it
cannot take a `Set<Long>` as-is, and a same-named overload isn't possible: `Set<E>` and `Set<Long>`
erase to the same raw `Set` parameter type, so `inSet(SqlFilterMapping, Set<Long>)` would be a
compile-time erasure clash against the existing generic method, not a valid overload. Separately,
`inSet()`'s `IN (:param)` template (`SqlOperator.IN`) is safe for enum sets (fixed, tiny
cardinality — 4 `EntityType` values, a handful of `ActionType` values) but is exactly the unbounded
-placeholder-expansion shape improvement-054/067 already fixed twice elsewhere in this session for
`Set<Long>`-typed id filters, whose cardinality is caller-controlled and not bounded the same way.

**Decision:** New `SqlOperator.ANY_OF` (`"%s = ANY(:%s)"`) and `SqlCondition.anyOf(SqlFilterMapping,
Set<Long>)` (array-bind via `.toArray(new Long[0])`, matching every other `= ANY()` call site in
this codebase) — a distinct name and operator, not an `inSet` overload, for the two reasons above:
the erasure clash, and the deliberate operator difference (`= ANY()`, not `IN`) for an
unbounded-cardinality `Set<Long>` versus `inSet()`'s small, fixed-cardinality enum sets. This is a
narrow, justified exception to ADR-003's API freeze — same shape as the `PaginationSqlBuilder`
amendment already logged there: reuses the existing `SqlCondition`/`SqlOperator` machinery
directly, adds no new abstraction layer, and closes a real gap (no existing factory method could
express this) rather than generalizing speculatively.

**Consequences:**
- `AuditLogRepository`'s `actorId`/`equalsTo` binding became `actorIds`/`anyOf` — `AuditTimelineFilterDto.actorId
  (Long)` → `actorIds (Set<Long>)`, matching the DTO's existing `entityTypes`/`actionTypes` shape.
- New `SqlConditionTest`/`SqlOperatorTest` cases cover `anyOf`/`ANY_OF` (non-empty, empty, null —
  same three-case shape as `inSet`'s own tests) plus a real-Postgres `AuditLogRepositoryTest` case
  proving `= ANY()` actually matches "any of N selected actors," not just a unit-level formatting
  check.
- Any *future* `Set<Long>`-typed filter needing "match any of" should reach for `anyOf()` directly,
  not reintroduce `IN (:set)` — this is now the established pattern for unbounded-cardinality id
  sets in query-lib, the same role `= ANY()` array-bind already plays for repository-level bulk
  lookups outside this library (`AttachmentRepository.findExistingUrls()`, `deleteByUrls()`, etc.).
