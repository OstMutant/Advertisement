# improvement-081: Extract duplicated embed-URL resolution and iframe security attributes from lightbox classes

**Type:** improvement — structural duplication cleanup, found via manual UI review.
**Module:** `marketplace-app` (`ui/views/components/attachment/AttachmentLightbox.java`,
`CardLightboxViewer.java`).
**Priority:** low-medium — pure duplication extraction, verbatim-identical logic, low risk.
**When:** anytime — independent of the other items in this batch.

## Problem

Two verbatim-duplicated pieces of logic exist across `AttachmentLightbox` and
`CardLightboxViewer`:

1. **Embed-URL resolution** — `AttachmentLightbox.resolveEmbedUrl()` (`.java:96-100`) and
   `CardLightboxViewer.embedSrc()` (`.java:109-113`) are byte-for-byte identical:
   ```java
   if (AttachmentMediaContentType.YOUTUBE.getValue().equals(a.contentType()))
       return YoutubeUtil.embedUrl(YoutubeUtil.extractId(a.url()));
   return a.url();
   ```
2. **Iframe security attribute block** — `AttachmentLightbox.buildIFrame()` (`.java:80-83`) and
   `CardLightboxViewer.init()` (`.java:41-44`) set the identical `allow`/`allowfullscreen`/
   `sandbox` attribute strings verbatim.

## Suggested fix

Extract both into one shared static helper both classes call. Since both classes live in
`marketplace-app` and the logic is Vaadin-specific (iframe attribute strings), the helper belongs
in `marketplace-app`'s own `ui/views/utils/` (a `*Util` class per this module's naming
convention) — **not** `platform-commons`, which is restricted to cross-module-consumed,
Vaadin-free utilities (`platform-commons/CLAUDE.md`).

Do not merge the two classes themselves — they serve genuinely different UX (single
non-navigable overlay vs. multi-item navigable gallery).

## Related

- Filed as part of a verified 8-item Vaadin UI refactor batch (2026-07-17); see improvement-076
  through improvement-080 and improvement-082/083 for the rest.
