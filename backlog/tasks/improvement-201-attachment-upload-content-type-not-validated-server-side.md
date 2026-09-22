# improvement-201: Attachment upload content-type validation + City/Taxon vertical duplication

**Type:** bug (Part 1) + improvement — DRY (Part 2), bundled per explicit user direction
**Module:** Part 1 — attachment-spring-boot-starter (AttachmentService, S3StorageService),
marketplace-app (AttachmentGallery/AttachmentUploadButton — caller, not part of the fix itself).
Part 2 — marketplace-app (ui/views/main/tabs/referencedata/, overlay/, overlay/modes/)
**Priority:** 🔴 Top — placed above every other backlog item; Part 1 is a real, confirmed security
gap, not tech debt. Filed 2026-09-22.
**When:** independent, no blockers — highest priority in the entire backlog. The two parts are
unrelated in scope and can land as separate PRs/passes within this one task file.

## Part 1: Attachment upload content-type is never validated server-side — stored-XSS-via-upload vector

## Current state

Verified end-to-end against current code (2026-09-22): `event.getContentType()`
(`AttachmentGallery.buildUploadHandler()`, marketplace-app) is taken as-is from the client
request and passed unvalidated through `AttachmentMediaService.upload()` (orchestrator, pure
pass-through) → `AttachmentService.upload()` (attachment-starter — no check) →
`S3StorageService.upload()`, which writes it directly as S3 object metadata:
`PutObjectRequest.builder()...contentType(contentType)`. The only type restriction anywhere,
`AttachmentUploadButton.setAcceptedMimeTypes("image/jpeg", "image/png", "image/webp",
"image/gif", "video/mp4", "video/webm")`, is Vaadin's client-side HTML5 `accept` attribute on the
file picker — trivially bypassed with a direct multipart POST. `AttachmentMediaContentType
.isEmbedded()/isVideo()` (platform-commons) is confirmed used only for UI rendering decisions
(which preview icon to show), never as a security gate. No controller serves attachment content —
the browser fetches the S3 object directly via its public URL, receiving whatever Content-Type
the attacker supplied at upload time.

## Why change

An attacker can upload a file with an attacker-controlled `Content-Type` (e.g. `text/html`) under
any filename, and the browser fetching that S3 object URL directly will render it according to
that header instead of downloading it — a classic stored-XSS-via-upload vector. This is a real
security bug, not a style/DRY finding.

## Expected benefit

Closes a confirmed stored-XSS vector: uploads restricted to genuinely-verified image/video
content, not merely a client-declared header.

## Approach

1. Server-side whitelist: validate `contentType` against the real allowed set (the same list
   `AttachmentUploadButton` already declares client-side: image/jpeg, image/png, image/webp,
   image/gif, video/mp4, video/webm) before any call reaches `S3StorageService.upload()` — reject
   with a clear error otherwise. Decide the right layer: `AttachmentService.upload()` (starter) is
   the natural enforcement point, since it's the last shared chokepoint before S3.
