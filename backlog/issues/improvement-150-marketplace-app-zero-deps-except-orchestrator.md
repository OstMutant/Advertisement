# improvement-150: marketplace-app should have zero direct `*Port`/`*Hook` (SPI) usage from platform-commons — not a zero-dependency goal

**Type:** architecture change
**Module:** `marketplace-app/src/main/java/org/ost/marketplace/services/security/AccessEvaluator.java`, `marketplace-app/src/main/java/org/ost/marketplace/ui/views/components/audit/{AuditActivityListRenderer,AuditTimelineListRenderer,AuditTimelineRowRenderer}.java`, `marketplace-app/src/test/java/org/ost/marketplace/architecture/ArchitectureRulesTest.java`, `marketplace-orchestrator`
**Priority:** 🔴 top — explicit user request to rank at the very top of the backlog
**When:** independent, no blockers (follow-up to improvement-149 Point 5)

## Problem

improvement-149 Point 5 moved `marketplace-app`'s 6 starter `<dependency>` blocks to
`marketplace-orchestrator/pom.xml`, leaving `marketplace-app/pom.xml` depending on
`platform-commons` + `marketplace-orchestrator` + `query-lib` (plus Vaadin/Spring/tooling deps).
This matched what improvement-149's own issue text recorded as the agreed direction at the time
("`marketplace-app` depends only on `marketplace-orchestrator`" — *plus* `platform-commons`,
`query-lib`, Vaadin, etc.).

**Scope narrowed (2026-08-12):** the goal is not a literal zero-`platform-commons`-import
dependency. DTOs (`UserDto`, `AdvertisementInfoDto`, `TaxonDto`, etc.) and shared-kernel utility
types (`ComponentFactory<T>`, reverted back to a `platform-commons` import after bucket A —
see Decisions log) are accepted to stay. The actual target is narrower: **zero direct `*Port`/
`*Hook` (SPI) usage from `platform-commons` in `marketplace-app`** — SPI types are the ones that
couple `marketplace-app` to a starter's/orchestrator's implementation shape, unlike a DTO, which is
just a data carrier. Earlier framing below (verified against real code before the scope narrowed,
still useful for the `query-lib` and general `platform-commons` usage count) predates this
narrowing:

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

   **Concrete angle on `ActivityEnrichHookImpl`, found while investigating why it wasn't moved
   alongside the other 6 Hooks in improvement-149 Point 4:** its real collaborator,
   `AdvertisementAuditEnrichService` (`marketplace-app/services/advertisement`), has 4 fields —
   `AttachmentMediaService`/`TaxonLookupService` (both already live in `marketplace-orchestrator`,
   zero Vaadin) vs. `LocaleProvider`/`I18nService` (both `marketplace-app`-only; the real
   `LocaleProvider` implementation, `VaadinLocaleProvider`, reads the current Vaadin session's
   locale directly). Only 2 of its 4 methods actually touch the UI-only pair: `labelFor(AdKind)`
   (`i18nService.get(...)`) and `resolveNames(Set<Long>)` (`localeProvider.getCurrentLocale()`,
   passed into `taxonLookupService.findByIds()`). This suggests the service could split: a
   `marketplace-orchestrator`-friendly part (category/city/attachment-state resolution, pure data,
   no i18n) plus a thin `marketplace-app` translation wrapper the orchestrator part calls back into
   — the same forwarder-SPI shape already used for `UiLabelHook`/`SessionActorHook`, rather than
   assuming the whole service is stuck in `marketplace-app` as one unit. Not designed in detail —
   this is a starting angle for whoever picks up this issue, not a finished plan. Worth noting: the
   original "`ActivityEnrichHookImpl` stays in `marketplace-app`" call
   (`marketplace-orchestrator/DECISIONS.md` ADR-004) was an unreviewed technical judgment made while
   proposing improvement-149 Point 4's overall plan, not a specific design the user separately
   evaluated and approved — worth real scrutiny here rather than treating it as settled.
