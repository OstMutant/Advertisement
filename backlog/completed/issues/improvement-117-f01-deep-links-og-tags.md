# improvement-117: F-01 — deep links + Open Graph meta tags (community-migration mechanic)

**Type:** feature — product roadmap Phase 1, item #1 (non-negotiable first per `private/roadmap.md`)
**Module:** `marketplace-app` (new `@Route`/`IndexHtmlRequestListener`/sitemap servlet), possibly
`platform-commons` if a shared `Class<T> targetClass`-style contract is needed for OG data lookup
(none expected — this is UI-layer only).
**Priority:** high — the sole hard-gate feature blocking product Phase 1 (community migration);
both of its former blockers are now resolved.
**When:** independent, no blockers — both dependencies below are already resolved.
**Status:** ✅ RESOLVED (2026-07-24) — all technical items implemented and verified; the one
inherently manual, non-automatable item (real-world Facebook/Telegram preview check) carved out
into [improvement-118](../../issues/improvement-118-f01-real-world-og-preview-verification.md).

## Problem

The platform's real audience is an existing 24k-member Facebook community
(`private/product-vision.md`). Every advertisement needs a stable, shareable URL that renders a
rich preview card (image, title, description) when posted into Facebook/Telegram/Viber — without
this, no other feature is visible to that audience. This is the single highest-leverage,
first-required feature per `private/roadmap.md` Phase 1.

Full spec: `private/features/F-01-deep-links-og-tags.md` (not committed — gitignored product
planning doc; this issue is the trackable, backlog-visible counterpart).

**Dependency check against the current codebase (verified, not assumed):**
- `improvement-005` (plain-text excerpt for `og:description`) — ✅ already resolved
  (`completed/issues/improvement-005-rich-text-excerpt-and-sanitizer-gaps.md`).
- `improvement-020` (security baseline hard gate) — ✅ resolved, but **differently than F-01's
  spec assumes**: deny-by-default (`anyRequest().denyAll()`) was tried and reverted after it broke
  Vaadin's own root route rendering (`marketplace-app/DECISIONS.md` ADR-025). The accepted,
  current posture is `anyRequest().permitAll()` with an established process rule instead: **every
  new non-Vaadin REST controller must add its own explicit `requestMatchers(...)` rule ahead of
  the catch-all**, following the existing `HealthController` precedent (see
  `marketplace-app/CLAUDE.md` "Security: @PreAuthorize and Vaadin"). This feature's new sitemap/OG
  paths must follow that same precedent — not attempt deny-by-default again.
