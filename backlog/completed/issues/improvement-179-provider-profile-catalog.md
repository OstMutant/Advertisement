# improvement-179: Providers catalog, OG/sitemap, deep link (public-facing)

**Type:** feature
**Module:** `marketplace-app` (new `ui/views/main/tabs/providers/`, `config/seo/OgMetaRequestListener.java`,
  `rest/SitemapController.java`, `ui/views/services/AppLinkService.java`,
  `ui/views/main/header/account/ProviderProfileViewModeHandler.java`), `playwright/e2e/`
**Priority:** 🟡 high — carves out the last remaining batch of `improvement-124` (F-04), which has
  been the top-priority backlog line since 2026-07-27
**When:** independent, no blockers — `improvement-178` (Batch C: `AccountOverlay`,
  `ProviderProfileSaveService`, permission model) is already closed

## Current state

`provider-profile-spring-boot-starter` and `ProviderProfilePort` exist and are fully functional
(create/edit/delete a provider profile, `kind` NOT NULL so every row already qualifies as public —
no `kind IS NULL` placeholder rows), but nothing surfaces a provider profile outside the owner's
own "My Account" overlay:

- No public listing exists — `ui/views/main/tabs/providers/` does not exist.
- `OgMetaRequestListener` only recognizes `^/ads/(\d+)$`; a provider profile URL has no OG/Twitter
  meta tags or JSON-LD when shared.
- `SitemapController` only emits advertisement URLs; provider profiles are invisible to search
  engines.
- `AppLinkService` only has `advertisementUrl(Long adId)`; there is no way to construct a public
  provider-profile URL anywhere in the codebase.
- No deep-link route exists for a provider profile (`AdvertisementDeepLinkView` has no counterpart).
- `ProviderProfileViewModeHandler` has an Edit button but no Delete button — `ProviderProfilePort
  .delete()`/`ProviderProfileSaveService.delete()` are fully wired end-to-end on the backend
  (confirmed by reading both) but nothing in the UI calls them.
- `04-provider-profile-flow.spec.js` already covers create/edit/view (self-service and
  admin-on-behalf) for a provider profile inside the account overlay — it does not cover delete,
  `SUPPORT`-kind rejection for non-privileged actors, or anything about the public catalog.
- `ProviderProfileDto` has no media field and the provider-profile edit form has no
  `AttachmentGalleryService` wiring — portfolio photos do not exist at the data level yet.

## Why change

A provider profile that only the owner can see has no marketplace value — the whole point of
`MASTER`/`SHOP`/`SUPPORT` profiles is that customers can find and evaluate providers. Today there
is no discovery path at all: no listing page, no shareable link with a rich preview, no search
engine visibility, and no way for a provider to remove their own listing once published.

## Expected benefit

- Visitors (including unauthenticated ones) can browse and filter providers by kind/category/city,
  mirroring the existing Advertisements catalog experience.
- A provider profile link shared in chat apps renders a rich preview (title/description/JSON-LD),
  matching what advertisements already get.
- Provider profiles are indexed by search engines via the sitemap.
- A provider can delete their own listing (or an admin/moderator can, on behalf of another user),
  closing the last asymmetry between "create" and "remove" for this entity.

## Approach

Mirror the existing Advertisement domain's public-facing pieces field-for-field, per this
codebase's established pattern-first convention (F-03 did the same for `AdKind`):

1. **Providers tab** (`ui/views/main/tabs/providers/`) — `ProvidersView` mirrors
   `AdvertisementsView`, `ProviderProfileCardView` mirrors `AdvertisementCardView` but stays
   text-only (kind badge, `about` excerpt, city chip, category chips — no photo, since no media
   field exists on `ProviderProfileDto`), query-layer trio (`ProviderProfileQueryBlock`/
   `ProviderProfileFilterMeta`/`ProviderProfileSortMeta`) mirrors the Advertisement trio using the
   already-existing `ProviderProfileFilterDto` (`kinds`, `categoryIds`, `cityTaxonId`) and
   `ProviderProfilePort.getFiltered()`/`count()` — no port changes needed, both already exist.
   **View-only, no edit mode**: unlike `AdvertisementOverlay` (whose `OverlaySession` has both VIEW
   and EDIT modes, since browsing and owner-editing share the same overlay), the catalog's own
   `ProviderProfileOverlay` never enters an EDIT mode — editing a provider profile already has its
   own dedicated, self-service path (`AccountOverlay`'s Provider Profile tab, shipped in
   `improvement-178`), so the catalog overlay has no Edit button and no `ProviderProfileFormOverlayModeHandler`
   counterpart. This also means no history/activity button here: confirmed by reading both
   `AdvertisementViewOverlayModeHandler` (Edit/Share/Close buttons only, no history) and
   `AdvertisementFormOverlayModeHandler` (the only one of the two that wires `EntityActivityOverlay`)
   — history has only ever been a form/edit-mode concern in this codebase, never a view-mode one, so
   a view-only overlay correctly has nothing to attach it to.
2. **`OgMetaRequestListener`** — add `PROVIDER_PATH = Pattern.compile("^/providers/(\\d+)$")`
   alongside the existing `AD_PATH`, inject `ComponentFactory<ProviderProfilePort>`, reuse the
   existing `og:title`/`og:description`/`og:url`/JSON-LD shape (`og:type` = `"person"` or
   `"organization"` rather than `"product"` — decide based on `kind`). Skip injection when no
   profile is found for the id.
