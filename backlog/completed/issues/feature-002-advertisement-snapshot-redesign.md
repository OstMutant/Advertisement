# feature-002: Advertisement Snapshot Redesign — Soft References + Bulk Data Loading — ✅ DONE

**Type:** feature — architectural redesign of the audit snapshot layer, condensed from the
original multi-file `advertisement-snapshot-redesign/SPEC.md` (pre-issue-file convention).
**Module:** `platform-commons`, `attachment-spring-boot-starter`, `advertisement-spring-boot-starter`,
`taxon-spring-boot-starter`, `marketplace-app`.
**Status:** done. Original spec's own "Out of scope" items were later delivered too —
`TaxonPort.findByIds()` and `AttachmentSnapshotService.captureAndGetId()` shipped via
[improvement-007](improvement-007-taxon-findbyids-and-snapshot-captureandgetid.md).

## Problem

`AdvertisementSnapshotDto` correlated cross-module audit state (attachment gallery, category
assignments) by **timestamp**, which broke inside a single transaction (`NOW()` identical for
every write in the same TX — caused an `isCurrentState` badge bug). `AttachmentSnapshotRepository`
queried the `audit_log` table directly — a cross-module SQL coupling violation. Category history
was recorded as a separate per-category `CategoryChangeSnapshotDto` record per save — redundant,
locale-broken (name frozen at save-time in one language), and not resolvable to current names.

## Design

Replaced timestamp correlation with **direct ID soft-references** stored in the snapshot itself:

```java
public record AdvertisementSnapshotDto(
    String title, String description,
    List<Long> categoryIds,          // soft ref to taxon, full set at save time
    Long attachmentSnapshotId        // soft ref to attachment_snapshot.id, nullable
) implements AuditableSnapshot {}
```

`isCurrentState` became a plain record `equals()` comparison (all four fields) — no separate
`mediaMatchCurrent()`/version-matching logic needed.

**Save orchestration moved to marketplace-app.** `AdvertisementService.save()` no longer writes
audit itself — `AdvertisementSaveService` (marketplace-app) orchestrates: save entity → commit
gallery (creates a new `attachment_snapshot` if changed) → get its id → replace category
assignments → build the `AdvertisementSnapshotDto` with all four fields → write audit last, once,
complete. `attachment_snapshot` gained its own `version INT` column, independent of `audit_log`'s
versioning, used only for internal ordering.

**`CategoryChangeSnapshotDto` deleted entirely** — `TaxonActivityService`/`TaxonAuditHookImpl`
removed; category history now lives implicitly in consecutive `categoryIds` diffs across
`AdvertisementSnapshotDto` versions. `AttachmentSnapshotRepository`'s `audit_log`-querying methods
were replaced with direct `attachment_snapshot.id`-keyed lookups (`getUrlsById`,
`findChangesById`, `getLatestId`) — the cross-module SQL coupling is gone. `AuditableSnapshot
.isRestorable()` (dead code) removed; `isVisible()` got `@JsonIgnore`.

Gallery restore and media-diff display were re-pointed from version numbers to
`attachmentSnapshotId` throughout (`AttachmentGallery.loadFromSnapshotId()`,
`AttachmentPort.getUrlsBySnapshotId()`, `ActivityEnrichHookImpl.merge()`).

## Deferred at the time, since resolved

- Deleted-category strikethrough display — split out to
  [feature-004](feature-004-category-ids-in-snapshot.md), whose own strikethrough item is now
  tracked as [improvement-008](../../issues/improvement-008-deleted-category-strikethrough.md)
  (still open).
- `TaxonPort.findByIds()` bulk lookup + `AttachmentSnapshotService.captureAndGetId()` — shipped
  via [improvement-007](improvement-007-taxon-findbyids-and-snapshot-captureandgetid.md).
- `AdvertisementTimelineEnrichService` bulk category/filename loading for timeline pages — the
  bulk-lookup mechanism this depended on shipped as part of improvement-007, but this specific
  service was superseded by the simpler `AdvertisementEnrichService.resolveCategoryNames()`
  approach that landed later (see [feature-004](feature-004-category-ids-in-snapshot.md)).

## Verification (at the time)

Full reactor build, `deploy-dev.sh`, targeted Playwright restore/history spec, full regression
suite — all green.
