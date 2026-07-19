# Architecture Scorecard

## Scoring Methodology

Each dimension scored 1-10 with reasoning tied to actual code observations:
- **1-3:** Poor — Major issues, significant refactoring needed
- **4-6:** Fair — Some issues, improvements recommended
- **7-8:** Good — Mostly solid, minor concerns
- **9-10:** Excellent — Well-executed, minimal concerns

---

## 1. Modularity (Score: 7/10)

**Definition:** Ability to build, test, and deploy modules independently.

**Evidence:**
- ✓ Each starter is a separate Maven module with independent Liquibase migrations
- ✓ Starters can be excluded from classpath (marketplace-app works with subset)
- ✓ No direct starter-to-starter code imports
- ✓ All SPI interfaces in platform-commons (starters can be swapped)
- ✗ marketplace-app depends on all starters; cannot scale independently
- ✗ audit/attachment starters marked optional but not guarded in code (runtime risk)
- ✗ Advertisement → User hard coupling via database foreign key (cannot deploy advertisement without user)

**Why 7, not higher:**
- Starters are modular, but marketplace-app acts as a monolith that coordinates them
- Optional dependencies create false optionality (will fail at runtime if excluded)
- Database schema coupling (FK constraints) creates deploy-time coupling

**Improvements:**
- Add ObjectProvider guards for optional starters
- Extract "UserReference" SPI if user must ever become optional
- Document that marketplace-app is the monolith; starters are its internal modules

---

## 2. Coupling (Score: 8/10)

**Definition:** Loose coupling between modules; changes in one do not ripple through others.

**Evidence:**
- ✓ No cyclic dependencies (DAG topology)
- ✓ All inter-module calls through platform-commons SPI (Ports/Hooks)
- ✓ No Vaadin in starters (clean separation of concerns)
- ✓ Repositories not imported by UI directly (all through Ports)
- ✓ **AccessEvaluator fixed (ADR-016, 2026-06-15)** — now uses `UserPort` + `AuthContextService` only; no `org.ost.user.*` internal imports
- ✓ **UserPortImpl mapping logic resolved (2026-07-01)** — `toDto()` and stream pipelines moved to `UserService`; port is pure delegation
- ✓ **SettingsPaginationService cross-session bleed resolved (ADR-028, improvement-018)** — singleton binding entries now carry an owning `userId`; a settings change no longer leaks into other users' live sessions
- ✗ Optional audit/attachment dependencies not guarded; runtime assumptions remain
- ~ Advertisement has FK to user; tight schema coupling (acceptable but limits independence)

**Why 8, not 9+:**
- Optional deps (audit/attachment in advertisement-starter) are still unguarded — runtime risk if excluded
- No way to swap audit or attachment implementations (tightly wired via Spring beans)

**Improvements:**
1. Add ObjectProvider guards for optional starters in advertisement-spring-boot-starter
2. Consider making all starter implementations injectable (allow swapping)

---

## 3. Cohesion (Score: 8/10)

**Definition:** Related code grouped together; each module has a single responsibility.

**Evidence:**
- ✓ User domain owns: User entity, UserService, UserRepository, UserPortImpl, security (UserPrincipal, RoleChecker)
- ✓ Advertisement domain owns: Advertisement entity, AdvertisementService, AdvertisementRepository, AdvertisementPortImpl
- ✓ Audit domain owns: AuditLog entity, AuditLogRepository, AuditReadService, DefaultAuditPort
- ✓ Attachment domain owns: Attachment entity, AttachmentService, S3StorageService, DefaultAttachmentPort
- ✓ Taxon domain owns: Taxon entities, TaxonService, DefaultTaxonPort
- ✓ Marketplace-app owns: UI views, overlays, forms, hook implementations
- ✓ platform-commons owns: SPI interfaces, DTOs, enums, shared validation
- ✓ query-lib owns: SQL filtering/sorting helpers (no domain knowledge)
- ~ I18nKey.java (370 lines) is a large enum but logically cohesive (all translation keys)

**Why 8, not higher:**
- Most modules are tightly focused; I18nKey is large but it's the correct place for all keys
- Slight concern: hook implementations in marketplace-app span multiple domains (audit, attachment, taxon) but this is acceptable by design (marketplace is the integrator)

**Improvements:**
- Monitor I18nKey growth; if >500 lines, consider splitting by domain (AdvertisementI18nKey, UserI18nKey)
- Keep hook implementations in marketplace-app; cohesion is intentional

---

## 4. SPI Design Quality (Score: 8/10)

**Definition:** Port/Hook interface design; clarity, extensibility, consistency.

