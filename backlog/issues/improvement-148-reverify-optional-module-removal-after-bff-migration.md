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
