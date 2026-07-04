# improvement-005: Rich text description — grid excerpt stripping + sanitizer allowlist gaps

**Type:** improvement — follow-up from rich-text-description feature
**Module:** marketplace-app + advertisement-spring-boot-starter
**Priority:** medium
**When:** Wave 1, first item — plain-text excerpt is a prerequisite for shareable-link previews AND fixes the card HTML leak
**Status:** ✅ RESOLVED (2026-07-04) — both parts implemented exactly per Suggested fix below:
`AdvertisementCardView.createDescription()` now renders `Jsoup.parse(html).text()` via
`setText(...)`; `AdvertisementService.HTML_SANITIZER` gained the additive `<pre>` policy;
`mailto:` links and extra tags kept as documented, accepted divergence. Playwright's
`assertAllRichTags` assertion on the card (which expected rich HTML tags there) was updated to
assert plain text instead — see `e2e/_flows/advertisement.flow.js` "card reflects saved
state" step. Full e2e suite 46/46 green after the test update.

## Problem

### A — Card excerpt leaks raw HTML

Verified current code, `AdvertisementCardView.java:161-173`:

```java
private Div createDescription(AdvertisementInfoDto ad) {
    String html = ad.getDescription() != null ? ad.getDescription().replace("&nbsp;", " ") : "";
    if (html.isBlank()) return new Div();

    Div description = new Div();
    description.addClassName("advertisement-description");
    description.addClassName("advertisement-description--truncated");
    description.getElement().setProperty("innerHTML", html);   // <-- raw sanitized HTML, not text
    ...
}
```

`html` is set as `innerHTML` verbatim and only visually clamped via the CSS class
`advertisement-description--truncated` (3-line `-webkit-line-clamp`, see
`advertisement-card.css:111-116`). Spec required a plain-text excerpt (tags stripped). Two
concrete symptoms of the current behavior:
- Formatting tags (bold/italic/underline/lists/headers/blockquotes) render styled inside the
  card, not as plain text — confirmed visually on e2e screenshot
  `UK Оголошення Адмін` (spec 04 rich-format edit run).
- Links (`<a href>`) render clickable inside the card excerpt — likely unintended for a list
  preview.

**Bonus dependency now in place:** `org.jsoup:jsoup:${jsoup.version}` was added to
`marketplace-app/pom.xml` on 2026-07-04 (for the description length validator,
`AdvertisementFormOverlayModeHandler`, see ADR-024) — the fix below no longer needs a new
dependency, Jsoup is already on this module's classpath.

**Why this blocks F-01:** the deep-links/OG-tags feature (`private/features/F-01`) needs a
plain-text `og:description` for messenger link previews. This same excerpt function is the
natural source for that string — fix it once, reuse in both places.

### B — Sanitizer allowlist diverges from spec

Verified current code, `AdvertisementService.java:42-44`:

```java
private static final PolicyFactory HTML_SANITIZER = Sanitizers.FORMATTING
        .and(Sanitizers.LINKS)
        .and(Sanitizers.BLOCKS);
```

- `<pre>` is missing from the allowlist (spec requires it) — the tag is stripped on save,
  content survives but loses monospace/whitespace-preserving formatting.
- `Sanitizers.LINKS` allows `mailto:` in addition to `http`/`https` (spec restricts to
  http/https only) — OWASP's built-in `LINKS` policy is not itself configurable per-scheme,
  so restricting this requires a custom `HtmlPolicyBuilder` rule rather than composing
  `Sanitizers.LINKS` as-is.
- Broader than spec in several extra tags allowed by `Sanitizers.FORMATTING`/`BLOCKS`
  (`font, o, sup, sub, ins, del, strike, tt, big, small, span, div, h4-h6`) — not necessarily
  harmful (still XSS-safe), but an undocumented divergence from the original spec's allowlist.

## Suggested fix

1. **Card excerpt (part A):** replace the raw `innerHTML` assignment with plain text —
   `description.setText(Jsoup.parse(html).text())` instead of
   `description.getElement().setProperty("innerHTML", html)`. No block-boundary separators
   (e.g. between list items/paragraphs) — deliberately kept simple for now; readability of
   flattened list-heavy descriptions can be revisited later if it proves to be a real problem
   in practice. Keep the existing CSS line-clamp for visual truncation on top of the plain text.

2. **Sanitizer allowlist (part B) — resolved 2026-07-04 by taking the maximal merge of
   spec ∪ current implementation, not by narrowing to the original spec:**
   - **Add** the missing `<pre>` tag — the only real gap. Cleanest as an additive policy
     rather than reconstructing `FORMATTING`/`BLOCKS`:
     ```java
     private static final PolicyFactory HTML_SANITIZER = Sanitizers.FORMATTING
             .and(Sanitizers.LINKS)
             .and(Sanitizers.BLOCKS)
             .and(new HtmlPolicyBuilder().allowElements("pre").toFactory());
     ```
   - **Keep** `mailto:` links and all the extra already-allowed tags (`font, o, sup, sub, ins,
     del, strike, tt, big, small, span, div, h4-h6`) as-is. Rationale: none of this is an XSS
     gap (OWASP's composed policies are safe by construction); restricting them now would be a
     pure behavior *removal* for existing/future content with no bug driving it, only a stale
     spec document. Explicitly documenting this as the accepted, intentional state — the
     original spec's narrower allowlist is superseded, not the implementation.
