# Risk Report

## Module Size Analysis

### Largest Modules by File Count

| Module | Java Files | Risk |
|--------|-----------|------|
| marketplace-app | 152 | MEDIUM — UI complexity, but expected |
| platform-commons | 50 | LOW — Mostly interfaces & DTOs, no logic |
| attachment-spring-boot-starter | 15 | LOW — Contains S3 storage service |
| taxon-spring-boot-starter | 15 | LOW — Taxonomies module |
| user-spring-boot-starter | 11 | LOW — Focused on user domain |
| audit-spring-boot-starter | 7 | LOW — Compact audit module |
| advertisement-spring-boot-starter | 7 | LOW — Focused on advertisements |
| query-lib | 7 | LOW — Utility library |

**Finding:** marketplace-app is significantly larger (152 files) but appropriate for a UI monolith. No unjustified bloat in starters.

---

## Largest Java Files (Potential Complexity)

| File | Lines | Module | Risk | Notes |
|------|-------|--------|------|-------|
| I18nKey.java | 370 | marketplace-app | MEDIUM | Large enum of all i18n keys; maintainability concern |
| AdvertisementFormOverlayModeHandler.java | 327 | marketplace-app | MEDIUM | Complex form validation + binder setup; candidate for extraction |
| TaxonFormOverlayModeHandler.java | 311 | marketplace-app | MEDIUM | Similar form complexity |
| AttachmentGallery.java | 307 | marketplace-app | MEDIUM | Large Vaadin component; consider sub-components |
| SettingsFormModeHandler.java | 253 | marketplace-app | LOW | Settings form; reasonable size |
| AdvertisementCardView.java | 239 | marketplace-app | LOW | Card grid view; normal Vaadin size |
| UserFormOverlayModeHandler.java | 235 | marketplace-app | MEDIUM | User form handler; similar to advertisement |
| TaxonManagementView.java | 205 | marketplace-app | LOW | Taxon list + management |
| AuditTimelineRowRenderer.java | 190 | marketplace-app | MEDIUM | Complex row rendering; consider factoring |
| AdvertisementsView.java | 180 | marketplace-app | LOW | Main advertisements list view |

**Risk Level: MEDIUM**

**Mitigation:** Largest files are complex UI components (forms, renderers, galleries). Complexity is inherent to Vaadin. Current structure is acceptable; monitor for growth beyond 400+ lines.

---

## Constructor Injection Complexity

Analyzed `@RequiredArgsConstructor` classes for excessive parameter counts (>5 = smell):

### High-Dependency Classes (4+ injected fields)

| Class | Module | Dependencies | Risk |
|-------|--------|--------------|------|
| DefaultAuditPort | audit-starter | 4 (auditLogRepo, currentActorHook, auditDomainHook, auditReadService) | LOW — All cohesive to audit operations |
| AuditDomainHookImpl | marketplace-app | 4+ (multiple ComponentFactory fields) | LOW — Factory pattern justifies count |
| AccessEvaluator | marketplace-app | 2 (userPort, authContextService) | LOW — ✅ Fixed (ADR-016, 2026-06-15) |
| DefaultTaxonPort | taxon-starter | 3 (taxonService, assignmentService, properties) | LOW — Cohesive to taxon operations |

**Finding:** Most classes have 1-3 dependencies. AccessEvaluator coupling violation resolved.

---

## Package God-Package Analysis

Checked for packages with excessive class concentrations (>20 classes = smell):

| Package | File Count | Risk | Notes |
|---------|-----------|------|-------|
| org.ost.marketplace.ui.views | 30+ | LOW | Expected: views are numerous in Vaadin apps |
| org.ost.marketplace.ui.query | 20+ | LOW | Query UI elements (filters, sorters, fields) |
| org.ost.marketplace.services | 15+ | LOW | i18n, audit, auth, security services; focused |
| org.ost.platform | 50+ | ACCEPTABLE | Shared kernel; mostly interfaces & DTOs |

**Finding:** No unjustified god packages. Package sizes align with domain responsibilities.

---

## Database Schema Risks

### 1. Foreign Key Constraints Without Cascading Deletes

