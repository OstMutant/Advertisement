# improvement-175: Stale-id-during-concurrent-delete fix (Advertisement + ProviderProfile)

**Type:** improvement — cross-domain cleanup, carved out of `improvement-124` Batch 124-B2
**Module:** `advertisement-spring-boot-starter`, `provider-profile-spring-boot-starter`,
`marketplace-orchestrator` (finding 2's guard), new `html-sanitizer-lib` module (finding 1)
**Priority:** 🟡 high — carved out of `improvement-124` and moved to the top of the backlog per
explicit user request, 2026-08-28.
**When:** **Unblocked (2026-08-29).** Was gated on `improvement-178` building `ProviderProfileSaveService`
in `marketplace-orchestrator` — that landed and is committed; both findings below have been
re-evaluated against the real, built code (not the pre-`178` assumption this issue originally
planned against). Ready to implement.

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

**Finding 1 — un-rejected (2026-08-29), new shared-library approach.** The original
`platform-commons`-based plan is still correctly rejected (see the superseded reasoning kept below
for the record) — but a **new, dedicated module**, `html-sanitizer-lib`, sidesteps both objections
that killed it: it is not `platform-commons` (no dependency-bloat-onto-every-module problem), and a
starter depending on a plain shared library module — not another starter, not `marketplace-app` —
is not a Module Import Rules violation, the same precedent `query-lib` already establishes
(`advertisement-spring-boot-starter`/others already depend on it directly). The sanitize/validate
**call itself stays inside each starter's own `buildEntity()`**, unconditionally on every write —
only the implementation code moves, not who invokes it — so `AdvertisementPort.save()`/
`ProviderProfilePort.save()` keep being safe by construction regardless of caller (a Hook,
orchestrator-side, or save()-parameter-function alternative were all evaluated and rejected: each
one makes sanitization depend on caller discipline instead of being a domain-owned guarantee — see
`platform-commons/DECISIONS.md` ADR-027's own "data-integrity guarantee, not UI/orchestrator
concern" reasoning for the `kind == SUPPORT` check, which applies here identically).
- New module `html-sanitizer-lib` (plain Java library, no Spring Boot autoconfiguration, mirrors
  `query-lib`'s shape): owns `owasp-java-html-sanitizer` + `jsoup` as its own `pom.xml`
  dependencies. One class, e.g. `HtmlSanitizer`, exposing the sanitize+visible-text-length-validate
  behavior both services currently duplicate (constructor or static factory takes the max-length
  value, since that differs per caller — `AdvertisementSaveDto.DESCRIPTION_MAX_LENGTH` vs.
  `ProviderProfileSaveDto.ABOUT_MAX_LENGTH`).
- **Admission criterion for this module, deliberately narrow (agreed 2026-08-29) — do not treat as
  a general-purpose utils dumping ground:** a utility belongs here only when (a) genuinely needed
  by ≥2 starters, not "might be useful later", and (b) it doesn't fit `platform-commons` specifically
  because of an external-dependency concern `platform-commons`'s own governance rule would reject —
  not merely "avoids duplication" as a standalone reason. Checked against every other starter
  (2026-08-29, direct grep, not assumed): no other genuine duplicate-implementation candidate exists
  today (`TaxonService.validateTranslations()`/`AttachmentVideoUtil.validateEmbedUrl()` matched a
  generic "validate*" grep but are unrelated, domain-specific logic, not duplicated code) — this
  module starts with exactly one class, not pre-populated speculatively.
- `advertisement-spring-boot-starter`/`provider-profile-spring-boot-starter`'s `pom.xml` each gain
  a `<dependency>` on `html-sanitizer-lib`; `AdvertisementService`/`ProviderProfileService` delete
  their own `HTML_SANITIZER`/`sanitizeHtml()`/`validate*Length()` and call the shared class instead.

**Finding 2 — stale-id fix, re-evaluated against the real, now-built orchestrator layer
(2026-08-29).** Originally planned as a guard inside each starter's own `buildEntity()`; re-checked
against the real `AdvertisementSaveService`/`ProviderProfileSaveService` (both now exist, confirmed
identical shape) and moved one layer up. Both orchestrator `SaveService`s already read a `before`
snapshot (`buildCurrentSnapshot(dto.id())`) ahead of calling the port's `save()`, purely to build
the audit diff — and already detect this exact race (`before == null` for a non-new `dto`), but
today only log a warning ("...updated but no 'before' snapshot was available (concurrent delete?)
- skipping audit capture") and proceed to call `save()` anyway, hitting the same
silent-insert-shaped-fallback bug the original plan described. Since the state needed is already
computed there, the fix is smaller than originally planned and needs no `buildEntity()` change in
either starter:
- `AdvertisementSaveService.save()`: right after computing `before` (and before calling
  `advertisementPortFactory.get().save(...)`), add
  `if (!isNew && before == null) throw new OptimisticLockingFailureException("Advertisement " + dto.id() + " was deleted before this edit could be saved");`
  — new import `org.springframework.dao.OptimisticLockingFailureException`.
- `ProviderProfileSaveService.save()`: same guard in the same position, message
  `"Provider profile " + dto.id() + " was deleted before this edit could be saved"`.
- Verified this exception type is already the correct, proven shape: `AdvertisementRepository`/
  `ProviderProfileRepository`'s own version-conflict checks already throw it, and
  `marketplace-app`'s `AbstractEntityOverlay.handleSave()` already has a dedicated
  `catch (OptimisticLockingFailureException e)` block showing `saveConfig().conflict()` — correct
  UI handling for free, no UI-layer change needed.

<details>
<summary>Superseded reasoning (2026-08-28) — finding 1's original platform-commons rejection, kept for the record</summary>

The originally-planned `HtmlSanitizerUtil` in `platform-commons` (`core.util`) was checked against
`platform-commons`'s own "What belongs here" list before implementing — **NOT ALLOWED** explicitly
names *"Feature helpers or generic utils (`DateUtils`, `StringUtils`, `JsonUtils`, etc.)"*. A
generic `sanitize(html, maxVisibleLength)` utility is exactly that shape. The `YoutubeUtil`
precedent the original plan cited doesn't actually support it: `YoutubeUtil` has **zero external
dependencies** (pure `java.util.regex`), while `HtmlSanitizerUtil` would pull two external
libraries (`owasp-java-html-sanitizer`, `jsoup`) onto `platform-commons`'s classpath — and
therefore onto **every** module in the reactor transitively, not just the two that actually need
HTML sanitization. This reasoning is still correct for `platform-commons` specifically — it's why
the fix moved to a *new*, dedicated module instead, not back into `platform-commons`.

</details>

## Testing strategy

`bash scripts/build-and-test.sh --unit --integration` green for
`advertisement-spring-boot-starter`, `provider-profile-spring-boot-starter`, and
`marketplace-orchestrator`. New unit tests for `html-sanitizer-lib`'s own sanitize+length-validate
behavior (mirrors what `AdvertisementServiceHtmlSanitizationTest`/`ProviderProfileServiceTest`
already verify — those two existing tests should keep passing unchanged once their services
delegate to the shared class, confirming behavior didn't change). New unit test per orchestrator
`SaveService` for the stale-id-during-delete path: call `save()` with a non-null `dto.id()` against
a port mock returning `Optional.empty()` from `findById`, assert
`OptimisticLockingFailureException` is thrown and the port's own `save()` is never called
(`verify(..., never()).save(...)`).

## Related

- `improvement-124` — originally tracked this as Batch 124-B2; carved out into this standalone
  issue 2026-08-28 per explicit user request, so it can rank independently at the top of the
  backlog instead of waiting behind Batch B2's position in that issue's own sequence. Gates that
  issue's Batch 124-C.
