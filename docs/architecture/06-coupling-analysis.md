# Coupling Analysis

## Architecture Violations — Current State

### ✅ RESOLVED: Marketplace → Starter Internal Imports (2026-06-15)

**Previously:** `AccessEvaluator` imported `org.ost.user.security.OwnershipChecker` and `org.ost.user.security.RoleChecker` directly.

**Resolution (ADR-016):** `AccessEvaluator` now depends only on `UserPort` (platform-commons SPI) and `AuthContextService`. Role and ownership checks go through `UserPort.isAdmin()`, `UserPort.isModerator()`, `UserPort.isOwner()`. All 22 files in marketplace-app now use `UserDto`/`UserPort` exclusively.

**File:** `/app/marketplace-app/src/main/java/org/ost/marketplace/services/security/AccessEvaluator.java`

---

### ✅ RESOLVED: UserPortImpl DTO Mapping Logic (2026-07-01)

**Previously:** `UserPortImpl` contained `toDto(User)` mapping method and inline `.stream().map(UserPortImpl::toDto)` pipelines — business logic inside a port class.

**Resolution:** DTO mapping moved into `UserService`. `UserPortImpl.findByEmail()` delegates to `userService.findDtoByEmail(email)`. Port methods are now pure single-line delegations.

**File:** `/app/user-spring-boot-starter/src/main/java/org/ost/user/spi/UserPortImpl.java`

→ [violation-004-userportimpl-mapping-logic](../../features/completed/issues/violation-004-userportimpl-mapping-logic.md) (completed)

---

## No Cyclic Dependencies Detected

All module dependencies form a DAG (Directed Acyclic Graph):
- `platform-commons` has no dependencies on any starter
- `query-lib` depends only on platform-commons
- All starters depend on platform-commons + query-lib (no inter-starter deps)
- marketplace-app depends on all starters (only leaf in dependency tree)

No modules A and B exist where A → B → A.

---

## No Direct Starter-to-Starter Imports

Starters do not import from sibling starters' internal packages. All inter-starter communication uses platform-commons SPI:
- `audit-spring-boot-starter` does not import from `user-spring-boot-starter`
- `advertisement-spring-boot-starter` calls `AttachmentPort` (interface), not `org.ost.attachment.services.*`
- `attachment-spring-boot-starter` calls `AuditPort` (interface), not `org.ost.audit.services.*`

✓ This constraint is maintained.

---

## Potential Layer Violations

### 1. Marketplace UI Importing Repositories (NOT FOUND)

Checked for direct repository imports in marketplace-app UI classes:
```
grep -r "import org\.ost\.\(user\|advertisement\|audit\|attachment\)\.repository" \
  /app/marketplace-app/src/main/java --include="*.java"
```

Result: No violations found.

**Finding:** All UI → Repository calls go through Ports. ✓

---

### 2. Vaadin in Starters (NOT FOUND)

Checked for Vaadin dependencies in starter code:
```
grep -r "import com\.vaadin\." /app/*/src/main/java --include="*.java" | grep -v marketplace-app
```

Result: No violations found.

**Finding:** Only marketplace-app imports Vaadin. ✓

---

### 3. Starter Importing from Other Starters' Internal (NOT FOUND)

Checked for cross-starter imports:
```
for starter in audit attachment user advertisement taxon; do
  grep -r "import org\.ost\.\(audit\|attachment\|user\|advertisement\|taxon\)" \
    /app/$starter-spring-boot-starter/src/main/java --include="*.java" \
    | grep -v "org\.ost\.platform"
done
```

Result: advertisement-spring-boot-starter imports audit and attachment via optional dependencies, but only calls:
- `AuditPort` (interface in platform-commons) ✓
- `AttachmentPort` (interface in platform-commons) ✓

No internal class imports detected. ✓

---

## Hidden Coupling: Advertisement → User Tight Coupling

**Severity:** MEDIUM — Schema-level coupling

