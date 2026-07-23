# improvement-103: AttachmentService — compress a 13+-method public API with near-duplicate variants

**Type:** improvement — simplification (interface bloat / SRP). Found via simplification review
(2026-07-19).
**Module:** `attachment-spring-boot-starter` (`services/AttachmentService.java`,
`spi/DefaultAttachmentPort`)
**Priority:** low-medium — no bug; the surface makes every change to attachment behavior a
"which of the five similar methods do I touch" exercise
**When:** Batch M (attachment API simplification) — see `backlog/BACKLOG.md` "Execution batches";
do after Batch B lands (090/093 touch the same file — avoid rebase churn)

## Problem

`AttachmentService` exposes 13+ public methods with overlapping variants:

- `upload` / `uploadDto` / `uploadTemp` — same flow, entity-vs-DTO-vs-temp variants;
- `addVideo` / `addVideoDto` / `addVideoTemp` — ditto, with the yt-vs-embed branching duplicated
  between `addVideo` and `addVideoTemp`;
- `commitTempUploads` / `commitTempUploadsQuiet` — differ only in whether snapshot+notify runs;
- `restoreToUrls` ×3 overloads (2-of-3 `@Transactional` self-invoking the third) +
  `restoreToUrlsAndCapture`;
- `delete` / `deleteSkipSnapshot` — differ only in snapshot capture;
- `getByEntityId` / `getByEntityIdDtos`, `getByEntityAndUrls` / `getByEntityAndUrlsDtos` —
  entity-vs-DTO pairs.

The `*Dto` twins exist because some callers want entities and some DTOs; the `*Quiet`/`Skip*`
twins encode a boolean (capture snapshot or not) as method identity; the video branching is
copy-pasted.

## Suggested fix (shape, adjust on implementation)

- Return DTOs from the service uniformly (entity variants become internal) — kills the four
  `*Dto` twins; `DefaultAttachmentPort` today is the only DTO consumer boundary that matters.
- Collapse `X`/`XQuiet`/`XSkipSnapshot` pairs into one method with an explicit
  `SnapshotCapture.CAPTURE/SKIP` enum parameter (self-documenting at call sites, unlike a bare
  boolean).
- Extract the yt-vs-embed resolution into one private `videoDescriptor(url)` used by both
  `addVideo` and `addVideoTemp`.
- Re-examine the `restoreToUrls` overload trio — one method taking an explicit actor, callers
  resolve `CurrentActorHook` themselves (they already do everywhere else).

Constraint: `AttachmentPort` (platform-commons) stays source-compatible or is updated in the
same PR with its marketplace call sites — this is an internal-shape cleanup, not an SPI redesign.

## Related

- `backlog/completed/issues/improvement-093-capturemediachanges-silent-skip-without-actor.md` —
  landed 2026-07-20 (Batch B); its `orElseThrow` decision shapes the actor-parameter question here.
- `backlog/issues/improvement-021-attachment-concurrency-and-batching.md` item C — `saveAll`
  batching sits in the same commit path; natural rider if Batch M and 021's trigger align.
