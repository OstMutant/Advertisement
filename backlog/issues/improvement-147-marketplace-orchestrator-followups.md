# improvement-147: are `orchestrator.shared`'s single-caller collaborators justified?

**Type:** improvement — tracked continuation of `improvement-136`'s extraction.
**Module:** `marketplace-orchestrator`
**Priority:** 🔴 top — moved to the very top of the backlog 2026-08-07 per explicit user request.
**When:** Independent research/design step now; the actual refactor decision depends on
`improvement-124` Batch 124-C landing (see below), but the "why did these stop having a second
consumer" investigation can start immediately.

## Problem

Three classes in `marketplace-orchestrator/src/main/java/org/ost/orchestrator/shared/`:

- `TaxonAssignmentWriteService.java` — wraps `TaxonPort.replaceAssignments()`
- `AttachmentSnapshotReaderService.java` — wraps `AttachmentPort.getLatestSnapshotId()`
- `AttachmentSoftDeleteService.java` — wraps `AttachmentPort.softDeleteAll()`

were built during `improvement-136` as shared, reusable single-port collaborators, on the
assumption that `ProviderProfileService`'s own category-assignment write would move into the
orchestrator too and reuse `TaxonAssignmentWriteService`. That move was deliberately **not** done
in `improvement-136` (building it then would have meant designing new `ProviderProfile` feature
work with no UI/audit path yet to justify it — out of that issue's scope). Today, all three classes
have exactly **one** real caller each — `AdvertisementSaveService`
(`marketplace-orchestrator/src/main/java/org/ost/orchestrator/advertisement/save/
AdvertisementSaveService.java`) — found during `improvement-136`'s own `/code-review` pass as a
possible premature abstraction, and left in place at the time as "pre-positioned for reuse, not yet
proven."

## Suggested fix

1. Investigate directly, don't assume: confirm (via grep, not memory) that these 3 classes really
   have zero other callers right now, and understand exactly why — was a second caller planned and
   dropped, or was reuse always speculative from day one? Write the answer down here before
   deciding anything.
2. `improvement-124` Batch 124-C now plans to build `ProviderProfileSaveService`
   (`org.ost.orchestrator.providerprofile.save`) mirroring `AdvertisementSaveService`'s shape,
   reusing `TaxonAssignmentWriteService` for its own category-assignment write (see
   `improvement-124`'s Batch 124-C section — updated in the same session this issue was filed).
   Once that lands, re-check this issue: did `ProviderProfileSaveService` end up actually calling
   `TaxonAssignmentWriteService`, or did it need something different?
3. Decide then, not now: if a real second caller exists and the shared shape holds up, keep it as
   documented, working design. If it doesn't — either `ProviderProfileSaveService` never lands, or
   it lands but doesn't fit `TaxonAssignmentWriteService`'s exact shape — fold the single-caller
   collaborator(s) back into `AdvertisementSaveService` as private methods rather than leaving a
   "shared" class with one permanent caller.
   `AttachmentSnapshotReaderService`/`AttachmentSoftDeleteService` have no obvious ProviderProfile
   equivalent (providers don't have attachments) — decide separately whether they're justified on
   their own merits (e.g. future reuse elsewhere) or should fold back regardless of how item 2
   resolves.

## Related

- `completed/issues/improvement-136-marketplace-orchestrator-extraction.md` — where these 3 classes
  were built and first questioned.
- `improvement-124` (F-04, Batch 124-C) — where the real second caller (`ProviderProfileSaveService`)
  is now planned; its own issue file section is the source of truth for that work, not this one.
- `marketplace-orchestrator/DECISIONS.md` ADR-001.
