# improvement-124: F-04 — ActorProfile (preferences + provider profile, one table), unified "My Account" overlay

**Type:** feature — product roadmap Phase 2, item #1 (largest single feature before
monetization); supersedes [improvement-123](improvement-123-f04-master-profile.md) (F-04
master-only draft, broadened during planning on 2026-07-27). One combined issue — the
provider-profile feature, the account-overlay consolidation it triggered, and the schema
decoupling that fell out of it are one continuous piece of work, not three unrelated ones.
**Module:** new `actor-profile-spring-boot-starter` (one module, one table — see "merge"
decision below), `platform-commons` (new `actorprofile.*` packages, new
`EntityType.ACTOR_PROFILE`), `marketplace-app` (new Providers tab/view/overlay, unified account
overlay, header entry points, OG/sitemap extension), `services/security/AccessEvaluator` (new
narrower check).
**Priority:** 🟡 high — first item of Phase 2 ("Supply + demand side"), the object reviews (F-06)
and verification (F-07) attach to for masters/shops.
**When:** improvement-002 landed 2026-07-28 — unblocked. `ActorProfileSnapshotDto` ships with the
real versioning pattern from day one: a plain `int schemaVersion` record component (last position)
+ `SCHEMA_VERSION = 1` constant + a legacy-arity delegating constructor, exactly matching
`AdvertisementSnapshotDto` (see `platform-commons/DECISIONS.md` ADR-024). **Not** a `@SchemaVersion`
annotation — that design was tried and explicitly reverted during improvement-002 itself; every
`@SchemaVersion` mention below is corrected accordingly (verified directly against
`AuditableSnapshot.java` and `AdvertisementSnapshotDto.java` — the interface has a bare
`int schemaVersion()` method, no annotation exists anywhere in the codebase).

## Problem

Base spec: `private/features/F-04-master-profile.md` (gitignored, master-only — broadened here).
The catalog has no supply-side anchor: masters have no shareable page for their FB posts/Viber
status/business card, shops have no page beyond a badge on their PRODUCT ads, and there is no
official "contact support" presence on the platform at all. Reviews (F-06) and verification (F-07)
need an object to attach to, and none of the three existing personas (master, shop, platform
support) has one.

Planning this surfaced two more, real, pre-existing gaps (verified against current code, not
assumed):

1. **A plain `USER`-role account cannot edit its own display name.** Name+role editing only exists
   in `UserFormOverlayModeHandler`, reachable only via the "Users" tab, which is only visible to
   `MODERATOR`/`ADMIN` (`AccessEvaluator.isPrivileged()`). A regular user has no self-service path
   to change their own name at all today.
2. `user_information` bundles pure-authentication data (email, password hash, role) together with
   personal preferences (`locale`, `settings` JSONB) in one table/module, coupling two concerns
   that don't need to be coupled.

## Decided 2026-07-27: one table, `actor_profile`, not two

Originally planned as two separate tables (`provider_profile` for the marketplace-facing
master/shop/support profile, `actor_profile` for locale/settings preferences) — **merged into one
table on the same day, per explicit decision**. Every actor gets exactly one `actor_profile` row:
created eagerly (at registration, alongside `user_information`) for the locale/settings columns,
which every actor needs regardless of whether they ever become a provider; the provider-facing
columns (`kind`, `about`, `city_taxon_id`) start `NULL` and only get populated once/if
that actor opts into being a provider. **"Does this actor have a provider profile" = `kind IS NOT
NULL`**, not "does a row exist" — the row always exists.

### `actor_profile` — full column list

| Column | Type | Note |
|---|---|---|
| `id` | `BIGSERIAL PK` | |
| `actor_id` | `BIGINT NOT NULL UNIQUE` — no `REFERENCES`/FK constraint, same as `advertisement.created_by`/`taxon`'s actor-reference columns | one row per actor, created eagerly at registration |
| `locale` | `VARCHAR(10)` | moved from `user_information.locale` |
| `settings` | `JSONB` | moved from `user_information.settings` (same page-size blob, untouched shape) |
| `kind` | `VARCHAR(20)`, nullable | `NULL` = not a provider yet; `MASTER`/`SHOP`/`SUPPORT` once set, immutable after |
| `about` | `VARCHAR(20000)`, nullable | only meaningful once `kind` is set |
| `city_taxon_id` | `BIGINT`, nullable | only meaningful once `kind` is set |
| `created_at`/`updated_at` | `TIMESTAMP WITH TIME ZONE` | |
| `version` | `BIGINT` | optimistic locking — was previously embedded inside the settings JSONB itself (ADR-044) for the preferences half; now one real column covering the whole row |

Indexes: unique on `actor_id` (already implied by the constraint), on `city_taxon_id`, on `kind`
(catalog filters by both of the latter two).