2. Consider whether the client-declared header alone is sufficient or whether actual content
   sniffing (magic-byte detection via Java's `MimetypesFileTypeMap`/Apache Tika/similar) is needed
   to prevent a mislabeled-but-malicious file from passing a header-only check — evaluate cost vs.
   the actual threat model before deciding scope.
3. Cover with tests: reject a disallowed/mismatched content type at the enforcement layer,
   confirm the existing allowed types still upload successfully.
4. Run `/code-review` (high effort) on the fix diff before Playwright verification — same
   discipline already used earlier this session for Phase 9's `TaxonPort` change.
5. Full Playwright verification of the attachment upload flow after the fix lands, since this
   touches upload behavior directly.

### Part 1 — Related

- Found via an external code-review-style pass over `marketplace-orchestrator`, re-verified
  directly against current source, 2026-09-22 — not previously tracked anywhere in this backlog.

---

## Part 2: City/Taxon vertical — 4 class pairs duplicated near-verbatim (~500+ lines)

### Current state

Verified end-to-end via normalized diff (City<->Taxon substitution before comparing) against
current source, 2026-09-22 — four parallel class pairs are near-identical, differing only in
`TaxonType.CATEGORY` vs `TaxonType.CITY`, i18n keys, CSS class prefixes, and overlay type:

- `CityManagementView`/`TaxonManagementView` (183/183 lines) — `refresh()`, `buildRow()`,
  `updateRowInPlace()`, `buildRowActions()`, `confirmAndDelete()`, `doRestore()` all duplicated
  near-verbatim; diff shows zero logic divergence beyond the type substitution.
- `CityOverlay`/`TaxonOverlay` (154/154 lines) — identical `OverlaySession` record, `switchTo()`,
  `openForView`/`openForCreate`/`openForEdit`.
- `CityViewOverlayModeHandler`/`TaxonViewOverlayModeHandler` (109/109 lines) — identical
  `buildPrimaryContent()`/`buildLocaleContent()`/`buildHeaderActions()`.
- `CityFormOverlayModeHandler`/`TaxonFormOverlayModeHandler` (225/226 lines) — only the form
  fields are already deduped via `LocaleTranslationForm<T>`; `buildHistoryButton()`, `save()`,
  `discardChanges()`, `buildDto()` remain duplicated outside what that class covers.

Backlog checked: only a narrow, unrelated finding about these two classes exists
(`improvement-147` — Add button rendered unconditionally), nothing about the wholesale
class-level duplication.

### Why change

This is confirmed the largest single duplication in the codebase — a project-established
precedent already exists for exactly this shape of fix (`LocaleTranslationForm<T>` deduped the
form-field portion of the fourth pair; `AbstractEntityOverlay<H>` is the existing generic-overlay
precedent), but it was never extended to the other three pairs or to the rest of the fourth
pair's own handler logic.

### Expected benefit

Removes ~500+ lines of duplicated logic across 4 class pairs; a future third `TaxonType`-backed
management view (if one is ever added) gets the abstraction instead of a fifth hand-copied pair.

### Approach

1. **`AbstractTaxonManagementView<T>`** (mirrors the existing `AbstractEntityOverlay<H>`
   precedent) — parameterized by `TaxonType` and the concrete overlay instance, covering
   `refresh()`/`buildRow()`/`updateRowInPlace()`/`buildRowActions()`/`confirmAndDelete()`/
   `doRestore()`. `CityManagementView`/`TaxonManagementView` become thin subclasses supplying only
   the `TaxonType`/overlay/i18n-key/CSS-prefix differences.
2. **Overlay pair** (`CityOverlay`/`TaxonOverlay`) — extract the shared `OverlaySession`/
   `switchTo()`/`openForView`/`openForCreate`/`openForEdit` logic into a generic base, same
   pattern as step 1.
3. **ViewOverlayModeHandler pair** — extract shared `buildPrimaryContent()`/
   `buildLocaleContent()`/`buildHeaderActions()`.
4. **FormOverlayModeHandler pair** — extend the existing `LocaleTranslationForm<T>` deduplication
   (or a sibling abstraction) to also cover `buildHistoryButton()`/`save()`/`discardChanges()`/
   `buildDto()`, the parts `LocaleTranslationForm<T>` doesn't reach today.
5. Design each abstraction before touching either concrete class — present for approval, per this
   project's own pattern for `AbstractEntityOverlay<H>`/`LocaleTranslationForm<T>`.
6. Full Playwright coverage of both City and Taxon management flows after each step, since this
   touches UI behavior directly on both domains simultaneously.
7. Run `/code-review` (high effort) on the full diff before considering this done.

### Part 2 — Related

- Found via an external code-review-style pass over marketplace-app, re-verified end-to-end
  directly against current source (normalized diff), 2026-09-22.
- `improvement-147` — the one existing, narrower finding about these two classes (Add button
  visibility), unrelated to this wholesale duplication.
- `LocaleTranslationForm<T>` — existing precedent this task extends.
