# improvement-201: Attachment upload content-type validation + City/Taxon vertical duplication + two deferred findings

**Type:** bug (Part 1) + improvement — DRY (Part 2) + two unverified precedent/design findings
(Parts 3-4), bundled per explicit user direction.
**Module:** Part 1 — attachment-spring-boot-starter (AttachmentService, S3StorageService),
marketplace-app (AttachmentGallery/AttachmentUploadButton — caller, not part of the fix itself).
Part 2 — marketplace-app (ui/views/main/tabs/referencedata/, overlay/, overlay/modes/). Part 3 —
user-spring-boot-starter/marketplace-orchestrator (UserService.cleanup()). Part 4 —
marketplace-orchestrator (ProviderProfileSaveService).
**Priority:** 🔴 Top for Parts 1-2 — placed above every other backlog item; Part 1 is a real,
confirmed security gap, not tech debt. **Parts 1-3 done (2026-09-23).** Part 4 carved out into
`improvement-202` (2026-09-23). Part 5 verified 2026-09-23 (root cause confirmed across 3 call
sites), fix option not yet chosen — unprioritized pending that decision. Filed 2026-09-22.
**When:** Parts 1-3 done, Part 4 moved to `improvement-202`. Part 5 sized, awaiting a fix-option
decision before implementation. All parts are unrelated in scope and landed/land as separate
PRs/passes within this one task file.

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

**Step 2 done.** `AbstractTaxonOverlay<H>` extracted per ADR-085 (updated). `CityOverlay`/
`CategoryOverlay` now ~60 lines each (was ~155). Verified: full reactor compiles, full Playwright
`e2e --ux` (50/50) passed. `/review` found no SOLID violations, no missed sharing opportunity.

One pre-existing, unrelated finding surfaced by step 2's review — see Part 5 below.

**Step 3 done.** `AbstractTaxonViewOverlayModeHandler` extracted per ADR-085 (updated).
`CityViewOverlayModeHandler`/`CategoryViewOverlayModeHandler` now ~50 lines each (was ~110).
Verified: full reactor compiles, full Playwright `e2e --ux` (50/50) passed — one run hit an
infra-level SIGKILL (exit 137, unrelated to the code, confirmed via clean retry). `/review` found
nothing to report.

**Step 4 done — Part 2 complete.** `AbstractTaxonFormOverlayModeHandler<T>` extracted per ADR-085
(updated), the richest of the 4 pairs (save/discard/restore/history/binder).
`CityFormOverlayModeHandler`/`CategoryFormOverlayModeHandler` now ~55 lines each (was ~226).
Connected simplifications: `EditDto` gained `setId(Long)` (safe — all 6 implementers already have
it via Lombok); `LocaleTranslationForm<T>` gained `extractTranslations()`/`applyTranslations()`;
`AbstractTaxonOverlay`'s own `getSavedEntityId()` abstract method removed (uniform accessor now on
the form handler itself). Verified: full reactor compiles, full Playwright `e2e --ux` (50/50)
passed twice (once for the step, once after a small `/review`-driven polish). `/review` found no
SOLID violations; one small duplication (Mode-translation ternary in both overlays'
`buildFormHandler()`) fixed directly via a shared `toHandlerMode()` helper.

**Part 2 totals:** ~700 duplicated lines removed across 4 class pairs. Full Playwright suite
(50/50) passed 7 times across the whole part. Zero SOLID violations across all 4 `/review` passes.
One pre-existing, unrelated bug found along the way (Part 5 below).

### Part 2 — Related

- Found via an external code-review-style pass over marketplace-app, re-verified end-to-end
  directly against current source (normalized diff), 2026-09-22.
- `improvement-147` — the one existing, narrower finding about these two classes (Add button
  visibility), unrelated to this wholesale duplication.
- `LocaleTranslationForm<T>` — existing precedent this task extends.

---

## Part 3: `UserService.cleanup()` — candidate to move to marketplace-orchestrator

**Status: done (2026-09-23).**

### Current state

Verified against current source, 2026-09-23: confirmed a real violation — `UserService.cleanup()`
directly composed `AdvertisementPort`/`ProviderProfilePort` to decide which soft-deleted users were
safe to purge, a domain starter orchestrating other domains.

### Approach

1. Read `UserService.cleanup()`'s real implementation and every real caller.
2. Confirm whether it composes more than one domain's own logic (the actual `marketplace-orchestrator`
   ownership test) or is legitimately single-domain user-starter logic that only looks
   orchestrator-shaped at a glance.
3. Only then decide whether this is a real move or a false positive.

### Part 3 — Implementation notes (2026-09-23)