| Constraint | Risk | Impact |
|-----------|------|--------|
| advertisement.created_by_user_id FK (RESTRICT) | MEDIUM | Cannot delete user if they have ads; may need manual cleanup |
| advertisement.last_modified_by_user_id FK (SET NULL) | LOW | OK; nullable, deletes orphan the reference |
| advertisement.deleted_by_user_id FK (SET NULL) | LOW | OK; nullable |
| attachment.entity_id (no FK, generic) | LOW | Attachment orphans possible; cleanup job handles via soft-delete |

**Mitigation:** RESTRICT on created_by_user_id is intentional (creator is immutable). Handle soft-delete via audit cleanup job.

### 2. JSONB Columns (Flexible Schema)

| Column | Risk | Notes |
|--------|------|-------|
| user_information.settings | LOW | Validated by application; reasonable defaults |
| audit_log.snapshot_data | MEDIUM | Schema varies by action_type (CREATE vs UPDATE); requires runtime validation |
| attachment_snapshot.changes_summary | LOW | Optional; flexible structure |

**Risk:** JSONB in audit_log requires application-level schema validation per action type.

### 3. Soft-Delete Queries Forget WHERE deleted_at IS NULL

**Risk:** HIGH — Silent bugs if queries forget the soft-delete filter.

**Pattern:** All queries should include `deleted_at IS NULL`:
```sql
SELECT * FROM advertisement WHERE deleted_at IS NULL AND ...;
```

**Mitigation:** Use repository methods that apply filter automatically (encapsulation).

---

## Dependency Chain Risks

### 1. marketplace-app → All Starters (Tight Binding)

marketplace-app directly depends on all starters (including taxon-spring-boot-starter). If any starter has an issue, the app fails.

**Impact:** No true optionality despite some starters marked `<optional>`.

**Mitigation:** Starters are core to the app; this coupling is acceptable.

### 2. Optional Dependencies Not Guarded (OPEN)

audit and attachment starters are marked `<optional>true/>` in advertisement pom.xml, but code assumes they exist.

**Impact:** Exclude either starter → runtime ClassNotFoundException.

**Mitigation:** Either make required (remove `<optional>`) or add ObjectProvider guards in code.

### 3. ✅ RESOLVED — Marketplace → User Internal Import Coupling (2026-06-15)

`AccessEvaluator` previously bypassed the SPI by importing `org.ost.user.security.*` classes directly. Now resolved — see ADR-016.

---

## Code Complexity Hot Spots

### 1. Audit Snapshot Diff Engine

**Risk:** MEDIUM — Complex business logic

**Location:** `org.ost.audit.services.AuditDiffService` (estimated, not inspected in detail)

**Issue:** Computing field-level diffs between snapshots using `@AuditedField` markers requires:
- Reflection to extract marked fields
- JSON serialization/deserialization
- Null-safe comparisons
- Type coercion for different field types

**Mitigation:** Unit test all diff scenarios; keep logic isolated in service.

### 2. Overlay/Form State Machine

**Risk:** MEDIUM — Complex lifecycle management

**Location:** `org.ost.marketplace.ui.views.main.tabs.advertisements.overlay.AdvertisementOverlay`

**Issue:** Overlay must manage:
- Mode transitions (CREATE → EDIT → VIEW → DELETE)
- Form validation
- Unsaved changes detection
- Session state updates after save

**Mitigation:** Strict separation of concerns (OverlaySession record, mode handlers, form handlers).

### 3. Query Filter/Sort Builder

**Risk:** LOW — Well-encapsulated utility

**Location:** `org.ost.query.filter.SqlFilterBuilder` (query-lib)

**Issue:** Builds dynamic SQL WHERE clauses; risk of SQL injection if not careful.

**Mitigation:** Uses parameterized queries (NamedParameterJdbcTemplate); injection-safe by design.

---

## Security Risks

### 1. Role-Based Access Control (RBAC)

**Risk:** MEDIUM — Authorization logic scattered

**Location:** `org.ost.marketplace.services.security.AccessEvaluator` + `org.ost.user.security.RoleChecker`

