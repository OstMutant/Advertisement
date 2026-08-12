# improvement-148: re-verify optional-module removability after the true-BFF migration

**Type:** verification (no code defect — confirms an assumption, doesn't fix one)
**Module:** marketplace-app, marketplace-orchestrator, taxon-spring-boot-starter,
  provider-profile-spring-boot-starter
**Priority:** low — no known break, this closes a verification gap rather than a live bug
**When:** unblocked — `improvement-147`'s true-BFF migration shipped 2026-08-08 (see
  `completed/issues/improvement-147-marketplace-orchestrator-followups.md`); this issue's
  verification can be picked up any time now

## Problem

`improvement-136`'s Phase 8 proved optional-module removability once, before the extraction: strip
an optional starter (taxon or provider-profile) out of the build, compile, boot the app, exercise a
dependent feature, and record the real result. That proof predates `improvement-147`'s true-BFF
migration, which moves *who* holds each `ComponentFactory<XPort>` — presence-guard checks and
per-entity-type existence dispatch move out of marketplace-app UI classes and into new
`marketplace-orchestrator` services (`TaxonCatalogService`, `AttachmentMediaService`,
`EntityExistenceService`, etc.).

The wiring mechanism itself (`ComponentFactory<XPort>` wrapping `ObjectProvider`, `taxon`/
`provider-profile` staying `<scope>runtime</scope>` in `marketplace-app/pom.xml`,
`marketplace-orchestrator`'s Enforcer rule banning any starter-jar dependency) is unchanged by the
migration — but "the mechanism didn't change" is an assumption, not a re-run test. The migration
touches every call site that used to hold a `*Port` reference, so it is exactly the kind of change
that could silently reintroduce a hard dependency (e.g. a new orchestrator service accidentally
injecting a `*Port` as a mandatory field instead of via `ComponentFactory`) without any existing
test catching it — `ArchitectureRulesTest`'s `≤2-port`/`no-persistence-access` rules check shape,
not actual optional-removability at runtime.

## Suggested fix

Once `improvement-147` lands, repeat the same manual removability check `improvement-136` Phase 8
already established as the pattern:
1. Remove `taxon-spring-boot-starter` from the root `pom.xml` `<modules>` (or comment out the
   dependency in `marketplace-app/pom.xml`), rebuild, boot the app, confirm it starts and a
   taxon-dependent feature (category picker, advertisement filter) degrades gracefully instead of
   failing to boot.
2. Repeat for `provider-profile-spring-boot-starter`.
3. Spot-check that the new `EntityExistenceService` (the class granted the ≤2-port exception in
   `improvement-147` Question B) degrades per-branch correctly when one of its 4 wrapped ports is
   absent, not just when all 4 are present.
4. Record the real result in the operational notes, same as `improvement-136` Phase 8 did — not a
   theoretical "should still work."

## Related

- `completed/issues/improvement-136-marketplace-orchestrator-extraction.md` — Phase 8, the original
  removability proof this repeats.
- `improvement-147-marketplace-orchestrator-followups.md` — the migration this re-verifies.

## Verification results (2026-08-12)

1. **`taxon-spring-boot-starter` removed** (commented out its `<dependency>` in
   `marketplace-orchestrator/pom.xml`, full rebuild + `deploy.sh`): app booted cleanly, `/health`
   returned 200, no errors in logs. **PASSED** — no code change needed.
2. **`provider-profile-spring-boot-starter` removed** (same procedure): app **failed to boot**
   (`UnsatisfiedDependencyException` — `UserService` constructor parameter,
   `ComponentFactory<ProviderProfilePort>` bean not found). Root cause: `UserService` holds a
   mandatory `ComponentFactory<ProviderProfilePort>` field, but no `@ConditionalOnMissingBean`
   fallback producer for that type existed in `UserAutoConfiguration` — the only producers were in
   `provider-profile-spring-boot-starter` itself and in `AdvertisementAutoConfiguration`, both
   absent once that starter is removed. **FAILED initially, then FIXED**: added the missing
   `ComponentFactory<ProviderProfilePort>` `@Bean` to
   `user-spring-boot-starter/src/main/java/org/ost/user/config/UserAutoConfiguration.java`,
   mirroring the existing `ComponentFactory<TaxonPort>` fallback pattern. Re-ran the same removal
   test after the fix: app booted cleanly, `/health` returned 200, no errors in logs. Recorded as
   `platform-commons/DECISIONS.md` ADR-006 amendment (2026-08-12).
3. **`EntityExistenceService` per-branch degradation** — spot-checked via direct code inspection
   (not a runtime test): all 4 branches (`ADVERTISEMENT`/`USER,USER_SETTINGS`/`TAXON`/
   `PROVIDER_PROFILE`) use the identical
   `ComponentFactory<X>.findIfAvailable().map(p -> p.findExistingIds(entityIds)).orElse(Set.of())`
   pattern — structurally sound, symmetric graceful degradation confirmed.
4. Full `bash scripts/unit-tests.sh` (72/72 passed, including `ArchitectureRulesTest`'s 16 tests)
   and `bash scripts/integration-tests.sh --sandbox` (165/165 passed) re-run after the fix, both
   green. Not a UI-visible change, so no Playwright run was required per the Definition of Done.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: re-verify an optional-starter removability assumption after a prior migration, found a real boot-time bug along the way
- flows_chosen: manual dependency-removal + deploy.sh verification (no dedicated skill for this)
- flows_matched: n/a

### Script/command runs
- bash scripts/deploy.sh (taxon-starter removed) | duration_s=n/a | mode=foreground | result=pass
- bash scripts/deploy.sh (provider-profile-starter removed, before fix) | duration_s=n/a | mode=foreground | result=fail
- bash scripts/deploy.sh (provider-profile-starter removed, after fix) | duration_s=n/a | mode=foreground | result=pass
- bash scripts/deploy.sh (full starter set restored) | duration_s=n/a | mode=foreground | result=pass
- bash scripts/unit-tests.sh | duration_s=n/a | mode=foreground | result=pass
- bash scripts/integration-tests.sh --sandbox | duration_s=n/a | mode=foreground | result=pass
