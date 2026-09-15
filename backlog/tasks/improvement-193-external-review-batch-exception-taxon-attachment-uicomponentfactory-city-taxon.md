# improvement-193: External review batch — ApiExceptionHandler / TaxonService / AttachmentService / UiComponentFactory / City-Taxon dedup

**Type:** improvement — bug-risk fix + type-safety + SOLID/DRY cleanup batch (5 independent items)
**Module:** marketplace-rest-api (`api/error/ApiExceptionHandler.java`), taxon-spring-boot-starter
  (`services/TaxonService.java`), attachment-spring-boot-starter (`services/AttachmentService.java`),
  marketplace-app (`ui/core/UiComponentFactory.java`,
  `ui/views/main/tabs/referencedata/overlay/modes/{City,Taxon}FormOverlayModeHandler.java`)
**Priority:** medium-high
**When:** independent, no blockers — item 6 builds on `improvement-072`/ADR-058's already-decided
  bound, not a re-opening of it

## Current state

An external SOLID/DRY/best-practices review submitted 7 findings; each verified against current
`main` before filing anything. 2 of 7 not carried into this batch: finding 1 (README "no UI yet"
for Provider Profile) is a **false positive** — no such claim exists anywhere in the current
`README.md` Module Layout table. Finding 2 (no CI pipeline) is already tracked as `improvement-028`,
deliberately deprioritized to ⚪ low with its own recorded reasoning (`scripts/ci.sh` already
provides the full pipeline locally; what's left is GitHub-Actions-specific migration, not new
coverage) — this batch doesn't relitigate that call. The remaining 5, renumbered 3-7 to match the
source review's own numbering:

3. `ApiExceptionHandler.handleIllegalState()` (`.java:42-46`) maps `IllegalStateException` → 429
   generically, correct only because the login rate-limiter is currently the sole source of that
   exception type in `org.ost.restapi.api` — any future unrelated `IllegalStateException` in that
   package would get the same wrong status code.
4. `TaxonService.update()` (`.java:73`) manually sets `.updatedAt(Instant.now())` even though
   `Taxon.java:30` already declares `@LastModifiedDate` for the same field.
5. `AttachmentService` has 15 public methods (confirmed by direct count) spanning upload /
   temp-session / video / lifecycle concerns. The source review's claim that `AttachmentPort`
   "already" reflects a clean 3-way grouping is overstated — it doesn't, on inspection. This
   project's own lighter, already-established "Service Class Section Headers" convention
   (`CLAUDE.md`) hasn't even been applied to this file yet — only one `// ── internals ──` marker
   exists today.
6. `UiComponentFactory<T>.build(P params)` still has an unchecked cast — **not for the reason the
   source review assumed**. `improvement-072`/`marketplace-app/DECISIONS.md` ADR-058 already
   bounded the class to `T extends Configurable<T, ?>` (2026-07-24), migrating the 10
   non-`Configurable` consumers to plain `ComponentFactory<T>` first — confirmed via direct read of
   the current file. The gap that's actually still open is narrower: `P` (the method's own type
   parameter) is not linked to the class's `Configurable<T, ?>` bound's second argument, so
   `build()` still cannot avoid casting to `Configurable<T, P>`. ~24 files currently declare
   `UiComponentFactory<Xxx>` (single type argument) and would need a second argument
   (`UiComponentFactory<Xxx, Xxx.Parameters>`) to close this the rest of the way.
7. `CityFormOverlayModeHandler` (283 lines) and `TaxonFormOverlayModeHandler` (282 lines) are
   near-exact structural duplicates — identical method names in the same order, identical
   `private record LocaleField`. `improvement-080` (completed) collapsed
   `TaxonFormOverlayModeHandler`'s own internal 4-field EN/UK repetition but never touched the
   cross-file City↔Taxon duplication — confirmed still present, unchanged, today.

## Why change

Items 3/4 are real, if narrow, correctness/consistency risks — worth closing before they cause a
confusing production symptom (wrong HTTP status; a redundant field write masking a real auditing
question). Items 5/6/7 are real structural debt with concrete evidence (method counts, line-by-line
duplication, an already-partially-fixed type-safety gap) rather than subjective taste.

