# promotion-spring-boot-starter

Split out as its own bounded context rather than folded into `provider-profile-spring-boot-starter`
or `advertisement-spring-boot-starter`: the reason an entry is lifted out of the catalog (a rating
today, a purchase later) must be pluggable without a column on any domain table, and either domain
owning it would make the other reach across the sibling-starter boundary. The same
entity-type-plus-id keying that `contact-spring-boot-starter` and `feedback-spring-boot-starter`
use keeps the table generic over what is promoted.

## What it provides

- One table of promotion placements, unique per `(entity_type, entity_id, reason)`: which entry is
  promoted, for what reason and until when, with no foreign key to the promoted entity.
- A read of the placements active now for an entity type, for every reason this version knows (a row
  with an unknown reason is skipped), and three writes that each touch only the rows of one reason:
  replace a reason's whole set, add one entity, remove one entity.
- **SPI implementation:** `PromotionPlacementPort` (`platform-commons`), consumed by
  `marketplace-orchestrator`.

## Data flow

A caller (via `PromotionPlacementPort`) passes an entity type, a reason and entity ids. The port
implementation delegates straight through to the service, which resolves persistence through the
repository:
- A read selects the rows of that entity type, for the known reasons, whose validity window covers now.
- A replace deletes the reason's rows missing from the given set and inserts the missing ones in one
  transaction, so every reason keeps exactly one writer's view and another reason's rows are never
  touched.
- Adding inserts one row (nothing happens when it is already there); removing deletes one row.

## Dependencies

- `platform-commons` — the port, its DTO and the reason enum (`promotion.*`) are this module's own
  contract; `EntityType` (`core.model`) is the generic entity key.
- No Maven dependency on any sibling starter (enforced by this module's own `maven-enforcer-plugin`
  rule) — what makes an entry qualify is decided in `marketplace-orchestrator`, never here.
