# Architecture Documentation

Complete architecture documentation for the Marketplace modular monolith (Java 25 / Spring Boot 4.0.6 / Vaadin 25.1.5).

## Files Overview

### Module Dependencies (not a `.md` file — see `docs/architecture-map.html`)
**Maven dependency graph.** Which modules depend on which others, rendered live from `pom.xml`
(Diagrams › Module Dependencies) — domain-colored, click a node to open its module page, plus a
Dependency Table and Key Observations on the same page. Not a separate `docs/architecture/*.md`
file: kept as one generated view instead of a second, separately-maintained copy.

### SPI Map (not a `.md` file — see `docs/architecture-map.html`)
**Extension points and implementations.** Which Ports and Hooks live in `platform-commons` and who
implements them, rendered live from real Java source (Diagrams › SPI Map) — grouped by subsystem,
click a node or table entry to open its real `.java` file, plus Call Flow Examples and
Implementation Rules on the same page. Not a separate `docs/architecture/*.md` file: kept as one
generated view instead of a second, separately-maintained copy.

### bounded-contexts.md
**Domain boundaries and integration patterns.** Identifies business domains (User, Advertisement, Audit, Attachment, Taxon, Provider Profile) plus the UI layer and shared kernel. Explains how domains communicate through Ports and Hooks. Documents the 3 main integration patterns (lifecycle with audit, media attachment, activity feed enrichment). Rendered as an interactive diagram at `docs/architecture-map.html`'s Diagrams › Bounded Contexts page — this file stays the real, hand-maintained source (its edges are conceptual domain relationships, not mechanically derivable the way Module Dependencies/SPI Map are), the page is a live rendering of it, not a second copy.

**Key diagram:** Context map showing all domains and their relationships.

### Database ERD (not a `.md` file — see `docs/architecture-map.html`)
**Entity relationship diagram and schema details.** Tables with columns, types, constraints, indexes, and foreign keys, rendered live from the real Liquibase changelogs (Diagrams › Database ERD) — each column's/table's own `remarks=` attribute in the changelog is the single source of truth for what it means (see root `CLAUDE.md`'s "Database Changes" guideline), shown next to it in the diagram and in a table-schema section below. Solid diagram lines are real foreign keys; dotted lines are relationships this codebase deliberately leaves unconstrained at the SQL level. Not a separate `docs/architecture/*.md` file: kept as one generated view instead of a second, separately-maintained copy.

### 05-sequence-diagrams.md
**Real code paths through actual class names.** 6 sequence diagrams tracing real interactions: advertisement creation, media upload, activity timeline query, snapshot restore, settings change, and list filtering. All classes named (e.g., AdvertisementOverlay, AdvertisementPortImpl, AdvertisementService).

**Key diagrams:** 6 Mermaid `sequenceDiagram` traces with actual class names from codebase.

### 06-coupling-analysis.md
**Architecture violations and coupling assessment.** Confirms: `AccessEvaluator` depends only on
the port-based SPI (ADR-016), audit/attachment optional dependencies are properly guarded, no
cyclic deps, no Vaadin in starters, no UI→Repository direct imports, good module sizes.

**Key findings:**
- PASS: Marketplace → starter internal imports (ADR-016)
- PASS: Optional dependency guards
- PASS: All other coupling checks

### 07-risk-report.md
**Risk assessment by category.** Analyzes module sizes, largest files, constructor complexity, database risks (JSONB validation, soft-delete discipline), dependency chain risks (optional deps), code complexity hot spots (audit diff engine, overlay state machine), security risks, and performance risks (audit log unbounded growth).

**Severity matrix:**
- HIGH: Soft-delete queries forgetting WHERE deleted_at IS NULL filter
- MEDIUM: JSONB schema validation in audit_log, SPI contract testing, optional deps
- LOW: Everything else

### 08-scorecard.md
**Architecture quality scorecard.** Scores 7 dimensions (Modularity, Coupling, Cohesion, SPI Design, Domain Isolation, Database Design, Testability) from 1-10 with evidence. Overall: **7.7/10 (GOOD)**.

See `08-scorecard.md`'s "Overall Assessment" table for the per-dimension scores — not restated here.

**Critical actions:** none outstanding — see `06-coupling-analysis.md`.

---

## How to Use This Documentation

1. **Understand module structure:** Start with `docs/architecture-map.html`'s Module Dependencies page
2. **Learn about SPI contracts:** `docs/architecture-map.html`'s SPI Map page for all ports/hooks
3. **Understand domain boundaries:** Study bounded-contexts.md
4. **Learn the database:** `docs/architecture-map.html`'s Database ERD page
5. **Trace real code paths:** Follow 05-sequence-diagrams.md for how features work
6. **Identify risks:** Check 06-coupling-analysis.md and 07-risk-report.md
7. **Assess quality:** Review 08-scorecard.md for strengths, weaknesses, and recommendations

---

## Key Metrics

Recomputed 2026-08-04 via `find */src/main/java -name "*.java" | wc -l` and equivalent one-line
commands — re-verify the same way next time this table is touched, per `doc-standards/SKILL.md`'s
"Hard-coded references" rule.

| Metric | Value |
|--------|-------|
| Total Modules | 10 (query-lib, platform-commons, 6 starters, marketplace-app, integration-tests test-only) |
| Total Java Files | 314 |
| Total Tables | 10 (user_information, user_preferences, advertisement, attachment, attachment_snapshot, audit_log, taxon, taxon_translation, taxon_assignment, provider_profile) |
| SPI Interfaces | 17 (10 Ports + 5 Hooks + 2 type/marker contracts) — see `docs/architecture-map.html`'s SPI Map page |
| Largest Module | marketplace-app (174 Java files) |
| Largest File | `I18nKey.java` (438 lines, marketplace-app) |
| Dependency Cycles | 0 (clean DAG) |
| Architecture Score | 7.7/10 (GOOD) — see `08-scorecard.md` |

---

## Critical Issues Found

None currently open — see `06-coupling-analysis.md` and `07-risk-report.md`'s "Dependency Chain
Risks" for the coupling and optional-dependency checks this documentation tracks.

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
  scaffolding, not part of the domain schema — deliberately excluded from the Database ERD page)

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
- [x] Resolve optional dependencies — done, 2026-07-16 (removed `<optional>` deps entirely)
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
- No circular dependencies (see `docs/architecture-map.html`'s Module Dependencies page)
- Shared kernel centralizes all cross-module contracts
- Flexible schema (JSONB) supports extensibility
- Starters are modular and independently deployable
- UI/data separation (Vaadin only in marketplace-app)
- Good indexing for query performance

**Weaknesses:**
- SPI contract testing requires discipline (no compile-time enforcement)

**With critical issues resolved, score would be 8-8.5/10.**