**Finding:**
Advertisement entity has foreign key to user_information:
```sql
-- /app/advertisement-spring-boot-starter/src/main/resources/db/advertisement-changelog/changes/01-advertisement-schema.xml
ALTER TABLE advertisement
ADD CONSTRAINT fk_advertisement_created_by
FOREIGN KEY (created_by_user_id)
REFERENCES user_information(id) ON DELETE RESTRICT;
```

**Impact:**
- advertisement-spring-boot-starter cannot run without user-spring-boot-starter in database
- Cannot soft-delete user if they have advertisements (FK RESTRICT)
- User domain changes ripple through advertisement data model

**Consequence:**
- In pom.xml, user-spring-boot-starter is NOT marked `<optional>`, so it's a required dependency
- This is correct given the schema constraint

**Recommendation:**
If user module must ever become truly optional, extract a lightweight "UserReference" SPI interface or accept that user is a mandatory core domain.

---

## Hidden Coupling: Singleton State Shared Across UI Sessions

### ✅ RESOLVED — SettingsPaginationService Cross-Session Bleed (2026-07-11)

**Previously:** `SettingsPaginationService` is a singleton `@Component` holding a
`CopyOnWriteArrayList<BindingEntry>` accumulated from **every user's** UI session, with no owner
association per entry. `onSettingsChanged(userId, settings)` filtered only on whether the
*current thread's* user matched `userId`, then pushed the new page size to every registered
`PaginationBar` regardless of which session it belonged to — one user's settings change silently
resized every other logged-in user's live grid.

**Resolution (ADR-028):** `BindingEntry` now carries the owning `userId` (captured from
`AuthContextService` at `register()` time); `onSettingsChanged` filters by
`entry.userId().equals(userId)` instead of gating on the current thread's user. Also added
`bar.addDetachListener(...)` so cleanup no longer depends solely on `@PreDestroy`.

**File:** `/app/marketplace-app/src/main/java/org/ost/marketplace/ui/views/services/pagination/SettingsPaginationService.java`

→ [improvement-018](../../features/completed/issues/improvement-018-settings-pagination-cross-session-bleed.md) (completed)

---

## Hidden Coupling: Optional Dependencies Without Guards

### ✅ RESOLVED at starter level (verified 2026-07-03)

The originally presumed unguarded call (`auditPort.captureCreation(...)` directly in
`AdvertisementService`) does not exist in current code. `AdvertisementService` injects
`ComponentFactory<AuditPort>`, `ComponentFactory<AttachmentPort>`, `ComponentFactory<TaxonPort>`
and resolves every call through `ifAvailable()` / `findIfAvailable()`. A grep for
`import org.ost.audit.` / `import org.ost.attachment.` in advertisement-spring-boot-starter
returns nothing — only platform-commons SPI types are referenced. The starter degrades
gracefully as designed.

### ✗ OPEN — residual risk relocated to marketplace-app UI (found 2026-07-03)

**Severity:** MEDIUM — startup failure risk

Three marketplace-app UI classes hard-inject starter ports instead of using
`ComponentFactory`:

| Class | Injection | Scope | Failure without the starter |
|-------|-----------|-------|------------------------------|
| `AttachmentGalleryService` | `AttachmentPort` | singleton | context fails **at startup** |
| `AttachmentGallery` | `AttachmentPort` | prototype | exception on first build |
| `AuditActivityPanel` | `AuditPort` | prototype | exception on first build |

Call-site guards (`galleryServiceFactory.ifAvailable(...)`) do not help: the component bean
definitions live in marketplace-app and always exist, so `getIfAvailable()` attempts
instantiation and throws `UnsatisfiedDependencyException`. Attachment and audit starters are
therefore effectively mandatory today, despite `<optional>true</optional>` in
advertisement-spring-boot-starter's pom.xml.

