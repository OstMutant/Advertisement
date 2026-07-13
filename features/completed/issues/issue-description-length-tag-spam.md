# Issue: Description length validator is vulnerable to HTML tag spam

**When:** Wave 2 — before public traffic (raised to medium; raw-size cap is step 1)
**Status:** ✅ RESOLVED (2026-07-04) — all three layers implemented: `@Size(max =
DESCRIPTION_RAW_MAX_LENGTH = 20_000)` on `AdvertisementSaveDto.description`
(platform-commons); Jsoup-based `Jsoup.parse(html).text().length()` check replacing the
regex in `AdvertisementFormOverlayModeHandler`'s binder validator (marketplace-app); Jsoup-based
`validateDescriptionLength()` guard in `AdvertisementService.sanitizeHtml()`
(advertisement-spring-boot-starter), throwing on overflow — surfaced to the user via the
existing generic `catch (Exception e)` in `AbstractEntityOverlay.handleSave()`, no new UI
plumbing needed. `jsoup:1.22.1` added to both modules via a new `jsoup.version` root pom
property. Full e2e suite green (46/46) after the fix. Unblocks `improvement-006`.

## Problem

The current binder validator for the advertisement description field strips HTML tags and
`&nbsp;` entities before measuring content length:

```java
String text = html.replaceAll("<[^>]+>", "").replace("&nbsp;", " ").trim();
return text.length() <= AdvertisementSaveDto.DESCRIPTION_MAX_LENGTH;
```

This means a payload of `<b></b>` repeated thousands of times passes the 2000-char content
check (stripped text is empty) but the raw HTML stored in the DB would be enormous.

## Background

The original `StringLengthValidator(1, 2000)` checked raw HTML length, which broke when
Quill 2.x started encoding every space as `&nbsp;`. For 2000 chars of plain text with ~14%
spaces, Quill produces ~3360 chars of HTML — exceeding the 2000-char limit. The regex
strip was introduced as a workaround to make the validator meaningful for users.

`@Size` on a raw String field measures the HTML string length, not the visible text length,
so it cannot enforce a meaningful text-length constraint on Quill content regardless of the
value chosen. For this reason, `AdvertisementSaveDto.description` carries only `@NotBlank`
at the service layer — the OWASP HTML sanitizer in `AdvertisementService` is the actual
defence against malicious or oversized payloads before persistence.

## DoS angle (added 2026-07-04, external audit round 5)

The abuse is worse than a validation bypass: `<b></b>` is a FORMATTING tag, and the OWASP
policy (`Sanitizers.FORMATTING.and(LINKS).and(BLOCKS)`) **preserves** formatting tags — so
100 KB of empty `<b></b>` pairs survives sanitization and is stored as-is (the column is
`TEXT` with no DB limit — see improvement-006). Repeated large payloads load the sanitizer
CPU-wise and bloat the table/audit snapshots. The earlier "sanitizer strips it anyway"
impact note below was wrong for formatting tags.

## Proper fix (deferred)

Three-layer protection:
1. **Raw-size cap (cheapest, do first)** — reject raw HTML above a hard byte limit
   (10-20 KB) before any parsing, in the binder AND as a service-level guard on
   `AdvertisementSaveDto`. Legitimate 2000-char rich text never approaches this.
2. **Content limit (UI binder)** — replace the `replaceAll` regex with Jsoup (already on
   classpath via OWASP sanitizer): `Jsoup.parse(html).text().length() <= 2000`. Jsoup
   correctly handles `&nbsp;` and returns 0 for empty tags like `<b></b>`.
3. **Payload abuse limit (AdvertisementSaveDto)** — add a custom `@Constraint` or a
   service-level guard that extracts text (via Jsoup) and validates length, rather than
   using `@Size` on the raw HTML string.

## Impact

Medium (raised from low, 2026-07-04): the sanitizer does NOT strip empty formatting tags,
so the payload reaches the DB. Must land before public traffic (product roadmap Phase 1).
