# improvement-009: Header "Signed in as" email has no width limit, breaks layout on long values

**Type:** improvement — found during general app walkthrough (Playwright `--ux` review)
**Module:** marketplace-app
**Priority:** low — cosmetic, edge case (very long email), no functional breakage
**When:** Week 0 — quick-wins batch
**Status:** ✅ RESOLVED (2026-07-04) — max-width + ellipsis added to `.header-auth-row span`
(commit 0f02b91d). `title` attribute for full email on hover was not added — optional,
left for a future pass.

## Problem

`HeaderBar.java:71-74` renders the current user's email in a plain `Span` with no width
constraint:

```java
Span userInfo = new Span();
...
userInfo.setText(i18n.get(HEADER_SIGNED_IN, currentUser.email()));
```

The matching CSS rule, `.header-auth-row span` (`header-bar.css:56-60`), only sets font size
and color:

```css
.header-auth-row span {
  font-size: 0.78rem;
  color: #94a3b8;
  letter-spacing: 0.01em;
}
```

No `max-width`, `white-space: nowrap`, or `text-overflow: ellipsis`. Confirmed visually on the
max-content boundary Playwright scenario (`max-boundary-en-...@max-domain-seg1-...` — a ~250
char email): the text wraps onto 3 lines and pushes the entire header row (locale switcher,
Settings, Log Out buttons) down, breaking the header layout across the whole app for that
session.

For contrast, the breadcrumb elsewhere in the app (e.g. `Users › MaxBoundaryUserName...`)
already truncates long values correctly with an ellipsis — the same pattern is simply missing
here.

## Suggested fix

Add a width cap + ellipsis to `.header-auth-row span` in `header-bar.css`:

```css
.header-auth-row span {
  font-size: 0.78rem;
  color: #94a3b8;
  letter-spacing: 0.01em;
  max-width: 260px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
```

Optionally set the full email as a `title` attribute on the `Span` in `HeaderBar.java` so it's
still available on hover.
