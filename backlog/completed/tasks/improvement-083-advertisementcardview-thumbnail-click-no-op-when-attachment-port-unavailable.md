# improvement-083: AdvertisementCardView thumbnail — decide UX when AttachmentPort is unavailable

**Type:** improvement — UX edge case, found via manual UI review.
**Module:** `marketplace-app` (`ui/views/main/tabs/advertisements/AdvertisementCardView.java`).
**Priority:** low-medium — real, reachable UX gap; not a data-loss/correctness bug.
**When:** anytime — independent of the other items in this batch.

## Problem

`AdvertisementCardView.createThumbnail()` wires the thumbnail's click handler through
`attachmentPortFactory.ifAvailable(_ -> galleryServiceFactory.get().openMediaLightbox(...))`
(`.java:121-124`) — when the attachment starter is absent from the classpath, clicking the
thumbnail silently no-ops (no lightbox opens, no feedback to the user).

## Correction to the original proposal

The original framing claimed this is reachable because `media_url`/`media_count` are
"denormalized directly onto `advertisement`, survive attachment-starter being disabled." This is
**stale** — verified directly: the `Advertisement` entity has no such columns at all, and
`advertisement-spring-boot-starter/CLAUDE.md` confirms ADR-035 removed that denormalization
entirely. `AdvertisementInfoDto.mediaUrl`/`mediaContentType`/`mediaCount` are populated per-read
via a bulk `AttachmentPort.getMediaSummaries()` lookup in `AdvertisementService
.enrichWithMediaSummary()` — when the attachment starter is absent, these DTO fields are simply
`null` for every advertisement, not "stale persisted data."

**The reachability claim is still correct, just for a different reason:** `createThumbnail()`
already guards with `if (ad.getMediaUrl() == null) return null;` at the top, so when the starter
is absent the thumbnail never renders in the first place (`getMediaUrl()` is always null in that
state) — meaning the "silent no-op on click" scenario in the original proposal is actually
**not reachable** the way it was described. It *would* still be reachable only in the narrower
window where the starter is present at write/read time (so media data exists and the thumbnail
renders) but becomes unavailable between page render and the user's click — an edge case, not the
"survives being disabled" scenario originally claimed.

## Suggested fix

Given the corrected, narrower reachability, this is lower-priority than originally framed but
still worth a small defensive fix for robustness: decide and implement one of:
- Hide/disable the thumbnail's click affordance when `attachmentPortFactory` is unavailable at
  render time (defense in depth, even though today's `getMediaUrl() == null` guard already covers
  the common case).
- Show a brief notification explaining why nothing happened, using this codebase's existing
  `NotificationService` conventions, for the narrow starter-removed-mid-session edge case.

## Related

- Filed as part of a verified 8-item Vaadin UI refactor batch (2026-07-17); see improvement-076
  through improvement-082 for the rest.
- `advertisement-spring-boot-starter/CLAUDE.md` — ADR-035 context on media enrichment being
  read-time only, never denormalized/cached on the `advertisement` row.
