# improvement-005: Rich text description — grid excerpt stripping + sanitizer allowlist gaps

**Type:** improvement — follow-up from rich-text-description feature
**Module:** marketplace-app + advertisement-spring-boot-starter
**Priority:** medium

## Problem

### A — Card excerpt leaks raw HTML
`AdvertisementCardView.createDescription()` (marketplace-app, lines 161-173) sets `innerHTML`
directly from the sanitized description and only visually truncates via CSS class
`advertisement-description--truncated`. Spec required a plain-text excerpt (tags stripped,
e.g. `Jsoup.clean(html, Safelist.none())`). Currently HTML tags/links render inside the card.

### B — Sanitizer allowlist diverges from spec
`AdvertisementService.HTML_SANITIZER = Sanitizers.FORMATTING.and(LINKS).and(BLOCKS)`:
- `<pre>` is missing from the allowlist (spec requires it) — tag is stripped on save, content survives without formatting.
- `LINKS` allows `mailto:` in addition to http/https (spec restricts to http/https only).
- Broader than spec in several extra tags (`font, o, sup, sub, ins, del, strike, tt, big, small, span, div, h4-h6`) — not necessarily harmful, but undocumented divergence.

## Suggested fix
- Add explicit tag-stripping (Jsoup `Safelist.none()`) for the card excerpt.
- Extend/adjust the OWASP policy to include `<pre>` and restrict `LINKS` to http/https.