`UserService` now exposes only single-domain primitives — `findIdsDeletedOlderThan(int)` and
`purge(Set<Long>)`. New `UserCleanupService`/`UserPurgeEligibilityService`
(`marketplace-orchestrator`) own the cross-domain referential-integrity decision and the scheduled
job (moved from `UserAutoConfiguration` to `OrchestratorAutoConfiguration`).
`.claude/rules/marketplace-orchestrator.md`'s "Not every cross-domain call moves here" section
rewritten: the `UserService.cleanup()` exception is removed, replaced by the real underlying test
(read-across-domain-to-decide moves here; fire-and-forget writes like `AuditPort.capture*()` stay
in the starter). New `marketplace-orchestrator` ADR (`DECISIONS.md`) records the decision;
`.claude/nav/adr-index.md` regenerated. `user-spring-boot-starter/README.md` corrected (was still
describing the removed `cleanup()`/ownership-check/scheduler behavior).

Verified: `marketplace-orchestrator` unit tests (`UserCleanupServiceTest` 4/4,
`UserPurgeEligibilityServiceTest` 6/6), `ArchitectureRulesTest` 20/20, `integration-tests`
`UserServiceTest` 8/8, full unit+integration suite green. `/review` (3 lenses) found one finding
(stale `user-spring-boot-starter/README.md`), fixed directly. No Playwright — no UI code touched.
Committed `2606a412`.

---

## Part 4: Generic exception crossing a module boundary in `ProviderProfileSaveService`

**Status: carved out into [improvement-202](improvement-202-optimisticlockingfailureexception-decoupling.md) (2026-09-23).**

### Current state

Verified against current source, 2026-09-23: real, not a false positive — but not unique to
`ProviderProfileSaveService`. `OptimisticLockingFailureException` (Spring Data framework type) is
used as the project-wide "stale write" signal across every domain, declared in `platform-commons`'s
own `*Port` Javadoc contracts. Fixing only `ProviderProfileSaveService` in isolation would make
the codebase *more* inconsistent, not less — the real fix is a single cross-cutting change (5
throw sites, 3 Port contracts, 2 catch/handler sites), too large for this task's scope. Sized and
moved to `improvement-202`, ranked Top per explicit user direction.

### Approach

See `improvement-202`.

---

## Part 5: post-save refetch returning empty is silently mishandled in 3 places (Taxon, Advertisement, ProviderProfile)

### Current state

**Root cause, confirmed 2026-09-23 across all 3 concrete manifestations:** every `*Overlay`/
`*FormOverlayModeHandler.save()` path that re-reads the just-saved entity by id right after commit
(purely to hand fresh data to the caller) has no defined behavior for when that re-read comes back
empty. Reachability is the same narrow race in all 3 cases — the just-written row would need to be
deleted by someone else in the split-second between commit and this synchronous re-read (both on a
single-instance Postgres via `JdbcClient`, no replica lag involved) — realistic for EDIT (two
admins/moderators touching the same row) and essentially unreachable for CREATE (nobody else knows
the new id yet), except where noted below. Each of the 3 call sites fails differently:

1. **`AbstractTaxonOverlay.proceed()`** (`referencedata/overlay/AbstractTaxonOverlay.java:56-67`,
   found via `/review` during Part 2 step 2, confirmed pre-existing via `git show` against
   pre-refactor `CityOverlay`/`TaxonOverlay` — not introduced by the refactor). `if (fresh == null)
   return;` after `getTaxonCatalogService().findById(savedId, Locale.ENGLISH)`. Since
   `AbstractEntityOverlay.handleSave()` (`components/overlay/AbstractEntityOverlay.java:64-69`)
   already fires the success notification and `currentFormHandler.afterSave(true)` (which disables
   Save/Discard) *before* calling `proceed()`, a `null` refetch leaves the user looking at a
   "success" toast over a frozen, button-disabled form with no error and no way back except the
   overlay's own Close (X) — `onUpdated()`/`onListChanged()` never fire, so the parent list never
   refreshes either.
2. **`AdvertisementOverlay.proceed()`** (`advertisements/overlay/AdvertisementOverlay.java:84-95`)
   — structurally the same silent no-op, but only in its EDIT branch (`if (fresh != null) {
   ...onUpdated... }`, nothing in the `else`). Its own CREATE branch is actually more robust: it
   never attempts a refetch at all, unconditionally calling `onListChanged()` +
   `closeToList()` — this is the divergence originally noted when Part 5 was filed, though on
   closer reading the divergence is specifically in the CREATE branch, not a blanket difference.
3. **`ProviderProfileFormOverlayModeHandler.save()`** (`header/account/
   ProviderProfileFormOverlayModeHandler.java:194-207`) — a different failure shape, since
   `AccountOverlay.proceed()` has no branch for `Section.PROVIDER_PROFILE` at all (by design — the
   whole overlay intentionally stays open after any section save, see its own code comment). The
   refetch happens inside `save()` itself: `providerProfileSaveService.findById(id).ifPresent(saved
   -> { dto.setId(...); dto.setVersion(...); })`. If empty, `dto`'s locally-tracked `version` (and,
   for a brand-new profile, `id`) never gets updated to the real post-save value. Symptom: not a
   frozen overlay (staying open is intended), but the **next** save attempt in the same session
   sends the stale `version`, triggering a false-positive `OptimisticLockingFailureException` — a
   "someone else changed this" conflict notification when nothing actually conflicted. For the
   CREATE sub-case (new profile, `dto.getId()` was `null` before save) it's worse: `id` also stays
   `null`, so the next Save attempt re-enters the `isNew` branch and could insert a second profile
   row for the same user instead of updating the first.

