# improvement-111: Authorization is enforced only in the UI — the service/port boundary trusts actingUserId without an ownership/role check

**Type:** improvement — security architecture (defense-in-depth gap; latent, not exploitable
today). Found via edge-case review (2026-07-19).
**Module:** `advertisement-spring-boot-starter` (`AdvertisementService`, `AdvertisementPortImpl`),
`user-spring-boot-starter` (`UserService`), `taxon-spring-boot-starter` (`TaxonService`);
authorization currently lives in `marketplace-app` (`services/security/AccessEvaluator`)
**Priority:** 🟡 high — the trigger has fired: `improvement-073` (now expanded to include a real
external/public REST API, not just dev-gated test seeding) is being picked up, so this hard gate
must land first or alongside it.
**When:** Active — `improvement-073`'s external API scope is exactly the trigger this issue was
waiting for; a design decision (option 1 vs. option 2 below) is needed before or as part of that
work.

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

## Implementation Plan v2 (Option 1, relocated to `marketplace-orchestrator`), revised 2026-09-02

Superseding the first draft below (kept for history, see "Superseded v1 draft" at the bottom).
User-requested relocation: put the checks in `marketplace-orchestrator`'s save/delete services
instead of inside each starter.

**Why this still closes the gap, verified by grep, not assumed:** every mutating Port method
(`AdvertisementPort.save/delete`, `ProviderProfilePort.save/delete`, `UserAccountPort.save/delete`,
`TaxonPort.create/update/softDelete/restore`) is called **only** from `marketplace-orchestrator`
today — never directly from `marketplace-app` (already ArchUnit-enforced:
`ArchitectureRulesTest.marketplace_app_must_not_depend_on_platform_commons_spi_directly`) and never
from a sibling starter for a human-actor-authorized mutation (the only cross-starter Port calls
from inside a starter are read-only lookups or narrow referential-integrity cascades, e.g.
`UserService.clearActorReferences`/`findOwnerIds`, not a "does actor X own entity Y" mutation
check). `marketplace-orchestrator` is therefore the real, already-enforced choke point every
mutation must pass through — putting the check there is as strong as putting it in each starter,
without touching `platform-commons` SPI signatures or any starter's own code at all.

