# improvement-123: F-04 — master public profile

**Superseded 2026-07-27 by [improvement-124](improvement-124-provider-profile.md):** scope
broadened mid-planning from master-only to a unified profile covering masters, shops, and an
official support contact (three `kind`s), plus a role-gated visibility toggle for the support
kind. This file is kept for the discussion history that led there; implement against
improvement-124 instead, not this one.

**Type:** feature — product roadmap Phase 2, item #1 (largest single feature before monetization)
**Module:** new `master-spring-boot-starter`, `platform-commons` (new `masterprofile.*` packages,
new `EntityType.MASTER_PROFILE`), `marketplace-app` (new Masters tab/view/overlay, header entry
point, OG/sitemap extension).
**Priority:** ⚪ high (superseded — kept for discussion history only, see banner above; act on improvement-124 instead) — first item of Phase 2 ("Supply + demand side"), the object reviews (F-06)
and verification (F-07) attach to.
**When:** blocked on [improvement-002](improvement-002-snapshot-schema-versioning.md) landing
first, in the same batch (see its "Decided 2026-07-27" update) — `MasterProfileSnapshotDto` ships
with `@SchemaVersion` from day one, not bolted on after.

## Problem

Full spec: `private/features/F-04-master-profile.md` (gitignored product doc). The catalog has no
supply-side anchor — masters have no shareable page to put in their own FB posts/Viber status/
business card. Reviews (F-06) and verification badges (F-07) need an object to attach to, and
neither exists without this. Answers the group's recurring "порадьте майстра" with a linkable
answer.

## Decisions made 2026-07-27

1. **improvement-002 lands first, same batch** — detection + logging only (no automatic
   migration), applied to all 4 existing snapshot classes plus `MasterProfileSnapshotDto`.
2. **New `master-spring-boot-starter` module** (spec's own recommendation, confirmed) — profile
   is a distinct concern from the account itself (`user-spring-boot-starter`), same rationale
   `taxon`/`advertisement` starters already follow.
3. **Entry point: header, Fiverr-style** — a button next to `.header-settings-button`/
   `.header-logout-button` in `HeaderBar.initUserInfoRow()`, not a banner on the Masters tab.
   Label/action depends on whether the current user already has a profile: "My Profile" (opens
   it) vs. "Create Profile" (opens the create form) — mirrors Fiverr's profile-menu "Become a
   Seller" entry, not a separate always-visible CTA.

## Suggested fix

Verified against current code (not assumed) before writing this plan — every touchpoint below is
grounded in the actual current file content, mirroring the Advertisement domain's shape
touchpoint-for-touchpoint (confirmed via direct reads of `AdvertisementAutoConfiguration`,
`AdvertisementPort`, `AuditDomainHookImpl`, `OgMetaRequestListener`, `SitemapController`,
`AppLinkService`, `HeaderBar`).

### 1. `platform-commons`

- New `EntityType.MASTER_PROFILE` value. This is the **first new `EntityType` since
  Advertisement/User/Taxon** — `AuditDomainHookImpl.findExisting()`'s exhaustive `switch` over
  `EntityType` will fail to compile until a `case MASTER_PROFILE` is added, which is a real
  safety net, not a risk to route around.
- New package `org.ost.platform.masterprofile.model` — no new model enum needed (unlike F-03's
  `AdKind`); city goes through the existing `TaxonType.CITY` mechanism (F-02 precedent), skills
  through `TaxonType.CATEGORY` via `taxon_assignment` (`EntityType.MASTER_PROFILE`).
- New package `org.ost.platform.masterprofile.dto`:
  - `MasterProfileDto` (read model): `id`, `userId`, `userName`, `userEmail`, `about`, `cityTaxonId`,
    `cityName`, `categoryIds`, `categoryNames`, `createdAt`, `updatedAt`, `version` — mirrors
    `AdvertisementInfoDto`'s enrichment shape (category/city names resolved server-side, actor
    name/email resolved via `UserPort`, same as `AdvertisementService.enrichWithActorInfo()`).
  - `MasterProfileSaveDto` record: `id`, `about` (`@Size` capped, same sanitization pattern as
    `AdvertisementSaveDto.description`), `categoryIds` (`Set<Long>`, capped like
    `AdvertisementSaveDto.CATEGORY_MAX_COUNT`), `cityTaxonId`, `version`. No `userId` field — the
    owning user is derived from the authenticated actor at save time, one profile per user (unique
    constraint), never reassignable.
  - `MasterProfileFilterDto`: `categoryIds`, `cityTaxonId` — mirrors `AdvertisementFilterDto`'s
    shape for the same two facets; no free-text title filter (profiles are found by category/city,
    not by name search, per the spec's catalog description).
  - `MasterProfileSnapshotDto` implements `AuditableSnapshot`, carries `@SchemaVersion(1)` from
    day one (improvement-002): `about`, `categoryIds`, `cityTaxonId` — no `attachmentSnapshotId`
    field needed if portfolio media diffing isn't required for v1 (confirm scope; if it is,
    mirror `AdvertisementSnapshotDto.attachmentSnapshotId` exactly).