### Fix options (not yet chosen)

- **Option A — local, per-class fallback.** In each of the 3 call sites, when the refetch comes
  back empty: show a generic error/warning notification (reusing `saveConfig().conflict()`'s slot
  where one exists) and force a safe, defined state instead of silently continuing — `closeToList()`
  for `AbstractTaxonOverlay`/`AdvertisementOverlay`'s EDIT branch (matching `AdvertisementOverlay`'s
  own CREATE-branch pattern of never trusting an uncertain post-state); for
  `ProviderProfileFormOverlayModeHandler`, forcing a switch back to Provider Profile's own View mode
  handler (which re-reads fresh from the DB in its own `activate()`) instead of letting the Edit
  form keep a stale `dto`. Smallest, most surgical change; leaves 3 separate (if now-consistent)
  fallback implementations.
- **Option B — one shared helper on `AbstractEntityOverlay`.** Extract the "refetch came back empty"
  decision into one reusable method (e.g. `handleMissingPostSaveEntity()`) that every subclass's
  `proceed()`/`save()` calls into on the empty branch, so behavior is defined and consistent in one
  place rather than re-derived 3 times. Larger surface (touches the shared base class), but removes
  the risk of a 4th call site repeating the same unguarded assumption later.
- **Option C — avoid the refetch where the caller doesn't actually need fresh data.** Mirrors
  `AdvertisementOverlay`'s own CREATE branch: for cases where the caller only needs "trigger a
  re-render," skip the refetch and its failure mode entirely rather than handling it after the fact.
  Doesn't fully apply to `AbstractTaxonOverlay`'s EDIT branch or `ProviderProfileFormOverlayModeHandler`,
  both of which genuinely need the fresh DB-assigned `version`/fields for correctness (splicing into
  a parent row, or the next save's optimistic-lock check) — for those two, some fallback (Option A/B)
  is still needed regardless.

### How this can be tested

**The actual race (a concurrent delete landing inside the single synchronous request, between
commit and refetch) has no realistic automated reproduction today:**
- No unit tests exist for any `*Overlay`/`*FormOverlayModeHandler` class in `marketplace-app` today
  (confirmed via `find` — zero `*OverlayTest.java` files) — these are heavy Vaadin/Spring-UI-scoped
  components, not the kind of class this codebase unit-tests directly.
- `integration-tests` (the module that could inject a real concurrent delete via a second DB
  connection mid-transaction) never depends on `marketplace-app` — the decision logic under test
  here lives entirely in UI classes, out of that module's reach by design (see
  `.claude/rules/integration-tests.md`).
- Playwright automating two real concurrent browser sessions to land a delete inside another
  session's single in-flight save request is not reliably reproducible without a dedicated
  test-only timing hook (e.g. a debug breakpoint or an injected delay) — flaky by construction, not
  a sound basis for a regression test.

**What real coverage looks like, depending on which fix option is chosen:**
- If the fix is written as a small, pure decision function (e.g. Option B's
  `handleMissingPostSaveEntity()`, or an equivalent extracted per-class), that function itself can
  get a plain JUnit test with no Vaadin/Spring context at all — call it with the "empty" case
  directly (no mocking of the real race needed, since the function's contract is just "given no
  fresh entity, do X") and assert the resulting notification/navigation call. This is the only
  practically reachable automated coverage for this specific branch.
- The happy-path (refetch succeeds) is already implicitly covered by Part 2's existing Playwright
  runs (7 full `e2e --ux` passes, City/Category save flows) and by the pre-existing Advertisement/
  Provider-Profile Playwright specs — a regression here would need a fresh full `e2e --ux` pass
  after the fix lands, same as any other change to these overlays.
- The empty-refetch branch itself stays outside Playwright's practical reach; verifying it is a
  code-review-level check (confirm the new fallback path is reachable and behaves as designed by
  reading the code), not an automated-test-level one — worth stating plainly rather than claiming
  coverage that doesn't exist.

### Approach

1. Choose a fix option (A/B/C above) — needs approval before implementation, not decided here.
2. Apply to all 3 real call sites (`AbstractTaxonOverlay`, `AdvertisementOverlay`,
   `ProviderProfileFormOverlayModeHandler`) in one pass, since they're the same root cause.
3. Add the plain JUnit coverage described above for whichever decision logic ends up extracted.
4. Full Playwright `e2e --ux` pass (all 3 overlays' save flows) after the fix lands.
