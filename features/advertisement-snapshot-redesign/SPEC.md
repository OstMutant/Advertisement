# Feature: Advertisement Snapshot Redesign — Soft References + Bulk Data Loading

## Goal

Replace timestamp-based cross-module correlation with direct ID references stored in
`AdvertisementSnapshotDto`. Delete `CategoryChangeSnapshotDto`. Decouple gallery and
category state from the audit write path. UI consumes pre-prepared data from marketplace services.

---

## Problems solved

1. `AttachmentSnapshotRepository` queries `audit_log` table — cross-module SQL coupling (violation)
2. Timestamp correlation broken (NOW() same within TX) — isCurrentState badge bug (test 29)
3. `CategoryChangeSnapshotDto` — separate per-category audit records (redundant, locale-broken)

---

## Core Design

### `AdvertisementSnapshotDto` (platform-commons)

```java
public record AdvertisementSnapshotDto(
    String title,
    String description,
    List<Long> categoryIds,          // IDs at save time — soft ref to taxon
    Long attachmentSnapshotId        // nullable — soft ref to attachment_snapshot.id
) implements AuditableSnapshot {}
```

`isCurrentState` = `Objects.equals(h.snapshotData(), cfg.currentSnapshot())` — record `equals()` compares all four fields including `attachmentSnapshotId`. No separate `mediaMatchCurrent()` needed.

### Save flow — audit written LAST by orchestrator

`AdvertisementService.save()` saves entity only — no audit write.
`AdvertisementSaveService` (marketplace-app) orchestrates:

```
1. Read before-state (EDIT only): findById + getForEntity + getLatestSnapshotId
2. advertisementPort.save(dto)         → entityId (entity saved, no audit)
3. commitGallery(entityRef)            → gallery committed, new attachment_snapshot created if changed
4. attachmentPort.getLatestSnapshotId  → attachmentSnapshotId (ID of current gallery snapshot)
5. taxonPort.replaceAssignments(...)   → categories set
6. Build AdvertisementSnapshotDto with title, description (from saved entity), categoryIds, attachmentSnapshotId
7. auditPort.captureCreation/captureUpdate → audit written with complete snapshot
```

### `attachment_snapshot` — add own `version` column

Own per-entity counter (INT), independent of `audit_log`. Incremented on each new snapshot.
Used for internal ordering; NOT correlated to audit versions.

### Cross-module SQL removal

All `AttachmentSnapshotRepository` methods that query `audit_log` are replaced:
- `getUrlsAtVersion(entity, version)` — REMOVED (used version-based cross-module SQL)
- `getUrlsForSnapshot(entity, snapshotId)` — REPLACED by `getUrlsById(Long id)` (direct lookup by PK)
- `findChangesBySnapshotId(entity, auditLogSnapshotId)` — REPLACED by `findChangesById(Long attachmentSnapshotId)` (lookup `changes_summary` by `attachment_snapshot.id`)
- `findChangesByVersion(entity, version)` — REMOVED
- New: `getLatestId(entityType, entityId)` — returns latest `attachment_snapshot.id` for entity

### `CategoryChangeSnapshotDto` — deleted entirely

`TaxonActivityService` + `TaxonAuditHookImpl` — deleted (no-op, `CategoryChangeSnapshotDto` written nowhere).
`TaxonAuditHook` interface — kept (may be repurposed later), impl deleted.
Category history encoded in consecutive `AdvertisementSnapshotDto.categoryIds` diffs in `audit_log`.

### `isRestorable()` / `visible: true` — cleaned up

`AuditableSnapshot.isRestorable()` removed (dead code per analysis).
`AuditableSnapshot.isVisible()` — `@JsonIgnore` added (default `true` is noise in JSON).
`AuditActivityPanel.restorableCount` → replaced with `items.size()` (all visible items are restorable).
`AuditActivityRowRenderer.mediaMatchCurrent()` → REMOVED (replaced by snapshot `equals()`).

### Media diff display

`AuditTimelineRowRenderer`: instead of `enrichHook.getMediaStateAtVersion(ref, version)` (version-based, cross-module),
use `enrichHook.getMediaStateForSnapshot(ref, attachmentSnapshotId)` extracted from `h.snapshotData()`.

`ActivityEnrichHookImpl.merge()`: extract `attachmentSnapshotId` from `AdvertisementSnapshotDto`,
call `attachmentAuditHook.getChangesBySnapshotId(attachmentSnapshotId)` (direct `attachment_snapshot.id` lookup).

### Gallery restore

`AttachmentGallery.loadFromSnapshot(int version)` → `loadFromSnapshotId(Long attachmentSnapshotId)`.
Caller (advertisement overlay restore handler) passes `attachmentSnapshotId` from the audit snapshot DTO.
`AttachmentPort.getSnapshotUrlsAtVersion()` → `getUrlsBySnapshotId(@NonNull Long snapshotId)`.

### Bulk data loading — marketplace-app service

New `AdvertisementTimelineEnrichService` (marketplace-app):
- Collects all `attachmentSnapshotId` + `categoryIds` from timeline page
- Bulk loads filenames via `attachmentPort.getFilenamesBySnapshotIds(ids)`
- Bulk loads category DTOs via `taxonPort.findByIds(ids, locale)` (includes soft-deleted)
- Deleted category display: `TaxonDto.deletedAt != null` → CSS strikethrough

