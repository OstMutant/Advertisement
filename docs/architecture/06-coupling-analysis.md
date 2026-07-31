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

→ [violation-004-userportimpl-mapping-logic](../../backlog/completed/issues/violation-004-userportimpl-mapping-logic.md) (completed)

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

## Hidden Coupling: Advertisement → User Tight Coupling — ✓ RESOLVED (improvement-120, 2026-07-25)

**Severity:** was MEDIUM — Schema-level coupling; resolved.

**Original finding:** `advertisement` had a hard SQL-level FK to `user_information`
(`fk_advertisement_created_by`/`fk_advertisement_modified_by`/`fk_advertisement_deleted_by`,
`ON DELETE RESTRICT`/`SET NULL`), the last remaining hard FK coupling between two starters —
`taxon`/`audit`/`attachment` already stored actor references as plain `BIGINT` with no FK.

**Resolution:** All three FK constraints removed from
`advertisement-spring-boot-starter/.../01-advertisement-schema.xml`. Replaced by two bulk
`AdvertisementPort` methods: `findOwnerIds(Set<Long>)` (mirrors the old `RESTRICT` — blocks a
retention purge) and `clearActorReferences(Set<Long>)` (mirrors the old `SET NULL` — nulls the
columns at the application level), called from `UserService.cleanup()`. Verified against 3 UI
scenarios (admin soft-delete, purge-blocked-by-ownership, purge-with-dangling-actor-ref) with no
regression — `updated_by`/`deleted_by` were never exposed to the UI. See
`backlog/completed/issues/improvement-120-advertisement-user-hard-fk-coupling.md`.

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

→ [improvement-018](../../backlog/completed/issues/improvement-018-settings-pagination-cross-session-bleed.md) (completed)

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

### ✓ RESOLVED — residual risk relocated to marketplace-app UI (found 2026-07-03, fixed improvement-011, 2026-07-13)

**Severity:** was MEDIUM — startup failure risk; resolved.

Three marketplace-app UI classes used to hard-inject starter ports instead of using
`ComponentFactory` (now fixed via `ComponentFactory`/`@ConditionalOnBean`):

| Class | Injection | Scope | Failure without the starter |
|-------|-----------|-------|------------------------------|
| `AttachmentGalleryService` | `AttachmentPort` | singleton | context fails **at startup** |
| `AttachmentGallery` | `AttachmentPort` | prototype | exception on first build |
| `AuditActivityPanel` | `AuditPort` | prototype | exception on first build |

Call-site guards alone (`galleryServiceFactory.ifAvailable(...)`) would not have helped: the
component bean definitions lived in marketplace-app and always existed, so `getIfAvailable()`
attempted instantiation and threw `UnsatisfiedDependencyException`. Resolved by additionally
gating the bean definitions themselves with `@ConditionalOnBean`, so attachment/audit starters are
genuinely optional now, matching `<optional>true</optional>` in
advertisement-spring-boot-starter's pom.xml.

→ See [improvement-011](../../backlog/completed/issues/improvement-011-unguarded-port-injection-in-ui-components.md)
(completed 2026-07-13) for the chosen resolution (`@ConditionalOnBean` on the component classes,
the consolidated "Option C").

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
| **Optional Deps Guarded** | ✓ RESOLVED | starter level + marketplace-app UI both guarded (`ComponentFactory`/`@ConditionalOnBean`) — `AttachmentGalleryService`/`AttachmentGallery`/`AuditActivityPanel` fixed (improvement-011, 2026-07-13) |
| **User ↔ Advertisement Coupling** | ✓ RESOLVED | Hard FK removed (improvement-120, 2026-07-25) — see "Hidden Coupling" section above |
| **Module Sizes** | ✓ PASS | No unjustified size outliers |

**Open Action Items:**
1. **MONITOR Advertisement → User:** No DB-level coupling remains (improvement-120); if a future
   need reintroduces one, extract a lightweight `UserReference` SPI rather than a raw FK.

