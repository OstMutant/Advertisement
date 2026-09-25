# improvement-199: F-05 — contact reveal + click analytics (F-11a request board deferred, see below)

**Type:** feature — new product capability (private/roadmap.md Phase 2, F-05; F-11a deferred
indefinitely 2026-09-24, no trigger set)
**Module:** new `contact-spring-boot-starter` (`contact_info` + `contact_view` tables, owns both —
see "Plan" below), platform-commons (`ContactPort` SPI + DTOs), marketplace-orchestrator
(fallback-resolution use-case composing `ContactPort`+`AdvertisementPort`+`ProviderProfilePort`),
marketplace-app (per-channel reveal UI, master-side per-channel view counters).
provider-profile-spring-boot-starter and advertisement-spring-boot-starter gain **no** new columns
or tables for this feature — see "Plan" for why.
**Priority:** 🟡 Top — next unblocked item in the private product roadmap (Phase 2, after F-04
shipped), user-requested Top placement, 2026-09-22
**When:** independent — depends only on F-04 (ProviderProfile), already shipped (improvement-124)

## Current state

`ProviderProfileDto` (platform-commons) has no phone/telegram/viber fields. No append-only event
table (`contact_view` or similar) exists in the schema. No contact-reveal-specific rate limiting
exists; the one existing `FailureRateLimiter` (`platform-commons`/`core`) is not currently wired to
this use case (`improvement-196` separately plans to reuse it for REST API rate limiting).

## Why change

Per `private/roadmap.md`, contact-reveal clicks are the platform's core demand-signal metric — the
number that later justifies PRO subscription pricing (F-09) and pay-per-lead pricing (F-11a/b/c).
No such metric exists today.

## Expected benefit

A measurable "contact reveals per week/per master" KPI, a structured phone/Telegram/Viber
deep-link contact block, and the event-log foundation F-11's pay-per-lead pricing will eventually
need.

## Approach

Full spec: `private/features/F-05-contact-reveal.md`. Original spec summary below is superseded in
detail by the "Plan" section further down (new dedicated module, per-channel independent
reveal/counters, generic `contact_info`/`contact_view` tables) — kept here only for the
still-accurate high-level shape:
- Validated phone/telegram/viber fields, attached to a provider profile (optionally overridden
  per-listing).
- Click-to-reveal interaction — contacts never in the initial DOM, revealed via a server
  round-trip to prevent trivial scraping. Each channel (phone/Telegram/Viber) reveals/opens
  independently of the others — see "Plan" → "UI".
- Append-only `contact_view` event table — no updates, aggregate in SQL on read until volume
  demands a rollup table.
- Per-master, per-channel "N this month" counters (not one combined number) — see "Plan" → "UI".
- Rate limit reveals — **decided 2026-09-24**: reuse the existing `FailureRateLimiter`
  (`platform-commons`) as-is, keyed by IP + `viewer_id` when authenticated (IP alone is weak —
  NAT/shared proxies put many real users behind one IP). No new algorithm/library; matches the
  spec's own "simple in-memory bucket first." Check for shared-implementation overlap with
  `improvement-196` once both are picked up.

## Approach — F-11a: request board free MVP — DEFERRED indefinitely, 2026-09-24

Deferred, no trigger set — user judgment: this reads as a "nice to have" framing exercise over an
already-working filter, not a necessity, so it's dropped from this task's active scope. Not
started, not designed beyond the one-line note in "Plan" → "UI" (`ContactRevealPanel` reuse). Pick
back up as its own separately-scoped task if/when it's actually prioritized — do not silently fold
it back into this task's Step 1/2 work.

Full spec (for whenever this is picked up): `private/features/F-11-request-board-leads.md`.
Verified against current code (2026-09-22): `AdKind.REQUEST` already exists (`platform-commons`),
and `AdvertisementFilterMeta.AD_KINDS` already supports filtering the catalog by listing type —
`F-03` (shipped) already covers the underlying mechanic the request feed needs. Per the spec's own
"re-phased"/"scope simplified further (review round 4)" notes, 11a's MVP has **no response entity
at all** — a master sees a request and clicks the F-05 "show contact" button, calls the client
directly. What would actually be new to implement: a dedicated "Request board" framing (its own
tab/view, not just a filter toggle inside the general catalog) over the existing `AdKind.REQUEST`
filter, plus wiring F-05's contact-reveal interaction onto request cards.

11b (matching + notifications) and 11c (points & packages) stay Phase 5, hard-dependent on F-07
(bot) and F-08 (payment rails) — already out of scope regardless of 11a's own status.

**Note:** `private/features/F-11-request-board-leads.md`'s own "Scope (phased inside the
feature)" section (its "11a" bullet, describing masters replying with a pitch and both sides
getting notified) is stale — it describes an earlier response-flow design the same document's own
"re-phased"/"scope simplified further" notes above it explicitly superseded down to "NO response
entity at all in the MVP." Left unedited in the private spec file itself; noted here so the
discrepancy isn't silently acted on.

## Plan (drafted 2026-09-24, revised 2026-09-24 — new dedicated module; UX direction agreed
2026-09-24 — per-channel independent reveal/counters over one combined block, accepted extra
schema/UI complexity for the usability/analytics gain; implementation not yet started/approved)

Decision: contact data and contact-reveal analytics are generic over entity (`PROVIDER_PROFILE`
and `ADVERTISEMENT` both need them), so — mirroring why `audit-spring-boot-starter` exists as its
own module rather than living inside any one domain starter (`audit-spring-boot-starter/DECISIONS.md`
ADR-003) — this feature gets its own **`contact-spring-boot-starter`**, with zero knowledge of
provider-profile or advertisement internals. Neither `advertisement-spring-boot-starter` nor
`provider-profile-spring-boot-starter` gain any new columns or tables of their own for this
feature; both stay fully decoupled from each other and from this new starter, consumed only
through a `ContactPort` (`platform-commons/contact/spi`) composed by `marketplace-orchestrator`.

