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

### Step 2 — advertisement override + click resolution, still fully decoupled

- Advertisement's optional per-listing contact override is just another `contact_info` row with
  `entity_type = ADVERTISEMENT` — no new column on the `advertisement` table itself.
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

## Related

- `private/features/F-05-contact-reveal.md` — full feature spec (goal, user story, scope, tech
  notes, KPI, risks).
- `private/features/F-11-request-board-leads.md` — full feature spec, including the 11b/11c
  Phase-5 scope this task does not cover.
- `private/roadmap.md` — Phase 2 sequencing (F-05 + F-11a next after F-04).
- improvement-124 (completed) — F-04 provider profile, the dependency this feature builds on.
- `improvement-196` — separate REST-API rate-limiting task also planning to reuse
  `FailureRateLimiter`.