- New package `org.ost.platform.masterprofile.spi` — `MasterProfilePort`:
  ```java
  public interface MasterProfilePort {
      List<MasterProfileDto> getFiltered(@NonNull MasterProfileFilterDto filter, int page, int size, @NonNull Sort sort, @NonNull Locale locale);
      int count(@NonNull MasterProfileFilterDto filter);
      Optional<MasterProfileDto> findById(@NonNull Long id);
      Optional<MasterProfileDto> findByUserId(@NonNull Long userId);  // for the header entry point + "one profile per user" check
      Long save(@NonNull MasterProfileSaveDto dto, @NonNull Long actingUserId);
      void delete(@NonNull Long id, @NonNull Long actingUserId, Long version);
      Set<Long> findExistingIds(@NonNull Set<Long> ids);              // AuditDomainHookImpl.findExisting()
      Set<Long> findOwnerIds(@NonNull Set<Long> userIds);             // mirrors AdvertisementPort — for user-cleanup FK checks
      void clearActorReferences(@NonNull Set<Long> userIds);          // mirrors AdvertisementPort
  }
  ```

### 2. New module: `master-spring-boot-starter`

- `pom.xml` — copy `advertisement-spring-boot-starter/pom.xml`'s shape exactly: depends on
  `platform-commons` + `query-lib`, `spring-boot-starter-data-jdbc`, `spring-boot-liquibase`,
  `spring-boot-starter-validation`, owasp-sanitizer + jsoup (for `about` text), same
  `maven-enforcer-plugin` `bannedDependencies` block (add `master-spring-boot-starter` to every
  *other* starter's banned list too, and add every other starter to this one's).
- Add `<module>master-spring-boot-starter</module>` to the root `pom.xml`.
- `entity/MasterProfile.java`: `id`, `userId` (`BIGINT NOT NULL UNIQUE`, FK to
  `user_information.id`), `about` (`VARCHAR`, sanitized HTML), `cityTaxonId` (nullable `BIGINT`),
  `createdAt`/`updatedAt` (`@CreatedDate`/`@LastModifiedDate`), `version` (`@Version`, same
  optimistic-locking convention as `Advertisement`/`Taxon` — see `marketplace-app/DECISIONS.md`
  ADR-029).
- `db/master-changelog/changes/01-master-profile-schema.xml`: `master_profile` table
  (`user_id BIGINT NOT NULL UNIQUE REFERENCES user_information(id)`, `about VARCHAR(20000)`,
  `city_taxon_id BIGINT`, `created_at`/`updated_at TIMESTAMP WITH TIME ZONE`, `version BIGINT`),
  index on `user_id` (already unique, but explicit index for the FK lookup path) and on
  `city_taxon_id`. `master-changelog-master.xml` includes it, same shape as
  `advertisement-changelog-master.xml`.