→ Tracked in [improvement-011](../../features/issues/improvement-011-unguarded-port-injection-in-ui-components.md)
with two resolution options (make degradation real via `ComponentFactory` +
`@ConditionalOnBean`, or drop `<optional>` and the degradation clause).

---

## Module Size & Complexity

| Module | Java Files | Largest File | Notes |
|--------|-----------|------|-------|
| query-lib | 7 | ~200 lines | Small utility library |
| platform-commons | ~47 | `I18nKey` is in marketplace-app | Mostly interfaces + DTOs |
| audit-spring-boot-starter | 7 | AuditReadService | Compact, focused |
| attachment-spring-boot-starter | 14 | AttachmentService | Medium, handles S3 + DB |
| user-spring-boot-starter | 11 | UserService | Small, focused |
| advertisement-spring-boot-starter | 7 | AdvertisementService | Small, focused; now calls `TaxonPort` via `ComponentFactory` |
| taxon-spring-boot-starter | 12 | DefaultTaxonPort | Medium; added 2026-06 |
| **marketplace-app** | **~175** | AdvertisementFormOverlayModeHandler | LARGEST MODULE — expected for UI monolith |

**Finding:** marketplace-app is 9x larger than any starter. Most complexity is in UI layer (views, overlays, components), which is expected for a Vaadin application.

---

## God Packages

Largest package hierarchies in marketplace-app:

| Package | Files | Notes |
|---------|-------|-------|
| `org.ost.marketplace.ui.views` | 30+ | Main UI structure (views, overlays, components) |
| `org.ost.marketplace.ui.query` | 20+ | Query builder UI elements (filter, sort, pagination) |
| `org.ost.marketplace.services` | 15+ | i18n, audit, auth, security services |

These are expected given Vaadin's component-heavy nature. No unjustified god packages detected.

---

## Constructor Injection Complexity

Checked for classes with excessive constructor parameters (>5 fields):

Most classes have 1-3 injected dependencies:
- `DefaultAuditPort`: 4 fields (auditLogRepository, currentActorHook, auditDomainHook, auditReadService)
- `AuditDomainHookImpl`: 4 fields (componentFactories for ports)
- `AccessEvaluator`: 2 fields (userPort, authContextService) — ✅ Fixed (ADR-016, 2026-06-15)

**Finding:** No excessive constructor bloat. Dependency injection is reasonable.

---

## Summary

| Category | Status | Notes |
|----------|--------|-------|
| **Cyclic Dependencies** | ✓ PASS | No cycles detected |
| **Starter → Starter Imports** | ✓ PASS | Only SPI contracts used |
| **UI → Repository Direct** | ✓ PASS | All through Ports |
| **Vaadin in Starters** | ✓ PASS | Vaadin only in marketplace-app |
| **Marketplace → Starter Internal** | ✓ RESOLVED | AccessEvaluator fixed (ADR-016, 2026-06-15); UserPortImpl mapping logic fixed (2026-07-01) |
| **Singleton State Isolation** | ✓ RESOLVED | SettingsPaginationService cross-session bleed fixed (ADR-028, improvement-018) |
| **Optional Deps Guarded** | ~ PARTIAL | starter level RESOLVED (ComponentFactory guards everywhere); marketplace-app UI still hard-injects `AttachmentPort`/`AuditPort` in 3 classes → improvement-011 (MEDIUM) |
| **User ↔ Advertisement Coupling** | ~ WARNING | Schema-level FK coupling; acceptable since both required |
| **Module Sizes** | ✓ PASS | No unjustified size outliers |

**Open Action Items:**
1. **DECIDE on Optional Deps (improvement-011):** Either make marketplace-app UI degradation real (`ComponentFactory` + `@ConditionalOnBean` on `AttachmentGalleryService`, `AttachmentGallery`, `AuditActivityPanel`) or remove `<optional>` from advertisement pom.xml and drop the degradation clause — record the decision in `marketplace-app/DECISIONS.md`
2. **MONITOR Advertisement → User:** If user becomes optional in future, extract UserReference SPI

