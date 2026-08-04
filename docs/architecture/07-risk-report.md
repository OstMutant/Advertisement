# Risk Report

## Module Size Analysis

### Largest Modules by File Count

| Module | Java Files | Risk |
|--------|-----------|------|
| marketplace-app | 174 | MEDIUM — UI complexity, but expected |
| platform-commons | 57 | LOW — Mostly interfaces & DTOs, no logic |
| user-spring-boot-starter | 16 | LOW — Focused on user domain |
| attachment-spring-boot-starter | 15 | LOW — Contains S3 storage service |
| taxon-spring-boot-starter | 15 | LOW — Taxonomies module |
| query-lib | 9 | LOW — Utility library |
| integration-tests | 8 | LOW — Test-only, never shipped |
| advertisement-spring-boot-starter | 7 | LOW — Focused on advertisements |
| provider-profile-spring-boot-starter | 7 | LOW — Focused on provider profiles |
| audit-spring-boot-starter | 6 | LOW — Compact audit module |

**Finding:** marketplace-app is significantly larger (314 Java files total across all 10 modules) but appropriate for a UI monolith. No unjustified bloat in starters.

---

## Largest Java Files (Potential Complexity)

Recomputed 2026-08-04 (`find marketplace-app/src/main/java -name "*.java" -exec wc -l {} \; | sort -rn | head`).

| File | Lines | Module | Risk | Notes |
|------|-------|--------|------|-------|
| I18nKey.java | 438 | marketplace-app | MEDIUM | Large enum of all i18n keys; maintainability concern |
| AttachmentGallery.java | 363 | marketplace-app | MEDIUM | Large Vaadin component; consider sub-components |
| AdvertisementFormOverlayModeHandler.java | 362 | marketplace-app | MEDIUM | Complex form validation + binder setup; candidate for extraction |
| CityFormOverlayModeHandler.java | 287 | marketplace-app | MEDIUM | Similar form complexity |
| TaxonFormOverlayModeHandler.java | 286 | marketplace-app | MEDIUM | Similar form complexity |
| AdvertisementCardView.java | 274 | marketplace-app | LOW | Card grid view; normal Vaadin size |
| SettingsFormModeHandler.java | 257 | marketplace-app | LOW | Settings form; reasonable size |
| AdvertisementsView.java | 247 | marketplace-app | LOW | Main advertisements list view |
| AdvertisementAuditEnrichService.java | 216 | marketplace-app | LOW | Category-name resolution for audit diffs |
| UserFormOverlayModeHandler.java | 214 | marketplace-app | MEDIUM | User form handler; similar to advertisement |

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
| AccessEvaluator | marketplace-app | 2 (authorizationPort, authContextService) | LOW — see ADR-016 |
| DefaultTaxonPort | taxon-starter | 3 (taxonService, assignmentService, properties) | LOW — Cohesive to taxon operations |

**Finding:** Most classes have 1-3 dependencies; `AccessEvaluator` depends only on the port-based
SPI (ADR-016).

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
| advertisement.created_by FK (RESTRICT) | MEDIUM | Cannot delete user if they have ads; may need manual cleanup |
| advertisement.updated_by FK (SET NULL) | LOW | OK; nullable, deletes orphan the reference |
| advertisement.deleted_by FK (SET NULL) | LOW | OK; nullable |
| attachment.entity_id (no FK, generic) | LOW | Attachment orphans possible; cleanup job handles via soft-delete |

**Mitigation:** RESTRICT on created_by is intentional (creator is immutable). Handle soft-delete via audit cleanup job.

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

See `01-module-dependencies.md` ("Marketplace App Dependency") for the fact — not restated here.

**Mitigation:** Starters are core to the app; this coupling is acceptable.

### 2. Optional Dependency Guards

`advertisement-spring-boot-starter/pom.xml` has no `<optional>` Maven dependency on
audit/attachment — zero Java source in this module imports `org.ost.audit.*`/
`org.ost.attachment.*` directly. All optional-port wiring (category assignment, author
enrichment, media-summary enrichment) goes through `platform-commons` SPI types via
`ComponentFactory<T>` — genuine runtime decoupling with zero build-time coupling to any other
starter. See `advertisement-spring-boot-starter/README.md`'s "Dependencies" section and
`06-coupling-analysis.md`'s "Optional Dependency Guards" section — not restated here.

### 3. Marketplace → User Internal Import Coupling

See `06-coupling-analysis.md`'s "Marketplace → Starter Internal Imports" section (ADR-016) — not
restated here.

### 4. UserPortImpl DTO Mapping Logic

See `06-coupling-analysis.md`'s "UserPortImpl DTO Mapping Logic" section — not restated here.

---

## Code Complexity Hot Spots

### 1. Audit Snapshot Diff Engine

**Risk:** MEDIUM — Complex business logic

**Location:** `AuditableSnapshot.diff()` (per-snapshot-type method on each `AuditableSnapshot` implementor in marketplace-app); called from `AuditReadService` at read time.

**Issue:** Computing field-level diffs between snapshots using `@AuditedField` markers requires:
- Reflection to extract marked fields
- JSON deserialization of snapshot_data JSONB pairs
- Null-safe comparisons across snapshot versions
- Same-type prev-snapshot post-processing (`withSameTypePrevSnapshot`) to skip cross-type LAG values

**Mitigation:** Unit test all diff scenarios per snapshot type; same-type correction is done in-memory in `AuditReadService` (see ADR-021).

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

**Risk:** LOW-MEDIUM — Authorization logic scattered across UI call sites

