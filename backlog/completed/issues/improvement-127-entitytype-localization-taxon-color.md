# improvement-127: EntityType localization + TAXON missing color in Timeline badge

**Type:** improvement — i18n gap + visual gap, found during improvement-126's investigation
**Module:** marketplace-app
**Priority:** low — carved out of improvement-126, not being worked on now
**When:** independent, no blockers

## Problem A — `EntityType` shown as raw, unlocalized enum name

`AuditTimelineRowRenderer.typeSpan()` (lines 89-94) renders `item.entityRef().entityType().name()`
directly — the raw Java enum name (`ADVERTISEMENT`, `USER`, `USER_SETTINGS`, `TAXON`) — with no
i18n lookup at all. `I18nKey` (`marketplace-app/services/i18n/I18nKey.java`) has `forAction()` and
`forAdKind()` static helpers for other enums but no equivalent `forEntityType()`, and no
`ENTITY_TYPE_*` keys exist.

## Fix A

1. Add an `ENTITY_TYPE_ADVERTISEMENT` / `ENTITY_TYPE_USER` / `ENTITY_TYPE_USER_SETTINGS` /
   `ENTITY_TYPE_TAXON` key family to `I18nKey`, plus a `forEntityType(EntityType)` static method
   (mirrors `forAdKind(AdKind)`'s exact shape).
2. Add the 4 EN + 4 UK translated labels to `messages_en.properties`/`messages_uk.properties` —
   exact wording not decided yet, get it from the user before applying (same rule as
   improvement-125's label rename).
3. `AuditTimelineRowRenderer.typeSpan()` — call `i18n.get(I18nKey.forEntityType(entityType))`
   instead of `entityType.name()`. CSS class name generation (`"activity-feed-type--" +
   typeName.toLowerCase()`) stays keyed off the raw enum name internally — only the *displayed
   text* changes, not the CSS class.

## Problem B — `TAXON` has no color in the Timeline entity-type badge

`activity-feed.css` (lines 44-66) defines `.activity-feed-type--advertisement` (amber),
`.activity-feed-type--user` (blue), `.activity-feed-type--user_settings` (violet) — `TAXON` has no
matching modifier rule at all, so it silently falls back to the unstyled default
(`.activity-feed-type` base rule only — no background/text color).

## Fix B

Add `.activity-feed-type--taxon` to `activity-feed.css`, following the same pattern as the other
three. Needs a genuinely distinct color not already used by `advertisement`/`user`/`user_settings`
— not decided yet, get confirmation from the user before applying (candidate considered but not
approved: reusing `--app-status-moderator-bg`/`--app-status-moderator-text`, currently only used
for the MODERATOR role badge — reusing a role color for an entity type could itself read as
confusing, same open question improvement-125 flagged about color reuse across taxonomies).

## Related

- [improvement-126](improvement-126-timeline-activity-diff-findings.md) — the display-name
  duplication fix this was carved out of; same investigation round.

## Decisions (2026-07-28)

- Wording: singular, nav-consistent — `Advertisement` / `User` / `User Settings` / `Category`
  (not `Taxon` — matches `reference.data.tab.categories` wording already used in the nav).
  UK: `Оголошення` / `Користувач` / `Налаштування користувача` / `Категорія`.
- TAXON badge color: brand-new teal, not reused from any existing status/action color —
  `--app-status-entity-taxon-bg: #ccfbf1` / `--app-status-entity-taxon-text: #0f766e`.
- Scope widened by one item found during investigation: `TimelineQueryBlock`'s "Entity type"
  filter dropdown (`EntityType.values()` fed into `QueryMultiSelectComboField` with no
  `itemLabelGenerator`) has the exact same raw-enum-name bug as the Timeline row badge — fixed in
  the same pass since `I18nKey.forEntityType()` is already being added. `TimelineQueryBlock`'s
  Action-type filter has the same class of gap but is out of scope (not part of this issue).

## Execution plan

1. `marketplace-app/services/i18n/I18nKey.java` — new `// === Entity Type ===` section (near
   `// === Timeline Filter ===`): `ENTITY_TYPE_ADVERTISEMENT("entityType.advertisement")`,
   `ENTITY_TYPE_USER("entityType.user")`, `ENTITY_TYPE_USER_SETTINGS("entityType.userSettings")`,
   `ENTITY_TYPE_TAXON("entityType.taxon")`; add `import org.ost.platform.core.model.EntityType;`
   and a `forEntityType(EntityType)` static method next to `forAdKind(AdKind)`.
2. `messages_en.properties` / `messages_uk.properties` — add the 4 keys above with the wording
   decided above.
3. `AuditTimelineRowRenderer.java` — `typeSpan(String)` → `typeSpan(EntityType)` (drop `static`,
   now needs the instance `i18n` field): label via `i18n.get(I18nKey.forEntityType(entityType))`,
   CSS modifier class still keyed off `entityType.name().toLowerCase()`. Update the one call site.
4. `styles.css` — add `--app-status-entity-taxon-bg` / `--app-status-entity-taxon-text` next to
   the existing `--app-status-entity-advertisement-*` pair.
5. `activity-feed.css` — add `.activity-feed-type--taxon` following the existing
   `--advertisement`/`--user`/`--user_settings` modifier pattern.
6. `TimelineQueryBlock.java` — add `entityTypeField.setItemLabelGenerator(t ->
   i18nService.get(forEntityType(t)));`, mirroring `AdvertisementQueryBlock`'s
   `adKindField.setItemLabelGenerator(t -> i18nService.get(forAdKind(t)));` pattern exactly.
7. Verify: `bash scripts/deploy-dev.sh`, then Playwright timeline spec (visual + filter-dropdown
   check) with `--ux`.

## Resolution (2026-07-28)

Implemented exactly per the execution plan above, via `/autopilot`. Verified: `scripts/unit-
tests.sh` (77/77), `scripts/integration-tests.sh --sandbox` (130/130), `scripts/deploy-dev.sh`,
and `scripts/playwright.sh e2e --full --ux` (50/50, including the `taxon-06-timeline-all-fields-
in-diff` screenshot confirming the new teal `Category` badge renders correctly in the live app).
