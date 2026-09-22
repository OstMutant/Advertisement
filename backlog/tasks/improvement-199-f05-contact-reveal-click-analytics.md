# improvement-199: F-05 — contact reveal + click analytics, F-11a — request board free MVP

**Type:** feature — new product capability (private/roadmap.md Phase 2, F-05 + F-11a)
**Module:** provider-profile-spring-boot-starter (contact fields), advertisement-spring-boot-starter
(optional per-listing contact override), platform-commons (contact DTOs, new event DTO/SPI),
marketplace-app (show-contacts UI interaction, master-side view counter)
**Priority:** 🟡 Top — next unblocked item in the private product roadmap (Phase 2, after F-04
shipped), user-requested Top placement, 2026-09-22
**When:** independent — depends only on F-04 (ProviderProfile), already shipped (improvement-124)

## Current state

`ProviderProfileDto` (platform-commons) has no phone/telegram/viber fields. No append-only event
table (`contact_view` or similar) exists in the schema. No contact-reveal-specific rate limiting
exists; the one existing `FailureRateLimiter` (`platform-commons`/`core`) is not currently wired to
this use case (`improvement-196` separately plans to reuse it for REST API rate limiting).

## Why change

Per `private/roadmap.md`, contact-reveal clicks are the platform's core demand-signal metric — the
number that later justifies PRO subscription pricing (F-09) and pay-per-lead pricing (F-11a/b/c).
No such metric exists today.

## Expected benefit

A measurable "contact reveals per week/per master" KPI, a structured phone/Telegram/Viber
deep-link contact block, and the event-log foundation F-11's pay-per-lead pricing will eventually
need.

## Approach

Full spec: `private/features/F-05-contact-reveal.md`. Summary:
- Validated phone/telegram/viber fields on provider profile (optionally per-listing override).
- Click-to-reveal "Show contacts" interaction — contacts never in the initial DOM, revealed via a
  server round-trip (`@ClientCallable` or equivalent) to prevent trivial scraping.
- Append-only `contact_view` event table (entity_ref, nullable viewer_id, timestamp) — no updates,
  aggregate in SQL on read until volume demands a rollup table.
- Per-master "Contact views: N this month" counter.
- Rate limit reveals per session/IP — reuse the existing `FailureRateLimiter` (`platform-commons`)
  rather than hand-rolling a second counter; check for shared-implementation overlap with
  `improvement-196` once both are picked up.

## Approach — F-11a: request board free MVP

Full spec: `private/features/F-11-request-board-leads.md`. Verified against current code
(2026-09-22): `AdKind.REQUEST` already exists (`platform-commons`), and
`AdvertisementFilterMeta.AD_KINDS` already supports filtering the catalog by listing type —
`F-03` (shipped) already covers the underlying mechanic the request feed needs.

Per the spec's own "re-phased"/"scope simplified further (review round 4)" notes, 11a's MVP has
**no response entity at all** — a master sees a request and clicks the F-05 "show contact"
button, calls the client directly. Given F-03's filter already exists, what's actually new to
implement here is small:
- A dedicated "Request board" framing (its own tab/view, not just a filter toggle inside the
  general catalog) over the existing `AdKind.REQUEST` filter — UI framing, not new business logic.
- Wiring F-05's contact-reveal interaction onto request cards specifically.

11b (matching + notifications) and 11c (points & packages) stay Phase 5, hard-dependent on F-07
(bot) and F-08 (payment rails) — out of scope here, not started.

**Note:** `private/features/F-11-request-board-leads.md`'s own "Scope (phased inside the
feature)" section (its "11a" bullet, describing masters replying with a pitch and both sides
getting notified) is stale — it describes an earlier response-flow design the same document's own
"re-phased"/"scope simplified further" notes above it explicitly superseded down to "NO response
entity at all in the MVP." Left unedited in the private spec file itself; noted here so the
discrepancy isn't silently acted on.

## Related

- `private/features/F-05-contact-reveal.md` — full feature spec (goal, user story, scope, tech
  notes, KPI, risks).
- `private/features/F-11-request-board-leads.md` — full feature spec, including the 11b/11c
  Phase-5 scope this task does not cover.
- `private/roadmap.md` — Phase 2 sequencing (F-05 + F-11a next after F-04).
- improvement-124 (completed) — F-04 provider profile, the dependency this feature builds on.
- `improvement-196` — separate REST-API rate-limiting task also planning to reuse
  `FailureRateLimiter`.