**Issue:**
- RoleChecker is in user.security.* (internal, imported directly from marketplace)
- No centralized authorization gateway
- UI layers call multiple security checks

**Mitigation:** Currently acceptable for a small team. Monitor for missing checks as app grows. Consider extracting a centralized AuthorizationService.

### 2. Spring Security Integration

**Location:** `org.ost.user.security.UserPrincipal` implements Spring Security UserDetails

**Risk:** LOW — Standard Spring Security pattern

**Issue:** None; properly integrated.

### 3. Password Storage

**Location:** user_information.password_hash (VARCHAR, stored as BCrypt)

**Risk:** LOW — Hashing handled by Spring Security; no plaintext leaks.

---

## Performance Risks

### 1. Audit Log Unbounded Growth

**Risk:** MEDIUM — No archival/cleanup strategy documented

**Issue:**
- Every entity change creates an audit_log row
- Indexes: (entity_type, entity_id, created_at DESC), (actor_id, created_at DESC)
- No partitioning or archival strategy visible

**Mitigation:** Liquibase migrations can partition by date in future. For now, queries use indexes effectively.

### 2. Large attachment_snapshot Queries

**Risk:** LOW — JSONB GIN index supports complex queries

**Issue:** attachment_snapshot.changes_summary is JSONB without size limit.

**Mitigation:** GIN index makes filtering efficient; snapshots are sparse (only on changes).

### 3. Soft Delete Index Coverage

**Risk:** LOW — deleted_at is indexed

**Issue:** Active record queries use `WHERE deleted_at IS NULL`; index supports this.

**Mitigation:** ✓ Properly indexed.

---

## Testing Risks

### 1. SPI Contract Testing

**Risk:** HIGH — Port/Hook implementations must match contracts exactly

**Issue:** If a new entity type is added, all AuditActivityFieldsHook implementations must handle it.

**Mitigation:** No compile-time enforcement; requires discipline + test coverage.

**Recommendation:** Unit tests for each hook implementation covering all entity types.

### 2. Database Migration Testing

**Risk:** MEDIUM — Liquibase scripts are version-controlled but not tested

**Issue:** Schema changes in migrations might fail on production due to data constraints.

**Mitigation:** Use Docker compose to test migrations locally before commit.

### 3. UI Component Integration

**Risk:** MEDIUM — Vaadin components tested via Playwright

**Issue:** Playwright tests are maintained in separate `/app/playwright` directory.

**Mitigation:** Test coverage must keep pace with UI changes.

---

## Architectural Debt

| Item | Priority | Effort | Notes |
|------|----------|--------|-------|
| ~~Fix AccessEvaluator internal imports~~ | ~~HIGH~~ | ~~SMALL~~ | ✅ Done (ADR-016, 2026-06-15) |
| Resolve optional dependencies | MEDIUM | SMALL | Remove `<optional>` or add ObjectProvider guards |
| Centralize authorization checks | MEDIUM | MEDIUM | Extract AuthorizationService if auth logic grows |
| Partition audit_log table | LOW | LARGE | Future scaling concern; not urgent |
| Test SPI contracts systematically | MEDIUM | SMALL | Add unit tests for all hook implementations |

---

## Summary

| Category | Risk Level | Status |
|----------|-----------|--------|
| **Module Size** | LOW | Acceptable; marketplace-app large but expected for UI |
| **Constructor Complexity** | LOW | 1-3 deps typical; AccessEvaluator problematic |
| **Package Organization** | LOW | No god packages; well-organized |
| **Dependency Cycles** | NONE | ✓ DAG verified |
| **Database Schema** | LOW-MEDIUM | JSONB schemas, soft-delete queries require discipline |
| **SPI Contract Safety** | MEDIUM | Hook implementations not compile-checked |
| **Performance** | MEDIUM | Audit log growth unbounded; indexes adequate for now |
| **Security** | MEDIUM | RBAC scattered; UserPrincipal well-integrated |
| **Coupling** | LOW-MEDIUM | AccessEvaluator fixed (ADR-016); optional deps still unguarded |

**Critical Action:** Fix AccessEvaluator to use UserPort instead of importing user.security.* classes.

