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

**Status: done (2026-09-22, autopilot run).** Full `scripts/ci.sh` pass (build/unit/integration/
e2e 63-63/sonar/archunit/lint/shellcheck/docs) all green. File stays open in `backlog/tasks/` —
Part 2 (below) is unrelated scope, not yet started.

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

Researched 2026-09-22: OWASP File Upload Cheat Sheet and current Java-ecosystem practice both
converge on the same two-layer control — a declared-header whitelist as a cheap first gate, plus
real magic-byte content sniffing as the actual security boundary (a header-only check trusts the
attacker to self-report, which is no control at all). Apache Tika (`tika-core`) is the established
library for the sniffing layer — actively maintained, no heavyweight transitive parsers needed
when only `tika-core` (not `tika-parsers`) is pulled in. Plan below implements both layers.

1. **Dependency:** add `tika-core` (magic-byte MIME detection) — root `pom.xml` gets a
   `<tika-core.version>4.0.0</tika-core.version>` property plus a `dependencyManagement` entry for
   `org.apache.tika:tika-core`; `attachment-spring-boot-starter/pom.xml` adds the dependency
   itself (no version — managed).
2. **Single-source whitelist:** new `org.ost.platform.attachment.model.AttachmentAllowedContentTypes`
   in `platform-commons` — `public static final Set<String> VALUES = Set.of("image/jpeg",
   "image/png", "image/webp", "image/gif", "video/mp4", "video/webm")`. Referenced by both
   `AttachmentUploadButton.setAcceptedMimeTypes(...)` (marketplace-app, client-side hint) and the
   server-side validator below (attachment-spring-boot-starter, the real gate) — one canonical list
   instead of the current hardcoded duplicate, reachable by both modules without violating the
   Module Import Rules (both sides only ever depend on platform-commons, never on each other).
3. **New validator:** `AttachmentContentTypeValidator` in
   `attachment-spring-boot-starter/.../org.ost.attachment.util` (same static-util shape as the
   existing `AttachmentVideoUtil` in that package) — `static InputStream validate(InputStream
   inputStream, String declaredContentType)`:
   - reject (`IllegalArgumentException` — this starter's/`html-sanitizer-lib`'s established
     convention for input-validation failures at a boundary) if `declaredContentType` is not in
     `AttachmentAllowedContentTypes.VALUES`;
   - wrap the stream in `TikaInputStream`, run `new Tika().detect(...)` against the real bytes
     (mark/reset, no full re-read), reject the same way if the *detected* type is not in the same
     whitelist — this is what catches a file whose declared header is an allowed type but whose
     actual bytes are not (e.g. an HTML payload declared as `image/jpeg`);
   - return the stream repositioned at the start, unchanged for the caller past this point.
4. **Enforcement call sites:** `AttachmentService.upload()` **and** `AttachmentService.uploadTemp()`
   in attachment-spring-boot-starter — both are directly reachable from
   `AttachmentGallery.buildUploadHandler()` (marketplace-app) with the attacker-controlled
   `event.getContentType()`, so both need the same guard before their existing
   `storageService.upload(...)` call.
5. **Tests:** new `AttachmentContentTypeValidatorTest` (attachment-spring-boot-starter's first unit
   test — `integration-tests` stays reserved for Testcontainers-based repository tests per
   `.claude/rules/integration-tests.md`, this validator needs neither DB nor S3). Cases: each
   allowed type's real bytes pass; a disallowed declared type (e.g. `text/html`) is rejected; an
   allowed declared type paired with mismatched/malicious actual bytes is rejected.
6. **Record the decision:** `/record-decision` for `platform-commons` (new shared
   `AttachmentAllowedContentTypes` constant) and for `attachment-spring-boot-starter` itself (new
   `tika-core` dependency + the content-type validation layer — this starter does have its own
   hand-authored `DECISIONS.md`, corrected after initially assuming otherwise) — required by this
   project's own Definition of Done for an architectural change.
7. Run `/code-review` (high effort) on the fix diff before Playwright verification — same
   discipline already used earlier this session for Phase 9's `TaxonPort` change.
8. Full Playwright verification of the attachment upload flow after the fix lands, since this
   touches upload behavior directly.

### Part 1 — Implementation notes (2026-09-22)

