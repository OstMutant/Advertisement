# Architecture & Technical Decisions — query-lib

---

## Renamed from query-starter to query-lib

**Decision:** Module renamed from `query-starter` to `query-lib` to accurately reflect its role — it is a plain Java helper library with no Spring Boot autoconfiguration and no domain knowledge.

**Why:** The `-starter` suffix implies Spring Boot autoconfiguration, which this module does not provide. `query-lib` is a static utility library used directly by repositories via `private static final` constants.

---

## Plain Java library — no autoconfiguration

**Decision:** `query-lib` provides only `SqlFilterBuilder`, `SqlBoundFilter`, `SqlCondition`, `SqlFilterBinding`, `SqlFilterMapping`, `SqlOperator`, and `OrderByBuilder`. No `@AutoConfiguration`, no Spring beans, no `META-INF/spring` registration.

**Why:** Keeping the query API decoupled from Spring allows unit-testing without a Spring context (pure JUnit), and prevents domain concerns from leaking into infrastructure.

---

## Hard limit — Do not extend the query-lib DSL beyond its current scope

**Decision:** The API is frozen at its current abstraction level. The following are explicitly out of scope:
- JOIN DSL or expression AST
- Automatic query rewriting or optimization
- Generic pagination abstractions beyond what already exists
- Conditional param helpers

**Rule:** If you feel the urge to add a new abstraction to query-lib, write raw SQL in the repository instead.

---

## Null-safe conditions via applyIfPresent

**Decision:** All `SqlCondition` factory methods (`like`, `equalsTo`, `after`, `before`, `inSet`) are null-safe. A null filter value silently skips the condition.

**Why:** Filter DTOs have optional fields. Null-safety at the condition level eliminates per-field null checks in every repository.