`user_information` loses its `locale` and `settings` columns entirely (Liquibase `dropColumn`,
data migrated into the new table's row first in the same changeset) — otherwise unchanged, `name`
stays there.

**Consequence of the merge — "deleting a provider profile" cannot delete the row.** Since the same
row also carries locale/settings, removing someone's provider status means nulling out `kind`/
`about`/`city_taxon_id` on their existing row, never a `DELETE` — that would destroy their
preferences too. `ActorProfileService`'s "remove provider
profile" operation must be an update, not a delete.

## Update 2026-07-31 — module/table split reconsidered, supersedes the "one table" decision above

Re-discussed with the user before implementation began. The single-table/single-module design
above is **superseded** by a 3-table, 2-module split — the merge rationale (`kind IS NOT NULL` as
the provider-profile existence check, eager row creation at registration) no longer applies.

**Three tables, not one:**
1. `user_information` (`user-spring-boot-starter`, unchanged) — auth only: email, password hash,
   role, name.
2. `user_preferences` (`user-spring-boot-starter`, new — second entity/repository/service in the
   same module, same shape as the existing `UserProfileUpdate` precedent) — `locale`, `settings`
   JSONB, `version`. Created eagerly at registration (every actor needs preferences regardless of
   provider status), same as today.
3. `provider_profile` (new `provider-profile-spring-boot-starter` module) — `kind` (now
   **`NOT NULL`**, not nullable — see below), `about`, `city_taxon_id`, `version`. Created
   **lazily**, only on first "become a provider" save — never eagerly at registration.

**Two modules, not one new one:**
- `user-spring-boot-starter` absorbs preferences as a second entity/repository/service (mirrors
  `UserProfileUpdate`) — no new module for this half, since it has no independent
  toggle/removability need and shares the registration transaction with auth.
- New `provider-profile-spring-boot-starter` owns only the provider-facing catalog entity — this
  is the half that behaves like a sibling of `Advertisement` (public catalog, filters, OG/sitemap,
  portfolio, future reviews/verification), and is the one plausible candidate for independent
  removal later.

**Simplification this unlocks — lazy creation, `kind NOT NULL`, no existence-guard duplication.**
Since `provider_profile` no longer also carries preferences, there is no longer a reason to create
a row for every actor at registration. A row is only created when an actor first opts in as a
provider, with `kind` supplied at creation time (the create form's `RadioButtonGroup<ProviderKind>`
already collects this). Consequence: `kind` becomes `NOT NULL`, "does this actor have a provider
profile" becomes "does a row exist for this `actor_id`" (a plain `findByActorId().isPresent()`),
and the `kind IS NOT NULL` guard that the original plan needed in three places (repository catalog
query, `OgMetaRequestListener`, `SitemapController`) is no longer needed anywhere — every row in
`provider_profile` is by definition a public provider profile. `clearProviderProfile()` also
becomes a real `DELETE`, not a column-nulling `UPDATE` (the "can't delete, it also carries
preferences" constraint from the merged-table design no longer applies).

**Naming — rename `ActorProfile*` → `ProviderProfile*` throughout this issue.** The "actor" framing
came from the row also covering preferences; now that it's provider-only, `ProviderProfile`/
`ProviderProfilePort`/`ProviderProfileDto`/`EntityType.PROVIDER_PROFILE`/
`provider-profile-spring-boot-starter`/`provider_profile` table is the consistent name throughout.
The rest of this document (Part 1's technical plan below) still uses the old `ActorProfile`/
`EntityType.ACTOR_PROFILE`/`actor-profile-spring-boot-starter` naming from the superseded
single-table design — apply the rename mechanically during Batch 124-B (see "Execution Batches"
below); the field lists, SQL shapes, and service responsibilities described below are otherwise
still accurate for the provider-facing half only (ignore every mention of `locale`/`settings`
living on this table — those now belong to `user_preferences` per this update).

**Resolves the open question on `EntityType.USER_SETTINGS`.** Since preferences never merges into
the provider-profile table after all, `USER_SETTINGS` simply keeps being used for the Settings tab
going forward, unchanged — no permanent-legacy-tag-vs-migrate decision needed.

**Resolves the "Activity/restore tab structure" open question (Part 2).** Three tabs, three
independent `EntityType`s, three independent Activity/restore panels — Name → `EntityType.USER`
(existing), Settings → `EntityType.USER_SETTINGS` (existing), Provider Profile → new
`EntityType.PROVIDER_PROFILE`. No cross-entity-type feed merging needed; `buildTabbedContent()`'s
N-tab generalization treats each tab's content + Activity panel as one independent pair.

## Part 1 — the provider-facing half (decisions made 2026-07-27, in order)

1. **improvement-002 lands first, same batch** — detection + logging only (no automatic
   migration), applied to all 4 existing snapshot classes, the settings JSONB blob, the
   attachment `changes_summary` array, plus this feature's new `ActorProfileSnapshotDto`.
2. **One `kind` column, not three separate concepts.** Mirrors F-03's `AdKind` pattern exactly:
   `ProviderKind { MASTER, SHOP, SUPPORT }`, nullable on the shared row (see merge decision above).
3. **`kind` is orthogonal to `Role` (ADMIN/MODERATOR/USER).** `Role` is authorization (what you can
   do in the admin panel); `kind` is what you offer on the marketplace. Any user of any role can
   have a `MASTER` or `SHOP` profile — an admin who also does tiling work sets `kind = MASTER`
   exactly like a plain `USER` would. Having `kind` set at all is optional regardless of role;
   there is no forced "every actor must be exactly one of N kinds."
4. **`SUPPORT` kind is different from `MASTER`/`SHOP` in one way:** only settable by an actor whose
   `Role` is `ADMIN` or `MODERATOR` — enforced server-side in `ActorProfileService`, not just
   hidden in the UI. Otherwise behaves exactly like `MASTER`/`SHOP` — always public once `kind` is
   set. **No `visible`/hide toggle in this pass** — dropped from scope (was planned as a
   `SUPPORT`-only visibility switch, removed 2026-07-27 before implementation since it isn't
   needed yet; revisit if/when it actually is).
5. **Entry point: header, Fiverr-style** — a button next to `.header-settings-button`/
   `.header-logout-button` in `HeaderBar.initUserInfoRow()`, labeled **"Мій акаунт" / "My
   Account"** (not "My Profile" — it opens the whole three-tab overlay from Part 2; "profile"
   appears exactly once in this whole feature, on the "Provider Profile" tab only). Opens the
   Account overlay directly on its "Provider Profile" tab — the create form's `kind` selector
   (`RadioButtonGroup<ProviderKind>`) shows `MASTER`/`SHOP` always, and additionally `SUPPORT` only
   when `access.isPrivileged()` is true.
6. **Naming: `ProviderKind`, not part of a `MasterProfile` name** — the concept covers all three
   personas, "master" no longer describes a shop or a support contact.

### Provider-facing half — technical plan

Grounded against current code, not assumed (`AdvertisementAutoConfiguration`, `AdvertisementPort`,
`AuditDomainHookImpl`, `OgMetaRequestListener`, `SitemapController`, `AppLinkService`, `HeaderBar`,
`AccessEvaluator`).

**`platform-commons`:**

- New `EntityType.ACTOR_PROFILE` value — forces `AuditDomainHookImpl.findExisting()`'s exhaustive
  `switch` to gain a `case ACTOR_PROFILE`, a real compiler-enforced safety net. (Named
  `ACTOR_PROFILE`, not `PROVIDER_PROFILE` — it's the id of the one merged row that categories/
  portfolio attach to via `taxon_assignment`/`attachment`, matching the table it actually is.)
  **Open question, must be decided before implementation:** `EntityType.USER_SETTINGS` already
  exists and is actively used today (`SettingsFormModeHandler`'s Activity tab, `SettingsSnapshotDto`
  audit capture) — real `audit_log` rows already carry this tag. Once settings persistence moves to
  `ActorProfilePort`/`ACTOR_PROFILE`, decide explicitly: (a) keep `USER_SETTINGS` in the enum
  permanently as a historical/read-only tag so old rows keep displaying correctly, with all *new*
  settings writes captured under `ACTOR_PROFILE` instead, or (b) something else. Do **not** remove
  `USER_SETTINGS` from the enum outright — that would break deserialization/display of every
  pre-existing settings-change audit row. (a) is the default recommendation unless there's a reason
  to migrate old rows instead.
  Full registration checklist for the new enum value, verified against current code (beyond the
  `AuditDomainHookImpl` case above): `I18nKey.forEntityType()` (compiler-enforced exhaustive switch
  — new `ENTITY_TYPE_ACTOR_PROFILE` key + EN/UK strings), a new CSS badge color pair in `styles.css`/
  `activity-feed.css` (`--app-status-entity-actor-profile-bg/-text`, `.activity-feed-type--actor_profile`
  — **not** compiler-enforced, easy to forget: `TAXON` had none until improvement-127 added it),
  `JacksonConfig.registerAuditSnapshotSubtypes()` (see below), and the new
  `ProviderProfileActivityFieldsHookImpl` bean (picked up automatically via Spring's
  `List<AuditActivityFieldsHook>` injection, no manual registry — but must exist or labels silently
  fall back to raw field keys). `TimelineQueryBlock`'s entity-type filter and `AuditReadService`
  need **no** changes — both confirmed fully data-driven off `EntityType.values()` already. The new
  `actor-profile-spring-boot-starter` itself needs **no** `audit.spi` implementations of its own —
  all audit-side wiring lives in marketplace-app, exactly matching how `taxon-spring-boot-starter`
  has none today.
- New package `org.ost.platform.actorprofile.model` — `ProviderKind { MASTER, SHOP, SUPPORT }`.
- New package `org.ost.platform.actorprofile.dto`:
  - `ActorProfileDto`: `id`, `actorId`, `actorName`, `actorEmail`, `locale`, `settings`, `kind`
    (nullable), `about`, `cityTaxonId`, `cityName`, `categoryIds`, `categoryNames`,
    `createdAt`, `updatedAt`, `version`.
  - `ProviderProfileSaveDto` record — the provider-facing subset only: `id`, `kind` (`@NotNull
    ProviderKind`, immutable once first set — changing it is out of scope, unset-and-reset
    instead), `about`, `categoryIds`, `cityTaxonId`, `version`.
  - `ProviderProfileFilterDto`: `kinds` (`Set<ProviderKind>`, mirrors F-03's `AdKind` filter
    shape), `categoryIds`, `cityTaxonId` — for the Providers catalog, which only ever lists rows
    where `kind IS NOT NULL`.
  - `ActorProfileSnapshotDto` implements `AuditableSnapshot` — record with `int schemaVersion` as
    its last component, `public static final int SCHEMA_VERSION = 1`, and a legacy-arity
    delegating constructor (copy `AdvertisementSnapshotDto`'s exact shape, ADR-024). Covers the
    provider-facing fields only (`kind`, `about`, `categoryIds`, `cityTaxonId`); locale/
    settings changes are not part of the audit trail (they never were, for the same reason
    `UserSettingsDto` today has its own `SettingsSnapshotDto` shape, separate from profile edits).
    **Must also be added to `JacksonConfig.registerAuditSnapshotSubtypes()`**
    (`marketplace-app/src/main/java/org/ost/marketplace/config/JacksonConfig.java`) — a hand-maintained
    `@PostConstruct` list of every `AuditableSnapshot` subtype registered on the polymorphic
    `auditObjectMapper` (`@JsonTypeInfo(use = NAME, property = "@type")`). This is **not**
    compiler-enforced and has **no existing test coverage** (confirmed: no test references
    `JacksonConfig` or exercises subtype registration directly) — omitting `ActorProfileSnapshotDto.class`
    here does not fail the build, it fails silently at snapshot *read* time later. Add it in the
    same commit as the DTO itself, and add a round-trip (de)serialization test for it since none
    of the 4 existing subtypes has one either.
- New package `org.ost.platform.actorprofile.spi` — `ActorProfilePort`:
  ```java
  public interface ActorProfilePort {
      // shared row lookup
      Optional<ActorProfileDto> findByActorId(@NonNull Long actorId);

      // preferences half
      void saveLocaleAndSettings(@NonNull Long actorId, String locale, @NonNull UserSettingsDto settings, @NonNull Long version);

      // provider-facing half — catalog only ever sees rows where kind IS NOT NULL
      List<ActorProfileDto> getFiltered(@NonNull ProviderProfileFilterDto filter, int page, int size, @NonNull Sort sort, @NonNull Locale locale);
      int count(@NonNull ProviderProfileFilterDto filter);
      Optional<ActorProfileDto> findById(@NonNull Long id);
      Long saveProviderProfile(@NonNull ProviderProfileSaveDto dto, @NonNull Long actingUserId, boolean actingUserIsPrivileged);
      void clearProviderProfile(@NonNull Long actorId, @NonNull Long actingUserId, Long version);  // nulls kind/about/cityTaxonId -- never deletes the row

      // shared actor-lifecycle
      Set<Long> findExistingIds(@NonNull Set<Long> ids);
      Set<Long> findOwnerIds(@NonNull Set<Long> actorIds);
      void clearActorReferences(@NonNull Set<Long> actorIds);
  }
  ```
  `saveProviderProfile()`'s `actingUserIsPrivileged` flag is what `ActorProfileService` checks
  before allowing `kind == SUPPORT` — passed in from marketplace-app's
  `AccessEvaluator.isPrivileged()`, not re-derived from a `UserPort` role lookup inside the starter.

**Module: `actor-profile-spring-boot-starter`** (one module for the whole merged table):

- `pom.xml` — same shape as `advertisement-spring-boot-starter/pom.xml` (platform-commons +
  query-lib, spring-boot-starter-data-jdbc, spring-boot-liquibase, spring-boot-starter-validation,
  owasp-sanitizer + jsoup for `about`, same `maven-enforcer-plugin` banned-dependencies block
  updated both directions with every other starter). Add
  `<module>actor-profile-spring-boot-starter</module>` to the root `pom.xml`.
- `entity/ActorProfile.java`: `id`, `actorId`, `locale`, `settings` (JSONB, same Jackson mapping
  `UserSettingsDto` already uses), `kind` (nullable `VARCHAR(20)`), `about`, `cityTaxonId`,
  `createdAt`/`updatedAt`, `version` (`@Version`, ADR-029).
- `db/actor-profile-changelog/changes/01-actor-profile-schema.xml`: the `actor_profile` table per
  the column list above, `@DependsOn`-equivalent Liquibase ordering after `user-changelog` (needs
  `user_information` to exist first, even without an FK, for the data-migration step that seeds
  every existing actor's row from their current `user_information.locale`/`settings`).
- `repository/ActorProfileRepository.java`: mirrors `AdvertisementRepository`'s shape —
  `ROW_MAPPER`, `SqlFilterBuilder<ProviderProfileFilterDto> FILTER` (`kinds` via `inSet()`, literal
  `AdvertisementRepository`'s `adKinds` binding, plus `categoryIds`/`cityTaxonId`). **Every
  provider-catalog query must filter `kind IS NOT NULL`** — rows with no provider profile must
  never appear in the Providers listing; an admin-only listing method can bypass this if a future
  admin view over all actors is ever needed, named distinctly (`findAllForAdmin`) so it's never
  accidentally reused for a public path. A separate `findByActorId()` (no `kind` filter) backs the
  preferences half and the "does this actor have a provider profile" check.
- `services/ActorProfileService.java`: two halves —
  - Preferences: `saveLocaleAndSettings()` — no sanitization needed, just validation (same
    `@Min`/`@Max` bounds `UserSettingsDto` already enforces).
  - Provider-facing: `saveProviderProfile()` sanitizes `about` (OWASP Sanitizer, same config as
    `AdvertisementService.sanitizeHtml()`), **enforces `kind == SUPPORT` requires
    `actingUserIsPrivileged == true`** (throws otherwise — the one real authorization rule this
    feature adds, test it explicitly), wires categories via `ComponentFactory<TaxonPort>`
    (`EntityType.ACTOR_PROFILE`), enriches actor name/email via `ComponentFactory<UserPort>`.
    `clearProviderProfile()` nulls the provider-facing columns only (see merge consequence above).
- `spi/ActorProfilePortImpl.java`: pure delegation, per `platform-commons/CLAUDE.md`.
- `config/ActorProfileAutoConfiguration.java`: mirrors `AdvertisementAutoConfiguration` — `@Bean
  actorProfileLiquibase` (ordered after `userLiquibase`), `@EnableJdbcRepositories`, port
  `ComponentFactory` bean.
- **Registration**: `UserService.register()` must create the `actor_profile` row (default locale,
  default settings, `kind = NULL`) in the same transaction as the new `user_information` row —
  every actor has exactly one `actor_profile` row from the moment they sign up, never lazily
  created on first provider-profile use.

**`marketplace-app`:**

- **`services/actorprofile/ProviderProfileSaveService.java` — the actual audit-write path, do not
  skip this.** Mirrors `AdvertisementSaveService` exactly: orchestrates
  `ActorProfilePort.saveProviderProfile()` then builds an `ActorProfileSnapshotDto` (via the
  already-enriched category/city names from `ProviderProfileEnrichService`) and calls
  `AuditPort.record()` with it. **This is what makes a provider-profile edit actually show up in
  both the entity's own Activity tab and the global Timeline tab** — `ActorProfilePortImpl`/
  `ActorProfileService` in the starter only persist the row; nothing about audit capture happens
  automatically just because the entity implements `AuditableSnapshot`. Confirmed by reading
  `AdvertisementSaveService`'s actual shape before writing this — it explicitly builds the
  snapshot and calls `AuditPort.record()` itself, this is not implicit anywhere in the framework.
  `EntityType.ACTOR_PROFILE` is picked up automatically by `TimelineQueryBlock`'s entity-type
  filter (built from `EntityType.values()` directly, confirmed by reading it) — no separate
  Timeline registration needed once the enum value and the `AuditPort.record()` call both exist.
- **New Providers tab** (`ui/views/main/tabs/providers/`) — `ProvidersView` mirrors
  `AdvertisementsView`, `ProviderProfileCardView` mirrors `AdvertisementCardView` (kind badge
  shape `provider-profile-badge--master`/`--shop`/`--support`, same as F-03's `AdKind` badge),
  `ProviderProfileQueryBlock`/`FilterMeta`/`SortMeta` mirror the Advertisement query-layer trio,
  filter row for `kinds` mirrors F-03's `AD_KINDS` multi-select filter exactly. Lists only rows
  where `kind IS NOT NULL` (enforced repository-side, per above).
- **Overlay**: `ProviderProfileOverlay` + `ProviderProfileFormOverlayModeHandler` +
  `ProviderProfileViewOverlayModeHandler` — near-exact mirror of the Advertisement overlay's
  field-for-field shape (no `title` — the display name comes from the account, not the profile).
  **Exact field list:**
  - **Form (edit mode):**
    1. `kind` — `RadioButtonGroup<ProviderKind>`: `MASTER`/`SHOP` always, `SUPPORT` only when
       `access.isPrivileged()`. Disabled/fixed once `kind` is already set — immutable after first
       save.
    2. `about` — `QuillEditor`, sanitized rich text (same OWASP config as advertisement
       `description`) — free-text "about me/us".
    3. Categories — `MultiSelectComboBox<TaxonDto>` (`TaxonType.CATEGORY`), same mechanism as
       advertisement's category field.
    4. City — `ComboBox<TaxonDto>` (`TaxonType.CITY`), same mechanism as F-02.
    5. Portfolio — `AttachmentGalleryService`/`AttachmentGallery`, same component as
       advertisement's media gallery, keyed by `EntityRef(EntityType.ACTOR_PROFILE, id)`.
  - **View mode:** kind badge, `about` text, category chips, city chip, portfolio gallery — same
    `buildChipRow()` reuse as the advertisement view overlay.
- **`spi/AuditDomainHookImpl.java`**: `case ACTOR_PROFILE -> actorProfilePortFactory...` —
  compiler-forced.
- **`services/actorprofile/ProviderProfileEnrichService.java`** + **`spi/ProviderProfileActivityFieldsHookImpl.java`**
  + **`spi/ProviderProfileActivityEnrichHookImpl.java`**: one triad per new domain, same shape as
  the Advertisement triad — **add every `Fields.*` case in `labelFor()` from the start.** Verified
  root cause of the bug this pre-empts: `labelFor()` is a `switch (rawFieldKey)` over `String`
  constants with a `default -> rawFieldKey` arm (`AuditActivityFieldsHook`'s own default method
  does the same) — a `String` switch can never be exhaustive, so the compiler cannot catch a
  missing `case` the way it catches a missing `EntityType` case. This bit `AdvertisementActivityFieldsHookImpl`
  for real once (`cityTaxonId` omitted, shipped, then hotfixed — `marketplace-app/DECISIONS.md`
  ADR-065 "Update (2026-07-25...)") and was pre-empted for `adKind` on the next domain (F-03).
  `ProviderProfileActivityEnrichHookImpl` is only needed if provider-profile portfolio attachments
  need enrichment — `TaxonActivityFieldsHookImpl`'s domain has no enrich-hook counterpart at all
  today (confirmed: only `ActivityEnrichHookImpl`, hardcoded to `EntityType.ADVERTISEMENT`, exists),
  so skip writing an enrich hook unless there's a concrete need for it, don't cargo-cult the triad.
- **`OgMetaRequestListener.java`**: add `PROVIDER_PATH = Pattern.compile("^/providers/(\\d+)")`,
  inject `ComponentFactory<ActorProfilePort>`; **skip OG injection entirely when `kind == null`**
  (no provider profile to show).
- **`SitemapController.java`**: add provider profile URLs, **only for rows where `kind IS NOT
  NULL`** — same rule as the OG listener and the repository's public-query filter. Verified: no
  shared "is this entity publicly listable" predicate exists today — `OgMetaRequestListener` and
  `SitemapController` are both fully advertisement-specific, hand-rolled, and independent of each
  other (advertisement has no analogous gate since every ad is inherently public). This is new,
  net-new convention for this feature, not a refactor of an existing shared helper — write one
  predicate/method (e.g. on `ActorProfileRepository` or `ActorProfilePort`) and have all three call
  sites (repository query, OG listener, sitemap) use it, rather than inlining `kind != null` three
  times independently.
- **`AppLinkService.java`**: `providerProfileUrl(@NonNull Long id)` mirrors `advertisementUrl()`.
- **`ProviderProfileDeepLinkView.java`**: `@Route("providers")`, mirrors
  `AdvertisementDeepLinkView`. Optional URL slug (`/providers/42-ivan-plytochnyk`, slug ignored on
  lookup) — cheap, include from day one.
- **Portfolio photos**: reuse `AttachmentGalleryService`/`AttachmentPort` keyed by
  `EntityRef(EntityType.ACTOR_PROFILE, profileId)` — no attachment-starter changes needed.

## Part 2 — unified "My Account" overlay

### Decisions made 2026-07-27

1. **One overlay, three tabs — "profile" appears exactly once, on the third tab only**: "Name"
   (self-editable, new capability — just the one field), "Settings" (existing page-size/locale
   prefs, now backed by `ActorProfilePort` instead of `UserPort`), "Provider Profile" (the
   provider-facing half above). Replaces the current single-purpose `SettingsOverlay` header
   button; the "Users" grid's per-row click reuses the *same* overlay component instead of
   `UserFormOverlayModeHandler`'s narrower name+role form. The header button ("Мій акаунт") opens
   this same overlay.
2. **Permission model for viewing/editing** — a genuine change from today's behavior:
   - **Self** (via header button): edit own name, own settings, own provider profile. Never sees a
     role field.
   - **`ADMIN`** (via Users grid click on another user): sees and can edit all three tabs — name,
     role, that user's settings, that user's provider profile. No point hiding any of it from
     admin — admin already has full DB visibility.
   - **`MODERATOR`** (via Users grid click on another user): sees all three tabs but **cannot save
     changes to any of them** — name, role, settings, provider profile all become view-only.
     **This narrows today's actual behavior**, where a moderator can currently edit another user's
     name and role via `UserFormOverlayModeHandler` with no restriction beyond
     `AccessEvaluator.isPrivileged()` (`true` for both `ADMIN` and `MODERATOR` today).
3. **New, narrower `AccessEvaluator` methods — do not repurpose `canOperate()`.**
   `AccessEvaluator.canOperate(Long ownerUserId)` treats `ADMIN`/`MODERATOR` identically today and
   is used across advertisement/taxon ownership checks; changing its semantics would silently
   affect those unrelated domains. Add two new, purpose-specific methods instead:
   ```java
   public boolean canEditUserAccount(Long targetUserId) {
       return currentUser().map(u -> userPort.isAdmin(u) || userPort.isOwner(u, targetUserId)).orElse(false);
   }
   public boolean canViewUserAccount(Long targetUserId) {
       return isPrivileged() || currentUser().map(u -> userPort.isOwner(u, targetUserId)).orElse(false);
   }
   ```
   (mirrors `canOperate()`'s existing shape/`isOwner()` usage, just without folding
   `isModerator()` into the edit check). Verified: `UserPort.isAdmin(UserDto)` and
   `UserPort.isOwner(UserDto, UserIdMarker|Long)` already exist with exactly these signatures
   (`platform-commons/.../UserPort.java`) — this snippet calls real, existing API, nothing new
   needed on `UserPort` itself. `AccessEvaluator` itself has no standalone `isOwner()`/`isAdmin()`
   of its own today — every existing check inlines the `userPort.isXxx(u)` call directly, so these
   two new methods follow the exact same style as `canOperate()`/`isPrivileged()`.
4. **Every tab's data loads lazily, only on first click into that tab** — none of the three load
   eagerly on overlay open, including whichever tab is shown by default. This generalizes the
   existing 2-tab `buildTabbedContent()`'s own behavior (its secondary tab already only loads on
   first switch) to N tabs.

### Open question — Activity/restore tab structure (must be decided before implementation)

Naively pairing each of the 3 form tabs with its own local Activity tab (today's per-entity
Edit+Activity pattern, `buildContentWithActivity()`) would produce 6 tabs — confirmed not the
right shape. But the global Timeline tab is **not** a substitute for the local Activity tab:
verified directly (`AuditTimelineRowRenderer.java` has no restore button/action at all) that
restore-from-snapshot (`AuditActivityPanel`'s `onRestoreRequested` → `handleRestoreFromActivity`)
only exists in the per-entity Activity panels (`Settings`, `Advertisement`, `Taxon`, `City`,
`User` overlays all have it today) — Timeline is flat cross-entity browsing only, with no
"current entity" context to restore into. Losing restore capability for name/role changes (today
provided by `UserFormOverlayModeHandler`'s own Activity tab) or for settings/provider-profile
changes would be a real regression, not a simplification.

Two real entities are involved: `USER` (name, and role when admin-viewed) and `ACTOR_PROFILE`
(settings + provider profile — same row, two save methods). Options to choose between:
1. One shared Activity tab per logical row — but `USER` and `ACTOR_PROFILE` are different
   `EntityType`s with independent snapshot histories, so "shared" would mean interleaving two
   different `AuditableSnapshot` subtypes' histories in one merged, chronologically-sorted feed
   (new capability, not something `AuditActivityPanel` does today).
2. Two Activity tabs: one scoped to `EntityType.USER` (covers Name, and Role when applicable) and
   one scoped to `EntityType.ACTOR_PROFILE` (covers Settings + Provider Profile) — 5 tabs total,
   straightforward reuse of the existing per-entity `AuditActivityPanel` pattern twice, no new
   merging logic needed.
3. Activity/restore only on the tabs where it currently exists in a comparable form (`Name`
   inherits `UserFormOverlayModeHandler`'s existing USER-scoped Activity/restore; `Provider
   Profile` gets a new ACTOR_PROFILE-scoped one) and `Settings` alone has no local Activity tab
   (its current audit trail is already low-stakes — page-size prefs — no restore need); global
   Timeline covers browsing everything.

Resolve this with the user before writing the `buildTabbedContent()` generalization below, since
the answer determines whether N-tab generalization needs 4, 5, or a variable number of tabs, and
whether any new cross-entity-type activity aggregation is needed.

### Account overlay — technical plan

- **Generalize `AbstractFormOverlayModeHandler.buildTabbedContent()` to N tabs, not 2.** Verified
  current shape (`AbstractFormOverlayModeHandler.java:52-66`): `buildTabbedContent(Tabs, Tab
  primaryTab, Div primaryContent, Supplier<Component> secondaryLoader)` is hardcoded to exactly one
  primary/secondary pair — one `tabbedSecondaryContent` `Div`, one `addSelectedChangeListener`
  toggling visibility, one lazy-load check (`tabbedSecondaryContent.getChildren().findFirst().isEmpty()`).
  Generalizing to N tabs means either a new overload taking an ordered list of
  `(Tab, Div content-or-null, Supplier<Component> lazyLoader)` triples with the "already loaded"
  check keyed per-tab instead of on one shared `Div`, or restructuring internally — **add a new
  overload, do not change the existing signature**: `buildContentWithActivity()` and
  `SettingsFormModeHandler`'s own direct 2-tab call must keep compiling and behaving unchanged.
  **Do not hand-roll a parallel tab-switching implementation for just this overlay**
  (`.claude/rules.md`'s `buildTabbedContent()` rule).
- **`AccountOverlay`** (renames/replaces `SettingsOverlay`): `openFor(Long targetUserId)` replaces
  `openSettings()` — self-view calls it with `currentUser.id()` (header button), the Users grid's
  row-click calls it with the clicked row's id. Each of the three tab contents is its own lazily-
  built mini-form (own `OverlayFormBinder`, own Save button, own `canEditUserAccount()`/
  `canViewUserAccount()` gate) — not one shared binder across all three: the Name tab saves via
  `UserPort.updateProfile()`, the Settings and Provider Profile tabs both save via
  `ActorProfilePort` but through its two distinct methods (`saveLocaleAndSettings()` vs.
  `saveProviderProfile()`) — same underlying row, still two independent save operations.
  **The Name tab's audit trail is unaffected (it already goes through `UserPort.updateProfile()`
  today). The Settings tab's existing `SettingsSnapshotDto` audit capture must be repointed to
  read/write through `ActorProfilePort` instead of the current `UserSettingsRepository`-based
  path.** Verified exact current location:
  `user-spring-boot-starter`'s `UserSettingsService.save(Long userId, UserSettingsDto settings)`
  (`@Transactional`) does three things in order — `repository.save(...)` (the persistence call to
  repoint), `hookFactory.ifAvailable(hook -> hook.onSettingsChanged(...))` (dispatches
  `UserSettingsChangedHook`), and
  `auditPortFactory.ifAvailable(p -> p.captureUpdate(userId, SettingsSnapshotDto.from(settings), userId))`
  (audit capture).

  **Correction after deeper verification — `UserSettingsChangedHook` is NOT dead, do not delete
  it or its dispatch.** An earlier pass of this issue wrongly claimed "zero implementations exist."
  Verified directly by reading the class: `marketplace-app`'s `SettingsPaginationService`
  (`ui/views/services/pagination/SettingsPaginationService.java:18`) **does** implement
  `UserSettingsChangedHook` — it is the live mechanism that resizes every currently-open pagination
  bar (Ads/Users/Timeline lists) immediately when a user saves new page-size settings, with no page
  reload, by pushing to each registered `PaginationBar` via `ui.access(...)` inside
  `onSettingsChanged()`. Confirmed load-bearing: `SettingsPaginationBinding` (which wraps this
  service) is registered by `AdvertisementsView`, `TimelineView`, and `UserView` — every list view
  in the app depends on this hook firing. **The repointed settings-save path must keep calling
  `hookFactory.ifAvailable(hook -> hook.onSettingsChanged(actorId, settings))` exactly as
  `UserSettingsService.save()` does today** — dropping this call would silently break live
  pagination-size updates across the whole app (a saved setting would only take effect after a
  manual page reload, not immediately).

  So only `UserSettingsService`/`UserSettingsRepository` (the persistence layer itself) become dead
  code in `user-spring-boot-starter` once repointed — delete those two. Keep
  `UserSettingsChangedHook` (interface) and `SettingsPaginationService` (implementation) exactly
  as-is; only re-wire *what* calls the hook (the new settings-save path, wherever it ends up —
  `ActorProfileService` in the new starter, mirroring today's shape, is the natural home).
  `UserPort.loadSettings()`/`saveSettings()` do become dead on `UserPort` itself and should be
  removed from the port + `UserPortImpl` once nothing calls them. The Provider Profile tab's audit
  trail is the new `ProviderProfileSaveService` above.
- **"Name" tab**: name field only (role only ever appears when the viewer is `ADMIN` viewing
  someone else, as a second conditionally-shown field on this same tab — mirrors how
  `UserFormOverlayModeHandler`'s `roleComboBox` already sits next to the name field today).
- **`HeaderBar.createSettingsButton()`** → renamed (e.g. `createAccountButton()`), still opens the
  same header slot, now targets `AccountOverlay.openFor(currentUser.id())`.
- **Users grid row click**: currently opens `UserOverlay`/`UserFormOverlayModeHandler` — repoint
  to `AccountOverlay.openFor(clickedRow.id())`. Verified: `UserOverlay` has exactly one consumer,
  `UserView.java`'s two row-click handlers (`openForView()`/`openForEdit()`, lines 90-91) — no
  other caller exists anywhere in the codebase. Once repointed, `UserOverlay` +
  `UserFormOverlayModeHandler` + `UserFormOverlayModeHandlerTest` become fully dead code —
  **delete them outright**, no thin remnant needed.
- **Field-level enable/disable, not tab hiding**, for the `MODERATOR`-viewing-another-user case:
  all three tabs render the same as for `ADMIN`, but every field is `.setReadOnly(true)`/save
  button `.setEnabled(false)` when `!canEditUserAccount(targetUserId)`.

## Explicitly out of scope

- Reviews (F-06), verification badges (F-07) — this issue only builds the profile object.
- Changing `ProviderKind` after first set (unset-and-reset instead, for now).
- A `visible`/hide toggle for any kind (including `SUPPORT`) — dropped from this pass entirely
  (see Part 1, decision 4); revisit as its own issue if a real need for hiding a provider profile
  shows up.
- Schedules, price lists, certificates, shop address/hours fields — scope-creep magnets per the
  original spec; confirm during implementation whether shop-specific fields are needed now or are
  a fast-follow.
- "Their active OFFER/PRODUCT listings" list on the profile page — nice-to-have, low cost via
  `AdvertisementPort.findByCreator()` filtered by `AdKind`, confirm in/out during implementation.
- Changing `canOperate()`'s existing ADMIN==MODERATOR treatment for advertisements/taxons — Part 2
  only narrows the *new* user-account-specific checks, nothing else.
- Avatar/photo upload on the "Name" tab — not requested.
- Self-service role requests ("ask to become a moderator") — admin-initiated only, unchanged.
- Migrating pre-existing `UserSettingsDto`'s embedded-version scheme (ADR-044) to reuse this
  table's real `version` column is implied by the merge but not separately re-litigated here — see
  the `actor_profile` column table's `version` row above.

## Testing strategy

- Unit tests: `ActorProfileServiceTest` — sanitization, the `SUPPORT`-requires-privileged-actor
  rule (both accept and reject cases), `clearProviderProfile()` nulls the right columns without
  touching `locale`/`settings`; `AccessEvaluatorTest` gains cases for `canEditUserAccount()`/
  `canViewUserAccount()` across all three roles × {self, other}.
- Integration tests: new `integration-tests/.../actorprofile/ActorProfileRepositoryTest`, added to
  `TestDataCleaner.cleanAll()`; a repository-level test for "row with `kind IS NULL` excluded from
  the Providers catalog query."
- Playwright — **priority order: update existing flows first, only add what genuinely has nothing
  to extend.**
  1. Spec 03's existing `adminEn edits userEn name` test (`UserFormOverlayModeHandler`'s current
     coverage) is the one to **update in place**, not duplicate: repoint its selectors to the new
     `AccountOverlay`/"Name" tab, then extend the *same* test with the new assertions — moderator
     opens another user's account overlay and every field is read-only/save disabled; admin edits
     another user's settings and provider profile tabs (not just name/role, which this test already
     covers); a plain `USER` self-edits their own name via the header button for the first time
     (genuinely new capability). Do not write a second, parallel test that duplicates what this one
     already checks.
  2. `_flows/user-management.flow.js` (or wherever `UserFormOverlayModeHandler`'s current helpers
     live) — rename/repoint helpers to the new overlay rather than leaving stale ones alongside new
     ones; delete any helper that becomes genuinely unused once the repoint is done, per the
     project's own dead-code-removal convention (verify call sites before deleting).
  3. Only the Provider Profile piece is a genuinely new page type with nothing to extend — new spec
     file `08-provider-profile-flow.spec.js` + `_flows/provider-profile.flow.js`, covering
     create/edit/restore/delete lifecycle for a `MASTER` and a `SHOP` profile, plus
     `SUPPORT`-creation-rejected-for-non-privileged-user.
  4. **Screenshot names: audit for duplicates before adding new ones.** Every `screenshot(page,
     name)` call across the touched spec/flow files must produce a distinct name — check the
     renamed/repointed helpers from step 2 don't collide with any name the new "Name" tab flow (or
     the Settings/Provider Profile tabs) introduces, and that extending an existing test (step 1)
     doesn't silently reuse a screenshot name from an earlier step in the same test with different
     content behind it.

## Execution Batches (2026-07-31)

One pass = one PR, one test run, per `.claude/rules.md`. Each batch's gate must be green before
starting the next — later batches depend on earlier ones compiling and passing.

### Batch 124-A — split preferences into `user_preferences` (user-spring-boot-starter, no UI change) — ✅ DONE (2026-07-31)
- New `user_preferences` table added directly to `01-user-schema.xml` (edited in place, no
  migration changeset — pre-production, "from scratch" per explicit user direction), `locale`/
  `settings` removed from `user_information`. No backfill needed (no prod data to preserve).
- New `UserPreferencesRepository` (replaces `UserSettingsRepository`, second repo in
  `user-spring-boot-starter`, mirrors the `UserProfileUpdate` precedent) — keyed by `actor_id`
  (no FK, deliberate, matches the codebase's actor-reference-column convention).
- `UserPort.loadSettings()`/`saveSettings()`/`updateLocale()` signatures unchanged,
  `UserSettingsChangedHook` dispatch unchanged, `EntityType.USER_SETTINGS` audit capture unchanged.
- **Gate → 124-B/C: PASSED.** `bash scripts/unit-tests.sh` (77/77) + `bash scripts/integration-
  tests.sh --sandbox` (136/136, every domain) green. `/code-review`'s full 8-angle pass found and
  fixed 4 real bugs (silent no-op on missing-row locale update, orphaned `user_preferences` row on
  user purge, two related NPEs in the bulk locale lookup, a dropped legacy-JSON test scenario) plus
  3 duplication cleanups — see `marketplace-app/DECISIONS.md` ADR-070 for the full list.

### Batch 124-B — new `provider-profile-spring-boot-starter` module (backend only, no UI)
- `platform-commons`: `EntityType.PROVIDER_PROFILE`, `ProviderKind`, `ProviderProfileDto`/
  `ProviderProfileSaveDto`/`ProviderProfileFilterDto`/`ProviderProfileSnapshotDto` (schemaVersion
  per ADR-024), `ProviderProfilePort`.
- Compiler-forced marketplace-app touches that must ship in this same batch (the new enum value
  forces these exhaustive switches to compile): `AuditDomainHookImpl`'s `case PROVIDER_PROFILE`,
  `I18nKey.forEntityType()`'s new case + EN/UK strings, new CSS badge color pair.
- New module `provider-profile-spring-boot-starter`: entity, own Liquibase changelog (`kind NOT
  NULL` per the simplification above — no nullable-kind placeholder rows), repository (no
  `kind IS NOT NULL` guard needed — every row qualifies), service (sanitization, `SUPPORT`-requires-
  privileged-actor rule, `deleteProviderProfile()` as a real delete), port impl, autoconfiguration.
  Root `pom.xml` + `integration-tests` reactor/staleness-check/`TestDataCleaner.cleanAll()`
  registration.
- **Gate → 124-C:** `bash scripts/unit-tests.sh` (`ProviderProfileServiceTest` — sanitization,
  `SUPPORT`-privilege accept/reject) + `bash scripts/integration-tests.sh --sandbox` (new
  `ProviderProfileRepositoryTest`) green; module builds standalone. No Playwright — no UI surfaces
  this module yet.

### Batch 124-C — `AccountOverlay`, 3 independent tabs + audit, permission model
- **Correction (2026-07-31, must be re-verified when this batch starts):** `buildTabbedContent()`/
  `buildContentWithActivity()`/`ActivityTabParams` were removed entirely per
  `marketplace-app/DECISIONS.md` ADR-067 — every domain now uses a single content area + a
  `.{domain}-history-button` icon button that opens the shared `EntityActivityOverlay` as a nested
  overlay (`.claude/rules.md` "History access"), not a 2-tab Content+Activity pair. The line below
  ("Generalize `buildTabbedContent()` to N tabs") is stale — it predates ADR-067's removal of that
  method. The 3 Name/Settings/Provider-Profile sections still need *some* navigation mechanism (a
  plain `Tabs` component switching between 3 content `Div`s, unrelated to the old
  Activity-pairing machinery), and each section gets its own history icon button opening
  `EntityActivityOverlay` for its own `EntityRef` — re-derive this section's technical plan from
  the current `AbstractFormOverlayModeHandler` shape before writing any code.
- ~~Generalize `AbstractFormOverlayModeHandler.buildTabbedContent()` to N tabs via a new overload
  (existing 2-tab callers unchanged).~~
- `AccountOverlay` (replaces `SettingsOverlay`), `openFor(Long targetUserId)`; Name tab (closes the
  "plain USER can't self-edit name" gap), Settings tab (reads/writes via Batch 124-A's table),
  Provider Profile tab (`ProviderProfileSaveService` audit-write orchestration, mirrors
  `AdvertisementSaveService`; `ProviderProfileActivityFieldsHookImpl` with every `Fields.*` case
  from day one, per ADR-065).
- New `AccessEvaluator.canEditUserAccount()`/`canViewUserAccount()`; field-level readonly for
  `MODERATOR` viewing another user.
- `HeaderBar` button repoint; Users grid row-click repoint; delete `UserOverlay`/
  `UserFormOverlayModeHandler`/its test once repointed.
- **Gate → 124-D:** unit tests (`AccessEvaluatorTest` new cases × 3 roles × {self, other}) +
  integration tests green; Playwright — update spec 03's existing `adminEn edits userEn name` test
  in place (repoint + extend, per the issue's Testing Strategy step 1), run via
  `bash scripts/playwright.sh e2e --ux`. Must be fully green — Batch 124-D builds the public
  surface on top of profiles only creatable through this overlay.

### Batch 124-D — Providers catalog, OG/sitemap, deep link (public-facing)
- `ProvidersView`/`ProviderProfileCardView`/query-layer trio — lists every `provider_profile` row
  (no `kind IS NOT NULL` filter needed, per the simplification above).
- `OgMetaRequestListener` provider path, `SitemapController` provider URLs, shared "is publicly
  listable" predicate (one method, all 3 call sites — no `kind != null` inlined independently,
  moot anyway since every row now qualifies).
- `AppLinkService.providerProfileUrl()`, `ProviderProfileDeepLinkView`.
- New Playwright spec `08-provider-profile-flow.spec.js` — create/edit/restore/delete for `MASTER`
  and `SHOP`, `SUPPORT`-rejected-for-non-privileged; audit new screenshot names for collisions
  against existing specs first.
- **Gate → Done:** `bash scripts/playwright.sh e2e --ux` green (no `--full` needed — this feature
  doesn't touch spec 05's seeded-pagination scenario). `marketplace-app/DECISIONS.md` updated
  (module/table split, `buildTabbedContent()` N-generalization, `EntityType.PROVIDER_PROFILE`
  naming). Issue moved to `completed/issues/`, `BACKLOG.md` row removed, `BACKLOG-ARCHIVE.md`
  entry added.

## Related

- `private/features/F-04-master-profile.md` — base product spec (gitignored, master-only; this
  issue's broadening is planning-time, not yet reflected in the private doc).
- `private/roadmap.md` — Phase 2 item #1; `private/features/F-09-pro-subscription.md` — confirms
  shops (`Business` tier) and masters (`PRO` tier) are already distinct personas in the
  monetization plan, which is what surfaced the master-only gap during this planning session.
- [improvement-002](improvement-002-snapshot-schema-versioning.md) — hard dependency, same batch.
- [improvement-123](improvement-123-f04-master-profile.md) — superseded master-only draft, kept
  for discussion history.
- [improvement-122](../completed/issues/improvement-122-f03-listing-types.md) — F-03, the direct
  precedent for the `kind`-column-not-separate-tables pattern and the `labelFor()`/
  `ActivityFieldsHookImpl` bug class that must not repeat a third time.
- [improvement-013](../completed/issues/improvement-013-raw-field-names-in-activity-diff.md) — the
  original, distinct bug in this same rendering path: a *wiring* gap (`labelFor()` never called at
  all on one code path), not a missing `case`. Fixed by threading `AuditActivityFieldsHook` through
  a shared `applyLabel()` helper (ADR-030). Relevant precedent alongside ADR-065/improvement-122:
  confirms there are two distinct historical bug classes in this rendering path, both worth
  guarding against for the new `ActorProfile` domain.
- `marketplace-app/DECISIONS.md` ADR-065 (city facet reuse), ADR-034 (actor enrichment pattern,
  and the `created_by`/`updated_by`/`deleted_by` actor-reference-column convention this issue's
  `actor_id` column follows), ADR-044 (settings-blob embedded version, superseded by this issue's
  real `version` column).
- `.claude/rules.md` "Form Handler Pattern" — `buildTabbedContent()` "do not duplicate" rule,
  directly relevant to the Part 2 generalization.
