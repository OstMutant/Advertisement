# Issue: Description length validator is vulnerable to HTML tag spam

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

## Proper fix (deferred)

Two-layer protection:
1. **Content limit (UI binder)** — replace the `replaceAll` regex with Jsoup (already on
   classpath via OWASP sanitizer): `Jsoup.parse(html).text().length() <= 2000`. Jsoup
   correctly handles `&nbsp;` and returns 0 for empty tags like `<b></b>`.
2. **Payload abuse limit (AdvertisementSaveDto)** — add a custom `@Constraint` or a
   service-level guard that extracts text (via Jsoup) and validates length, rather than
   using `@Size` on the raw HTML string.

## Impact

Low priority — the OWASP HTML sanitizer in `AdvertisementService` already processes all
incoming HTML before persistence, limiting what can actually be stored. Tag spam would be
stripped to empty content by the sanitizer anyway.