### Step 1 — new `contact-spring-boot-starter` module, two tables

- `contact_info` — "what to show": at most one row per entity (`UNIQUE(entity_type, entity_id)`),
  holding phone/telegram/viber. No DB-level FK to `provider_profile`/`advertisement` (same
  no-FK, `entity_type`+`entity_id` convention `audit_log` already uses) — resolved only via
  `ContactPort`, never a SQL join.
- `contact_view` — "where/how many clicked": append-only event log, one row per reveal, same
  immutable-table shape as `audit_log` (never updated, only inserted).
- `ContactPort` (platform-commons `contact.spi`) exposes read/write for `contact_info` and
  record-reveal + count for `contact_view`, generic over `(EntityType, Long entityId)` — no
  domain-specific methods.
- `marketplace-app`: provider profile form binder gets phone/telegram/viber fields (view + edit
  mode), backed by `ContactPort` via `marketplace-orchestrator` — not a new field on
  `ProviderProfileDto` itself.

### Step 2 — advertisement click resolution, still fully decoupled

**Superseded 2026-09-25 (Checkpoint 5):** the "ad's own per-listing `contact_info` override"
concept below is dropped — no UI to write an `ADVERTISEMENT`-level `contact_info` row was ever
built or is planned; ads only ever read the fallback-resolved contact. `ContactPort`/
`ContactAccessService` stay entity-generic (nothing prevents an `ADVERTISEMENT` row existing), but
nothing in this app writes one. Left below only for history.

- ~~Advertisement's optional per-listing contact override is just another `contact_info` row with
  `entity_type = ADVERTISEMENT` — no new column on the `advertisement` table itself.~~