**Evidence:**
- ✓ Consistent naming: *Port (marketplace → starter), *Hook (starter → marketplace)
- ✓ Clear contracts: Each interface has 2-6 well-named methods
- ✓ No ambiguous direction (suffix encoding is explicit)
- ✓ All interfaces in platform-commons (starters need not import each other)
- ✓ DTOs separate from SPI (data carriers don't pollute interfaces)
- ✓ Implementations are pure delegation (no business logic in ports/hooks)
- ✓ ComponentFactory<T> abstraction for optional singleton services
- ~ AttachmentMediaChangeHook fires but has no implementation today (its former receiver, MediaChangeHookImpl, was removed by ADR-035) — acceptable, gracefully-degraded optional SPI
- ~ Some hooks have 1 method (AttachmentMediaChangeHook.onChange), others have many (AuditPort.captureCreation, captureUpdate, captureDeletion, getSnapshotContent, getEntityActivity, getLastSnapshot, getTimelinePage, countTimeline) — acceptable variation

**Why 8, not higher:**
- Overall excellent design; naming is clear, contracts are focused
- Minor concern: AuditPort is large (8 methods); consider if it could split into read/write ports (but cohesion is fine as-is)

**Improvements:**
- Document each Port/Hook's call direction and expected implementations in platform-commons/CLAUDE.md (already done)
- Add JSDoc to each interface method explaining pre/post-conditions

---

## 5. Domain Isolation (Score: 8/10)

**Definition:** Domains are separated; interactions only through contracts.

**Evidence:**
- ✓ User domain: pure user management, no business rules about advertisements
- ✓ Advertisement domain: CRUD and ownership checks, knows attachments only via optional `AttachmentPort` (read-time media-summary enrichment, ADR-035)
- ✓ Audit domain: cross-cutting, doesn't know domain semantics (calls hooks to understand)
- ✓ Attachment domain: file storage, knows about entity_type/entity_id generically
- ✓ Taxon domain: taxonomy management, self-contained; no FK to advertisement
- ✓ Advertisement imports AuditPort + AttachmentPort but only calls via interfaces ✓
- ✓ Attachment fires MediaChangeHook without importing any receiver (currently no implementation at all — ADR-035); advertisement media summaries come from read-time `AttachmentPort.getMediaSummaries()` bulk lookups ✓
- ✓ AccessEvaluator fixed (ADR-016, 2026-06-15) — no more user.security.* imports
- ~ Advertisement → User via database FK (schema coupling, not code import; acceptable)

**Why 8, not higher:**
- Database FK coupling between advertisement and user is acceptable but limits independence
- Taxon filtering uses `TaxonPort.findEntityIdsWithAnyTaxon()` to avoid exposing taxon_assignment table — correct pattern

**Improvements:**
1. Document that User is a mandatory core domain (not truly optional)
2. Consider extracting UserReference SPI if independence becomes critical

---

## 6. Database Design (Score: 8/10)

**Definition:** Schema clarity, normalization, support for business operations.

**Evidence:**
- ✓ Clear entity tables: user_information, advertisement, attachment, audit_log
- ✓ Generic audit_log: entity_type + entity_id allow any entity to be audited without schema changes
- ✓ Generic attachment table: entity_type + entity_id allow attaching to any entity type
- ✓ Soft-delete support: deleted_at + deleted_by columns + indexes
- ✓ Snapshots: attachment_snapshot and audit_snapshot for historical recovery
- ✓ Proper indexing: composite indexes on (entity_type, entity_id, created_at DESC)
- ✓ JSONB for flexible schema (user settings, audit snapshot data)
- ✓ FK constraints with RESTRICT/SET NULL (no cascading deletes)
- ~ Audit log snapshot_data JSONB schema varies by action_type (requires runtime validation, not compile-time)
- ~ Soft-delete queries require discipline (WHERE deleted_at IS NULL); not enforced by constraints

**Why 8, not higher:**
- Schema is well-designed; indexes are appropriate
- Minor concern: JSONB audit_log.snapshot_data requires application-level validation per action type
- Minor concern: soft-delete requires query discipline (could use views or triggers to enforce)

**Improvements:**
1. Add comments to audit_log.snapshot_data explaining format for each action_type (CREATE vs UPDATE vs DELETE)
2. Consider PostgreSQL views: `advertisement_active` as `SELECT * FROM advertisement WHERE deleted_at IS NULL` (auto-apply soft-delete filter)
3. Test migration scripts in Docker before committing

---

## 7. Testability (Score: 7/10)

**Definition:** Ease of unit testing, mocking dependencies, integration testing.

**Evidence:**
- ✓ SPI interfaces are mockable (test marketplace without starters)
- ✓ Services have single responsibility (testable in isolation)
- ✓ Repositories accept NamedParameterJdbcTemplate (injectable, mockable)
- ✓ No static dependencies (all injected)
- ✓ Vaadin components use Configurable pattern (can test in isolation via configure())
- ✓ Playwright tests for end-to-end UI verification
- ✗ Port implementations are pure delegation; unit tests may feel trivial
- ✗ Hook implementations must be tested across all entity types (no compile-time enforcement of coverage)
- ✗ Optional starters not guarded; integration tests may fail if excluded

**Why 7, not higher:**
- Good structure for testability; Port/Hook design enables mocking
- Concern: Pure delegation in ports means less logic to test (trade-off for clarity)
- Concern: SPI contract testing requires discipline (no automatic checking)

**Improvements:**
1. Add unit tests for all Hook implementations covering all entity types
2. Add integration tests with optional starters excluded (ensure graceful failure or documented requirement)
3. Document test expectations for each SPI interface in platform-commons/CLAUDE.md

---

## Overall Assessment

| Dimension | Score | Status |
|-----------|-------|--------|
| **Modularity** | 7/10 | GOOD — Starters modular; marketplace-app is coordinator |
| **Coupling** | 8/10 | GOOD — AccessEvaluator fixed (ADR-016); optional deps still unguarded |
| **Cohesion** | 8/10 | GOOD — Each module single responsibility |
| **SPI Design** | 8/10 | GOOD — Clear, consistent, well-executed |
| **Domain Isolation** | 8/10 | GOOD — AccessEvaluator fixed; schema coupling acceptable |
| **Database Design** | 8/10 | GOOD — Flexible schema, proper indexing, soft-delete support |
| **Testability** | 7/10 | GOOD — Mockable contracts; SPI testing requires discipline |
| **AVERAGE** | **7.7/10** | **GOOD** |

---

## Strengths

1. **Clear SPI Design:** Port/Hook pattern with consistent naming (marketplace → Port, Hook ← starter)
2. **No Cyclic Dependencies:** Dependency graph is a clean DAG
3. **Shared Kernel:** All cross-module contracts centralized in platform-commons; no circular imports
4. **Flexible Schema:** JSONB audit_log and attachments allow extensibility without migrations
5. **Modular Starters:** Each starter is independently deployable (ignoring optional dependency issues)
6. **UI/Data Separation:** Vaadin only in marketplace-app; starters are UI-agnostic
7. **Good Indexing:** Query performance optimized via composite indexes

---

## Open Issues

1. ~~**AccessEvaluator Coupling (HIGH)**~~ — ✅ Resolved (ADR-016, 2026-06-15)

2. ~~**UserPortImpl Mapping Logic (LOW)**~~ — ✅ Resolved (2026-07-01): mapping moved to `UserService`; port is pure delegation.

3. **Optional Dependencies Not Guarded (MEDIUM):**
   - audit/attachment marked optional in advertisement-starter pom.xml but not protected with ObjectProvider
   - Runtime failure if excluded
   - Fix: Remove `<optional>` or add guards

---

## Recommendations (Priority Order)

### 1. URGENT (Sprint 1)
- [x] Refactor AccessEvaluator to use UserPort + ComponentFactory<UserPort> — ✅ Done (ADR-016, 2026-06-15)
- [x] Remove OwnershipChecker / RoleChecker utility classes or extract as SPI methods — ✅ Done (ADR-016, 2026-06-15)

### 2. HIGH (Sprint 2)
- [ ] Decide: make audit/attachment required (remove optional) OR add ObjectProvider guards in AdvertisementService
- [ ] Document whether User domain must always be present (currently it is)
- [ ] Add unit tests for all Hook implementations with all entity types

### 3. MEDIUM (Sprint 3+)
- [ ] Extract centralized AuthorizationService if authorization logic grows
- [ ] Add database migration testing to CI/CD
- [ ] Consider PostgreSQL views for soft-delete filters (advertisement_active, attachment_active)
- [ ] Document SPI contract expectations in platform-commons/CLAUDE.md

### 4. LOW (Future)
- [ ] Monitor I18nKey growth; consider splitting by domain if >500 lines
- [ ] Plan audit_log partitioning strategy for table growth beyond 1M rows
- [ ] Consider CQRS for audit read side if query performance degrades

---

## Conclusion

**Rating: 7.7/10 (GOOD)**

This is a well-structured modular monolith with solid architectural foundations:
- Clear module boundaries enforced via SPI contracts
- No circular dependencies
- Good separation of concerns (UI, domain, data)
- Flexible schema supporting extensibility

**Primary concern:** AccessEvaluator boundary violation resolved (ADR-016, 2026-06-15) — now uses `UserPort` + `AuthContextService` exclusively.

**Remaining concern:** Optional dependencies (audit, attachment) are not guarded in advertisement-starter pom.xml code; needs clarification (required or optional with guards).

With this remaining issue resolved, the architecture would score 8-8.5/10.