3. Re-run `improvement-149`'s Definition of Done (full reactor build, `deploy.sh` boot, unit +
   integration + Playwright) once a plan is agreed and implemented.

**Superseded by the "Scope narrowed" note above and Step 6 below:** point 2 here assumed the goal
was zero `platform-commons` imports entirely (DTOs included). It no longer is — see Step 6 for the
current, narrower, actionable plan (SPI-only).

## Step-by-step plan

**Step 1 — remove `query-lib`. ✅ Done** (see Decisions log). Confirmed zero `org.ost.query.*`
imports under `marketplace-app/src/main/java`; dependency block deleted, verified with
`bash scripts/unit-tests.sh marketplace-app`, no source changes needed.

**Step 2 — fix the 17 real Sonar findings. ✅ Done** (see Decisions log). Quality Gate was FAILED
on this branch (`new_violations=17`); all 11 listed fixes applied, re-verified with
`bash scripts/sonar.sh`: `new_violations=0`. Full list, in Sonar's own severity order:
1. CRITICAL `marketplace-orchestrator/.../AdvertisementSaveService.java:48` (S3776) — cognitive
   complexity 16 > 15 in `save()`, needs refactor to reduce branching.
2. CRITICAL `marketplace-app/.../AdvertisementFormOverlayModeHandler.java:129` (S1192) —
   `"data-testid"` string literal duplicated 4×, extract to a constant.
3. CRITICAL `marketplace-app/.../BaseOverlay.java:47` (S1192) — `"overlay--visible"` duplicated
   4×, extract to a constant.
4. MAJOR `marketplace-orchestrator/.../AttachmentMediaService.java:31` (S1068) — unused field
   `attachmentSoftDeleteService`, confirmed by direct grep (declared/injected, never read in the
   class). Contradicts `marketplace-orchestrator/CLAUDE.md`'s claim it's reused internally — that
   doc line needs correcting once the field is actually removed or wired in for real.
5. MAJOR `user-spring-boot-starter/.../UserPreferencesRepository.java:101,114` (S112) — generic
   `Exception` instead of a specific/custom exception type, 2 occurrences.
6. MAJOR `marketplace-app/.../AdvertisementFormOverlayModeHandler.java:243` (S3358) — nested
   ternary, extract into a plain statement.
7. MINOR `marketplace-app/.../UserQueryBlock.java:29,32,34` (S2065) — 3× stray `transient`
   modifier on fields (meaningless outside serialization).
8. MINOR `taxon-spring-boot-starter/.../TaxonService.java:165` (S1659) — `descEn` and following
   declared on one line, split.
9. MINOR `marketplace-app/.../UserView.java:151` (S7467) — replace caught `ex` with an unnamed
   pattern.
10. MINOR `user-spring-boot-starter/.../UserService.java:116` (S135) — too many break/continue in
    one loop.
11. INFO×4 `marketplace-app/.../AdvertisementAuditEnrichService.java:165,175` (S7475) — unused
    type in unnamed pattern.

**Step 3 — IDEA "unused declaration" dump triage. ✅ Done** (see Decisions log). 4 confirmed real
(write-only `@LastModifiedDate`/`@LastModifiedBy` fields, documented not deleted), 4 false
positives (framework/codegen blind spots).

