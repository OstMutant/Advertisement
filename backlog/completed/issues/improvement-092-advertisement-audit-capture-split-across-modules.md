# improvement-092: Advertisement audit capture is split across two modules (save in marketplace-app, delete in the starter)

**Type:** improvement — architecture consistency (SRP / pattern symmetry), design decision needed.
Found via pattern-focused code review (2026-07-19).
**Module:** `marketplace-app` (`services/advertisement/AdvertisementSaveService.java`),
`advertisement-spring-boot-starter` (`services/AdvertisementService.java`)
**Priority:** low-medium — no bug today; the risk is divergence (one side evolves, the other is
forgotten) and a misleading precedent for future domains
**When:** independent — needs a short design decision first; batch with any advertisement-domain
touch

## Problem

The "capture an audit snapshot around a lifecycle mutation" responsibility for advertisements
lives in two different modules depending on the verb:

- **save** (create/update): `marketplace-app`'s `AdvertisementSaveService` builds before/after
  `AdvertisementSnapshotDto`s and calls `captureCreation`/`captureUpdate` around
  `AdvertisementPort.save()`.
- **delete**: the starter's `AdvertisementService.delete()` builds the snapshot and calls
  `captureDeletion` itself.

Both do snapshot assembly the same way (title/description + category ids via `TaxonPort` +
attachment snapshot id via `AttachmentPort`) — `AdvertisementSaveService.buildCurrentSnapshot()`
and `AdvertisementService.delete()`'s inline block are near-duplicates in different modules.
Meanwhile user and taxon capture entirely inside their starters — advertisement is the only
domain with a split brain.

Why it likely drifted this way: save needs the *gallery commit* (`commitGallery` callback, a UI
concern) sequenced inside the same transaction, which pulled the whole orchestration up into
marketplace-app. Delete never needed that, so it stayed down in the starter.

## Suggested fix

Decide a single home and move the minority case:

- **Option A (recommended):** move delete-side capture up into a marketplace-app
  `AdvertisementDeleteService` (or fold into `AdvertisementSaveService` as a lifecycle service) so
  *all* advertisement audit orchestration is one module, and the starter's `delete()` shrinks to
  pure domain work — mirrors how save already works, and the snapshot-assembly duplicate collapses
  into the existing `buildCurrentSnapshot()`.
- **Option B:** push save-side capture down into the starter — rejected-by-default because the
  gallery-commit callback would drag a UI-flow concept into the starter's API.

Record the outcome in `marketplace-app/DECISIONS.md` either way.

## Related

- `backlog/completed/issues/improvement-058-taxon-assignment-audit-trail-missing.md` — prior art
  for "audit responsibility lives where the flow is orchestrated".
