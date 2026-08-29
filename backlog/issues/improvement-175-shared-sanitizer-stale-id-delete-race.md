# improvement-175: Stale-id-during-concurrent-delete fix (Advertisement + ProviderProfile)

**Type:** improvement — cross-domain cleanup, carved out of `improvement-124` Batch 124-B2
**Module:** `advertisement-spring-boot-starter`, `provider-profile-spring-boot-starter` (finding 1's
`platform-commons` scope was rejected during planning — see Approach)
**Priority:** 🟡 high — carved out of `improvement-124` and moved to the top of the backlog per
explicit user request, 2026-08-28.
**When:** **resequenced (2026-08-28), explicit user request — now gated on `improvement-178`, not
the other way around.** `improvement-178` builds `ProviderProfileSaveService` in
`marketplace-orchestrator` (the first real orchestrator layer for this domain) — once it exists,
re-evaluate whether this fix's `dto.id() != null && before == null` guard (and even finding 1's
rejected shared-sanitizer question) belongs there instead of inside `ProviderProfileService`
itself, the same way `AdvertisementSaveService` already fronts `AdvertisementService`. Do not
implement this issue's Approach as currently written until that's checked against the real,
built `ProviderProfileSaveService`.

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

**Finding 1 — REJECTED (2026-08-28), verified against `platform-commons/CLAUDE.md`'s own
governance rule.** The originally-planned `HtmlSanitizerUtil` in `platform-commons` (`core.util`)
was checked against `platform-commons`'s own "What belongs here" list before implementing —
**NOT ALLOWED** explicitly names *"Feature helpers or generic utils (`DateUtils`, `StringUtils`,
`JsonUtils`, etc.)"*. A generic `sanitize(html, maxVisibleLength)` utility is exactly that shape.
The `YoutubeUtil` precedent the original plan cited doesn't actually support it: `YoutubeUtil` has
**zero external dependencies** (pure `java.util.regex`), while `HtmlSanitizerUtil` would pull two
external libraries (`owasp-java-html-sanitizer`, `jsoup`) onto `platform-commons`'s classpath —
and therefore onto **every** module in the reactor transitively (audit/attachment/user/taxon
starters, `query-lib`, `marketplace-orchestrator`), not just the two that actually need HTML
sanitization. Starters cannot share code directly with each other either (Module Import Rules), so
there is no rules-compliant shared location for this utility in the current architecture. Leaving
the sanitizer logic duplicated in both services — a real but minor DRY violation, the lesser
problem compared to violating `platform-commons`'s own governance rule or bloating the shared
kernel's dependency footprint.

**Finding 2 — stale-id fix, refined during planning (verified against real code, not assumed).**
In both `AdvertisementService.buildEntity()` and `ProviderProfileService.buildEntity()`: when
`dto.id() != null && before == null`, throw
**`org.springframework.dao.OptimisticLockingFailureException`** — not a generic exception as
originally worded. Verified directly: this exact type is already what
`AdvertisementRepository`/`ProviderProfileRepository`'s own version-conflict checks throw
(`"Advertisement " + id + " was modified by another session"`), and
`marketplace-app`'s `AbstractEntityOverlay.handleSave()` already has a dedicated `catch
(OptimisticLockingFailureException e)` block showing `saveConfig().conflict()` — a proper
"someone else changed this" notification. Reusing it means this fix gets correct UI handling for
free, no UI-layer change needed, and both domains share one real, already-proven error shape for
the same race instead of inventing a new one.
- `AdvertisementService.buildEntity()`: add at the top, before the builder call —
  `if (dto.id() != null && before == null) throw new OptimisticLockingFailureException("Advertisement " + dto.id() + " was deleted before this edit could be saved");`
  — new import `org.springframework.dao.OptimisticLockingFailureException`.
- `ProviderProfileService.buildEntity()`: same guard, message
  `"Provider profile " + dto.id() + " was deleted before this edit could be saved"`.

## Testing strategy

`bash scripts/build-and-test.sh --unit --integration` green for both
`advertisement-spring-boot-starter` and `provider-profile-spring-boot-starter` — no behavior change
to either domain's happy path (finding 1 is not implemented, no sanitizer code touched at all). New
unit test per service for the stale-id-during-delete path: call `save()` with a non-null `dto.id()`
against a repository mock returning `Optional.empty()` from `findById`, assert
`OptimisticLockingFailureException` is thrown.

## Related

- `improvement-124` — originally tracked this as Batch 124-B2; carved out into this standalone
  issue 2026-08-28 per explicit user request, so it can rank independently at the top of the
  backlog instead of waiting behind Batch B2's position in that issue's own sequence. Gates that
  issue's Batch 124-C.
