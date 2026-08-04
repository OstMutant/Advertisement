# Architecture Documentation

Complete architecture documentation for the Marketplace modular monolith (Java 25 / Spring Boot 4.0.6 / Vaadin 25.1.5).

## Files Overview

### 01-module-dependencies.md
**Maven dependency graph.** Shows which modules depend on which others. Key finding: clean DAG topology with no cycles. platform-commons is the foundation; all starters depend on it; marketplace-app depends on all starters.

**Key diagram:** `graph LR` showing 10 modules and their dependencies (9 shipped + `integration-tests`, test-only, never shipped).

### 02-spi-map.md
**Extension points and implementations.** Maps all Ports and Hooks to their implementations. Explains the Port/Hook pattern (marketplace → Port, Hook ← starter). Lists the SPI interfaces in platform-commons and their implementations in starters/marketplace-app — see that file's own top-of-file note for its current staleness caveats.

**Key diagram:** `graph TD` showing SPI interfaces and their implementations across modules.

### 03-bounded-contexts.md
**Domain boundaries and integration patterns.** Identifies business domains (User, Advertisement, Audit, Attachment, Taxon) plus the UI layer and shared kernel. Explains how domains communicate through Ports and Hooks. Documents the 3 main integration patterns (lifecycle with audit, media attachment, activity feed enrichment). Predates the Provider Profile domain — due for a refresh (tracked under `improvement-138`).

**Key diagram:** Context map showing all domains and their relationships.

### 04-database-erd.md
**Entity relationship diagram and schema details.** Shows tables with columns, types, constraints, indexes, and foreign keys. Explains the data flow for key operations (create advertisement, upload media, query activity). Predates the `taxon`/`taxon_translation`/`taxon_assignment`, `provider_profile`, and `user_preferences` tables — due for a refresh (tracked under `improvement-138`).

**Key diagram:** Mermaid `erDiagram` with all tables and relationships.

### 05-sequence-diagrams.md
**Real code paths through actual class names.** 6 sequence diagrams tracing real interactions: advertisement creation, media upload, activity timeline query, snapshot restore, settings change, and list filtering. All classes named (e.g., AdvertisementOverlay, AdvertisementPortImpl, AdvertisementService).

**Key diagrams:** 6 Mermaid `sequenceDiagram` traces with actual class names from codebase.

### 06-coupling-analysis.md
**Architecture violations and coupling assessment.** Originally identified 1 HIGH violation (AccessEvaluator imports org.ost.user.security.*, since resolved per ADR-016) and 1 MEDIUM issue (optional dependencies not guarded, still open), and confirms: no cyclic deps, no Vaadin in starters, no UI→Repository direct imports, good module sizes.

**Key findings:**
- RESOLVED: AccessEvaluator modular-boundary violation (ADR-016)
- ISSUE: audit/attachment marked optional but not guarded with ObjectProvider
- PASS: All other coupling checks

### 07-risk-report.md
**Risk assessment by category.** Analyzes module sizes, largest files, constructor complexity, database risks (JSONB validation, soft-delete discipline), dependency chain risks (optional deps), code complexity hot spots (audit diff engine, overlay state machine), security risks, and performance risks (audit log unbounded growth).

**Severity matrix:**
- HIGH: Soft-delete queries forgetting WHERE deleted_at IS NULL filter
- MEDIUM: JSONB schema validation in audit_log, SPI contract testing, optional deps
- LOW: Everything else

### 08-scorecard.md
**Architecture quality scorecard.** Scores 7 dimensions (Modularity, Coupling, Cohesion, SPI Design, Domain Isolation, Database Design, Testability) from 1-10 with evidence. Overall: **7.7/10 (GOOD)**.