- `repository/MasterProfileRepository.java`: `JdbcClient`-based, mirrors
  `AdvertisementRepository`'s shape (`ROW_MAPPER`, `SqlFilterBuilder<MasterProfileFilterDto>
  FILTER` with `categoryIds`/`cityTaxonId` bound conditions reusing `query-lib`'s `SqlCondition`
  factories) — `*CrudRepository extends CrudRepository<MasterProfile, Long>` for
  `save()`/`findById()`, custom `JdbcClient` methods for `findByFilter`/`findByUserId`/
  `countByFilter`.
- `services/MasterProfileService.java`: create/update, sanitizes `about` via OWASP HTML
  Sanitizer (same `Sanitizers.FORMATTING.and(LINKS).and(BLOCKS)` config as
  `AdvertisementService.sanitizeHtml()`), enforces one-profile-per-user (checks
  `findByUserId(actingUserId)` before insert, throws if a profile already exists and this isn't an
  update of the caller's own row), wires category assignments through `ComponentFactory<TaxonPort>`
  (`EntityType.MASTER_PROFILE`), enriches with actor name/email via `ComponentFactory<UserPort>`
  (mirrors `AdvertisementService.enrichWithActorInfo()`).
- `spi/MasterProfilePortImpl.java`: pure delegation to `MasterProfileService`, per
  `platform-commons/CLAUDE.md`'s `*PortImpl` rule.
- `config/MasterProfileAutoConfiguration.java`: mirrors `AdvertisementAutoConfiguration` — `@Bean
  masterLiquibase` with `@DependsOn("userLiquibase")` (FK to `user_information`),
  `@EnableJdbcRepositories(basePackages = "org.ost.masterprofile.repository")`,
  `ComponentFactory<MasterProfilePort>` bean. No cleanup scheduler needed unless profiles get
  soft-delete (decide: profiles likely hard-delete-on-user-delete via `clearActorReferences`-style
  cascade, not their own retention window — confirm during implementation).

### 3. `marketplace-app`

- **New Masters tab** (`ui/views/main/tabs/masters/`) — `MastersView` mirrors `AdvertisementsView`'s
  `init()` structure (CSS class → grid/cards → contentWrapper → overlay → subscriptions →
  `refresh()`), `MasterProfileCardView` mirrors `AdvertisementCardView`, `MasterProfileQueryBlock`/
  `MasterProfileFilterMeta`/`MasterProfileSortMeta` mirror the Advertisement query-layer trio
  exactly (`Fields.*` constants, `FilterFieldMeta`/`SortFieldMeta`).
- **Overlay**: `MasterProfileOverlay` (`OverlaySession`, `switchTo()`/`launchSession()` per the
  documented Overlay pattern), `MasterProfileFormOverlayModeHandler` (buildBinder separate,
  category `MultiSelectComboBox` + city `ComboBox` exactly like
  `AdvertisementFormOverlayModeHandler`'s, `about` field as a `QuillEditor` same as
  advertisement's description), `MasterProfileViewOverlayModeHandler` (`AbstractViewOverlayModeHandler`,
  category/city chip rows via the same `buildChipRow()` shape).
- **Header entry point** (`HeaderBar.java`): new `createMasterProfileButton()` next to
  `createSettingsButton()`/`createLogoutButton()` in `initUserInfoRow()`'s logged-in branch —
  calls `ComponentFactory<MasterProfilePort>.findByUserId(currentUser.id())`: if present, opens
  `MasterProfileOverlay` in VIEW mode on that profile; if absent, opens it in CREATE mode. New
  `HEADER_MASTER_PROFILE`/`HEADER_CREATE_MASTER_PROFILE` i18n keys, label switches based on which
  case applies (mirrors the login/signup button pair's conditional shape in
  `initUserInfoRow()` already).
- **`spi/AuditDomainHookImpl.java`**: add `case MASTER_PROFILE -> masterProfilePortFactory
  .findIfAvailable().map(p -> p.findExistingIds(entityIds)).orElse(Set.of());` — the compiler
  forces this, per point 1 above.
- **`services/masterprofile/MasterProfileEnrichService.java`**: mirrors
  `AdvertisementAuditEnrichService` — resolves `categoryIds`/`cityTaxonId` to display names in
  activity/timeline diffs via `TaxonPort.findByIds()`, same `resolveField()` reuse.
- **`spi/MasterProfileActivityFieldsHookImpl.java`** / **`spi/MasterProfileActivityEnrichHookImpl.java`**:
  one pair per new domain, same shape as the Advertisement pair — **add every `Fields.*` case from
  the start** (the exact bug class F-02/F-03 both hit and had to hotfix — do not repeat it here a
  third time).
- **`OgMetaRequestListener.java`**: extend `AD_PATH` matching — add a second `MASTER_PATH =
  Pattern.compile("^/masters/(\\d+)")`, inject `ComponentFactory<MasterProfilePort>`, branch
  `modifyIndexHtmlResponse()` on which pattern matched, build OG tags from `MasterProfileDto`
  (`og:title` = master's user name, `og:description` = excerpt of `about`, `og:type` = "profile"
  not "product").
- **`SitemapController.java`**: add a second `allMasterProfiles(port)` stream alongside
  `allAdvertisements(port)`, appending `<url>` entries for `appLinkService.masterProfileUrl(id)`.
- **`AppLinkService.java`**: add `masterProfileUrl(@NonNull Long id)` mirroring
  `advertisementUrl()` exactly (`publicBaseUrl + "/masters/" + id`).
- **`MasterProfileDeepLinkView.java`** (`ui/views/main/tabs/masters/`): `@Route("masters")`,
  `implements HasUrlParameter<Long>`, mirrors `AdvertisementDeepLinkView` exactly (session
  attribute + `event.forwardTo("")`).
- **URL slug** (`/masters/42-ivan-plytochnyk`): include from day one per the spec's own suggestion
  — cheap (slug is a display-only suffix, ignored on lookup, just parse the leading digits same as
  `AdvertisementDeepLinkView`'s `Long adId` parameter already does via Vaadin's URL parameter
  binding — confirm Vaadin's `HasUrlParameter<Long>` tolerates a trailing slug segment or needs a
  custom parameter type; if not, switch to `HasUrlParameter<String>` and parse the leading digits
  manually).
- **Portfolio photos**: reuse `AttachmentGalleryService`/`AttachmentPort` exactly as Advertisement
  does, keyed by `EntityRef(EntityType.MASTER_PROFILE, profileId)` — no new attachment-starter code
  needed, this SPI is already entity-type-agnostic.

## Explicitly out of scope (per the feature spec)

- Reviews (F-06) and verification badges (F-07) — this issue only builds the profile object they
  attach to later.
- Schedules, price lists, certificates — explicit spec note: scope-creep magnets, cut for now,
  post-launch iteration fed by real usage.
- "Their active OFFER listings" list on the profile page — nice-to-have from the spec; confirm
  during implementation whether it ships in this pass or as a fast-follow (needs `AdvertisementPort
  .findByCreator(userId)` filtered to `AdKind.OFFER`, already available — low cost if included).

## Testing strategy

- Unit tests: `MasterProfileServiceTest`-equivalent coverage for sanitization + one-profile-per-user
  enforcement, mirroring existing service test shapes.
- Integration tests: new `integration-tests/.../masterprofile/MasterProfileRepositoryTest`, added to
  `TestDataCleaner.cleanAll()` (new table, FK-safe position — after `user_information`, before
  nothing depends on it yet).
- Playwright: **this is a new page type, not an extension of existing advertisement tests** — unlike
  F-02/F-03, there's no existing "masters" flow to fold into. New spec file
  `08-master-profile-flow.spec.js` + new `_flows/master-profile.flow.js`, following the same
  create/edit/restore/delete lifecycle shape as `advertisement.flow.js`, reusing `city.flow.js`/
  `category.flow.js`'s selection helpers where the UI shape matches (category multi-select, city
  single-select).

## Related

- `private/features/F-04-master-profile.md` — full product spec (gitignored).
- `private/roadmap.md` — Phase 2 ("Supply + demand side"), item #1.
- [improvement-002](improvement-002-snapshot-schema-versioning.md) — hard dependency, same batch.
- [improvement-122](../completed/issues/improvement-122-f03-listing-types.md) — F-03, the most
  recent precedent for adding a new domain-scoped filter/snapshot field; the `labelFor()`/
  `ActivityFieldsHookImpl` bug class it hit twice must not repeat here.
- `marketplace-app/DECISIONS.md` ADR-065 (F-02 city facet — `TaxonType.CITY` reuse pattern this
  profile's city field follows) and ADR-034 (actor-reference column naming, `UserPort` bulk lookup
  pattern this profile's actor enrichment follows).