Steps 1-5 done, autopilot run. One autonomous fix during implementation, worth recording since it
changes step 3's signature from the plan above: `AttachmentContentTypeValidator.validate` takes a
**`filename` parameter too** (`validate(InputStream, String filename, String declaredContentType)`),
threaded through from `AttachmentService.upload()`/`uploadTemp()`'s existing `filename` argument.
Reason, confirmed empirically against real `tika-core:4.0.0` behavior (not guessed): Tika's own
`tika-mimetypes.xml` has no magic-byte-only pattern that resolves to literally `video/webm` (WebM
is glob-only, `*.webm`, sub-class of `application/x-matroska`; a byte-only WebM upload detects as
`application/x-matroska`, outside the whitelist) or reliably to `video/mp4` for the common `isom`
major-brand case (Tika's own `video/mp4` magic pattern only matches literal `ftypmp41`/`ftypmp42`
at offset 4; an `isom`-branded file falls back to the broader `video/quicktime` magic match).
Without the filename hint, the security fix would have silently rejected the large share of
real-world MP4/WebM uploads that don't happen to carry those exact bytes — a functional regression
masquerading as a passing security check. Confirmed the filename hint doesn't weaken the actual
security property: an HTML payload named `a.mp4`/`a.webm`/`a.jpeg` still detects as `text/html`
(content magic wins over a lying extension) — verified directly against the same real Tika build
before adopting this, not assumed.

Steps 6-8 done. `/review` (`deep-review-orchestrator`) found and 2 findings were fixed directly
(auto-report bucket, both CONFIRMED): (1) `AttachmentContentTypeValidator.validate()` leaked the
caller's `InputStream` on every rejection path — fixed by having the validator itself close the
stream before throwing, instead of relying on the caller's post-success `closeQuietly()`. (2) the
two required `/record-decision` entries were missing — added as `attachment-spring-boot-starter`
ADR-015 and `platform-commons` ADR-033 (correcting an earlier planning mistake: this starter does
have its own hand-authored `DECISIONS.md`, step 6 above wrongly assumed otherwise). A third,
medium-confidence finding (DRY: `video/mp4`/`video/webm` string literals duplicated between the
new `AttachmentAllowedContentTypes` and the existing `AttachmentMediaContentType` enum) was left
for human review rather than auto-applied — proposed separately for `improvement-133`'s deferred-
findings bucket, not fixed here.

Running the full verification pass (`scripts/ci.sh --sonar` first) surfaced a real regression the
new validator caused: two pre-existing `integration-tests` files
(`AttachmentServiceTest`/`AttachmentServiceTransactionTest`) fed fake placeholder byte payloads
(`"data".getBytes()`, bare Mockito-mocked `InputStream`s) declared as `image/jpeg`/`video/mp4` —
content the new Tika check now correctly rejects, and mock-identity mismatches once the validator
started returning a wrapping stream instead of the original reference. Fixed both files to use
real magic-byte fixtures and a `TrackingInputStream` test double (mirroring
`AttachmentContentTypeValidatorTest`'s own) instead of asserting `close()` against the original
mock reference. Confirmed via Sonar itself: the quality gate passed and flagged nothing in the new
code — the failure was entirely these two pre-existing fixtures never having been updated for the
new validation step.

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

### Part 2 — Progress (2026-09-22)

**Step 1 done.** `AbstractTaxonManagementView` + `TaxonManagementOverlay` extracted, per ADR-085
(`marketplace-app/DECISIONS.md`). `CityManagementView`/`CategoryManagementView` now ~35 lines each
(was ~184). Verified: full reactor compiles, full Playwright `e2e --ux` (50/50, spec 06 skipped by
design) passed twice. `/review` found no SOLID violations, no missed renames; one KISS finding
(intersection-generic `getOverlay()`) kept as-is per ADR-082's own cast-avoidance precedent.

Bundled with step 1: renamed the concrete Category-specific classes (`TaxonOverlay`→`CategoryOverlay`,
`TaxonManagementView`→`CategoryManagementView`, `TaxonViewOverlayModeHandler`→
`CategoryViewOverlayModeHandler`, `TaxonFormOverlayModeHandler`→`CategoryFormOverlayModeHandler`,
`TaxonEditDto`→`CategoryEditDto`, cascaded through 33 i18n keys + properties + CSS + Playwright) —
per user direction, since these were misleadingly named "Taxon" from before City existed. Confirmed
this does not reopen ADR-065's rejected parameterized-view alternative (annotated there).

Steps 2-4 (Overlay pair, ViewOverlayModeHandler pair, FormOverlayModeHandler pair) not yet started.

### Part 2 — Related

- Found via an external code-review-style pass over marketplace-app, re-verified end-to-end
  directly against current source (normalized diff), 2026-09-22.
- `improvement-147` — the one existing, narrower finding about these two classes (Add button
  visibility), unrelated to this wholesale duplication.
- `LocaleTranslationForm<T>` — existing precedent this task extends.
