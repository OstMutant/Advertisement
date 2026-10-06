# Architecture & Technical Decisions — contact-spring-boot-starter

---

## ADR-001: New contact-spring-boot-starter module owns contact_info/contact_view, generic over owning entity
**Status:** Accepted

**Context:** Contact data (phone/Telegram/Viber) and contact-reveal click analytics are needed by
both `PROVIDER_PROFILE` and `ADVERTISEMENT` entities (an advertisement's contact is an optional
per-listing override of its owner's provider profile contact). Neither
`provider-profile-spring-boot-starter` nor `advertisement-spring-boot-starter` can own this alone
without either duplicating the logic across both or one starter reaching into the other — forbidden
by the "no direct imports between sibling modules" rule.

**Decision:** Dedicated `contact-spring-boot-starter`, mirroring why `audit-spring-boot-starter`
exists as its own module rather than being owned by any one domain starter: generic over
`EntityType`, no FK to `provider_profile`/`advertisement`, zero knowledge of either starter's
internals. Two tables: `contact_info` (at most one row per `(entity_type, entity_id)`, upserted)
and `contact_view` (append-only reveal/click events, same immutable shape as `audit_log`).
`ContactPort` SPI declared in `platform-commons` (`contact.spi`), generic over
`(EntityType, Long entityId)`. Fallback resolution (ad's own `contact_info` row if present, else
the ad owner's `provider_profile` row) is composed by `marketplace-orchestrator`, not implemented
inside this starter.

**Consequences:** `provider-profile-spring-boot-starter` and `advertisement-spring-boot-starter`
gain no new columns/tables for this feature. Each channel (phone/Telegram/Viber) reveals and is
counted independently — `contact_view.channel`, not one combined counter.

**Rejected alternatives:** Adding `phone`/`telegram`/`viber` columns directly to `provider_profile`
and `advertisement` — rejected because it would duplicate reveal/analytics logic across both
starters and require one starter to query the other's table directly for fallback resolution.

---
