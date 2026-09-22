# improvement-200: F-06 — reviews & ratings

**Type:** feature — new product capability (private/roadmap.md Phase 3, F-06)
**Module:** new `review-spring-boot-starter` (review table, port + hook in platform-commons,
own Liquibase changelog), `provider-profile-spring-boot-starter` (aggregate rating/count
denormalized onto profile), `marketplace-app` (review list, star rating UI, review form overlay,
moderation admin UI)
**Priority:** 🟡 Top — active roadmap item, sequenced after F-05/F-11a (`improvement-199`) per
`private/roadmap.md`'s Phase 2→3 order; user-requested Top placement, 2026-09-22
**When:** blocked on `improvement-199` landing first (Phase 2 before Phase 3, per roadmap order)
— depends only on F-04 (provider profile), already shipped (`improvement-124`)

## Current state

No review/rating table, port, or UI exists anywhere in the codebase — confirmed no
`review-spring-boot-starter` module exists.

## Why change

Per `private/roadmap.md`, reviews are the #1 trust signal and this project's stated "moat" vs
the Facebook group it's migrating users from — persistent reputation is the single strongest
reason to use the platform over the group, and the substance behind future PRO/badge
monetization (F-09).

## Expected benefit

A measurable trust signal (reviews/week, % masters with ≥10 reviews, contact-reveal uplift on
reviewed profiles) and a real competitive differentiator the FB group structurally cannot offer
(comment-thread reputation evaporates; a review stays).

## Approach

Full spec: `private/features/F-06-reviews-ratings.md`. Summary:
- New `review-spring-boot-starter`, mirroring the project's existing modular-starter pattern
  (own Liquibase changelog, Port + Hook in platform-commons): `review` table (`author_id`,
  `master_profile_id`, `rating`, sanitized text, `created_at`, `response_text`, `response_at`,
  moderation status).
- Rules: one review per author per master (unique constraint), registered users only, edit
  window (~48h), one master response per review.
- Moderation: visible by default (NEW), flag/report → hidden pending admin decision via the
  existing management area.
- Denormalized avg rating + count on the master profile, updated in the same transaction as
  the review write — read path stays one query; reconciled by a scheduled job (existing
  cleanup-service pattern).
- UI: paginated review list on profile, rating stars on master cards/search results, review
  form overlay.
- Audit: review create/edit/moderate through the existing audit starter.
- Anti-fraud minimum: registered-only, one-per-master, rate limit reviews/day per author,
  admin delete with audit trail.

## Related

- `private/features/F-06-reviews-ratings.md` — full feature spec (goal, user story, scope, tech
  notes, KPI, risks).
- `private/roadmap.md` — Phase 3 sequencing (F-06 + F-07 after F-05/F-11a).
- `improvement-199` — F-05/F-11a, sequenced immediately before this per roadmap order.
- improvement-124 (completed) — F-04 provider profile, the dependency this feature builds on.
