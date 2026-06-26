# Coupling Analysis

## Architecture Violations — Current State

### ✅ RESOLVED: Marketplace → Starter Internal Imports (2026-06-15)

**Previously:** `AccessEvaluator` imported `org.ost.user.security.OwnershipChecker` and `org.ost.user.security.RoleChecker` directly.

**Resolution (ADR-016):** `AccessEvaluator` now depends only on `UserPort` (platform-commons SPI) and `AuthContextService`. Role and ownership checks go through `UserPort.isAdmin()`, `UserPort.isModerator()`, `UserPort.isOwner()`. All 22 files in marketplace-app now use `UserDto`/`UserPort` exclusively.

**File:** `/app/marketplace-app/src/main/java/org/ost/marketplace/services/security/AccessEvaluator.java`

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

## Hidden Coupling: Optional Dependencies Without Guards

**Severity:** MEDIUM — Runtime failure risk

**Finding:**
advertisement-spring-boot-starter pom.xml declares optional dependencies:
```xml
<dependency>
    <groupId>org.ost</groupId>
    <artifactId>audit-spring-boot-starter</artifactId>
    <optional>true</optional>
</dependency>
<dependency>
    <groupId>org.ost</groupId>
    <artifactId>attachment-spring-boot-starter</artifactId>
    <optional>true</optional>
</dependency>
```

But in code, these are called without ObjectProvider guards:
```java
// In AdvertisementService (presumed):
auditPort.captureCreation(...);  // WILL FAIL if audit starter not on classpath
```

**Impact:**
- If audit or attachment starters are excluded from classpath, the app fails at runtime
- No graceful degradation

**Options:**
1. Remove `<optional>true/>` — make them required dependencies
2. Add `ObjectProvider<AuditPort>` guards in code
3. Add autom configuration checks to fail early with clear message

**Recommendation:**
Option 1 (remove optional) is simplest. Audit and attachment are core to the marketplace feature set. If true optionality is needed, guards must be added.

---

## Module Size & Complexity

| Module | Java Files | Largest File | Notes |
|--------|-----------|------|-------|
| query-lib | 7 | ~200 lines | Small utility library |
| platform-commons | ~49 | I18nKey is in marketplace-app | Mostly interfaces + DTOs |
| audit-spring-boot-starter | 7 | AuditReadService | Compact, focused |
| attachment-spring-boot-starter | 16 | AttachmentService | Medium, handles S3 + DB |
| user-spring-boot-starter | 11 | UserService | Small, focused |
| advertisement-spring-boot-starter | 7 | AdvertisementService | Small, focused |
| taxon-spring-boot-starter | 12 | DefaultTaxonPort | Medium; new as of 2026-06 |
| **marketplace-app** | **~170** | AdvertisementFormOverlayModeHandler | LARGEST MODULE — expected for UI monolith |

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
- `AccessEvaluator`: 2 fields (roleChecker, ownershipChecker) — PROBLEMATIC (should come from UserPort)

**Finding:** No excessive constructor bloat. Dependency injection is reasonable.

---

## Summary

| Category | Status | Notes |
|----------|--------|-------|
| **Cyclic Dependencies** | ✓ PASS | No cycles detected |
| **Starter → Starter Imports** | ✓ PASS | Only SPI contracts used |
| **UI → Repository Direct** | ✓ PASS | All through Ports |
| **Vaadin in Starters** | ✓ PASS | Vaadin only in marketplace-app |
| **Marketplace → Starter Internal** | ✓ RESOLVED | AccessEvaluator fixed (ADR-016, 2026-06-15) |
| **Optional Deps Guarded** | ✗ OPEN | audit/attachment optional but not guarded with ObjectProvider (MEDIUM) |
| **User ↔ Advertisement Coupling** | ~ WARNING | Schema-level FK coupling; acceptable since both required |
| **Module Sizes** | ✓ PASS | No unjustified size outliers |

**Open Action Items:**
1. **DECIDE on Optional Deps:** Either remove `<optional>` from advertisement pom.xml or add ObjectProvider guards
2. **MONITOR Advertisement → User:** If user becomes optional in future, extract UserReference SPI