---

## Detailed Change List

### `platform-commons`
| File | Change |
|------|--------|
| `AdvertisementSnapshotDto` | `String categories` → `List<Long> categoryIds` + `Long attachmentSnapshotId`; update `diff()` + `allFields()` |
| `CategoryChangeSnapshotDto` | **DELETE** |
| `AttachmentPort` | `void captureSnapshot()` stays; add `Long getLatestSnapshotId(EntityType, Long)`; `String[] getSnapshotUrlsAtVersion()` → `String[] getUrlsBySnapshotId(Long snapshotId)` |
| `AttachmentAuditHook` | Remove `getMediaChanges`, `mediaMatchCurrent`, `getMediaStateAtVersion`; add `List<ChangeEntry> getChangesBySnapshotId(Long)`; keep `getMediaStateForSnapshot` |
| `AuditActivityEnrichHook` | Remove `getAdditionalChanges`, `matchesCurrent`, `getMediaStateAtVersion`; keep `getMediaStateForSnapshot` |
| `TaxonPort` | Add `Map<Long, TaxonDto> findByIds(Set<Long> ids, Locale locale)` (includes deleted) |
| `AuditableSnapshot` | `@JsonIgnore` on `isVisible()`; remove `isRestorable()` |

### `attachment-spring-boot-starter`
| File | Change |
|------|--------|
| Liquibase | `attachment_snapshot` ADD `version INT NOT NULL DEFAULT 0`; populated via `(SELECT COALESCE(MAX(version),0)+1 ...)` on insert |
| `AttachmentSnapshotRepository` | Add `getUrlsById(Long)`, `findChangesById(Long)`, `findLatestId(EntityType, Long)`; remove all `audit_log`-referencing SQL |
| `AttachmentSnapshotService` | `capture()` stays void; add `captureAndGetId()` → `Optional<Long>`; replace `getMediaStateForSnapshot` → use `getUrlsById`; remove `getUrlsAtVersion`, `mediaMatchCurrent`, `getChangesForVersion`; add `getChangesBySnapshotId(Long)` |
| `DefaultAttachmentPort` | Add `getLatestSnapshotId()`; replace `getSnapshotUrlsAtVersion()` → `getUrlsBySnapshotId()`; delegate new methods |
| `AttachmentAuditHookImpl` | Implement new `getChangesBySnapshotId()`; remove deleted method impls |

### `advertisement-spring-boot-starter`
| File | Change |
|------|--------|
| `AdvertisementService.save()` | Remove all audit writes; remove `resolveCurrentCategoryNames/resolveNewCategoryNames`; entity save only |

### `taxon-spring-boot-starter`
| File | Change |
|------|--------|
| `DefaultTaxonPort` | Add `findByIds(Set<Long> ids, Locale locale)` — includes soft-deleted entries |
| `TaxonService` / `TaxonRepository` | Add underlying `findByIds` query |

### `marketplace-app`
| File | Change |
|------|--------|
| `AdvertisementSaveService` | Full restructure: read before → save entity → gallery → getLatestSnapshotId → set categories → write audit |
| `TaxonActivityService` | **DELETE** |
| `TaxonAuditHookImpl` | **DELETE** (hook impl removed; factory returns empty) |
| `ActivityEnrichHookImpl` | Remove `matchesCurrent`, `getAdditionalChanges`, `getMediaStateAtVersion`; `merge()` extracts `attachmentSnapshotId` from snapshot DTO |
| `AuditActivityRowRenderer` | `isCurrentState` = snapshot `equals()` only; remove `mediaMatchCurrent()` method |
| `AuditActivityPanel` | `historySize = items.size()` (remove `isRestorable()` check) |
| `AuditTimelineRowRenderer` | `mediaLookup` supplier uses `getMediaStateForSnapshot(ref, attachmentSnapshotId)` from `AdvertisementSnapshotDto` |
| `AttachmentGallery` | `loadFromSnapshot(int version)` → `loadFromSnapshotId(Long snapshotId)` |
| Advertisement overlay restore | Pass `attachmentSnapshotId` from snapshot DTO instead of version |
| New `AdvertisementTimelineEnrichService` | Bulk load filenames + category names for timeline page |

### Liquibase
| Migration | Change |
|-----------|--------|
| `attachment-spring-boot-starter` | ADD COLUMN `version INT NOT NULL DEFAULT 0` to `attachment_snapshot` |

---

## Out of scope (future)

- Deleted category display with strikethrough (deferred to `category-ids-in-snapshot` feature)
- `AdvertisementTimelineEnrichService` bulk load (deferred — implement after core refactor works)
- `snapshot-cleanup` (`@JsonIgnore` on `isVisible`) — separate small task

---

## Verification

1. `mvn clean test` — JUnit
2. `bash scripts/deploy-dev.sh` — deploy
3. `bash scripts/playwright.sh 04-advertisement --ux` — restore/history tests (test 29)
4. `bash scripts/playwright.sh --full --ux` — full regression
