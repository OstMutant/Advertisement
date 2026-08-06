# Coupling Analysis

## Architecture Violations — Current State

### Marketplace → Starter Internal Imports

`AccessEvaluator` depends only on `UserAuthorizationPort` (platform-commons SPI) and
`AuthContextService` — no direct import of `org.ost.user.security.*`. Role and ownership checks
go through `UserAuthorizationPort.isAdmin()`, `.isModerator()`, `.isOwner()`. See ADR-016
(`marketplace-app/DECISIONS.md`).

**File:** `/app/marketplace-app/src/main/java/org/ost/marketplace/services/security/AccessEvaluator.java`

---

### UserPortImpl DTO Mapping Logic

`UserPortImpl` methods are pure single-line delegations to `UserService` — no `toDto(User)`
mapping or `.stream().map(...)` pipelines inside the port itself. `UserPortImpl.findByEmail()`
delegates to `userService.findDtoByEmail(email)`.

**File:** `/app/user-spring-boot-starter/src/main/java/org/ost/user/spi/UserPortImpl.java`

---

## No Cyclic Dependencies Detected

See `docs/architecture-map.html`'s Module Dependencies page ("No Circular Dependencies" / "Marketplace App Dependency" observations) for
the dependency graph and the DAG-shape/depends-on-all-starters findings — not restated here.

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
for starter in audit attachment user advertisement taxon provider-profile; do
  grep -r "import org\.ost\.\(audit\|attachment\|user\|advertisement\|taxon\|provider\)" \
    /app/$starter-spring-boot-starter/src/main/java --include="*.java" \
    | grep -v "org\.ost\.platform"
done
```

Result: advertisement-spring-boot-starter imports audit and attachment via optional dependencies, but only calls:
- `AuditPort` (interface in platform-commons) ✓
- `AttachmentPort` (interface in platform-commons) ✓

No internal class imports detected. ✓

---

## Actor-Reference Coupling: Advertisement → User

`advertisement`'s actor-reference columns (`created_by`/`updated_by`/`deleted_by`) are plain
`BIGINT` with no FK to `user_information` — matching `taxon`/`audit`/`attachment`'s convention.
Purge-safety is enforced at the application level, not via a DB constraint: two bulk
`AdvertisementPort` methods, `findOwnerIds(Set<Long>)` (blocks a retention purge while ads exist)
and `clearActorReferences(Set<Long>)` (nulls the columns), called from `UserService.cleanup()`.
`updated_by`/`deleted_by` are not exposed to the UI.

---

## Singleton State Isolation Across UI Sessions

`SettingsPaginationService` is a singleton `@Component` holding a
`CopyOnWriteArrayList<BindingEntry>` across every user's UI session. Each `BindingEntry` carries
the owning `userId` (captured from `AuthContextService` at `register()` time), and
`onSettingsChanged(userId, settings)` filters by `entry.userId().equals(userId)` before pushing a
new page size — one user's settings change never touches another session's grid. Cleanup uses both
`bar.addDetachListener(...)` and `@PreDestroy`. See ADR-028.

**File:** `/app/marketplace-app/src/main/java/org/ost/marketplace/ui/views/services/pagination/SettingsPaginationService.java`

---

## Optional Dependency Guards

### Starter Level

`AdvertisementService` injects `ComponentFactory<AuditPort>`, `ComponentFactory<AttachmentPort>`,
`ComponentFactory<TaxonPort>` and resolves every call through `ifAvailable()` /
`findIfAvailable()`. A grep for `import org.ost.audit.` / `import org.ost.attachment.` in
advertisement-spring-boot-starter returns nothing — only platform-commons SPI types are
referenced. The starter degrades gracefully when a sibling starter isn't on the classpath.

### Marketplace-App UI

Three marketplace-app UI classes inject their starter ports via `ComponentFactory`, with
`@ConditionalOnBean` on the bean definitions themselves (not just a call-site `ifAvailable()`
guard, since these bean definitions live in marketplace-app and always exist there):

| Class | Injection | Scope | Failure without the starter |
|-------|-----------|-------|------------------------------|
| `AttachmentGalleryService` | `AttachmentPort` | singleton | context fails **at startup** |
| `AttachmentGallery` | `AttachmentPort` | prototype | exception on first build |
| `AuditActivityPanel` | `AuditPort` | prototype | exception on first build |

This ensures attachment/audit starters are genuinely optional, matching `<optional>true</optional>`
in advertisement-spring-boot-starter's `pom.xml`.

---

## Module Size & Complexity

| Module | Java Files | Largest File | Notes |
|--------|-----------|------|-------|
| query-lib | 9 | ~150 lines | Small utility library |
| platform-commons | 57 | mostly interfaces + DTOs | `I18nKey` is in marketplace-app |
| audit-spring-boot-starter | 6 | `AuditLogRepository.java` (251 lines) | Compact, focused |
| attachment-spring-boot-starter | 15 | `AttachmentRepository.java` (221 lines) | Handles S3 + DB |
| user-spring-boot-starter | 16 | `UserService.java` (200 lines) | Focused; largest of the small starters |
| advertisement-spring-boot-starter | 7 | `AdvertisementService.java` (182 lines) | Small, focused |
| taxon-spring-boot-starter | 15 | `DefaultTaxonPort.java` (243 lines) | Taxonomy + assignment |
| provider-profile-spring-boot-starter | 7 | `ProviderProfileService.java` (149 lines) | Small, focused |
| **marketplace-app** | **174** | `I18nKey.java` (438 lines) | LARGEST MODULE — expected for UI monolith |

**Finding:** marketplace-app is ~11x larger than any single starter (314 total Java files across
all 10 modules). Most complexity is in the UI layer (views, overlays, components), expected for a
Vaadin application. See `07-risk-report.md` for marketplace-app's own largest-file breakdown.

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
- `AccessEvaluator`: 2 fields (authorizationPort, authContextService) — see ADR-016

**Finding:** No excessive constructor bloat. Dependency injection is reasonable.

---

## Summary

| Category | Status | Notes |
|----------|--------|-------|
| **Cyclic Dependencies** | ✓ PASS | No cycles detected |
| **Starter → Starter Imports** | ✓ PASS | Only SPI contracts used |
| **UI → Repository Direct** | ✓ PASS | All through Ports |
| **Vaadin in Starters** | ✓ PASS | Vaadin only in marketplace-app |
| **Marketplace → Starter Internal** | ✓ PASS | `AccessEvaluator` depends only on `UserAuthorizationPort` (ADR-016); `UserPortImpl` is pure delegation |
| **Singleton State Isolation** | ✓ PASS | `SettingsPaginationService` scopes each `BindingEntry` by `userId` (ADR-028) |
| **Optional Deps Guarded** | ✓ PASS | starter level + marketplace-app UI both guarded (`ComponentFactory`/`@ConditionalOnBean`) |
| **User ↔ Advertisement Coupling** | ✓ PASS | No DB-level FK — see "Actor-Reference Coupling" section above |
| **Module Sizes** | ✓ PASS | No unjustified size outliers |

**Open Action Items:**
1. **MONITOR Advertisement → User:** No DB-level coupling exists today; if a future need
   reintroduces one, extract a lightweight `UserReference` SPI rather than a raw FK.