- Fallback resolution ("ad's own `contact_info` row if present, else the ad owner's
  `provider_profile` row") lives in `marketplace-orchestrator` only — a small use-case service
  composing `ContactPort` + `AdvertisementPort` + `ProviderProfilePort` as needed (the ad-owner →
  profile link already exists via `actor_id`, nothing new needed for that step).
- Every reveal ("Show contacts" click) records `contact_view` against the entity the user was
  actually viewing (the ad's `entity_ref` on an ad page, the profile's `entity_ref` on a profile
  page) — independent of whether the displayed data came from the ad's own override or the
  profile fallback, so click attribution never needs the contact data itself duplicated.

### Draft column list — `contact_info`

| Column | Type | Remarks (business meaning) |
|---|---|---|
| `id` | `BIGSERIAL` PK | Auto-increment |
| `entity_type` | `VARCHAR(50)` NOT NULL | Owning entity type (`EntityType`: `PROVIDER_PROFILE` or `ADVERTISEMENT`). No FK — resolved via `ContactPort`, not a join |
| `entity_id` | `BIGINT` NOT NULL | ID of the owning entity. References `provider_profile(id)` or `advertisement(id)` depending on `entity_type`, no FK |
| `phone` | `VARCHAR(32)` nullable | Validated phone number, optional |
| `telegram` | `VARCHAR(64)` nullable | Telegram username (without `@`), optional |
| `viber` | `VARCHAR(32)` nullable | Viber-reachable phone number, optional |
| `created_at` | `TIMESTAMP WITH TIME ZONE` NOT NULL, default `NOW()` | Creation timestamp |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | Last update |
| `version` | `BIGINT` NOT NULL, default `0` | Optimistic-lock counter (`@Version`, native `CrudRepository.save()`) |

`UNIQUE(entity_type, entity_id)` — at most one `contact_info` row per entity.

### Draft column list — `contact_view`

| Column | Type | Remarks (business meaning) |
|---|---|---|
| `id` | `BIGSERIAL` PK | Auto-increment |
| `entity_type` | `VARCHAR(50)` NOT NULL | Type of entity whose contact was revealed (`EntityType`: `PROVIDER_PROFILE` or `ADVERTISEMENT`). No FK |
| `entity_id` | `BIGINT` NOT NULL | ID of the entity whose contact was revealed |
| `channel` | `VARCHAR(20)` NOT NULL | Which contact channel was revealed/clicked — `PHONE` / `TELEGRAM` / `VIBER`. Each channel reveals and counts independently of the others |
| `viewer_id` | `BIGINT` nullable | Acting user id if authenticated, `null` for anonymous reveals. References `user_information(id)`, no FK |
| `created_at` | `TIMESTAMP WITH TIME ZONE` NOT NULL, default `NOW()` | Reveal timestamp. Immutable write-only table (never updated, only inserted), same shape as `audit_log` |

Index: `idx_contact_view_entity` on `(entity_type, entity_id, channel, created_at DESC)` — supports
the per-channel "N views this month" aggregate read, mirroring `audit_log`'s own
`idx_audit_entity`.

### UI — where each piece lands, using existing overlay/view structure

- **Contact fields, edit:** `ProviderProfileFormOverlayModeHandler`'s `buildBinder()`
  (`marketplace-app/ui/views/main/header/account/`) — three new bound fields next to the existing
  `about`/`kind`/category/city fields, same form.
- **Own-profile view + counters:** `ProviderProfileViewModeHandler.buildProfileCard()` — three
  independent stats, one per channel ("Телефон: 47", "Telegram: 12", "Viber: 5" this month), not
  one combined number — each channel's reveal/click count is tracked and shown separately. This is
  the master's own cabinet view, not the public one — always visible to the owner, no reveal
  gating needed here.
- **Public reveal interaction — shared component:** a new `ContactRevealPanel` (`Configurable`
  prototype bean, `ui/views/components/`) since the same per-channel click-to-reveal behavior is
  needed in at least two public view surfaces below — one component, not duplicated per-view
  logic. Each channel (phone/Telegram/Viber) is its own independent button with its own
  reveal/click state — revealing Telegram does not reveal phone or Viber, and each records its own
  `contact_view` row (`channel` column):
  - `AdvertisementViewOverlayModeHandler.buildPrimaryContent()` (`main/tabs/advertisements/overlay/modes/`)
    — added as another card alongside the existing `textCard`/gallery/`metaPanel`, wired to
    `EntityRef(EntityType.ADVERTISEMENT, ad.getId())`.
  - `ProviderProfileCatalogViewModeHandler` (`main/tabs/providers/overlay/`) — same panel, wired
    to `EntityRef(EntityType.PROVIDER_PROFILE, profile.getId())`.
  - Phone: click reveals the digits in place (no page navigation) and records the view. Telegram/
    Viber: click both records the view and immediately opens the deep link (`t.me/...`,
    `viber://chat?number=...`) — no separate "reveal then click" step needed since the deep link
    itself is the destination, not sensitive text to display first.
- **Card-level (`AdvertisementCardView`, `ProviderProfileCardView`, catalog grid tiles):** settled
  2026-09-24 — no reveal button here for now, user must open the full detail overlay first.
- **F-11a request board (out of this task's Step 1/2, noted for continuity):** request cards reuse
  the same `ContactRevealPanel` once that board view exists — no new reveal component needed then.

**Mockups (agreed 2026-09-24):**

Edit form (`ProviderProfileFormOverlayModeHandler`):
```
┌─ Профіль майстра ────────────────────────────┐
│ Kind: [ MASTER ▾ ]                            │
│ About: [textarea, rich text]                  │
│ Категорії: [chip] [chip] [+ додати]           │
│ Місто: [ Київ ▾ ]                              │
│                                                │
│ ── Контакти ──────────────────────────────    │
│ Телефон:   [ +380 XX XXX XX XX        ]       │
│ Telegram:  [ @username                ]       │
│ Viber:     [ +380 XX XXX XX XX        ]       │
│                                                │
│              [ Скасувати ]   [ Зберегти ]     │
└────────────────────────────────────────────────┘
```

Own-profile view (`ProviderProfileViewModeHandler`) — three independent counters:
```
┌─ Профіль майстра ─────────────────  [Edit] [X]┐
│ 🧰 MASTER  ...                                 │
│ ── Перегляди контактів (цього місяця) ──       │
│ 📞 Телефон:    47                              │
│ 💬 Telegram:   12                              │
│ 📲 Viber:       5                              │
└─────────────────────────────────────────────────┘
```

`ContactRevealPanel` — **vertical stack**, not horizontal row (a horizontal row reflows/shifts the
other buttons sideways once the phone digits expand in place; a vertical stack only grows that one
row's own height, the other two rows stay put):
```
before:
┌─ Контакти ──────────────────────┐
│ [ 📞 Показати телефон        ]  │
│ [ 💬 Telegram                ]  │
│ [ 📲 Viber                   ]  │
└──────────────────────────────────┘

after phone click:
┌─ Контакти ──────────────────────┐
│ [ 📞 +380 XX XXX XX XX       ]  │
│ [ 💬 Telegram                ]  │
│ [ 📲 Viber                   ]  │
└──────────────────────────────────┘
```
Telegram/Viber clicks skip the "reveal" step entirely — they open the deep link
(`t.me/...`/`viber://chat?number=...`) immediately, recording the view at the same time; their own
row never changes height/shape.

**Embedded in the advertisement detail overlay** (`AdvertisementViewOverlayModeHandler`) — the
panel is just another card in the existing vertical stack, below the gallery/meta card:
```
┌─ Оголошення ─────────────────────  [Share][X]┐
│ Ремонт сантехніки, виїзд по Києву              │
│ Опис оголошення...                             │
│ [фото] [фото] [фото]                           │
│ Категорії: [Сантехніка]  Місто: [Київ]         │
│                                                 │
│ ┌─ Контакти ──────────────────────┐            │
│ │ [ 📞 Показати телефон        ]  │            │
│ │ [ 💬 Telegram                ]  │            │
│ │ [ 📲 Viber                   ]  │            │
│ └──────────────────────────────────┘           │
│                                                 │
│ Створено: ... Оновлено: ...                    │
└─────────────────────────────────────────────────┘
```

**Embedded in the public provider-profile catalog overlay** (`ProviderProfileCatalogViewModeHandler`)
— same card, below the profile's about/category/city block:
```
┌─ Профіль майстра ────────────────────  [X]┐
│ 🧰 MASTER                                  │
│ Опис майстра...                            │
│ Категорії: [Сантехніка] [Електрика]        │
│ Місто: [Київ]                              │
│                                             │
│ ┌─ Контакти ──────────────────────┐        │
│ │ [ 📞 Показати телефон        ]  │        │
│ │ [ 💬 Telegram                ]  │        │
│ │ [ 📲 Viber                   ]  │        │
│ └──────────────────────────────────┘       │
└───────────────────────────────────────────────┘
```
Same `ContactRevealPanel` instance in both places — only the `EntityRef` passed in differs
(`ADVERTISEMENT`/`ad.getId()` vs. `PROVIDER_PROFILE`/`profile.getId()`).

### Audit integration — contact_info changes must show up in the owning entity's audit timeline

`contact_info` changes must be captured in the audit log, attributed to the **owning** entity
(`PROVIDER_PROFILE` or `ADVERTISEMENT`) — same precedent as `categoryIds`/`cityTaxonId`, which
physically live in `taxon_assignment` (a different table, a different starter) yet are still part
of `ProviderProfileSnapshotDto`'s own `diff()`/`allFields()`, fetched via `TaxonPort` at
snapshot-capture time, so they show up in the provider profile's own activity timeline rather than
needing a separate "taxon_assignment changed" audit entity type.

Concretely: `ProviderProfileSnapshotDto` (and the equivalent snapshot DTO covering an
advertisement's contact override) gets `phone`/`telegram`/`viber` added to its own record +
`diff()` + `allFields()`, fetched via `ContactPort` at snapshot-capture time inside
`marketplace-orchestrator`'s save-service (`ProviderProfileSaveService` and the advertisement
equivalent) — not a standalone "contact_info changed" `EntityType` of its own.

### Test coverage

- Unit tests: `ContactPort` implementation (`contact-spring-boot-starter`), fallback-resolution
  use-case service in `marketplace-orchestrator`, `ProviderProfileSnapshotDto`/advertisement
  snapshot diff coverage for the new contact fields.
- `integration-tests`: repository-level Testcontainers coverage for `contact_info` +
  `contact_view` (own fixtures, same convention as every other starter's repository tests).
- Playwright: prefer extending the existing provider-profile-form, advertisement-form, and
  entity-activity-timeline scenarios with new assertions/steps (contact fields, reveal click,
  counter, audit entry) over adding brand-new test files — a new scenario only if none of the
  existing flows can be extended to cover a given piece. Covers: "Show contacts" reveal on both a
  provider-profile page and an advertisement page (own contact vs. fallback to profile contact),
  the "Contact views: N this month" counter, and the audit-timeline entry showing a contact-field
  change on the owning entity — required per the Definition of Done ("full Playwright `e2e --full
  --ux` scenario ... whenever the change touches UI-visible behavior").

### Format validation — researched standard patterns (2026-09-24)

- **Phone:** ITU-T E.164 — `^\+[1-9]\d{1,14}$` (leading `+`, no spaces/dashes, max 15 digits
  total). The widely-used baseline pattern; format-only, doesn't verify a number is real/reachable
  (a full library like `libphonenumber` is next-level, out of scope for this MVP).
- **Viber:** no separate username concept — Viber identifies contacts by phone number, and its own
  deep link (`viber://chat?number=...`) requires the same E.164-style digits-with-leading-`+`
  format, or it silently fails to open. Same regex as phone.
- **Telegram:** `^[A-Za-z0-9_]{5,32}$`, must start with a letter — Telegram's own username rules
  (5–32 chars, letters/digits/underscore only, no periods/hyphens).

Still open: whether `contact-spring-boot-starter` needs `integration-tests` coverage added
alongside the other starters (it will, per the existing convention — not yet spelled out here).

## Implementation checkpoints (drafted 2026-09-24 — execution order, none started yet)

Each checkpoint below is one approve-then-implement unit; the next checkpoint is proposed only
after the previous one is done and confirmed working (build/tests green).

- **Checkpoint 1 — contract + module skeleton (Plan's Step 1) — DONE 2026-09-24:**
  - `platform-commons`: `contact.model.ContactChannel` enum (`PHONE`, `TELEGRAM`, `VIBER`),
    `contact.dto.ContactInfoDto` + `contact.dto.ContactViewCountDto` records
    (`@FieldNameConstants`), `contact.spi.ContactPort` interface.
  - New module `contact-spring-boot-starter`, registered in root `pom.xml`: `pom.xml`,
    `ContactInfo`/`ContactView` entities, `ContactInfoCrudRepository`/`ContactRepository`
    (`JdbcClient` for `contact_view` inserts + per-channel monthly count query), Liquibase
    changelog (`contact_info` + `contact_view`, `remarks` on every column per root `CLAUDE.md`
    guideline 6), `ContactAutoConfiguration`, `ContactPortImpl`.
  - Module `README.md` (via `module-readme-standards` skill) + `contact-spring-boot-starter/DECISIONS.md`
    ADR-001 (new module, generic over owning entity) via `/record-decision`.
  - `integration-tests`: `ContactRepositoryTest` (6 tests) + `ContactServiceTest` (2 tests),
    `TestDataCleaner.cleanAll` updated. Found and fixed a pre-existing systemic issue while
    verifying: the full suite's ~18 distinct `@SpringBootTest` contexts have no HikariCP pool-size
    cap, and adding one more context (mine) tipped Postgres over its `max_connections` limit
    (`ProviderProfileRepositoryTest` failed with "too many clients already"). Fixed by capping only
    the new contact tests' own pool (`spring.datasource.hikari.maximum-pool-size=2`, via
    `@TestPropertySource` — these tests don't need concurrency) rather than touching the shared
    `RepositoryTestSupport` allow-list. Full suite green after: 251/251 tests, `BUILD SUCCESS`.
- **Checkpoint 2 — advertisement override + orchestrator fallback resolution (Plan's Step 2) — DONE 2026-09-24:**
  Split into two classes to respect the ≤2-domain-port-per-class rule (`ArchitectureRulesTest`):
  `AdvertisementOwnerProfileLookupService` (`AdvertisementPort` + `ProviderProfilePort`, resolves an
  ad's owner's provider profile id) and `ContactAccessService` (`ContactPort` + the lookup service as a
  plain collaborator — find/save/recordView/countViewsThisMonth/isAvailable, plus
  `resolveContact()`'s fallback: an ad's own `contact_info` row if present, else its owner's
  profile row). `contact-spring-boot-starter` added as `marketplace-orchestrator`'s 8th
  `<dependency>` (runtime scope, mirrors `taxon`/`provider-profile`/`apikey`). Mockito unit tests:
  `AdvertisementOwnerProfileLookupServiceTest` (4 tests), `ContactServiceTest` (8 tests) — both
  green, full reactor `BUILD SUCCESS`.
- **Checkpoint 3 — provider-profile form + own-profile counters — DONE 2026-09-24:**
  `ProviderProfileEditDto` gains `phone`/`telegram`/`viber` (mapped manually, not via MapStruct —
  contact data isn't part of `ProviderProfileDto`). `ProviderProfileFormOverlayModeHandler` binds
  the 3 new `UiTextField`s with E.164/Telegram-username regex validators
  (`ContactInfoDto.PHONE_PATTERN`/`TELEGRAM_PATTERN`); `save()` upserts the profile's `contact_info`
  row via the new orchestrator service after the profile itself saves.
  `ProviderProfileViewModeHandler.buildProfileCard()` renders a "Contact views (this month)" block
  with independent Phone/Telegram/Viber counters. New i18n keys (fields, validation messages, view
  labels) in both `messages_en.properties`/`messages_uk.properties`.
  Found and fixed a real bug during deploy: the new orchestrator service was originally named
  `ContactService`, colliding with `contact-spring-boot-starter`'s own internal
  `org.ost.contact.services.ContactService` bean (same default Spring bean name from two different
  packages) — app failed to start with `ConflictingBeanDefinitionException`. Renamed to
  `ContactAccessService`, matching the module's own convention that orchestrator-level services
  never reuse a starter's bare `<Domain>Service` name (`ProviderProfileSaveService`/`ReadService`,
  never bare `ProviderProfileService`).
  Verified live: extended `playwright/e2e/04-provider-profile-flow.spec.js`'s first test with two
  `test.step`s (invalid-format rejection + valid save; view-mode counters render 0/0/0) rather than
  waiting for Checkpoint 6 — full `e2e --ux` suite green afterward (50 passed, 13 skipped as usual
  for `06-seed-*` without `--full`, 0 failed), screenshots confirmed both the filled form and the
  counters block render correctly.
- **Checkpoint 4 — `ContactRevealPanel` + wiring into advertisement/provider-profile detail overlays — DONE 2026-09-24:**
  New `ContactRevealPanel` (`ui/views/components/`, Configurable prototype), vertical stack of
  per-channel rows, rendered only for channels the resolved contact actually has. Phone reveals
  the number in place (button replaced by a span); Telegram/Viber immediately open their deep link
  (`https://t.me/{username}`, `viber://chat?number={number}`) via `UI.getCurrent().getPage().open()`.
  Every click records a `contact_view` row through `ContactAccessService.recordView()`. Rate
  limiting added (surfaced as a scope gap during planning, not originally itemized in any
  checkpoint): new singleton `marketplace-app/services/security/ContactRevealRateLimiter`, reusing
  `FailureRateLimiter` as-is (30 reveals / 15 min, keyed by client IP + viewer id when
  authenticated — same key shape as `AuthService.login()`'s own limiter). Wired into
  `AdvertisementViewOverlayModeHandler.buildPrimaryContent()` (after the gallery, before the meta
  panel) and `ProviderProfileCatalogViewModeHandler.buildPrimaryContent()` (after the kind badge,
  before the meta panel); new `ComponentFactoryConfig` factory bean.
  Found and fixed a real bug during verification: `scripts/deploy-and-run/reset-clean.sql` (used by
  `--reset-only-db`) never listed `contact_info`/`contact_view` in its `TRUNCATE ... RESTART
  IDENTITY` — Checkpoint 1 added the `TestDataCleaner.cleanAll` fix for `integration-tests`'s own
  Testcontainers cleanup but missed this separate dev/Playwright-loop reset script. Leftover
  `contact_info` rows plus `provider_profile`'s restarted id sequence collided (`entity_id` reused
  across runs), silently switching a same-`(entityType,entityId)` upsert into an INSERT with an
  explicit stale `id` — Spring Data JDBC decides insert-vs-update by whether `@Version` is null,
  not `@Id`, so a "new" save with a stale matched `id` hit `contact_info_pkey` head-on. Fixed by
  adding both tables to the script's `TRUNCATE` list.
  Verified live: extended `04-provider-profile-flow.spec.js`'s deep-link test with a new
  `test.step` (all 3 rows render, phone reveals in place, Telegram click opens a new tab to the
  right `t.me` URL — Viber intentionally not click-verified, to avoid custom-URI-scheme flakiness
  in headless Chromium) — full `e2e --ux` green after the fix (50 passed, 0 failed). Advertisement-
  side wiring compiles and renders without error but has no live click-through coverage yet (no ad
  in the current seed data resolves a non-empty contact via the fallback) — left for Checkpoint 6's
  own dedicated fixture setup.
- **Checkpoint 5 — audit integration — DONE 2026-09-25 — provider-profile only, advertisement scope
  dropped:** user clarified mid-task: advertisements never get their own editable `contact_info` —
  no per-listing contact-override UI was ever built (Checkpoint 3 only touched the provider-profile
  form) and none is planned; ads only ever *read* the fallback-resolved contact (Checkpoints 2/4).
  So the original Plan's Step 2 "advertisement override" concept is dropped, and this checkpoint is
  provider-profile-only.
  `ProviderProfileSnapshotDto` gains `phone`/`telegram`/`viber` in the record + `diff()`/`allFields()`
  (kept the old 4-arg and added a 7-arg delegating constructor, `SCHEMA_VERSION` unchanged per
  ADR-024's own reasoning — an addition, not a rename/type change).
  Restructured the save flow to match the already-established `categoryIds`/`cityTaxonId` precedent
  (ADR-030, platform-commons): contact_info previously saved as a *separate* UI-layer step
  (`ProviderProfileFormOverlayModeHandler.saveContact()`, Checkpoint 3) *after* the audit snapshot
  was captured, meaning any snapshot would have shown stale contact data. Moved the write inside
  `ProviderProfileSaveService.save()`'s own transaction instead — `ProviderProfileSaveDto` gains
  `phone`/`telegram`/`viber`, `ContactAccessService` added as a plain collaborator (doesn't count
  against the module's ≤2-domain-port rule, same as `TaxonAssignmentWriteService`), contact
  upserted right after the profile itself, "after" snapshot built from the just-saved values.
  `ProviderProfileFormOverlayModeHandler.saveContact()`/`currentContact` field removed entirely;
  `ProviderProfileEditDto`'s phone/telegram/viber bindings gained `.withNullRepresentation("")` so a
  blank field commits `null` (not `""`, which `@Pattern` would reject) to the SaveDto.
  Also fixed to keep `PUT /api/provider-profiles/{id}` (marketplace-rest-api) from silently
  clearing UI-set contacts: added the same 3 fields to `ProviderProfileWriteRequest` (full-replace
  semantics, matching every other field on that endpoint) instead of hardcoding `null`.
  Verified live: extended "userEn edits provider profile" with a phone-only-edit `test.step` —
  confirms the SAME `ProviderProfileSnapshotDto`/audit entry carries the diff (no separate
  "contact_info changed" entity type), and that unrelated fields (Category/City/Telegram/Viber)
  render as plain current-state values with no `→` arrow when unchanged. Full `e2e --ux` green
  (50 passed, 0 failed) after fixing two of my own test bugs found along the way: a missing
  `.blur()` before expecting Save to re-enable (Vaadin's TextField syncs on blur, not per
  keystroke — Playwright won't click an already-disabled button to trigger it), and a wrong
  assumption that the changes block only lists changed fields (it lists every field, arrow-diffing
  only the ones that actually changed) which broke the later "deep link" reveal test's hardcoded
  phone assertion (now points at the post-edit value).
- **Checkpoint 6 — Playwright coverage — DONE 2026-09-25:** provider-profile contact-field
  validation/save, the view-mode counters block, the provider-profile-side `ContactRevealPanel`
  interaction (phone reveal + Telegram deep link), and the contact-field audit-diff entry are all
  covered (pulled forward into Checkpoints 3/4/5's own live verification). The last remaining item,
  the **advertisement-side** `ContactRevealPanel` click-through, is now covered too: a new
  `test.step` in `04-provider-profile-flow.spec.js`'s "userEn edits provider profile" test creates a
  real ad (userEn still has a live phone/telegram/viber at this exact point in the file's serial
  sequence), opens its View overlay, clicks the phone reveal (asserts `+380507654321`) and the
  Telegram button (asserts the opened tab's URL contains `t.me/electro_master`) — confirming the
  panel resolves the contact via the ad-to-owner-profile fallback, not just the profile's own page
  — then deletes the ad so it doesn't affect later specs' ad counts. No Java changes needed (the
  underlying feature was already fully implemented in Checkpoint 4); verified live via the already-
  running app, no redeploy needed — full `e2e --ux` green on the first attempt (50 passed, 0
  failed, 13 skipped, 9.4m).
- **Checkpoint 7 — data-hygiene + reveal-UX gaps found via manual testing on a real clean deploy,
  2026-09-25 — DONE 2026-09-25:**
  - **Confirmed bug — orphaned `contact_info` on profile delete:** `ProviderProfileSaveService.delete()`
    only calls `providerProfilePortFactory.get().delete(id, version)` — the profile's own
    `contact_info` row is never removed (no FK, `entity_type`+`entity_id` convention, so nothing
    cascades). Verified directly via `psql` after a full clean Playwright run: a `contact_info` row
    for a since-deleted `provider_profile` id survives as a permanent orphan. Fix: `delete()` (or
    `ContactAccessService`) must also delete the entity's `contact_info` row in the same
    transaction. Open question: also delete its `contact_view` history, or keep it for historical
    stats even after the profile is gone? Leaning toward keeping `contact_view` (append-only event
    log, same as `audit_log` never deletes on entity removal) and only deleting `contact_info`
    itself — needs confirmation before implementing.
  - **Confirmed gap — `contact_view` doesn't snapshot the revealed value:** the table records only
    `entity_type/entity_id/channel/viewer_id/created_at`, never the actual phone/telegram/viber
    value shown at reveal time. If the owner later changes their number, all historical and new
    clicks collapse into the same `(entity_id, channel)` bucket with no way to tell which clicks
    happened against which number. Fix direction: add a `revealed_value` column to `contact_view`,
    populated by `ContactAccessService.recordView()`/`ContactService` at insert time from the same
    `ContactInfoDto` the panel just resolved — needs a concrete column design + migration before
    implementing.
  - **Reveal-panel one-time-click behavior — confirmed as-designed, not a bug (user decision
    2026-09-25):** `ContactRevealPanel`'s public panel intentionally shows no click count next to
    the revealed value (counts stay owner-only, in `ProviderProfileViewModeHandler`'s own "Contact
    views" block) and the phone button intentionally stays a one-time reveal per panel render (no
    re-click without reopening the overlay, which already re-fetches fresh data — traced the real
    `discardChanges()`→`afterDiscard()`→`switchTo()` path, no caching found). No code change here.
  - **Testing gap acknowledged:** none of the above are caught by the current Playwright suite —
    the green `--full --ux` run doesn't exercise delete-then-inspect-orphan or a number change
    followed by re-inspecting `contact_view`. New/extended specs needed once the two real fixes
    below land.

  **Decisions (2026-09-25):** delete only `contact_info` on profile delete, keep `contact_view`
  history (same append-only precedent as `audit_log`). Reveal-panel UX (count visibility, one-time
  click) confirmed as-is, no change. Remaining real scope: (A) cascade-delete `contact_info` on
  profile delete, (B) add a `revealed_value` snapshot column to `contact_view`.

  **Plan (A) — cascade-delete `contact_info` on profile delete:**
  - `ContactPort`/`ContactAccessService` gains a `delete(EntityType, Long entityId)` method
    (delegates to a new `ContactRepository`/`ContactInfoCrudRepository` delete-by-entity call).
  - `ProviderProfileSaveService.delete()` calls it, in the same try block, after the port delete
    succeeds — same transaction boundary as the profile delete itself.

  **Plan (B) — snapshot the revealed value on each `contact_view` insert:**
  - New Liquibase changeset (`contact-spring-boot-starter`'s own changelog) adding
    `contact_view.revealed_value VARCHAR(64)` (nullable — historical rows before this migration
    have none), `remarks` stating it's the phone/telegram/viber value actually shown at reveal
    time, so historical clicks stay attributable to the number that was live then.
  - `ContactView` entity gains the field; `ContactPort.recordView(...)`/`ContactAccessService
    .recordView(...)` gain a `revealedValue` parameter.
  - `ContactRevealPanel`'s two call sites (`buildPhoneRow`/`buildDeepLinkRow`) pass the actual
    resolved value (`contact.phone()`/`contact.telegram()`/`contact.viber()`) through.

  **Test coverage for both:** `integration-tests` repository-level coverage
  (`ContactRepositoryTest`) for the cascade-delete and the new column; Playwright — extend
  `04-provider-profile-flow.spec.js`'s existing delete test to assert no orphan row remains (or a
  new backend-only check if Playwright can't inspect the DB directly), and extend the phone-reveal
  `test.step` to assert `revealed_value` is populated.

  **Implemented and verified 2026-09-25:** both fixes landed. `ContactPort.delete(EntityType, Long)`
  + `ContactRepository.deleteByEntity`/`ContactService.delete`/`ContactPortImpl.delete`
  (contact-spring-boot-starter), `ContactAccessService.delete` (marketplace-orchestrator), wired
  into `ProviderProfileSaveService.delete()` right after the port delete. New Liquibase changeset
  `02-contact-view-revealed-value.xml` adds `contact_view.revealed_value VARCHAR(64)`;
  `ContactPort.recordView(...)`/the full call chain down to `ContactRevealPanel`'s two call sites
  now carry the actually-revealed value. Full `build-and-test.sh --unit --integration` green: unit
  78/78 (`marketplace-app`), `ContactAccessServiceTest` 10/10, `ProviderProfileSaveServiceTest`
  14/14; integration 257/257 including new `ContactRepositoryTest` 8/8 and
  `ContactServiceTest` (starter-level) 3/3.
  Verified live end-to-end: clean redeploy (`--reset-only-db`) + full `e2e --ux` Playwright run
  (50 passed, 0 failed, 13 skipped — spec 06 not run without `--full`). Direct `psql` inspection
  after the run confirmed both fixes against real data: `contact_info` has zero
  `PROVIDER_PROFILE`-type rows left after the suite's own create-then-delete flow (no orphan);
  `contact_view` retained 2 historical rows (a phone reveal and a telegram reveal) for that
  since-deleted profile, each with `revealed_value` correctly populated
  (`+380507654321`/`electro_master`) — confirms both the cascade-delete and the value-snapshot
  fix, and that view history survives the profile's own deletion as decided. No new Playwright
  spec needed — neither fix has a UI-visible signal to assert (per the earlier product decisions:
  reveal counts stay owner-only, revealed value is never shown in the panel itself).

- **Checkpoint 8 — owner-view UX gaps found via manual testing on the live env, 2026-09-25 — DONE
  2026-09-25:** three further real gaps, found after Checkpoint 7 shipped:
  1. **`ProviderProfileViewModeHandler.buildContactViewsBlock()` shows only the click count, never
     the value itself** ("Phone: 0" instead of "Phone: +380... (0)") — owner has no way to see
     their own saved number next to its reveal count in the private view. Fix: fetch
     `contactService.find(...)` in this method, render `"<value> (<count>)"` per channel, skip a
     channel row entirely when its value is null (mirrors `ContactRevealPanel`'s own
     hidden-when-empty rule).
  2. **`AccountOverlay.proceed()` is a no-op for `PROVIDER_PROFILE` — confirmed root cause of the
     "profile tab doesn't update" complaint:** the form deliberately stays open in Edit after Save
     (comment: "same as SettingsOverlay"), but Vaadin's `Tabs` component never re-fires a
     `SelectedChangeEvent` for a click on the already-selected tab — so after creating/editing a
     profile, clicking the still-selected "Provider Profile" tab does visibly nothing, reading as
     "nothing updated." Fix (deliberate reversal of the earlier "stays open" decision, provider-
     profile-only — Name/Settings keep their own current behavior): add a `PROVIDER_PROFILE`+`EDIT`
     branch to `proceed()` that switches to View mode after a successful save, same transition
     `afterDiscard()`/close-X already uses (fresh `findByActorId`/`countViewsThisMonth`, confirmed
     no caching in that path during the Checkpoint 7 investigation).
  3. **No read-only contact preview in the advertisement Edit form** — an ad's own contact is
     always resolved via fallback to the owner's profile (Checkpoint 2), shown only in the ad's own
     View via `ContactRevealPanel`; the Edit form shows nothing at all, so an owner editing their ad
     has no visibility into what contact will display. Scope, confirmed with user: **Edit mode
     only** (View stays exactly as-is, `ContactRevealPanel` unchanged) — a read-only
     phone/telegram/viber block in `AdvertisementFormOverlayModeHandler`, resolved via
     `ContactAccessService.resolveContact(ADVERTISEMENT, adId)`, each field carrying a `title`
     attribute (native browser tooltip) noting it's pulled from the profile. Hidden entirely when
     the fallback resolves nothing (no profile, or profile has no contact set).

  **Implemented and verified 2026-09-25.** (1) `ProviderProfileViewModeHandler.buildContactViewsBlock()`
  now fetches `contactService.find(...)` and renders `"<value> (<count>)"` per channel, skipping
  a channel row entirely when unset. (2) `AccountOverlay.proceed()` gained a `PROVIDER_PROFILE`+
  `EDIT` branch switching to View after a successful save (deliberate reversal of the earlier
  "stays open" design, provider-profile-only) — this exposed a second real gap along the way:
  `ProviderProfileViewModeHandler` had no history button at all (only the Edit form did), so
  landing in View right after Save would have hidden history access. Fixed by adding the same
  `buildHistoryButton()` pattern to the View handler too (`canOperate(false)` — View-mode history
  is read-only; restoring a past revision still goes through the Edit form's own history button,
  which can actually load the restored data into the binder), plumbing `breadcrumbSteps` through
  `ProviderProfileViewModeHandler.Parameters` from `AccountOverlay.switchTo()`. (3) New read-only
  contact-preview block in `AdvertisementFormOverlayModeHandler.activate()` (Edit mode only, hidden
  on create since a not-yet-saved ad has no id to resolve a fallback from), two new i18n keys
  (`advertisement.overlay.contactPreview.label/hint`).

  Fixed 6 call sites in `04-provider-profile-flow.spec.js` that assumed the old "Save keeps the
  form open, Cancel switches to View" behavior (raw form-field assertions right after Save, or an
  unconditional Cancel click that no longer has a button to find) — updated to assert the rendered
  View content directly. Full `build-and-test.sh --unit --no-integration` green (78/78
  `marketplace-app`, 20/20 ArchUnit) both before and after the spec fixes; live-verified via clean
  redeploy + full `e2e --ux` (50 passed, 0 failed, 13 skipped) twice — once catching the spec
  breakage from item (2)'s behavior change, once fully green after fixing it.

- **Checkpoint 9 — two more real gaps found via manual testing after Checkpoint 8 shipped,
  2026-09-25 — DONE 2026-09-25:**
  1. **Contact preview was hidden on advertisement Create, not just Edit — a scoping mistake I made
     without confirming with the user.** Checkpoint 8's read-only contact block was gated on
     `!isCreate` on the reasoning that a not-yet-saved ad has no id to resolve
     `ContactAccessService.resolveContact(ADVERTISEMENT, adId)` from. That reasoning is correct but
     incomplete: during Create, the eventual owner is always the current actor, so the block should
     instead resolve straight from the current actor's own provider profile. Fix:
     `AdvertisementFormOverlayModeHandler` gained `ProviderProfileSaveService`;
     `buildContactPreviewBlockForCurrentActor()` (Create path) does
     `providerProfileSaveService.findByActorId(currentUserId)` →
     `contactAccessService.find(PROVIDER_PROFILE, profileId)`, guarded by
     `providerProfileSaveService.isAvailable()`. `buildContactPreviewBlock(Long adId)` (Edit path,
     unchanged logic) and the new Create path both funnel into one shared
     `buildContactPreviewBlock(ContactInfoDto)` rendering method.
  2. **`ProvidersView` (the public "Providers" tab) never refreshes on tab switch — a systemic gap
     in `MainView`'s tab-switching mechanism, not specific to this feature.** `MainView`'s
     `tabs.addSelectedChangeListener` only toggles `.setVisible(true/false)` between the 5 top-level
     tab views (Advertisements/Providers/Users/Timeline/Reference Data) — none of them re-fetch on
     becoming visible. This never surfaced before because every other cross-view create/edit path
     (e.g. creating an advertisement) opens its overlay *from* the same view it needs to refresh, so
     that view's own `onSaved`/`onListChanged` callback already triggers its own `refresh()`. A
     provider profile is created via `AccountOverlay` (opened from `HeaderBar`, structurally
     decoupled from `ProvidersView`), so there was no path back to `ProvidersView`'s own `refresh()`
     at all — confirmed: switching to the Providers tab after saving a profile in Settings showed
     stale (pre-save) data until a full page reload. Fix, scoped to the concretely reported tab
     only (the same latent gap likely exists for Users/Timeline/Reference Data too — flagged, not
     fixed here, since none of those were reported and fixing every tab is a larger, unrequested
     scope): `ProvidersView` gained a `public void refreshOnTabSelect()` one-line wrapper around its
     existing private `refresh()` (keeping `refresh()` itself private per the standing View Pattern
     rule); `MainView`'s selected-change listener now calls it whenever the newly-selected tab is
     the Providers tab.
  **Implemented and verified 2026-09-25.** New Playwright coverage, both in
  `04-provider-profile-flow.spec.js` (userEn already has a live profile+contact at this exact
  point in the file's serial sequence, before its later deletion): a
  `'advertisement create form — read-only contact preview pulled from own provider profile'`
  test.step (opens Create, asserts `.advertisement-contact-preview` shows the current
  phone/telegram/viber values and a `title` tooltip, closes without saving) and a
  `'Providers tab reflects the just-created profile without a page reload'` test.step (switches to
  the Providers tab directly after closing the Settings overlay, no `page.goto`, asserts the new
  MASTER card is visible). Found and fixed one bug in my own new test during verification: the
  Create form's close-X button doesn't remove `.advertisement-overlay` from the DOM (same
  `.overlay--visible` class-toggle shape `AccountOverlay` uses), so the initial
  `toHaveCount(0)` assertion never resolved — fixed to `waitForOverlayClosed(page)`, the existing
  shared helper already built for exactly this. Full `build-and-test.sh --unit --no-integration`
  green (78/78) before redeploy; live-verified via clean redeploy + full `e2e --ux` (50 passed, 0
  failed, 13 skipped) — one real failure caught and fixed on the first attempt (the test bug above),
  fully green on the second.

## Related

- `private/features/F-05-contact-reveal.md` — full feature spec (goal, user story, scope, tech
  notes, KPI, risks).
- `private/features/F-11-request-board-leads.md` — full feature spec, including the 11b/11c
  Phase-5 scope this task does not cover.
- `private/roadmap.md` — Phase 2 sequencing (F-05 + F-11a next after F-04).
- improvement-124 (completed) — F-04 provider profile, the dependency this feature builds on.
- `improvement-196` — separate REST-API rate-limiting task also planning to reuse
  `FailureRateLimiter`.
