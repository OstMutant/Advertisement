# improvement-150: marketplace-app should depend on nothing but marketplace-orchestrator — not even platform-commons or query-lib

**Type:** architecture change
**Module:** `marketplace-app/pom.xml`, `marketplace-app/src/main/java/org/ost/marketplace/spi/*` (UiLabelHookImpl, SessionActorHookImpl, ActivityEnrichHookImpl), `marketplace-app/services/security/AccessEvaluator.java`, `platform-commons`, `marketplace-orchestrator`
**Priority:** 🔴 top — explicit user request to rank at the very top of the backlog
**When:** independent, no blockers (follow-up to improvement-149 Point 5)

## Problem

improvement-149 Point 5 moved `marketplace-app`'s 6 starter `<dependency>` blocks to
`marketplace-orchestrator/pom.xml`, leaving `marketplace-app/pom.xml` depending on
`platform-commons` + `marketplace-orchestrator` + `query-lib` (plus Vaadin/Spring/tooling deps).
This matched what improvement-149's own issue text recorded as the agreed direction at the time
("`marketplace-app` depends only on `marketplace-orchestrator`" — *plus* `platform-commons`,
`query-lib`, Vaadin, etc.).

The user's expectation, restated explicitly in this session, is stricter: **zero direct
dependencies from `marketplace-app` except `marketplace-orchestrator`** — not even
`platform-commons` or `query-lib`. Verified against real code, not assumed:

- **`query-lib`**: zero files under `marketplace-app/src/main/java` import `org.ost.query.*` —
  this dependency appears to be entirely unused by `marketplace-app`'s own code already. Removing
  it from `marketplace-app/pom.xml` may be a pure pom.xml edit with no source changes required —
  needs confirming with a real `mvn compile` after removal, not just the grep.
- **`platform-commons`**: genuinely used — 73 files import types from it (DTOs for UI binding —
  `UserDto`, `AdvertisementInfoDto`, `TaxonDto`, etc.; core model enums — `EntityType`,
  `ActionType`, `EntityRef`, `ChangeEntry`; SPI interfaces `marketplace-app` itself implements —
  `UiLabelHookImpl implements UiLabelHook`, `SessionActorHookImpl implements SessionActorHook`,
  `ActivityEnrichHookImpl implements AuditActivityEnrichHook`; and `AccessEvaluator`'s direct
  `UserAuthorizationPort` field, the one named exception documented in
  `marketplace-orchestrator/CLAUDE.md`). Removing this dependency is not a mechanical pom.xml edit
  — it requires either routing every one of these type usages through `marketplace-orchestrator`
  (DTOs/enums returned from orchestrator service calls instead of imported directly; the 3 Hook
  implementations and `AccessEvaluator` would need to move into `marketplace-orchestrator` too, the
  same way improvement-149 Point 4 already moved 6 other Hook implementations there) or some other
  design not yet chosen.

## Suggested fix

Not yet designed — needs a real plan before implementation, given the platform-commons removal is
substantial:
1. **`query-lib`**: verify via `mvn compile` after removing the dependency; if clean, this is a
   same-day, trivial fix.
2. **`platform-commons`**: design how `marketplace-app` gets DTOs/enums without importing the
   package directly — likely `marketplace-orchestrator` becomes the sole source of every type
   `marketplace-app` touches (re-exporting or wrapping platform-commons types isn't free — it's a
   real design decision, not a mechanical move). Move `UiLabelHookImpl`/`SessionActorHookImpl`
   (already thin forwarders) and `ActivityEnrichHookImpl` into `marketplace-orchestrator` — but
   `SessionActorHookImpl`/`ActivityEnrichHookImpl` need `AuthContextService`/
   `AdvertisementAuditEnrichService`, which themselves depend on Spring Security /
   Vaadin-adjacent UI services that don't belong in `marketplace-orchestrator` per its own
   "no Vaadin, no persistence" constraints — this may mean the *hooks* can't fully leave
   `marketplace-app` even if the *pom.xml dependency* is removed, i.e. there may be a real tension
   between "zero pom.xml dependency on platform-commons" and "these classes must implement a
   platform-commons interface to exist at all." Needs to be resolved as a design question, not
   assumed solvable.
3. Re-run `improvement-149`'s Definition of Done (full reactor build, `deploy.sh` boot, unit +
   integration + Playwright) once a plan is agreed and implemented.

## Related

- `backlog/issues/improvement-149-architecture-map-module-deps-vs-bounded-contexts.md` Point 5 —
  the migration this tightens; its own text explicitly allowed `platform-commons`/`query-lib` to
  remain, which is the discrepancy this issue exists to resolve. Still open at the time this issue
  was filed — sequenced directly after it, not blocking it.
- `marketplace-orchestrator/DECISIONS.md` ADR-004, `platform-commons/DECISIONS.md` ADR-029 — the
  Hook-relocation precedent this issue's fix would likely extend further.
- `marketplace-orchestrator/CLAUDE.md` — documents `AccessEvaluator`'s direct `UserAuthorizationPort`
  as "the one remaining direct `*Port` reference in marketplace-app, by design" — this issue would
  need to either accept that exception still holds, or also close it.
