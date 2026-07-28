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