**Location:** `org.ost.marketplace.services.security.AccessEvaluator`

**Issue:**
- `AccessEvaluator` calls `UserAuthorizationPort.isAdmin()`/`isModerator()`/`isOwner()` through the correct SPI — see `06-coupling-analysis.md` (ADR-016), not restated here
- No centralized authorization gateway; UI components each call `AccessEvaluator`
- Risk decreases as `AccessEvaluator` is the single point of security policy

**Mitigation:** Currently acceptable for a small team. Monitor for missing checks as app grows. Consider extracting a centralized AuthorizationService.

### 2. Spring Security Integration

**Location:** `org.ost.user.security.UserPrincipal` implements Spring Security UserDetails

**Risk:** LOW — Standard Spring Security pattern

**Issue:** None; properly integrated.

### 3. Password Storage

**Location:** user_information.password_hash (VARCHAR); encoded via
`PasswordEncoderFactories.createDelegatingPasswordEncoder()` (`UserAutoConfiguration.java`).
Stored hashes carry an `{bcrypt}` prefix identifying the algorithm, so a future migration to a
stronger algorithm (e.g. Argon2id) can be rolled in without a data rewrite — new hashes just get
encoded with the new default while old `{bcrypt}`-prefixed ones still verify correctly.

**Risk:** LOW — Hashing handled by Spring Security; no plaintext leaks; algorithm migration
path no longer requires a data rewrite.

### 4. Rate Limiting / Brute-Force Protection

**Location:** `AuthService.login()` and `UserService.register()` (`org.ost.user.services`),
both backed by an in-memory Caffeine cache (5 attempts / 15 min window).

**Design:** Both limiters increment their counter only on an actual failure — `login()` on
`BadCredentialsException`, `register()` on `DuplicateKeyException` — never on success. See
`marketplace-app/DECISIONS.md` ADR-026 for the rationale.

**Keying:** `login()` keys on `remoteAddr + "|" + email` (a lockout scopes to one target
account even if `remoteAddr` collapses behind a proxy). `register()` keys on the real client IP,
resolved via `server.forward-headers-strategy: framework` in `application-prod.yml` so
`request.getRemoteAddr()` returns the actual client address behind Render's proxy rather than the
platform's shared edge address — see ADR-027.

**Risk:** LOW — both paths correctly scoped; whether Render actually forwards
`X-Forwarded-For` is not verifiable from this dev environment and is worth confirming once
deployed.

### 5. URL-Level Access Control

**Location:** `org.ost.marketplace.config.SecurityConfig`

**Design:** `anyRequest().permitAll()` at the Spring Security filter-chain level — deliberate,
not an oversight. Vaadin's root route bootstrap request is not covered by
`HandlerHelper.isFrameworkInternalRequest()` (which only recognizes Vaadin's own internal
AJAX/RPC calls), so a deny-by-default (`anyRequest().denyAll()`) baseline denies the very first
page load for every user. See `marketplace-app/DECISIONS.md` ADR-025 for why deny-by-default does
not fit this app's single-route Vaadin SPA model, and the process rule this created for any
future non-Vaadin REST controller (must add its own explicit `requestMatchers(...)` ahead of the
catch-all).

**Risk:** LOW-MEDIUM — acceptable for a single-route Vaadin SPA with no REST endpoints yet;
becomes a real gap the moment a REST controller is added without its own explicit matcher.

---

## Concurrency Risks

### 1. Singleton State Isolation — SettingsPaginationService

See `06-coupling-analysis.md`'s "Singleton State Isolation Across UI Sessions" section (ADR-028)
— not restated here.

### 2. Optimistic Locking on Concurrent Entity Edits

**Location:** `Advertisement`, `User`, `Taxon` entities (all three starters)

**Design:** `version BIGINT` on all three tables; `@Version` on all three entities.
`Advertisement`/`Taxon` get native Spring Data JDBC checking via `CrudRepository.save()`; `User`'s
real edit path bypasses `CrudRepository` via hand-written SQL, so `UserRepository.updateProfile()`
implements the check manually. UI shows a dedicated conflict notification (no auto-reload, to
avoid silently discarding in-progress form edits) — see `marketplace-app/DECISIONS.md` ADR-029.

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
| Centralize authorization checks | MEDIUM | MEDIUM | Extract AuthorizationService if auth logic grows |
| Partition audit_log table | LOW | LARGE | Future scaling concern; not urgent |
| Test SPI contracts systematically | MEDIUM | SMALL | Add unit tests for all hook implementations |

---

## Summary

| Category | Risk Level | Status |
|----------|-----------|--------|
| **Module Size** | LOW | Acceptable; marketplace-app large but expected for UI |
| **Constructor Complexity** | LOW | 1-3 deps typical |
| **Package Organization** | LOW | No god packages; well-organized |
| **Dependency Cycles** | NONE | ✓ DAG verified |
| **Database Schema** | LOW-MEDIUM | JSONB schemas, soft-delete queries require discipline |
| **SPI Contract Safety** | MEDIUM | Hook implementations not compile-checked |
| **Performance** | MEDIUM | Audit log growth unbounded; indexes adequate for now |
| **Security** | LOW-MEDIUM | RBAC scattered across UI call sites; rate limiting and URL access control follow ADR-026/027/025 |
| **Concurrency** | LOW | Settings isolation and optimistic locking covered by ADR-028/029 |
| **Coupling** | LOW | AccessEvaluator uses port-based SPI (ADR-016); no unguarded optional deps |

**Open Action:** none remaining from this report.