3. **`SitemapController`** — add a parallel `allProviders()` stream + `.forEach()` block next to
   the existing `allAdvertisements()` one, using the same XML-building pattern.
4. **`AppLinkService`** — add `providerProfileUrl(@NonNull Long id)` mirroring `advertisementUrl()`.
5. **`ProviderProfileDeepLinkView`** — mirrors `AdvertisementDeepLinkView` (`@Route("providers")`,
   `HasUrlParameter<Long>`, forward-to-root + session-attribute pending-deep-link pattern).
   Optional slug in the URL (`/providers/42-ivan-plytochnyk`), ignored on lookup.
6. **Delete button** — add to `ProviderProfileViewModeHandler` next to the existing Edit button,
   visible under the same permission check as Edit, calling the already-wired
   `ProviderProfileSaveService.delete()` through a confirmation dialog (mirror whatever confirm-
   dialog pattern the Advertisement delete flow already uses).
7. **Extend `04-provider-profile-flow.spec.js`** — no new spec file; the advertisement domain's own
   deep-link/share/sitemap test lives inline inside `05-marketplace-advertisement-flow.spec.js`
   alongside its create/edit/restore tests, not in a separate file, so the provider domain follows
   the same convention. Add new tests to `04` covering what it does not already exercise: public
   catalog listing/filtering, deep-link navigation to a provider profile via URL (+ sitemap.xml
   listing it, mirroring the advertisement deep-link test's own assertions), delete flow, and
   `SUPPORT`-kind rejection for a non-privileged actor. Audit new screenshot names against every
   existing spec first to avoid collisions.

**Gate → Done:** `bash scripts/playwright.sh e2e --ux` green (no `--full` needed — this feature
doesn't touch spec `06`'s seeded-pagination scenario). `marketplace-app/DECISIONS.md` updated via
`/record-decision` (public catalog + OG/sitemap/deep-link pattern applied to a second domain,
`og:type` choice for a person/organization vs. a product). Issue moved to `completed/issues/`,
`BACKLOG.md` row removed, `BACKLOG-ARCHIVE.md` entry added — this also closes `improvement-124`
itself, since this is its last open batch.

## Related

- `improvement-124-provider-profile.md` — parent issue; this is its Batch 124-D, carved out per
  the same pattern as `improvement-175`/`improvement-178` for Batches B2/C.
- See `.claude/nav/adr-index.md` for the ADR that originally established the OG/sitemap/deep-link
  pattern this issue mirrors (advertisement domain).

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a (sonar-analyst Agent call token/tool-use/duration not captured in this transcript segment)
- review_signal_ratio: n/a — no /code-review ran this task
- context_loading_task_type: bug-fix/verification continuation of an in-progress autopilot feature batch
- context_loading_consulted: yes
- context_loading_matched: yes
- flows_situation: continuing an interrupted /autopilot run — verify Playwright, fix real bugs found, close issue lifecycle
- flows_chosen: direct script invocation (deploy-and-run.sh, playwright/run.sh, ci.sh --sonar) per this session's own established pattern, matching flows.md's mechanism table
- flows_matched: yes

### Agent calls
- Sonar quality-gate findings query | subagent_type=sonar-analyst | tokens=n/a | tool_uses=n/a | duration_s=n/a | mode=foreground | batch=solo

### Script/command runs
- scripts/deploy-and-run.sh --reset | duration_s=~200 | mode=background | result=pass
- playwright/run.sh e2e --ux (post read-only-order fix) | duration_s=~372 | mode=background | result=fail (1/45, SUPPORT-kind item-list assertion)
- scripts/deploy-and-run.sh --reset | duration_s=~113 | mode=background | result=pass
- playwright/run.sh e2e --ux (post SUPPORT-item-enabled fix) | duration_s=~370 | mode=background | result=pass (45/45, 0 failed)
- scripts/ci.sh --sonar --no-docs --no-archunit-metrics (1st) | duration_s=~200 | mode=background | result=fail (html-sanitizer-lib module missing from scanner copy)
- scripts/ci.sh --sonar --no-docs --no-archunit-metrics (2nd, post scripts fix) | duration_s=~220 | mode=background | result=fail (real 3-issue quality gate)
- scripts/deploy-and-run.sh --reset-only-db | duration_s=~95 | mode=foreground | result=pass
- playwright/run.sh e2e --ux (post category-filter/sort test additions) | duration_s=~354 | mode=background | result=fail (sort-order assertion wrong about default state)
- scripts/deploy-and-run.sh --reset-only-db | duration_s=~116 | mode=foreground | result=pass
- playwright/run.sh e2e --ux (post sort-icon-state assertion fix) | duration_s=~348 | mode=background | result=pass (45/45, 0 failed)
- scripts/ci.sh --sonar --no-docs --no-archunit-metrics (3rd, post S7467/S8491/S1192 fixes) | duration_s=~600 | mode=background | result=fail (new_violations: 0, only new_coverage gap remains -- pre-existing, tracked separately as improvement-114)
- docs/architecture/scripts/generate-architecture-model.sh | duration_s=~90 | mode=background | result=pass (first check was premature -- script was still writing; confirmed complete via a proper wait-loop the second time)
- .claude/nav/scripts/generate-adr-index.sh | duration_s=<5 | mode=foreground | result=pass
