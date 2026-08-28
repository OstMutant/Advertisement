# improvement-175: Shared HTML-sanitizer utility + stale-id-during-concurrent-delete fix (Advertisement + ProviderProfile)

**Type:** improvement — cross-domain cleanup, carved out of `improvement-124` Batch 124-B2
**Module:** `platform-commons` (new util package), `advertisement-spring-boot-starter`,
`provider-profile-spring-boot-starter`
**Priority:** 🟡 high — carved out of `improvement-124` and moved to the top of the backlog per
explicit user request, 2026-08-28. Gates `improvement-124` Batch 124-C (the `AccountOverlay` work
mirrors both domains' `save()` shape further, so should build on the cleaned-up version).
**When:** independent, no blockers.

## Current state

Two findings from `improvement-124` Batch 124-B's `/code-review --fix` pass survived verification
(CONFIRMED/PLAUSIBLE) but touch `advertisement-spring-boot-starter` files outside that batch's own
scope, so they weren't fixed inline. Both are directly caused by `ProviderProfileService` mirroring
`AdvertisementService`'s shape, not unrelated drive-by findings — carved out as their own issue
(not folded into `improvement-133`'s deferred-findings bucket, per explicit user direction at the
time) since they're concrete, sizeable, and ready to implement now, not oversized/speculative.

1. **Duplicated HTML-sanitizer/visible-text-length logic.** `ProviderProfileService.HTML_SANITIZER`/
   `sanitizeHtml()`/`validateAboutLength()` are near-verbatim copies of `AdvertisementService`'s
   `HTML_SANITIZER`/`sanitizeHtml()`/`validateDescriptionLength()` — same OWASP `PolicyFactory`
   construction, same Jsoup-based visible-text-length check shape, differing only in field/constant
   names and the max-length value.

2. **Stale-id-during-concurrent-delete edge case in `save()`.** In both
   `AdvertisementService.save()`/`buildEntity()` and `ProviderProfileService.save()`/`buildEntity()`:
   when the DTO carries a non-null `id` (an edit) but the row was deleted between read and write,
   `repository.findById(id)` returns empty, `before` is `null`, and `buildEntity()` silently falls
   back to insert-shaped defaults (`createdBy`/`createdAt` or `actorId`/`createdAt`) while still
   carrying the now-stale `id` — Spring Data JDBC then attempts an update-path against a
   non-existent row instead of surfacing a clear "not found"/conflict error. Confirmed as an exact
   pre-existing pattern already shipped in `AdvertisementService`, not something Batch 124-B
   introduced.

## Why change

Finding 1 is duplicated logic that will now drift independently across two domains every time
either one's sanitization rule changes (a classic DRY violation the project's own quality-first
rule flags). Finding 2 is a real, if narrow, correctness gap — a genuine race window (delete
between read and write) produces a confusing downstream error (an update against a nonexistent
row) instead of a clear, actionable one, in *two* domains that would otherwise silently drift into
two different error shapes for the same race if fixed separately.

## Expected benefit

One canonical sanitizer implementation instead of two copies that can silently diverge. One
consistent, clear exception shape for the stale-id-during-delete race in both domains, instead of
an opaque downstream JDBC failure.

## Approach

1. **Shared sanitizer utility.** New `org.ost.platform.core.util.HtmlSanitizerUtil` in
   `platform-commons` (e.g. `HtmlSanitizerUtil.sanitize(html, maxVisibleLength)`) — the same
   "utility class used by ≥2 modules" precedent `YoutubeUtil` already establishes
   (`platform-commons/CLAUDE.md`). Neither starter currently pulls OWASP/Jsoup into
   `platform-commons`, but both already depend on `platform-commons`, so this doesn't add a new
   starter-to-starter coupling. Repoint both `AdvertisementService` and `ProviderProfileService` to
   call it; delete both local copies.
2. **Stale-id fix.** In both `AdvertisementService.buildEntity()` and
   `ProviderProfileService.buildEntity()`: when `before == null && dto.id() != null`, throw a clear
   "already deleted"/conflict exception instead of falling through to insert-shaped defaults with a
   stale id. Fix both call sites together so the two domains share one error shape for the same
   race, not two independently-drifted ones.

## Testing strategy

`bash scripts/unit-tests.sh` + `bash scripts/integration-tests.sh --sandbox` green for both
`advertisement-spring-boot-starter` and `provider-profile-spring-boot-starter`; no behavior change
for either domain's happy path — verify via existing `AdvertisementServiceHtmlSanitizationTest`/
`ProviderProfileServiceTest` continuing to pass unmodified against the new shared utility. New unit
test(s) for the stale-id-during-delete exception path in both services.

## Related

- `improvement-124` — originally tracked this as Batch 124-B2; carved out into this standalone
  issue 2026-08-28 per explicit user request, so it can rank independently at the top of the
  backlog instead of waiting behind Batch B2's position in that issue's own sequence. Gates that
  issue's Batch 124-C.
