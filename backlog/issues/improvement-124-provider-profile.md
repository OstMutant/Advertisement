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
**Priority:** high — first item of Phase 2 ("Supply + demand side"), the object reviews (F-06)
and verification (F-07) attach to for masters/shops.
**When:** blocked on [improvement-002](improvement-002-snapshot-schema-versioning.md) landing
first, same batch — `ActorProfileSnapshotDto` ships with `@SchemaVersion` from day one.

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
  - `ActorProfileSnapshotDto` implements `AuditableSnapshot`, `@SchemaVersion(1)` — covers the
    provider-facing fields only (`kind`, `about`, `categoryIds`, `cityTaxonId`); locale/
    settings changes are not part of the audit trail (they never were, for the same reason
    `UserSettingsDto` today has its own `SettingsSnapshotDto` shape, separate from profile edits).
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
  the Advertisement triad — **add every `Fields.*` case from the start** (the exact bug class
  F-02/F-03 both hit twice; do not repeat it a third time here).
- **`OgMetaRequestListener.java`**: add `PROVIDER_PATH = Pattern.compile("^/providers/(\\d+)")`,
  inject `ComponentFactory<ActorProfilePort>`; **skip OG injection entirely when `kind == null`**
  (no provider profile to show).
- **`SitemapController.java`**: add provider profile URLs, **only for rows where `kind IS NOT
  NULL`** — same rule as the OG listener and the repository's public-query filter; confirm those
  three call sites can share one "is this row a public provider profile" predicate rather than
  reimplementing it.
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
   `isModerator()` into the edit check).
4. **Every tab's data loads lazily, only on first click into that tab** — none of the three load
   eagerly on overlay open, including whichever tab is shown by default. This generalizes the
   existing 2-tab `buildTabbedContent()`'s own behavior (its secondary tab already only loads on
   first switch) to N tabs.

### Account overlay — technical plan

- **Generalize `AbstractFormOverlayModeHandler.buildTabbedContent()` to N tabs, not 2.** The
  current method only supports one primary + one lazy-loaded secondary tab (today's "Edit /
  Activity" pair everywhere else). Needs either an overload taking a `List<Tab, Supplier<Component>>`
  or a small generalization — **do not hand-roll a parallel tab-switching implementation for just
  this overlay** (`.claude/rules.md`'s `buildTabbedContent()` rule). Every existing overlay's
  2-tab call site must keep working unchanged.
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
  path — find whatever marketplace-app service currently builds `SettingsSnapshotDto` and calls
  `AuditPort.record()` for a settings change today, and repoint its persistence call only, not its
  audit-capture logic.** The Provider Profile tab's audit trail is the new
  `ProviderProfileSaveService` above.
- **"Name" tab**: name field only (role only ever appears when the viewer is `ADMIN` viewing
  someone else, as a second conditionally-shown field on this same tab — mirrors how
  `UserFormOverlayModeHandler`'s `roleComboBox` already sits next to the name field today).
- **`HeaderBar.createSettingsButton()`** → renamed (e.g. `createAccountButton()`), still opens the
  same header slot, now targets `AccountOverlay.openFor(currentUser.id())`.
- **Users grid row click**: currently opens `UserOverlay`/`UserFormOverlayModeHandler` — repoint
  to `AccountOverlay.openFor(clickedRow.id())`. Confirm whether `UserOverlay`/
  `UserFormOverlayModeHandler` become fully dead code after this (delete, don't leave unused) or
  keep a thin remnant if anything else still references them directly.
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
- `marketplace-app/DECISIONS.md` ADR-065 (city facet reuse), ADR-034 (actor enrichment pattern,
  and the `created_by`/`updated_by`/`deleted_by` actor-reference-column convention this issue's
  `actor_id` column follows), ADR-044 (settings-blob embedded version, superseded by this issue's
  real `version` column).
- `.claude/rules.md` "Form Handler Pattern" — `buildTabbedContent()` "do not duplicate" rule,
  directly relevant to the Part 2 generalization.
