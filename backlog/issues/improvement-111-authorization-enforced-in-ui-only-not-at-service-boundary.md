# improvement-111: Authorization is enforced only in the UI — the service/port boundary trusts actingUserId without an ownership/role check

**Type:** improvement — security architecture (defense-in-depth gap; latent, not exploitable
today). Found via edge-case review (2026-07-19).
**Module:** `advertisement-spring-boot-starter` (`AdvertisementService`, `AdvertisementPortImpl`),
`user-spring-boot-starter` (`UserService`), `taxon-spring-boot-starter` (`TaxonService`);
authorization currently lives in `marketplace-app` (`services/security/AccessEvaluator`)
**Priority:** medium — no open hole in the current UI-only architecture, but mandatory to resolve
before the first non-UI mutation caller (REST/seeding/OG-bot) exists
**When:** Deferred — trigger: before any non-UI endpoint that mutates domain state ships (hard
gate, same shape as the completed improvement-020 security baseline); needs a design decision
first

## Problem

Mutating service/port methods take an `actingUserId` but never check it against the target
entity's owner or the caller's role:

```java
public Long save(@NonNull @Valid AdvertisementSaveDto dto, @NonNull Long actingUserId) {
    Optional<Advertisement> before = dto.id() == null ? Optional.empty() : repository.findById(dto.id());
    Advertisement ad = buildEntity(dto, before.orElse(null));   // preserves before.createdBy
    return repository.save(ad).getId();                          // no owner/role check
}
public void delete(@NonNull Long id, @NonNull Long actingUserId, Long version) { /* captures + soft-deletes; no check */ }
```

`actingUserId` is used only as the audit actor and to preserve `createdBy`. All authorization —
ownership and role — lives in `marketplace-app`'s `AccessEvaluator` (`canOperate`,
`isPrivileged`), consulted purely to gate button visibility / early-return in click handlers
(`AdvertisementCardView`, the `*OverlayModeHandler`s, `UserView`).

**Not exploitable today:** the Vaadin UI is the only caller, server-side component state means a
non-owner has no edit overlay wired to another user's entity, and there is no REST endpoint that
takes an id + payload. This is missing defense-in-depth, not an open hole.

**Why it still matters:**
- The first non-UI mutation caller (F-01 OG/sitemap, improvement-073 seeding endpoints, any future
  API) inherits **zero authorization** at the port — save/delete any entity by id.
- It's inconsistent with the project's own "Strict Boundaries" intent and the completed
  improvement-020 ("security baseline before public endpoints"): the port is the module boundary,
  but authorization sits above it, so the boundary is porous for authz.
- `@PreAuthorize` on services is deliberately absent (Vaadin view-wiring reason, see
  `marketplace-app/CLAUDE.md`) — coherent for a pure SPA, a landmine at the first endpoint.

## Suggested fix (decision first)

Two coherent options — pick and record in a DECISIONS.md entry:

1. **Move authorization to the service boundary:** the port/service verifies `actingUserId` may
   modify the target (owner-or-privileged) before mutating, throwing a dedicated
   `AccessDeniedException` the UI already-gated path will simply never hit. Makes every caller
   safe by construction. Requires the service to resolve role/ownership (via `UserPort` /
   `created_by`), which `AccessEvaluator` already does — extract the rule so both share it.
2. **Keep UI-only authz, but make the invariant enforceable:** an ArchUnit/convention rule that
   every non-Vaadin controller must carry an explicit authorization check, plus a documented
   "services trust their caller; the caller is responsible for authz" contract. Cheaper now,
   relies on discipline at each new endpoint.

Option 1 is the safer default for a project heading toward public endpoints; option 2 defers the
cost but must land its guard before the first endpoint, not after.

## Related

- `backlog/completed/issues/improvement-020-security-baseline-before-public-endpoints.md` — the
  precedent; this is its service-layer counterpart.
- `backlog/issues/improvement-073-rest-endpoint-infrastructure-test-seeding.md` — the first
  concrete non-UI caller; whichever option wins must be in place before 073's endpoints mutate
  state.
- `marketplace-app/CLAUDE.md` — "Services intentionally have no `@PreAuthorize`" — this issue
  revisits that decision at the boundary, not by re-adding class-level annotations.
