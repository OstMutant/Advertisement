# Taxonomy — Specification

## Problem statement

Advertisements need **categories**: many-to-many tags that classify what an ad is about (Electronics, Real Estate, Services, etc.). Categories must be managed at runtime by privileged users (moderators and admins) — not hard-coded in source.

A category is `name + description`. Both are user-facing text and must be available in every supported locale of the application.

The same mechanism will be reused for other dynamic per-advertisement classifiers in the future (tags, statuses, condition labels, etc.) — so the underlying machinery must be generic from day one, not a single-purpose "categories table".

## Goals

- Advertisements carry zero-to-many categories. Categories can be attached and detached from the advertisement edit form.
- Categories live in a dedicated administration view (the **Reference Data** tab) accessible only to `ADMIN` and `MODERATOR` roles.
- Reference Data view supports full CRUD: create, edit, soft-delete, restore.
- Every category has translated `name` and `description` in **every supported locale** (`uk` and `en` for now). The creator must fill in both locales — partial entries are rejected.
- At display time, the user sees the translation matching their current locale. If a translation is somehow missing, the system falls back to the default locale (`en`).
- All category changes (CRUD on the category itself, plus assignment / unassignment on an advertisement) flow through the audit subsystem and appear in the relevant audit history.
- Soft-delete preserves historical assignments: a deleted category disappears from selectors and chip displays, but past audit records still reference it by id.
- **An advertisement may carry zero categories.** No auto-assignment, no default entry — an empty category set is a valid, intentional state. Cards and detail views simply render no chips when the set is empty.
- **The advertisements list filter panel exposes a category filter.** Users can pick one or more categories; only ads carrying at least one of the selected categories are shown.
- **Each advertisement card in the list shows the categories assigned to it** as a chip strip, so users see classification without opening the ad.
- The mechanism is delivered as a **standalone Spring Boot starter** (`taxon-spring-boot-starter`) following the same conventions as `audit-spring-boot-starter` and `attachment-spring-boot-starter`.
- **Complete decoupling — removing the starter from the build must not break marketplace.** With `taxon-spring-boot-starter` excluded from `marketplace-app/pom.xml`, the project must still compile, package, deploy, and run. The only observable difference is that taxonomy-related UI surfaces silently disappear (Reference Data tab, category selector in the form, chips on cards and detail view, category filter in the query block). Existing ads remain functional; no broken links, no missing-bean errors, no failed startups.

## Non-goals (out of scope for this feature)

- Hierarchical categories (parent / child). Flat list only.
- Per-advertisement category ordering. Set semantics, not list.
- Free-text search inside category names from the advertisement list. (Category filter is a multi-select chip combo, not a search box.)
- Public-facing taxonomy browsing (e.g., "show all ads in Electronics" as a URL). Categories are metadata, not navigation routes.
- Translation workflow (drafts, approvals, translation memory). Direct CRUD only.
- Bulk import of categories from a file or API.

## Resolved design questions

| Q | A |
|---|---|
| Single starter for categories only, or generic taxonomy? | **Generic taxonomy**. One starter manages multiple taxon types (CATEGORY now; TAG, STATUS, etc. later). |
| Are taxon types creatable at runtime? | **No** — types are a closed `enum` (`TaxonType` in `platform-commons`), same pattern as `Role` and `EntityType`. Adding a new type is a release-level change (requires UI integration, audit translations, seed entries). Only **entries** are user-creatable. |
| Where does the many-to-many link table live? | **In the starter**, as a generic `taxon_assignment(entity_type, entity_id, taxon_id)` — same decoupling pattern as `attachment-starter`. |
| Audit category assignments on advertisements? | **Yes**. Add / remove appears in advertisement activity history via a hook (analogous to `AttachmentAuditHook`). |
| Behaviour on category deletion? | **Soft delete**. Entry marked `deleted_at`; assignments retained; entry hidden from selectors and management list (unless "show deleted" is toggled). |
| Localisation: per-translation vs single name? | **Per translation, mandatory for all supported locales**. Form rejects save unless every locale tab has both name and description. |
| Default locale for fallback? | **`en`** (system default). Configurable via `taxon.default-locale=en`. |
| Reference Data tab label? | **"Reference Data"** (`en`) / **"Довідники"** (`uk`). |
| Default category behaviour? | **No default.** An ad with zero categories is a valid state — the chip strip simply renders empty. No starter-side auto-assignment, no seeded "General" entry. Keeps the starter's responsibility narrow (the starter never invents domain data). |

## Out of scope items discovered during spec

- Field-level filtering of categories by user role (e.g., some categories visible only to moderators). Flat visibility for now.
- Category icons / colours. Plain text only.
- Per-locale category creation (a creator who only speaks one language). All locales required.

## Stakeholders

- **End users** — see category chips on advertisements; choose categories in the edit form.
- **Moderators / admins** — manage categories via the Reference Data tab.
- **Future feature owners** — reuse the taxonomy mechanism for new classifier types without adding tables or services.
