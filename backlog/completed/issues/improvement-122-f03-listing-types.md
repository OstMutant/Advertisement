# improvement-122: F-03 — listing types (offer / request / product)

**Type:** feature — product roadmap Phase 1, item #3, the last piece of "Shareability foundation"
(F-01 and F-02 already shipped — see `improvement-117`/`improvement-119` in
`backlog/completed/`).
**Module:** `platform-commons` (new `advertisement.model.AdKind`, DTO fields),
`advertisement-spring-boot-starter` (schema, entity, repository, filter), `marketplace-app`
(query block, form/view overlay, card, activity-diff label, i18n).
**Priority:** high — completes the Phase 1 gate ("every card shareable... catalog filterable by
city and type"); ~2-3 day estimate per the feature spec, no blockers.
**When:** independent, no blockers. F-02 already extended the same touchpoints (filter meta, query
block, card, snapshot DTO) this issue extends again — same shape, proven pattern.

## Problem

Full spec: `private/features/F-03-listing-types.md` (gitignored product doc; this issue is the
trackable counterpart). The catalog is currently one mixed feed. The three real audiences from the
source FB group post by convention already: майстер пропонує послугу (OFFER), клієнт шукає майстра
(REQUEST), магазин рекламує товар (PRODUCT) — the catalog should let visitors filter to what they
came for instead of scrolling a mixed feed. This also unblocks later roadmap items: F-08 promo
pricing per type (shops pay more for PRODUCT visibility), F-11 request board (`type=REQUEST` +
response flow), master profiles aggregating their OFFERs.

## Suggested fix

Unlike F-02 (which added zero schema — city reused `taxon_assignment`), listing type is a genuine
new column on `advertisement` itself, since it's a property of the advertisement row, not an
assignment to an external dictionary. Verified against current code (not assumed) before writing
this plan — every touchpoint below is grounded in the actual current file content, not the F-02
issue's shape copied blindly.

1. **`platform-commons`:** new enum `AdKind { OFFER, REQUEST, PRODUCT }` in
   `org.ost.platform.advertisement.model` (new package — mirrors `taxon.model`/`user.model`/
   `attachment.model`'s existing role in their own subsystems, confirmed no `advertisement.model`
   package exists yet). Add `adKind` to `AdvertisementInfoDto` (`AdKind`, required —
   unlike `cityTaxonId` this is never null after backfill), `AdvertisementSaveDto` (`@NotNull
   AdKind adKind` — new required record component, positioned after `description`
   before `categoryIds` to match creation-form field order), `AdvertisementFilterDto`
   (`Set<AdKind> adKinds` — plural, multi-select like `categoryIds`/`UserFilterDto
   .roles`, not singular like `cityTaxonId`), and `AdvertisementSnapshotDto` (`AdKind
   adKind` — add to `diff()`/`allFields()` following the exact `cityTaxonId` pattern already
   there, i.e. `Fields.adKind` compared via `Objects.equals` and formatted via
   `adKind.name()` or `""` when null pre-backfill).
2. **Liquibase:** edit `advertisement-spring-boot-starter/src/main/resources/db/advertisement
   -changelog/changes/01-advertisement-schema.xml` **in place** (not a new incremental changeset —
   confirmed this project's own convention: the DB has never been released, see
   `marketplace-app/DECISIONS.md` ADR-064's identical precedent for this same table). Add
   `ad_kind VARCHAR(20) NOT NULL DEFAULT 'OFFER'` to the `advertisement` table.
3. **`Advertisement` entity** (`advertisement-spring-boot-starter/.../entity/Advertisement.java`):
   add `AdKind adKind;` field — Spring Data JDBC maps enums to `VARCHAR` natively via
   `.name()`, same as `Role role` already does on the `User` entity (confirmed existing precedent,
   no custom converter needed).
4. **`AdvertisementRepository`:**
   - `ROW_MAPPER`: add `.adKind(AdKind.valueOf(rs.getString("ad_kind")))`.
   - Add `a.ad_kind` to the column list of all three custom `SELECT`s
     (`findAdvertisementById`, `findByCreator`, `findByFilter`).
   - `FILTER`: add `SqlBoundFilter.of(adKinds, "a.ad_kind", (m, v) ->
     inSet(m, v.getAdKinds()))` — this is the literal `UserRepository.roles` pattern
     (`inSet(m, v.getRoles())` on an enum `Set`), the closest existing precedent, confirmed by
     reading `UserRepository.java` directly.
   - `buildEntity()` in `AdvertisementService`: add `.adKind(dto.adKind())`.
5. **`AdvertisementSaveService`:** both `AdvertisementSnapshotDto` construction sites (`after` in
   `save()`, and `buildCurrentSnapshot()`) need `saved.getAdKind()`/`ad.getAdKind()`
   threaded in as the new positional argument — no taxon lookup needed, `adKind` comes
   straight off the already-enriched `AdvertisementInfoDto`, unlike category/city.
6. **`marketplace-app` UI, mirroring the city/category touchpoints file-for-file:**
   - `AdvertisementEditDto`: add `AdKind adKind` field.
   - `AdvertisementFormOverlayModeHandler`: add a `RadioButtonGroup<AdKind>` field (new
     Vaadin component for this codebase — no `RadioButtonGroup` used anywhere yet, confirmed via
     grep; standard choice for "exactly one of 3, always visible, has a default" per Vaadin
     convention, better fit than the existing `ComboBox`/`MultiSelectComboBox` patterns used for
     optional/multi-value fields). Default-select `OFFER` for new ads. Required field (unlike
     `cityComboBox`, no `!= null` conditional-visibility guards needed — always present). Thread
     through the same 9 touchpoints the F-02 city field touched in this file (confirmed by
     grep — `activate()`'s field construction + labeling, `buildBinder()`'s `.bind(...)`,
     `save()`'s `AdvertisementSaveDto` construction, `discardChanges()`, `loadRestored()`,
     `handleRestoreFromActivity()` ×2, the two `tgt.set...`/`dto.set...` copy sites).
   - `AdvertisementFilterMeta`: add `AD_KINDS` — `FilterFieldMeta<Set<AdKind>,
     AdvertisementFilterDto, Set<AdKind>>`, mirroring `UserFilterMeta.ROLES` exactly (closest
     existing precedent — plain enum `Set`, no DTO-to-id mapping needed unlike `CATEGORY_IDS`).
   - `AdvertisementQueryBlock`: add a "Listing type" filter row — `MultiSelectComboBox<AdKind>`
     with `.setItems(AdKind.values())`, `.setItemLabelGenerator(t ->
     i18nService.get(I18nKey.forAdKind(t)))`, mirroring the existing Categories row's shape
     (multi-select, since the spec explicitly wants `inSet`-style multi-value filtering — "show me
     OFFER and PRODUCT, hide REQUEST" — not the single-select shape city uses). **Decided
     2026-07-27:** filter-row in the existing query panel, not visual tabs — the spec's ambiguous
     "filter/tabs" wording is resolved in favor of the filter row (reuses proven F-02
     infrastructure at near-zero new risk; visual tabs would conflict with the spec's own `inSet`
     multi-select requirement and would be a much larger, separate UI restructuring of
     `AdvertisementsView`, out of scope for this issue's 2-3 day estimate).
   - `AdvertisementCardView`: two additions per the spec's explicit split — (1) a small type badge
     (new `Span`, positioned near the title, using `I18nKey.forAdKind(...)`, mirroring the
     existing `advertisement-thumbnail-badge` shape) and (2) a CSS modifier class on the card root
     itself (`advertisement-card--offer`/`--request`/`--product`, added in `configure()` alongside
     the existing `addClassName("advertisement-card")` in `init()`) for the "existing BEM-ish
     convention in advertisement-card.css" the spec calls out — confirmed `advertisement-card.css`
     exists at `marketplace-app/src/main/frontend/themes/my-app/`.
   - `AdvertisementViewOverlayModeHandler`: show the same type badge in the view overlay (mirrors
     the existing category/city chip row's presence there).
   - `AdvertisementActivityFieldsHookImpl.labelFor()`: **add the `adKind` case from the
     start** — this is the exact bug class improvement-119 shipped with and then had to hotfix
     (`cityTaxonId` missing from this switch meant the Activity tab showed the raw field key
     instead of a translated label; see `marketplace-app/DECISIONS.md` ADR-065's "Update
     (2026-07-25...)" section). Do not repeat it: add `case AdvertisementSnapshotDto.Fields
     .adKind -> i18n.get(I18nKey.CHANGES_FIELD_AD_KIND);` in the same commit that adds
     the field, not as a follow-up fix.
   - `I18nKey`: typed entries per the "no dynamic keys" rule already in force — one key per type
     for the radio label/filter item/badge text (`ADVERTISEMENT_AD_KIND_OFFER`/`_REQUEST`/
     `_PRODUCT`), plus a `CHANGES_FIELD_AD_KIND` for the activity-diff label, plus a static
     `I18nKey.forAdKind(AdKind)` switch method mirroring the existing
     `I18nKey.forAction(ActionType)` method exactly (same file, same shape, confirmed by reading
     it directly).
7. **Testing — extend existing tests, not new spec files** (explicit project preference from the
   F-02 session, applies here too): fold listing-type selection into the existing spec 04
   create/edit advertisement tests (`runCreateAdvertisementFlow`/`runEditAdvertisementFlow` in
   `advertisement.flow.js` — add a `adKind` param mirroring the `city` param added for F-02),
   fold a type filter check into spec 05's existing seed/filter test (mirroring `fillCategory`/
   `fillCity` in `filter.flow.js`), fold a card-badge/CSS-modifier check into the same card
   assertions F-02 already added. Unit test for `AdvertisementSnapshotDto.diff()`'s new
   `adKind` field, mirroring the `cityTaxonId` diff tests added in improvement-119's
   integration-tests module.

## Explicitly out of scope (per the feature spec)

- Per-type promo pricing (F-08) — this issue only adds the type facet, not monetization.
- Request-response flow (F-11) — `type=REQUEST` filtering is enough for now; the response
  mechanic is a separate, later feature.
- Master-profile OFFER aggregation (F-04) — depends on this shipping first, not bundled here.

## Related

- `private/features/F-03-listing-types.md` — full product spec (gitignored).
- `private/roadmap.md` — Phase 1 ("Shareability foundation"), item #3, completes the phase gate.
- [improvement-119](../completed/issues/improvement-119-f02-city-dictionary-geo-filter.md) — F-02,
  the direct precedent this plan mirrors touchpoint-for-touchpoint (filter meta, query block, card
  line/badge, snapshot diff, activity-field label). Also the source of the `labelFor()` bug class
  this plan explicitly pre-empts (see point 6 above) and the `AdvertisementEnrichService
  .resolveField()`/`AdvertisementService.resolveTaxonIdFilter()` DRY pattern worth checking for
  reuse if the city/category filter-combination logic (`resolveTaxonFilter()`) needs a third
  AND-branch for `adKinds` — verify during implementation whether `adKinds` should
  intersect with the category/city taxon-filter result the same way, or is independent (it's a
  plain column filter via `inSet`, not a taxon-assignment lookup, so it likely stays entirely
  separate in `AdvertisementRepository.FILTER`, not routed through `resolveTaxonFilter()` at all —
  confirm this during implementation, don't assume).
- `marketplace-app/DECISIONS.md` ADR-065 — F-02's full implementation record and the two real bugs
  found during its post-implementation review, both directly relevant precedents for this issue.