**Residual gap, disclosed:** a hypothetical *future* direct-Port caller added inside a starter or a
new module (bypassing orchestrator entirely) would still have zero authorization — same residual
Option 2 already accepted for non-REST callers an ArchUnit rule can't reach. Not closing this now;
flag if it ever becomes a real shape (e.g. a `@Scheduled` job mutating on a user's behalf).

### Step 0 — `marketplace-orchestrator` shared infrastructure

- New `org.ost.orchestrator.services.AccessDeniedException extends RuntimeException` (no
  Spring Security dependency needed — this module doesn't have one).
- Extend `AuthorizationService` (already exists, already the UI's own delegate) with an
  `ActorLookupService` field (existing shared collaborator) and three new methods, all resolving
  the acting `UserDto` once via `actorLookupService.findById(actingUserId)`:
  - `boolean canOperate(@NonNull Long actingUserId, Long ownerId)` — admin, moderator, or
    `ownerId != null && actingUserId.equals(ownerId)`; missing actor → `false`.
    `ownerId == null` naturally reduces to "admin or moderator" (covers Taxon's no-owner case).
  - `boolean canEditAccount(@NonNull Long actingUserId, @NonNull Long targetUserId)` — admin or
    self, **no moderator** — matches `AccessEvaluator.canEditUserAccount`'s stricter rule (a
    moderator gets read-only on accounts, never edit).
  - `void requireCanOperate(...)` / `void requireCanEditAccount(...)` — throwing wrappers around
    the two booleans above, `AccessDeniedException` on denial; every call site below uses the
    `require*` form, never the raw boolean.

### Step 1 — `AdvertisementSaveService`

- `save(dto, actorId, commitGallery)`: when `!isNew`, fetch
  `advertisementPortFactory.get().findById(dto.id())` (already exposes `createdBy`), call
  `authorizationService.requireCanOperate(actorId, existing.getCreatedBy())` before the port
  `save()` call. No check on create.
- `delete(id, actorId, version)`: fetch `advertisementPortFactory.get().findById(id)`, same
  `requireCanOperate` check, before the port `delete()` call.
- No `AdvertisementPort`/`AdvertisementService` signature changes.

### Step 2 — `ProviderProfileSaveService`

- `save(dto, targetUserId, actorId, actorIsPrivileged)` — **drop the trusted `actorIsPrivileged`
  parameter**; compute it internally instead: `boolean privileged = authorizationService.canOperate(actorId, null)`.
  Call `authorizationService.requireCanOperate(actorId, targetUserId)` at the top (self-or-privileged
  may write this profile). Pass the internally-computed `privileged` down to
  `providerProfilePortFactory.get().save(dto, targetUserId, actorId, privileged)` — the starter's
  own existing `kind == SUPPORT` check (documented, deliberate exception in
  `provider-profile-spring-boot-starter`) now receives a value orchestrator computed itself, not one
  the UI passed in — same starter-level defense-in-depth, now backed by a real check upstream too.
- `delete(id, actorId, version)`: fetch `providerProfilePortFactory.get().findById(id)` (exposes
  `actorId`, the row owner), `requireCanOperate(actorId, existing.getActorId())` before the port
  `delete()` call.
- `marketplace-app` call-site update: `ProviderProfileFormOverlayModeHandler` (line ~197-200) drops
  the trailing `access.isPrivileged()` argument from its `providerProfileSaveService.save(...)`
  call — orchestrator computes it now.
- No `ProviderProfilePort`/`ProviderProfileService` signature changes.

### Step 3 — `UserProfileService` / `UserDeleteService`

- `UserProfileService.save(dto, actingUserId)`: fetch `userPort.findById(dto.id())` first.
  `authorizationService.requireCanEditAccount(actingUserId, dto.id())` always. **Additionally**,
  if `existing.role() != dto.role()` (a real role-escalation path today — `UserProfileDto.save()`
  is the *only* write path for both name and role, the UI merely disables the role field rather
  than the service refusing the value), require the stricter `canEditRole` rule inline: admin
  *and* `!actingUserId.equals(dto.id())` (never self, even an admin's own role) — mirrors
  `AccessEvaluator.canEditRole` exactly. Surfaced as an adjacent real gap while implementing this
  issue, not originally in scope — flag for confirmation before folding it in (see question below).
- `UserDeleteService.delete(userId, actingUserId)`: `requireCanEditAccount(actingUserId, userId)`
  before cascading deletes.
- No `UserAccountPort`/`UserService` signature changes.

### Step 4 — `TaxonCatalogService`

- `create/update/softDelete/restore` — no ownership concept, privileged-only:
  `authorizationService.requireCanOperate(actorId, null)` before each port call.
- No `TaxonPort`/`TaxonService` signature changes.

### Step 5 — dedupe `AccessEvaluator` vs. `AuthorizationService` composition logic (found 2026-09-03) -- ✅ done 2026-09-03

Steps 0-4 landed `AuthorizationService.canOperate`/`canEditAccount`/`canEditRole` (id-only,
`ActorLookupService`-resolving) alongside `marketplace-app`'s pre-existing `AccessEvaluator`,
which composes the *same* three business rules (admin-or-moderator-or-owner /
admin-or-self / admin-editing-someone-else) independently, using an already-in-hand `UserDto`
from `CurrentUserService` instead of an id lookup. Real duplication: the same rule is expressed
in two places. Simply making `AccessEvaluator` call the new id-based methods would regress a
documented perf decision (`marketplace-orchestrator/CLAUDE.md`'s "Zero direct `*Port`/`*Hook`
references..." section) — `AccessEvaluator`'s checks run on nearly every UI render, and the
id-based methods each do a fresh `ActorLookupService.findById()` round-trip that the session-bound
`UserDto` doesn't need.

**Fix:** add `UserDto`-taking overloads to `AuthorizationService` holding the actual rule
composition; the existing id-based methods resolve once via `ActorLookupService` then delegate to
the `UserDto` overload; `AccessEvaluator` resolves `currentUser()` (already free) and delegates to
the same `UserDto` overload instead of re-implementing the boolean expression itself:

```java
// AuthorizationService — new overloads, the single source of truth for each rule
public boolean canOperate(UserDto actor, Long ownerId) {
    return isAdmin(actor) || isModerator(actor) || (ownerId != null && isOwner(actor, ownerId));
}
public boolean canOperate(@NonNull Long actingUserId, Long ownerId) {
    return actorLookupService.findById(actingUserId).map(u -> canOperate(u, ownerId)).orElse(false);
}
// canEditAccount(UserDto, Long) / canEditRole(UserDto, Long) + their id-based callers, same shape
```
```java
// AccessEvaluator — becomes a thin session-resolving wrapper, no rule logic of its own
public boolean canOperate(Long ownerUserId) {
    return currentUser().map(u -> authorizationService.canOperate(u, ownerUserId)).orElse(false);
}
```

Touches: `AuthorizationService` (+ `AuthorizationServiceTest`), `AccessEvaluator` (+ existing
27-test `AccessEvaluatorTest` — behavior must stay identical, only the implementation moves).
No signature change to any orchestrator service from Steps 1-4 (they already call the id-based
methods, which keep their existing signature).

**Addendum (found during the same 2026-09-03 audit):** `ProviderProfileSaveService`/
`TaxonCatalogService` (Steps 2/4) call `authorizationService.canOperate(actorId, null)` to mean
"is this actor privileged" — correct, but relies on the reader knowing `null` ownerId reduces
`canOperate` to admin-or-moderator; not self-documenting at the call site. Add a named alias while
touching this class for Step 5:
```java
public boolean isPrivileged(@NonNull Long actingUserId) {
    return canOperate(actingUserId, null);
}
```
and update those two call sites to use it instead of the raw `canOperate(actorId, null)` call.
Purely a readability fix, no behavior change.

### Step 6 — `AccessDeniedException` leaks its raw internal message to the end user (found 2026-09-03) -- ✅ done 2026-09-03

Every `save()`/`delete()` call site in `marketplace-app` catches exceptions (no crash/blank-screen
path exists), but several interpolate the raw `e.getMessage()` into an otherwise-translated
notification instead of showing a fixed, localized string — pre-existing pattern (already true for
any unexpected exception), but Step 1-4's new `AccessDeniedException` is the first exception whose
message (`"User 42 may not operate on resource owned by 99"` — English, internal numeric ids) is
expected to actually surface under a plausible (if rare, defense-in-depth-only) scenario, e.g. a
stale UI session after a role/ownership change mid-use.

Confirmed leak sites (grep-verified, `e.getMessage()` passed as a notification template arg) --
6 total, 2 more than originally scoped (`TaxonManagementView`/`CityManagementView`'s `restore()`
flows, found while implementing, not caught by the original grep pass):
- `AbstractEntityOverlay.handleSave()`'s generic `catch (Exception e)` branch (shared by every
  form-overlay save button — Advertisement, ProviderProfile, Taxon, City, User)
- `UserView` delete flow
- `TaxonManagementView` delete **and** restore flows
- `CityManagementView` delete **and** restore flows

Not affected (already show a fixed, safe message, no interpolation): `AdvertisementCardView`
delete, `ProviderProfileDeleteUtil.confirmAndDelete()`.

**Fix implemented:** one shared `I18nKey.COMMON_NOTIFICATION_ACCESS_DENIED` (new `=== Common ===`
section — the first genuinely domain-agnostic notification string in this enum, so a shared key is
the deliberate exception here rather than the established per-view-key convention; translated in
both `messages_en.properties`/`messages_uk.properties`). Each of the 6 leak sites above gained a
`catch (AccessDeniedException e)` branch before its generic `catch (Exception e)`, showing this
fixed key instead of `e.getMessage()` — mirrors the existing
`OptimisticLockingFailureException`/`saveConfig().conflict()` special-case pattern already in
`AbstractEntityOverlay.handleSave()`. The generic `catch (Exception e)` branches themselves stay
as-is for genuinely unexpected errors.

### Cross-cutting

- Fail-open only where it already exists structurally (`ComponentFactory<XPort>.ifAvailable`/
  `findIfAvailable` on the *domain* port, e.g. skipping an unavailable `AdvertisementPort`) — the
  new `AuthorizationService` itself is a **mandatory** orchestrator dependency (same shape as
  `UserAccountPort` in `UserDeleteService`), so there is no "port unavailable" case to fail open
  on for the authorization check itself; `user-spring-boot-starter` is already a compile-scope,
  non-optional dependency of the final app via `marketplace-orchestrator`'s own `pom.xml` (see
  `marketplace-orchestrator/CLAUDE.md`), so this doesn't newly introduce that requirement.
- Unit tests per orchestrator service (owner succeeds, non-owner-non-privileged throws
  `AccessDeniedException`, privileged succeeds; User: role-change-by-non-admin throws even when
  editing self) — added to each service's existing test suite in `marketplace-orchestrator`.
- No UI-visible behavior change expected for any existing flow (UI already gates the same cases) —
  Playwright coverage stays regression-only.
- `DECISIONS.md` entry in `marketplace-orchestrator/DECISIONS.md` via `/record-decision` once
  implemented.

**Resolved 2026-09-02:** the Step 3 role-escalation fix (`canEditRole` check) stays folded into
this issue's scope — same root cause (service boundary trusts the caller), same `AuthorizationService`
work, no benefit to splitting.

**Resolved 2026-09-02 (still applies):** execution is stepwise, one orchestrator service (Step) at
a time, each reviewed and confirmed before moving to the next.

## Superseded v1 draft (2026-09-02, checks inside each starter — replaced by v2 above same day)

<details>
<summary>Original service-boundary-in-starters design, replaced before any code was written</summary>

Decision was Option 1 with checks inside each of the 4 starters (`ComponentFactory<UserAuthorizationPort>`
injected into `AdvertisementService`/`ProviderProfileService`/`TaxonService`, plus a new
`UserAuthorizationPort.canOperate(Long, Long)` SPI method) rather than in `marketplace-orchestrator`.
Replaced same-day per explicit user request to relocate the checks to the orchestrator layer
instead, once grep confirmed orchestrator is already the sole real caller of every mutating Port
method — see "Why this still closes the gap" above. No code was written against the v1 draft.

</details>



## Related

- `backlog/completed/issues/improvement-020-security-baseline-before-public-endpoints.md` — the
  precedent; this is its service-layer counterpart.
- `backlog/issues/improvement-073-rest-endpoint-infrastructure-test-seeding.md` — the first
  concrete non-UI caller; whichever option wins must be in place before 073's endpoints mutate
  state.
- `marketplace-app/CLAUDE.md` — "Services intentionally have no `@PreAuthorize`" — this issue
  revisits that decision at the boundary, not by re-adding class-level annotations.
- `backlog/issues/improvement-171-formalize-deep-review-agents.md` — while scoping that
  issue's review agents, a `security-boundary-reviewer` LLM lens was drafted (later deleted, kept
  only `solid-dry-reviewer`) whose entire job was catching exactly this gap per-diff. Discussion
  concluded Option 2 above is largely mechanizable: an `ArchitectureRulesTest` rule requiring every
  method in a `*Controller` class under `rest/` to carry `@PreAuthorize` (or be explicitly
  allowlisted, matching `HealthController`'s existing precedent) would catch the most common new
  non-UI caller shape (a new REST endpoint) automatically, cheaper and more reliably than an LLM
  review pass — leaving only non-REST callers (a `@Scheduled` job, an event listener) as the
  residual gap an ArchUnit rule can't close. Worth folding into whichever option is picked when
  this issue's trigger fires.

## Operational notes
- token_cost_review: 855549 (8 `/code-review` finder-angle agents)
- token_cost_research: 139218 (security/auth architecture inventory agent 46285 + CI-integration-failure investigation via dagu-analyst 92933)
- token_cost_verification: 500950 (7 code-review verify-step agents 431408 + sonar-analyst bug check 34771; excludes deploy/test script runs, which have no token cost)
- review_signal_ratio: 5 / 22 (5 CONFIRMED/PLAUSIBLE survived out of 22 raw candidates across all 8 finder angles, after cross-angle dedup to 9 verified sub-findings)
- context_loading_task_type: n/a (not explicitly checked against a context-loading.md row this session)
- context_loading_consulted: no
- context_loading_matched: n/a
- flows_situation: choosing among /code-review, /review, /sonar, /ci, /sync-docs, /record-decision across a long multi-phase session (implement → review → sonar → CI → docs sync → ADR)
- flows_chosen: /code-review --fix, /sonar, /ci, /sync-docs, /record-decision
- flows_matched: yes (flows.md's mapping table checked explicitly before choosing /code-review over /review; the other four were direct, unambiguous user-named commands)

### Agent calls
- security/auth architecture inventory | subagent_type=Explore | tokens=46285 | tool_uses=41 | duration_s=221 | mode=background | batch=solo
- code-review angle A (line-by-line) | subagent_type=general-purpose | tokens=126821 | tool_uses=26 | duration_s=145 | mode=background | batch=review-finders
- code-review angle B (removed-behavior) | subagent_type=general-purpose | tokens=115985 | tool_uses=21 | duration_s=119 | mode=background | batch=review-finders
- code-review angle C (cross-file tracer) | subagent_type=general-purpose | tokens=111706 | tool_uses=19 | duration_s=308 | mode=background | batch=review-finders
- code-review angle: reuse | subagent_type=general-purpose | tokens=77871 | tool_uses=5 | duration_s=30 | mode=background | batch=review-finders
- code-review angle: simplification | subagent_type=general-purpose | tokens=106625 | tool_uses=12 | duration_s=69 | mode=background | batch=review-finders
- code-review angle: efficiency | subagent_type=general-purpose | tokens=102266 | tool_uses=8 | duration_s=69 | mode=background | batch=review-finders
- code-review angle: altitude | subagent_type=general-purpose | tokens=99135 | tool_uses=6 | duration_s=64 | mode=background | batch=review-finders
- code-review angle: conventions | subagent_type=general-purpose | tokens=115140 | tool_uses=12 | duration_s=71 | mode=background | batch=review-finders
- verify: role-check silent skip | subagent_type=general-purpose | tokens=76595 | tool_uses=12 | duration_s=54 | mode=background | batch=review-verify
- verify: AccessDeniedException logging | subagent_type=general-purpose | tokens=53466 | tool_uses=5 | duration_s=27 | mode=background | batch=review-verify
- verify: read-skew race | subagent_type=general-purpose | tokens=66765 | tool_uses=14 | duration_s=68 | mode=background | batch=review-verify
- verify: 6x duplicated catch | subagent_type=general-purpose | tokens=55031 | tool_uses=6 | duration_s=22 | mode=background | batch=review-verify
- verify: redundant double-fetch | subagent_type=general-purpose | tokens=58787 | tool_uses=2 | duration_s=13 | mode=background | batch=review-verify
- verify: self-edit/cascade redundant lookups | subagent_type=general-purpose | tokens=62125 | tool_uses=6 | duration_s=35 | mode=background | batch=review-verify
- verify: AuthorizationService internal structure | subagent_type=general-purpose | tokens=58639 | tool_uses=6 | duration_s=28 | mode=background | batch=review-verify
- SonarQube bug check | subagent_type=sonar-analyst | tokens=34771 | tool_uses=2 | duration_s=19 | mode=background | batch=solo
- CI integration-stage failure investigation | subagent_type=dagu-analyst | tokens=92933 | tool_uses=11 | duration_s=55 | mode=background | batch=solo

### Script/command runs
- scripts/build-and-test.sh --unit-test (multiple, per-step, Steps 0-6) | duration_s=~110 each | mode=background | result=pass (after 2 test-file fixes mid-Step-5)
- scripts/build-and-test.sh --unit (full suite, 4 runs across the session) | duration_s=~100-115 each | mode=background | result=pass
- scripts/sonar.sh | duration_s=~180 | mode=background | result=fail (quality gate; 0 real BUG/CRITICAL confirmed via sonar-analyst)
- scripts/ci.sh (full unit+integration+e2e+sonar+archunit_metrics+docs) | duration_s=~2100 | mode=background | result=partially_succeeded (integration + sonar failed, both root-caused as non-code issues)
- integration-tests/run.sh --sandbox smoke (verify container-cleanup fix) | duration_s=~320 | mode=background | result=pass
- docs/architecture/scripts/generate-architecture-model.sh | duration_s=<10 | mode=foreground | result=pass
- .claude/nav/scripts/generate-adr-index.sh | duration_s=<5 | mode=foreground | result=pass

### Review angle yield
- line-by-line diff scan | survived=1 | total_candidates=3 | tokens=126821
- removed-behavior auditor | survived=0 | total_candidates=2 | tokens=115985
- cross-file tracer | survived=0 | total_candidates=0 | tokens=111706
- reuse | survived=1 | total_candidates=2 | tokens=77871
- simplification | survived=2 | total_candidates=5 | tokens=106625
- efficiency | survived=1 | total_candidates=5 | tokens=102266
- altitude | survived=0 | total_candidates=4 | tokens=99135
- conventions | survived=0 | total_candidates=1 | tokens=115140
