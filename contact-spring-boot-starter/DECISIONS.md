# Architecture & Technical Decisions — contact-spring-boot-starter

---

## ADR-002: Only a provider profile owns contact_info; an advertisement shows its owner's contact
**Status:** Accepted
**Applied:** 1

**Context:** The first design let an advertisement carry its own `contact_info` row as an optional
per-listing override of its owner's provider-profile contact. The override was dropped: no code
path writes an advertisement row, contacts are edited only in the provider profile form, and the
advertisement screens only ever read the owner's profile contact. The lookup of an advertisement's
own row stayed behind as dead code and several descriptions still described the override.

**Decision:** Only a `PROVIDER_PROFILE` owns a `contact_info` row. An advertisement never has one;
`marketplace-orchestrator` shows it the contact of its owner's provider profile
(`ContactAccessService.resolveContact`, which rejects any other entity type). `contact_info` stays
generic over `(entity_type, entity_id)` and `contact_view` stays generic, recording each reveal
against the entity that was shown (an advertisement id for a reveal on an advertisement). A reveal
on an advertisement is therefore counted per advertisement, while the owner's monthly counters
cover only reveals made on the profile.

**Rejected alternatives:** Narrowing `contact_info` to a provider-profile id: no safety gain (no
cross-module foreign keys exist anyway), it would make the contact module name another module's
concept and break the `entity_type` + `entity_id` convention shared with the audit, feedback and
attachment tables. Bringing back a per-listing override: nothing writes it and it would duplicate
the profile's contacts.

---

## ADR-001: New contact-spring-boot-starter module owns contact_info/contact_view, generic over owning entity
**Status:** Accepted
**Verified:** 2026-10-27

**Context:** Contact data (phone/Telegram/Viber) and contact-reveal click analytics are needed by
both `PROVIDER_PROFILE` and `ADVERTISEMENT` entities (an advertisement shows its owner's provider
profile contact). Neither
`provider-profile-spring-boot-starter` nor `advertisement-spring-boot-starter` can own this alone
without either duplicating the logic across both or one starter reaching into the other — forbidden
by the "no direct imports between sibling modules" rule.

**Decision:** Dedicated `contact-spring-boot-starter`, mirroring why `audit-spring-boot-starter`
exists as its own module rather than being owned by any one domain starter: generic over
`EntityType`, no FK to `provider_profile`/`advertisement`, zero knowledge of either starter's
internals. Two tables: `contact_info` (at most one row per `(entity_type, entity_id)`, upserted)
and `contact_view` (append-only reveal/click events, same immutable shape as `audit_log`).
`ContactPort` SPI declared in `platform-commons` (`contact.spi`), generic over
`(EntityType, Long entityId)`. Resolving an advertisement to its owner's `provider_profile` contact
is composed by `marketplace-orchestrator`, not implemented inside this starter.

**Consequences:** `provider-profile-spring-boot-starter` and `advertisement-spring-boot-starter`
gain no new columns/tables for this feature. Each channel (phone/Telegram/Viber) reveals and is
counted independently — `contact_view.channel`, not one combined counter.

**Rejected alternatives:** Adding `phone`/`telegram`/`viber` columns directly to `provider_profile`
and `advertisement` — rejected because it would duplicate reveal/analytics logic across both
starters and require one starter to query the other's table directly for fallback resolution.

---
