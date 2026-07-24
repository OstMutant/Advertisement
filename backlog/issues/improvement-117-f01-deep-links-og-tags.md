# improvement-117: F-01 — deep links + Open Graph meta tags (community-migration mechanic)

**Type:** feature — product roadmap Phase 1, item #1 (non-negotiable first per `private/roadmap.md`)
**Module:** `marketplace-app` (new `@Route`/`IndexHtmlRequestListener`/sitemap servlet), possibly
`platform-commons` if a shared `Class<T> targetClass`-style contract is needed for OG data lookup
(none expected — this is UI-layer only).
**Priority:** high — the sole hard-gate feature blocking product Phase 1 (community migration);
both of its former blockers are now resolved.
**When:** independent, no blockers — both dependencies below are already resolved.

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

## Progress (2026-07-24)

**Step 1 (day-1 prototype gate) — done.** `AdvertisementDeepLinkView` (`@Route("ads")`,
`HasUrlParameter<Long>`) + `OgMetaRequestListener` (`config/seo/`) + `HtmlExcerptUtil` extraction —
see `marketplace-app/DECISIONS.md` ADR-059 for the full design. Verified: real-browser navigation
to `/ads/:id` opens the correct overlay (new Playwright test, full e2e suite 50/50). No
`SecurityConfig` change was actually needed (corrected from item 2/5 below — `/ads/**` is already
covered by the existing `anyRequest().permitAll()`, and this step introduces no new REST endpoint).

**Still open, in this order:**
- Manual verification (cannot be automated — needs a public URL): share a real `/ads/:id` link
  into an actual Facebook post and Telegram chat, confirm the rich preview renders correctly. Per
  the day-1 binary gate, a wrong preview means switching immediately to a crawler-only
  `@RestController` fallback instead of debugging further.
- Item 4: "Share" button on card + view overlay.
- Item 5: `sitemap.xml` servlet (will need its own explicit `requestMatchers` permit entry — this
  one *is* a genuine new REST endpoint, unlike step 1).
- Item 6: `og:image` cache-busting versioning (`?v=<updatedAt>`).
- Item 2's JSON-LD (`Product`/`LocalBusiness`) and browser History API sync (manual open →
  URL update) were not part of step 1 either — still open.

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