## Expected benefit

- Item 3: a future unrelated `IllegalStateException` in `org.ost.restapi.api` gets its own correct
  status instead of silently inheriting 429.
- Item 4: one less place manually duplicating what Spring Data JDBC auditing already guarantees.
- Item 5: `AttachmentService` becomes scannable by concern instead of one 15-method flat list.
- Item 6: closes the specific unchecked-cast gap `improvement-072` deliberately left open at the
  time, now that the harder question (the `T`-bound) is already settled.
- Item 7: one canonical locale-form implementation instead of two ~283-line near-copies drifting
  independently on every future taxon/city edit.

## Approach

**Item 3 — `ApiExceptionHandler`:** add a dedicated `TooManyAttemptsException extends
RuntimeException` in the same rate-limiter path that currently throws `IllegalStateException`; map
`TooManyAttemptsException` → 429 in `ApiExceptionHandler` instead of the generic
`IllegalStateException` handler. Decide what (if anything) the generic `IllegalStateException`
handler should map to once it's no longer serving double duty — likely 500, since an
`IllegalStateException` reaching here afterward is a genuine unexpected-state bug, not a rate-limit
signal.

**Item 4 — `TaxonService.update()`:** remove the manual `.updatedAt(Instant.now())` and confirm
`@LastModifiedDate` auditing actually populates it on `taxonRepository.save()` (the project-wide
`AuditorAware<Long>` / JDBC auditing config already relied on elsewhere per root `CLAUDE.md`). If
auditing does not fire here for some JDBC-specific reason, document that reason with a one-line
comment instead of silently keeping the redundant manual set.

**Item 5 — `AttachmentService`:** start with this project's own lighter, already-established
convention — add `// ── ... ──` section headers grouping the 15 public methods into their real
concern clusters (permanent attachments / temp upload session / video / lifecycle-restore) before
considering a full class split. Only escalate to the source review's heavier 3-class-split
suggestion (`AttachmentUploadService`/`AttachmentTempSessionService`/`AttachmentLifecycleService`)
if section headers alone don't make the class scannable enough — a real judgment call to make with
the actual grouped view in hand, not decided in the abstract now.

**Item 6 — `UiComponentFactory<T, P>`:** add the second type parameter —
`public class UiComponentFactory<T extends Configurable<T, P>, P> extends ComponentFactory<T>`,
`build(P params)` becomes `return get().configure(params);` with no cast at all. Update all ~24
declaration sites to the two-argument form. Verify against `improvement-072`'s own resolution
history first — that task already tried and reverted one adjacent generics idea (the
`AuditActivityEnrichHook` dispatch) for a documented reason; this is a different, narrower change
(only `UiComponentFactory`'s own second parameter) but confirm no similar compile-time surprise
exists before committing to the full 24-site migration.

**Item 7 — City/Taxon dedup:** extract the shared locale-form logic (the `LocaleField` record,
`activate()`/`buildDto()`/`buildBinder()`/`copyLocaleFields()`/`handleRestoreFromActivity()`/
`discardChanges()`/`afterSave()` shape) into a composed helper — composition, not a shared base
class — parameterized by: the `TaxonType` (CITY vs CATEGORY), the i18n key set, and DTO field
accessor references. Both handlers hold this helper as a field and delegate to it instead of
carrying near-duplicate method bodies. Run the full Playwright e2e suite specifically for this item
— `improvement-080`'s own risk note about this exact file's binder-validation wiring being delicate
still applies.

## Related

- [improvement-028](improvement-028-minimal-ci-pipeline.md) — CI pipeline, not relitigated by this
  batch (source review's finding 2).
- `improvement-072` (`completed/tasks/`) + `marketplace-app/DECISIONS.md` ADR-058 —
  `UiComponentFactory<T>`'s `Configurable<T, ?>` bound, already decided; item 6 extends it, does not
  reopen it.
- `improvement-071`/`improvement-080` (`completed/tasks/`) — prior `TaxonFormOverlayModeHandler`
  cleanups; item 7 is the cross-file duplication those two didn't cover.
- `platform-commons/src/main/java/org/ost/platform/attachment/spi/AttachmentPort.java` — checked
  directly for item 5's grouping claim.
