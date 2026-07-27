# improvement-118: F-01 — verify real-world Open Graph preview rendering in Facebook/Telegram

**Type:** verification — manual, inherently not automatable
**Module:** `marketplace-app` (`config/seo/OgMetaRequestListener.java`), none — no code change
expected unless the check fails
**Priority:** high — this is the "day-1 binary gate" the F-01 spec itself calls non-negotiable;
until it passes, the whole OG feature (improvement-117) is unconfirmed in the one environment
that actually matters
**When:** independent, no blockers — needs only a publicly reachable deployment

## Problem

[improvement-117](../completed/issues/improvement-117-f01-deep-links-og-tags.md) implemented and
automated-tested every technical part of deep links + Open Graph meta tags (`og:*`/`twitter:card`
attributes, `og:image` cache-busting, JSON-LD, `sitemap.xml`, History API sync) — all verified via
`curl`-based crawler simulation and Playwright. What **cannot** be verified from this sandbox:
whether Facebook's and Telegram's own crawlers actually render an attractive rich-preview card
from a real `/ads/:id` URL. This sandbox has no public URL — Facebook/Telegram bots cannot reach
`localhost`.

`private/features/F-01-deep-links-og-tags.md`'s own spec (gitignored, not in version control)
frames this explicitly as a **day-1 binary gate**: prototype the listener, share one URL into a
real Facebook post and a real Telegram chat on day 1, before building anything further on top.
That gate was effectively satisfied out of order (the full feature was built first, since this
sandbox couldn't do the real check at all) — this issue is the deferred gate check.

## Suggested fix

1. Deploy to a real, publicly reachable environment (e.g. the intended Render deployment) with
   `APP_PUBLIC_URL` set to the real domain.
2. Pick any existing advertisement, copy its `/ads/:id` link (via the "Share" button, or directly).
3. Paste the link into a real Facebook post (or use the
   [Facebook Sharing Debugger](https://developers.facebook.com/tools/debug/) against the URL) —
   confirm title, description, and image render correctly.
4. Paste the same link into a real Telegram chat — confirm the preview card renders correctly.
5. **Binary outcome, per the spec's own decision rule:**
   - Both render correctly → done, close this issue, no further action.
   - Either renders wrong → do not debug the `IndexHtmlRequestListener` approach further; pivot
     immediately to a crawler-only `@RestController` (user-agent-based routing, serving static
     HTML with the same meta tags only to known bot user agents) — the spec estimates this
     fallback at ~100 lines, and `SecurityConfig` already has the `HealthController`/
     `SitemapController` precedent for adding its own explicit permit entry.

## Verification plan

- Facebook Sharing Debugger shows the correct title/description/image for a real `/ads/:id` URL.
- A real Telegram chat shows a correctly-rendered link preview for the same URL.
- If either fails: the `@RestController` fallback is implemented and re-verified the same way.

## Related

- `backlog/completed/issues/improvement-117-f01-deep-links-og-tags.md` — the implementation this
  verifies; see `marketplace-app/DECISIONS.md` ADR-059 for the `IndexHtmlRequestListener` design
  and its documented fallback plan.
- `private/features/F-01-deep-links-og-tags.md` — original spec (gitignored), source of the
  day-1 binary gate rule.