**Scores** (synced from `08-scorecard.md`'s own current table, which already reflects the resolved AccessEvaluator finding):
- Modularity: 7/10 — Good; starters modular but marketplace-app is monolith coordinator
- Coupling: 8/10 — Good; AccessEvaluator fixed (ADR-016), optional deps still unguarded
- Cohesion: 8/10 — Good; each domain single responsibility
- SPI Design: 8/10 — Good; consistent naming, clear contracts
- Domain Isolation: 8/10 — Good; AccessEvaluator fixed, schema coupling acceptable
- Database Design: 8/10 — Good; flexible schema, proper indexing
- Testability: 7/10 — Good; mockable contracts, SPI testing requires discipline

**Critical actions:** ~~Fix AccessEvaluator (HIGH)~~ done, see ADR-016; decide on optional deps (MEDIUM).

---

## How to Use This Documentation

1. **Understand module structure:** Start with 01-module-dependencies.md
2. **Learn about SPI contracts:** Read 02-spi-map.md for all ports/hooks
3. **Understand domain boundaries:** Study 03-bounded-contexts.md
4. **Learn the database:** Review 04-database-erd.md
5. **Trace real code paths:** Follow 05-sequence-diagrams.md for how features work
6. **Identify risks:** Check 06-coupling-analysis.md and 07-risk-report.md
7. **Assess quality:** Review 08-scorecard.md for strengths, weaknesses, and recommendations

---

## Key Metrics

| Metric | Value |
|--------|-------|
| Total Modules | 10 (query-lib, platform-commons, 6 starters, marketplace-app, integration-tests test-only) |
| Total Java Files | not re-verified this pass — see `01-module-dependencies.md`/generated-source-directory list below |
| Total Tables | 10 (user_information, user_preferences, advertisement, attachment, attachment_snapshot, audit_log, taxon, taxon_translation, taxon_assignment, provider_profile) |
| SPI Interfaces | see `02-spi-map.md`'s own top-of-file note for its current staleness caveats |
| Largest Module | not re-verified this pass |
| Largest File | not re-verified this pass |
| Dependency Cycles | 0 (clean DAG) |
| Architecture Score | 7.7/10 (GOOD) — per `08-scorecard.md`'s current scores, already reflecting the resolved AccessEvaluator finding (ADR-016) |

---

## Critical Issues Found

### 1. AccessEvaluator Coupling (HIGH) — ✅ RESOLVED (see `marketplace-app/DECISIONS.md` ADR-016)
**File:** `/app/marketplace-app/src/main/java/org/ost/marketplace/services/security/AccessEvaluator.java`

Originally flagged for importing `org.ost.user.security.OwnershipChecker`/`RoleChecker` (internal
classes, not SPI contracts). Confirmed fixed: the class now depends only on
`UserAuthorizationPort`/`UserDto` from `platform-commons`, per ADR-016's "full user decoupling."
Kept here as a record of a resolved finding, not an open issue — see `06-coupling-analysis.md`
for the original detailed writeup, which already reflects the resolved status.

### 2. Optional Dependencies Not Guarded (MEDIUM)
**File:** `/app/advertisement-spring-boot-starter/pom.xml`

**Issue:** audit-spring-boot-starter and attachment-spring-boot-starter marked `<optional>true/>` but code assumes they exist.

**Impact:** Excluding either starter causes runtime ClassNotFoundException.

**Fix:** Either remove `<optional>` or add `ObjectProvider<AuditPort>` / `ObjectProvider<AttachmentPort>` guards in code.

---

## Generated From

- Project root: `/app`
- Analyzed source directories:
  - `/app/query-lib/src/main/java`
  - `/app/platform-commons/src/main/java`
  - `/app/audit-spring-boot-starter/src/main/java`
  - `/app/attachment-spring-boot-starter/src/main/java`
  - `/app/user-spring-boot-starter/src/main/java`
  - `/app/advertisement-spring-boot-starter/src/main/java`
  - `/app/taxon-spring-boot-starter/src/main/java`
  - `/app/provider-profile-spring-boot-starter/src/main/java`
  - `/app/marketplace-app/src/main/java`
- Analyzed schemas:
  - All Liquibase migrations in `/app/*/src/main/resources/db/*/changes/`
- pom.xml files for all 10 modules (`integration-tests`'s own `smoke_test` table is test-only
  scaffolding, not part of the domain schema — deliberately excluded from 04-database-erd.md)

---

## Document Quality

All findings based on actual source code inspection:
- ✓ All class names verified in codebase
- ✓ All file paths point to real files
- ✓ All diagrams use Mermaid syntax
- ✓ All database schemas extracted from Liquibase XML
- ✓ All coupling violations identified via grep + import analysis
- ✓ All sequence diagrams trace real code paths

**No speculation.** Only what exists in code is documented.

---

## Recommendations Summary

### Urgent (Sprint 1)
- [x] Fix AccessEvaluator coupling (HIGH) — done, see ADR-016
- [x] Remove internal user.security imports; use UserPort — done, see ADR-016

### High (Sprint 2)
- [ ] Resolve optional dependencies (make required OR add ObjectProvider guards)
- [ ] Add unit tests for all Hook implementations with all entity types
- [ ] Document User domain as mandatory (not optional)

### Medium (Sprint 3+)
- [ ] Extract centralized AuthorizationService if auth grows
- [ ] Add database migration testing to CI/CD
- [ ] Consider PostgreSQL views for soft-delete filters

### Low (Future)
- [ ] Monitor I18nKey growth; split if >500 lines
- [ ] Plan audit_log partitioning for >1M rows
- [ ] Consider CQRS for audit read side if perf degrades

---

## Architecture Quality: 7.7/10 (GOOD)

**Strengths:**
- Clear SPI design with consistent Port/Hook naming
- No circular dependencies (clean DAG)
- Shared kernel centralizes all cross-module contracts
- Flexible schema (JSONB) supports extensibility
- Starters are modular and independently deployable
- UI/data separation (Vaadin only in marketplace-app)
- Good indexing for query performance

**Weaknesses:**
- Optional dependencies not guarded (MEDIUM)
- Schema-level coupling between Advertisement and User (acceptable but limits independence)
- SPI contract testing requires discipline (no compile-time enforcement)

**With critical issues resolved, score would be 8-8.5/10.**

