# promotion-spring-boot-starter

Auto-configures promotion placements: the reason an entry is lifted out of the catalog (a rating
today, a purchase later) is stored in one table keyed by entity type, id and reason, with no column
on any domain table. It is its own starter, keyed like `contact-spring-boot-starter` and
`feedback-spring-boot-starter`, so the reason stays pluggable and no domain starter has to reach
across the sibling-starter boundary.

## What it provides

- One table of promotion placements, unique per `(entity_type, entity_id, reason)`: which entry is
  promoted and for what reason, with no foreign key to the promoted entity.
- A read of the promoted placements of an entity type, for every reason this version knows (a row
  with an unknown reason is skipped), and three writes that each touch only the rows of one reason:
  replace a reason's whole set, add one entity, remove one entity.
- **SPI implementation:** `PromotionPlacementPort` (`platform-commons`), consumed by
  `marketplace-orchestrator`.

## Data flow

A caller (via `PromotionPlacementPort`) passes an entity type, a reason and entity ids. The port
implementation delegates straight through to the service, which resolves persistence through the
repository:
- A read selects the rows of that entity type, for the known reasons.
- A replace deletes the reason's rows missing from the given set and inserts the missing ones in one
  transaction, so every reason keeps exactly one writer's view and another reason's rows are never
  touched.
- Adding inserts one row (nothing happens when it is already there); removing deletes one row.

## Dependencies

- `platform-commons` — the port, its DTO and the reason enum (`promotion.*`) are this module's own
  contract; `EntityType` (`core.model`) is the generic entity key.
- No Maven dependency on any sibling starter (enforced by this module's own `maven-enforcer-plugin`
  rule) — what makes an entry qualify is decided in `marketplace-orchestrator`, never here.