**Step 4 — SPI dead-method sweep across every `*Port`/`*Hook` interface. ✅ Done** (see Decisions
log; this step's actual content — swept every `platform-commons` SPI method for real call-site
evidence — superseded the "platform-commons removal" text this step number originally held).
2 genuinely dead methods removed (`UserPreferencesPort.findLocale`,
`AuditDomainHookImpl.resolveDisplayName`); 4 more confirmed "0 usages" but intentionally kept
(`ProviderProfilePort`'s planned-but-unwired public API).

**Step 5 (bucket A+B) — `ActivityEnrichHookImpl`/`AdvertisementAuditEnrichService` move. ✅ Done**
(see Decisions log, ADR-005). Split `AdvertisementAuditEnrichService` so its 2 i18n-touching calls
go through the `UiLabelHook`/`CurrentLocaleHook` forwarder pair, moved it plus `ActivityEnrichHookImpl`
into `marketplace-orchestrator`. Bucket A (`ComponentFactory<T>` duplication) was tried and
reverted — see Decisions log for why. Verified end-to-end (unit + integration + a real
`deploy.sh --reset` + Playwright `e2e --ux`, all green) and committed as `79f44c91`.

**Step 6 (current) — close the remaining `*Port`/`*Hook` gaps found while reviewing Step 5, per
the "Scope narrowed" note above.** Full inventory in the Decisions log entry below. Three code
changes, then a guard test:

1. **`AuditActivityListRenderer`/`AuditTimelineListRenderer`** — stop injecting `AuditDomainHook`
   directly. Both calls it makes (`.resolveNames(Set<Long>)`, `.findExisting(EntityType,
   Set<Long>)`) are pure delegation in `AuditDomainHookImpl` to two already-public
   `marketplace-orchestrator` services — `UserActorNameService.resolveNames(Set<Long>)` and
   `EntityExistenceService.findExisting(EntityType, Set<Long>)`. Repoint both renderers to call
   those services directly instead of the raw `platform-commons` interface — a 1:1 swap, no new
   orchestrator code needed.
2. **`AuditTimelineRowRenderer`** — stop collecting `List<AuditActivityEnrichHook<?>>` directly.
   Add a small `marketplace-orchestrator` service (new method on `AuditQueryService`, or a new
   dedicated service) that itself collects `List<AuditActivityEnrichHook<?>>` (the orchestrator is
   the correct place for this — it already owns `ActivityEnrichHookImpl`, the interface's only real
   implementation) and exposes plain DTO-in/DTO-out methods (`mergeTimelineItems`,
   `enrichActivityItems`, `getMediaStateForSnapshot`) that dispatch by `EntityType` internally.
   Repoint `AuditTimelineRowRenderer` to call that service instead.
3. **`AccessEvaluator`** — move its `UserAuthorizationPort`/`UserIdMarker` usage into
   `marketplace-orchestrator` (already accepted as necessary, not just for SPI purity but for
   future REST-adapter readiness — see the AccessEvaluator Decisions log entry above). Design not
   yet detailed: needs an orchestrator-facing authorization service exposing
   `isAdmin`/`isModerator`/`isOwner`-shaped checks without introducing >2-port coupling.
4. **Add the ArchUnit guard, last, after 1-3 land** — a new rule in `ArchitectureRulesTest`
   banning any `org.ost.marketplace..` class from depending on `org.ost.platform..spi..`, with an
   allowlist (mirroring `ORCHESTRATOR_HOOK_ALLOWLIST`'s existing shape) for the legitimate
   remaining cases: `AuthenticatedPrincipal` (consumed via `instanceof`, never implemented) and
   `UserSettingsChangedHook` (`SettingsPaginationService` implements it — correct `*Hook`
   direction, starter calls back into marketplace-app). Adding this test *before* 1-3 land would
   force a large allowlist that immediately shrinks to nothing useful — sequence it last so the
   test locks in the narrow, final shape rather than documenting a temporary wide one.

**Step 7 — re-run Definition of Done** (full reactor build, `deploy.sh` boot, unit + integration +
Playwright) once Step 6 is implemented.

## Decisions log

- **2026-08-12** — Step 2 (fix the 17 real Sonar findings) done. All 11 listed fixes applied:
  cognitive-complexity refactor of `AdvertisementSaveService.save()` (extracted
  `unionAssignmentIds()`, `captureAudit()`, `resolveAttachmentSnapshotId()`); `DATA_TESTID`/
  `VISIBLE_CLASS` constants in `AdvertisementFormOverlayModeHandler`/`BaseOverlay`; removed the
  genuinely-unused `attachmentSoftDeleteService` field from `AttachmentMediaService` (and corrected
  the now-inaccurate reuse claim in its Javadoc and `marketplace-orchestrator/CLAUDE.md`); replaced
  `UserPreferencesRepository`'s manual try/catch-rethrow-`RuntimeException` with `@SneakyThrows`
  (matching `AttachmentSnapshotRepository`'s existing precedent for the same Jackson-wrapping
  shape); extracted `AdvertisementFormOverlayModeHandler.save()`'s nested ternary into
  `commitGallery()`; added `@SuppressWarnings("java:S2065")` to `UserQueryBlock`/
  `AdvertisementQueryBlock` for the `transient` Spring-proxy-field pattern (confirmed real and
  intentional — Vaadin `Component` is `Serializable`, `TimelineQueryBlock` already had the same
  suppression) instead of stripping `transient`; split `TaxonService`'s one-line 4-variable
  declaration; replaced `UserView`'s unused `catch (Exception ex)` with the project's established
  `catch (Exception _)` pattern; extracted `UserService.cleanup()`'s 2 `continue`s into one
  `isStillOwner()` helper; removed the redundant `var` from 4 unnamed record-pattern bindings in
  `AdvertisementAuditEnrichService`. One own regression caught and fixed during verification: the
  `resolveAttachmentSnapshotId` extraction was first written as a ternary with a nested ternary
  inside (same S3358 shape being fixed) — re-ran `bash scripts/sonar.sh` after the first pass,
  caught the new issue, extracted it into a proper `if`-based private method instead. Verified with
  `bash scripts/unit-tests.sh` (full run, 72 tests PASSED) + `bash scripts/unit-tests.sh
  marketplace-orchestrator` (18 tests PASSED, including the refactored `AdvertisementSaveServiceTest`
  9/9) + a final `bash scripts/sonar.sh`: `new_violations=0`. Quality Gate is still ERROR overall,
  but only on `new_coverage=0%` — a separately-tracked, pre-existing gap (improvement-114), not
  part of this step's scope.


- **2026-08-12** — Step 1 (remove `query-lib` from `marketplace-app/pom.xml`) done: dependency
  block deleted, `bash scripts/unit-tests.sh marketplace-app` PASSED, no source changes needed.
- **2026-08-12** — Ran `bash scripts/sonar.sh` per explicit user request before continuing to Step
  2. Quality Gate **FAILED**: `new_violations=17` (threshold 0), `new_coverage=0%` (threshold 80%,
  already tracked separately as improvement-114), `new_duplicated_lines_density=2.96%` (threshold
  3%, passed). Full 17-issue breakdown: 3 CRITICAL (cognitive complexity in
  `AdvertisementSaveService.save()`, duplicated string literals `"data-testid"`/
  `"overlay--visible"`), 4 MAJOR (unused field `AttachmentMediaService.attachmentSoftDeleteService`
  — confirmed by direct grep, declared/injected but never read anywhere in the class, contradicting
  `marketplace-orchestrator/CLAUDE.md`'s claim that it's reused internally; 2× generic-exception in
  `UserPreferencesRepository`; 1× nested ternary), rest MINOR/INFO. Not yet triaged into fix-now vs.
  defer — waiting on user direction on whether/how these fold into this issue's scope or go
  elsewhere.
- **2026-08-12** — A large IntelliJ "unused declaration" inspection export was pasted into chat
  (hundreds of findings). Assessed as near-total false-positive noise: it flags essentially every
  Spring-managed field, `@Autowired` field, JPA/Spring-Data entity field, and Vaadin component class
  across the whole repo as "unused," because the inspection has no Spring/Vaadin-aware entry-point
  configuration and can't see DI/reflection-based usage. Not acted on — flagged to the user rather
  than treated as an actionable list.

- **2026-08-12** — Step 3 (IDEA dump triage) done via a background verification agent against the
  hand-filtered "0 usages" shortlist (see Step 3 above). Result: 4 confirmed real, 4 false
  positives. Confirmed real: `Advertisement.updatedAt`/`updatedBy`, `ProviderProfile.updatedAt`,
  `UserEditableFields.updatedAt` — all `@LastModifiedDate`/`@LastModifiedBy`-annotated entity
  fields genuinely never read via the entity object in Java (their DTOs read the same DB column
  straight from a raw SQL `ResultSet` instead). **Important correction to the agent's "CONFIRMED
  DEAD" framing, made before acting on it:** these are NOT safe to delete despite being unread —
  Spring Data JDBC's auditing populates them by reflecting over exactly these Java fields on
  `save()`; removing the field would silently stop the underlying DB column from being included in
  the generated `UPDATE` at all, freezing `updated_at`/`updated_by` forever with no test or
  compile-time signal. Per explicit user decision, added a one-line comment on each of the 4
  fields instead of deleting them, documenting the write-only-but-load-bearing shape so a future
  cleanup pass doesn't repeat this mistake. False positives, root-caused: `AttachmentMediaService
  .attachmentSoftDeleteService` (stale — already handled differently by Step 2, the field no
  longer exists under that name); `ArchitectureMetricsExport` (ArchUnit `@ArchTest` tool class,
  invoked reflectively by `scripts/architecture/generate-architecture-model.sh`, not a real test);
  the `i18nService` field in 12 UI classes (all implement `I18nParams`, whose default methods call
  `getI18nService()` — Lombok's `@Getter` on the field satisfies that contract, invisible to IDEA's
  static analysis); `AdvertisementEditDto`/`UserEditDto` fields (populated via MapStruct-generated
  code / the `Identifiable` interface contract, also invisible to the same analysis). Conclusion:
  the "0 usages" filter is a useful signal but not sufficient alone — half the shortlist was still
  a framework/codegen blind spot, not real dead code.

- **2026-08-12** — Step 4 (SPI dead-method sweep) done. Background agent swept all 10 `*Port` +
  6 `*Hook` interfaces method-by-method for real call-site evidence (not just implementation).
  Found 6 dead methods total; only 2 were genuinely obsolete and safe to delete —
  `UserPreferencesPort.findLocale`/`AuditDomainHookImpl.resolveDisplayName` — removed from the
  interface, `*PortImpl`/`*HookImpl`, and the anonymous test stub in `AuditLogRepositoryTest`.
  The other 4 (`ProviderProfilePort.getFiltered`/`count`/`findById`/`save`) were correctly
  identified as "0 usages" but are NOT dead code — `provider-profile-spring-boot-starter` is
  deliberately backend-only with no UI wired up yet (documented in its own CLAUDE.md, tracked as
  improvement-124 Batch C/D), so these are the planned public API surface for a feature not yet
  built, not forgotten code — left untouched. Verified with a full reactor
  `bash scripts/unit-tests.sh` run: PASSED (marketplace-orchestrator + marketplace-app both
  SUCCESS). Also fixed, opportunistically, while reviewing these same interfaces: 4 broken Javadoc
  `{@link org.springframework.dao.OptimisticLockingFailureException}` references in
  `AdvertisementPort`/`ProviderProfilePort`/`TaxonPort` (×2) — `platform-commons` has no
  `spring-tx` on its classpath, so the FQCN was genuinely inaccessible; replaced with plain text.
- **2026-08-12** — Quantified the real cost of Step 5 (full `platform-commons` removal from
  `marketplace-app`) before starting it: 39 distinct types across 72 files. Only
  `ComponentFactory<T>` (13 files, cheap — `UiComponentFactory<T>` can stop extending it and
  become self-contained) and `AuditActivityEnrichHook` (the already-scoped `ActivityEnrichHookImpl`
  split) are realistically actionable. The remaining ~30 DTO/enum types (`UserDto`,
  `AdvertisementInfoDto`, `TaxonDto`, `EntityType`, snapshot DTOs, etc., ~60+ files) would require
  ~30 new marketplace-app-owned wrapper types plus mapping at every `marketplace-orchestrator` call
  site to reach literal zero — a large, permanent dual-maintenance cost for no functional benefit,
  flagged as disproportionate rather than started without explicit sign-off.
- **2026-08-12** — Implemented the two actionable items from the cost quantification above
  ("bucket A+B"): (A) duplicated a minimal local `ComponentFactory<T>` in
  `marketplace-app/ui/core`, repointing 13 files off `platform-commons`'s version; (B) moved
  `ActivityEnrichHookImpl`/`AdvertisementAuditEnrichService` into `marketplace-orchestrator`
  (`marketplace-orchestrator/DECISIONS.md` ADR-005), adding `CurrentLocaleHook` and extending
  `UiLabelHook` with `labelFor(AdKind)` so the service's 2 i18n-touching calls go through a
  forwarder instead of a direct `LocaleProvider`/`I18nService` dependency — mirroring the
  `AuditDomainHookImpl`/`CurrentActorHookImpl` precedent. While reviewing the moved service, found
  and fixed two more UI-shaped leaks that slipped through the move unnoticed: raw `<s>` HTML markup
  for a soft-deleted taxon name, and a hardcoded non-localized `"—"` placeholder for "no media" —
  both moved behind `UiLabelHook` too (`markDeleted(String)`, `noMediaPlaceholder()` — the latter
  backed by a real `I18nKey.AUDIT_CHANGES_NO_MEDIA` translation entry, not a bare literal). Also
  caught and fixed a real bug surfaced by this same review: `ArchitectureRulesTest`'s
  `ORCHESTRATOR_HOOK_ALLOWLIST` never had `CurrentLocaleHook` added when it was introduced, so the
  `hooks_live_only_in_platform_commons` ArchUnit rule would have failed on it. None of this was
  compiled/tested end-to-end before the next entry below.
- **2026-08-12** — Reverted part (A) above (the local `ComponentFactory<T>` duplication) after
  discussion surfaced the actual trade-off it created: `platform-commons`'s `ComponentFactory<T>`
  is genuinely used by 8+ modules (every domain starter, for wrapping optional `*Port` beans, plus
  `marketplace-orchestrator`/`integration-tests`) — a real shared-kernel type per
  `platform-commons/CLAUDE.md`'s own "used by ≥2 modules" allowance, and it cannot move into
  `marketplace-orchestrator` (starters must never depend on it — "Module Import Rules"). Duplicating
  it in `marketplace-app` bought one fewer `platform-commons` import at the cost of two divergent
  classes with the same name to maintain in parallel, for a type with no actual coupling pain on
  its own (unlike the real DTO/enum bulk in bucket C/D/E). Decision: not worth it — `ComponentFactory<T>`
  stays a `platform-commons` import in `marketplace-app`, same footing as `AccessEvaluator`'s
  `UserAuthorizationPort` (see the next entry). Reverted: deleted
  `marketplace-app/ui/core/ComponentFactory.java`, restored all 13 files'
  `import org.ost.platform.core.ComponentFactory;`, restored `UiComponentFactory<T>`'s import, and
  restored `marketplace-app/CLAUDE.md`'s "Configurable prototype beans" section and `ui/core/`
  bullet to their pre-bucket-A wording (verified byte-identical to `git show HEAD` for those
  sections). Part (B) — the `ActivityEnrichHookImpl`/`AdvertisementAuditEnrichService` move and its
  two follow-up UI-leak fixes — was **not** reverted and stays in place. Not yet compiled/tested.
- **2026-08-12** — Resolved the open question in "Related" below about `AccessEvaluator`'s direct
  `UserAuthorizationPort` field: it should **not** stay a permanent exception. The rationale
  originally used to justify it ("hot path, no architectural benefit to routing through the
  orchestrator") didn't account for the reason `marketplace-orchestrator` exists in the first
  place — `improvement-136`'s founding target-architecture diagram shows both the current Vaadin UI
  *and* a future REST API feeding through the orchestrator, and
  `marketplace-orchestrator/DECISIONS.md` ADR-001 explicitly rejected keeping cross-domain logic in
  `marketplace-app` because it would leave "no room to add a REST adapter later." A REST client
  would have no equivalent to `AccessEvaluator` at all — `isAdmin`/`isModerator`/`isOwner` checks
  only exist today in a class a REST caller can never reach. If/when a REST adapter is actually
  built, this becomes a functional gap, not just a purity one. Decision: this exception is
  temporary, not by-design-permanent — moving `AccessEvaluator`'s authorization checks into
  `marketplace-orchestrator` is accepted as eventual scope for this issue (or a follow-up), sized
  and picked up as a future step, not as part of the current implementation batch. Implementation
  not started.
- **2026-08-12** — Verified bucket B end-to-end and committed. `bash scripts/run-all-tests.sh
  --integration "--sandbox"`: unit-tests PASSED (72 tests, incl. `ArchitectureRulesTest` 16/16
  confirming the `ORCHESTRATOR_HOOK_ALLOWLIST` fix), integration-tests PASSED (165/165). The same
  run's Playwright leg (5 failed) was discarded as a false signal — it exercised a stale Docker
  image never rebuilt this session, not the current code (`playwright.sh` only resets the DB and
  restarts the existing container, it never runs `docker build`). Re-verified properly: `bash
  scripts/deploy.sh --reset` (full image rebuild + DB/MinIO wipe) followed by `bash
  scripts/playwright.sh e2e --ux` — 37 passed, 0 failed, 13 skipped (spec 05's `--full`-gated
  boundary tests, expected). Notably test #22 (`adminEn edits Electronics — ... advertisement view
  and activity diff show struck-through category while deleted ...`) exercises the moved
  `markDeleted()` path directly. Committed as `79f44c91`
  ("feat(improvement-150): move ActivityEnrichHookImpl/AdvertisementAuditEnrichService to
  marketplace-orchestrator") — 28 files. `docs/test-coverage.md` deliberately left uncommitted:
  its regenerated 37/50 line reflects only that this particular verification run used `e2e --ux`
  without `--full`, not a real coverage regression from the prior committed 50/50, so committing it
  would misrepresent actual coverage. Also caught and reverted before staging: an unrelated,
  unintentional formatting corruption in `platform-commons/.../ValidRangeValidator.java` (a stray
  line break splitting `public class` in two) that had nothing to do with this batch of work.
- **2026-08-12** — Scope narrowed (see the "Scope narrowed" note under Problem above): DTOs and
  `ComponentFactory<T>` are accepted platform-commons dependencies; the real target is zero direct
  `*Port`/`*Hook` usage. Full inventory of every `org.ost.platform.*.spi.*` import remaining in
  `marketplace-app/src/main/java` (grep-verified):
  - `AuthContextService` → `AuthenticatedPrincipal` — consumed only (an `instanceof` cast on the
    Spring Security principal), never implemented here. Correct shape as-is.
  - `SettingsPaginationService implements UserSettingsChangedHook` — correct `*Hook` direction
    (starter calls back into marketplace-app to refresh cached pagination sizes on settings
    change). Correct shape as-is.
  - `AccessEvaluator` → `UserAuthorizationPort`, `UserIdMarker` — already decided (see the
    AccessEvaluator entry above): not a permanent exception, future scope, not yet implemented.
  - `AuditActivityListRenderer`/`AuditTimelineListRenderer` → `AuditDomainHook` — both hold a
    direct field and call `.resolveNames(Set<Long>)`/`.findExisting(EntityType, Set<Long>)` for
    their own UI-rendering needs (actor-name resolution, entity-existence checks for the timeline).
    `AuditDomainHook`'s real implementor (`AuditDomainHookImpl`) already lives in
    `marketplace-orchestrator`, which already has `ActorLookupService`/`AuditQueryService` wrapper
    services doing adjacent work — these two renderers bypass that layer and call the raw
    `platform-commons` interface directly. Candidate for routing through an orchestrator service
    instead — investigation pending (next step, this session).
  - `AuditTimelineRowRenderer` → `List<AuditActivityEnrichHook<?>>` — collects every registered
    `AuditActivityEnrichHook` bean directly (keyed by `EntityType`) and calls
    `.merge()`/`.enrichActivity()`/`.getMediaStateForSnapshot()`. The interface's only real
    implementation, `ActivityEnrichHookImpl`, was just moved into `marketplace-orchestrator` this
    session (ADR-005) — this renderer still reaches past that move to the raw `platform-commons`
    interface and multi-bean collection pattern directly, rather than through an
    orchestrator-owned service. Same candidate status as the two renderers above — investigation
    pending.
- **2026-08-12** — Step 6 item 1 done: `AuditActivityListRenderer`/`AuditTimelineListRenderer`
  repointed off `AuditDomainHook` onto `marketplace-orchestrator`'s already-public
  `UserActorNameService.resolveNames(Set<Long>)`/`EntityExistenceService.findExisting(EntityType,
  Set<Long>)` — a 1:1 swap, no new orchestrator code needed. Verified with `bash
  scripts/unit-tests.sh marketplace-app`: BUILD SUCCESS, `ArchitectureRulesTest` 16/16 (confirms no
  architecture rule broke). Not yet committed at the time this entry was written. Items 2
  (`AuditTimelineRowRenderer`), 3 (`AccessEvaluator`), and 4 (the ArchUnit guard) remain.
- **2026-08-12** — Item 1 committed as `731b3b4a`.
- **2026-08-13** — Step 6 item 2 done: `AuditTimelineRowRenderer` no longer collects
  `List<AuditActivityEnrichHook<?>>` directly. `AuditQueryService` (marketplace-orchestrator)
  gained the field plus two new methods — `hasEnrichHook(EntityType)` and
  `getMediaStateForSnapshot(EntityRef, Long)` — both a plain linear scan over the (currently
  single-entry) hook list, no `@PostConstruct`/map-building introduced, matching this module's
  existing `@RequiredArgsConstructor`-only convention (confirmed via grep: zero `@PostConstruct`
  usage anywhere else in `marketplace-orchestrator` before this change, so a map-building lifecycle
  method would have been a new, unprecedented pattern for the module — a linear scan over a
  single-digit-sized list needs no such machinery). The renderer's `init()` collapsed to a trivial
  `{ return this; }`, matching the sibling `AuditActivityRowRenderer`'s existing shape. Verified
  with `bash scripts/unit-tests.sh marketplace-app`: BUILD SUCCESS (10m19s), `ArchitectureRulesTest`
  16/16. Items 3 (`AccessEvaluator`) and 4 (the ArchUnit guard) remain.

## Related

- `backlog/issues/improvement-149-architecture-map-module-deps-vs-bounded-contexts.md` Point 5 —
  the migration this tightens; its own text explicitly allowed `platform-commons`/`query-lib` to
  remain, which is the discrepancy this issue exists to resolve. Still open at the time this issue
  was filed — sequenced directly after it, not blocking it.
- `marketplace-orchestrator/DECISIONS.md` ADR-004, `platform-commons/DECISIONS.md` ADR-029 — the
  Hook-relocation precedent this issue's fix would likely extend further.
- `marketplace-orchestrator/CLAUDE.md` — documents `AccessEvaluator`'s direct `UserAuthorizationPort`
  as "the one remaining direct `*Port` reference in marketplace-app, by design." Resolved in the
  Decisions log above (2026-08-12): not a permanent exception — accepted as eventual scope, not yet
  implemented.