- Thumbnails on upload — **not a hard blocker**: F-01's own spec explicitly allows shipping
  `og:image` against the full-size original first, swapping to a thumbnail later when that work
  lands (still untracked as its own issue — only referenced inside
  `improvement-017-sync-s3-upload-in-request-thread.md`'s deferred step 2).

**Conclusion: no remaining hard blockers.** This is ready to implement.

## Suggested fix (from the feature spec, condensed)

1. `@Route("ads/:id")` — URL-parameterized route opening the existing view overlay on navigation;
   browser Back closes it; History API sync.
2. `IndexHtmlRequestListener` inspecting the request path, injecting `og:title`, `og:description`
   (reusing the improvement-005 plain-text excerpt), `og:image` (full-size initially),  `og:url`,
   `twitter:card`, and JSON-LD (`Product`/`LocalBusiness`) — this is the standard, documented
   Vaadin approach (crawlers don't execute the SPA; meta must be server-side injected per request).
   Add its own explicit `requestMatchers` permit entry per the security precedent above.
3. Cache the per-id OG data lookup (Caffeine, ~5 min) — crawlers hit in bursts on first share.
4. "Share" button on card + view overlay (copy link + native share API on mobile).
5. `sitemap.xml` servlet (simple, lists ad URLs) — also needs its own explicit permit entry.
6. Preview-cache-busting: version the `og:image` URL with `?v=<updatedAt>` so FB/Telegram's
   per-URL preview cache doesn't serve a stale image after an ad edit.
7. **Day-1 binary gate** (already decided, see feature spec): prototype the listener, share one
   URL into a real FB post and a real Telegram chat. If either preview renders wrong, switch
   immediately to a crawler-only `@RestController` (user-agent routing) — no further debate; this
   fallback costs ~100 lines and already has its own explicit permit-list entry either way.

## Progress — all technical items done (2026-07-24)

Implemented and verified in four passes, each with its own `marketplace-app/DECISIONS.md` ADR:

1. **Step 1 (day-1 prototype gate) — ADR-059.** `AdvertisementDeepLinkView` (`@Route("ads")`,
   `HasUrlParameter<Long>`) stores the requested id in `VaadinSession` and forwards to root;
   `AdvertisementsView.openPendingDeepLinkIfAny()` (called from `MainView.init()`) opens the
   existing overlay for it. `OgMetaRequestListener` (`config/seo/`) injects `og:*` meta tags into
   the server-rendered `index.html` for `/ads/:id` requests (Caffeine-cached 5 min).
   `HtmlExcerptUtil` extracted from `AdvertisementCardView`'s inline Jsoup call. No
   `SecurityConfig` change needed — `/ads/**` was already covered by `anyRequest().permitAll()`,
   and this introduces no new REST endpoint.
2. **"Share" button — ADR-060.** `AppLinkService` (builds the absolute `/ads/:id` URL) +
   `ShareUtil` (native Web Share API on mobile, clipboard-copy fallback on desktop) +
   `ShareActionButton`, wired into both `AdvertisementCardView` and
   `AdvertisementViewOverlayModeHandler`.
3. **`sitemap.xml` — ADR-061.** `SitemapController` pages through the existing
   `AdvertisementPort.getFiltered()` (no new port method), Caffeine-cached 15 min; genuine new
   REST endpoint, so it *does* get its own `SecurityConfig` permit entry. Found and fixed a real,
   unrelated bug while verifying: `deploy.sh` never set `APP_PUBLIC_URL`, so locally-generated
   links pointed at the container's internal port (8080) instead of the externally-reachable one
   (8081) `deploy.sh` itself publishes to.
4. **`twitter:card` fix, `og:image` cache-busting, JSON-LD, History API sync — ADR-062.** Found a
   real bug myself (no manual check needed) by crawler-simulating `GET /ads/:id` via `curl`:
   `twitter:card` was emitted with the wrong HTML attribute (`property=` instead of the
   Twitter-required `name=`). Fixed alongside the three remaining spec items: `og:image` now
   carries a `?v=<updatedAt-epoch>` cache-busting query param; a `Product` JSON-LD block is
   injected; `AdvertisementOverlay` now syncs browser history (`pushState` on open/close,
   `History.setHistoryStateChangeHandler` for Back/Forward) — verified with a real
   `page.goBack()` in Playwright, not just a same-page assertion. Known limitation recorded in
   ADR-062: Vaadin's `History` API is a single-handler slot per `UI`, so a *second* domain adding
   its own deep-link route later cannot just copy this pattern independently.

**Verification, cumulative:** every item above lands in the same Playwright test
(`04-marketplace-advertisement-flow.spec.js`, "userEn opens a deep link..."), extended test-step
by test-step across all four passes. Full e2e suite 50/50 after each pass, unit-tests 73/73.

**The one item that was never automatable** — sharing a real `/ads/:id` link into an actual
Facebook post and a real Telegram chat, confirming the rich preview renders correctly — is carved
out into [improvement-118](../../issues/improvement-118-f01-real-world-og-preview-verification.md),
since it needs a public URL this sandbox doesn't have and isn't blocked on anything else in this
issue.

## Verification plan

- Playwright: new spec covering the deep-link route (direct navigation to `ads/:id` opens the
  correct overlay, browser Back closes it).
- Manual (not automatable): share a real deep link into an actual FB post and Telegram chat,
  confirm the rich preview renders per the day-1 gate above.
- `sitemap.xml` returns valid XML listing current ad URLs.

## Related

- `private/roadmap.md`, `private/product-vision.md`, `private/features/F-01-deep-links-og-tags.md`
  — full product context (gitignored, not in version control).
- `backlog/completed/issues/improvement-005-rich-text-excerpt-and-sanitizer-gaps.md` — resolved
  dependency (og:description source).
- `backlog/completed/issues/improvement-020-security-baseline-before-public-endpoints.md` — resolved
  dependency; see its ADR-025 cross-reference for why deny-by-default is not the path here.
- `backlog/issues/improvement-017-sync-s3-upload-in-request-thread.md` — soft dependency
  (thumbnails), not blocking.
- `backlog/issues/improvement-118-f01-real-world-og-preview-verification.md` — the one item
  carved out of this issue (manual real-world Facebook/Telegram preview check).
