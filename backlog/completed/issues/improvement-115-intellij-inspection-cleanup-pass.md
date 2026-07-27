# improvement-115: Code quality cleanup from IntelliJ IDEA inspection export

**Type:** improvement — code hygiene + 2 potential null-safety bugs
**Module:** cross-cutting — touches `marketplace-app`, `platform-commons`, `query-lib`,
`advertisement-spring-boot-starter`, `audit-spring-boot-starter`, `taxon-spring-boot-starter`,
`user-spring-boot-starter`, `attachment-spring-boot-starter`, `integration-tests`
**Priority:** medium — mostly hygiene/tech-debt, but includes two real potential-NPE findings and
one dead-code branch
**When:** independent, no blockers — batch fixable in 4 ordered sub-passes (see Suggested fix)

## Problem

A full-project IntelliJ IDEA inspection was run and exported to `/app/errors/*.xml` (one file per
inspection type, 33 files total). Filtering out noise categories (`Annotator` — markdown code
blocks misparsed as Java, `SpellCheckingInspection`/`GrazieInspection`/`GrazieStyle` — prose
spelling/grammar, `Markdown*` — doc formatting, `HttpUrlsUsage` — standard Liquibase XSD
namespace URLs, `Html*`/`XmlHighlighting` — low-value generated/library files), ~230 real findings
remain across 22 categories:

- **Potential bugs (2):** `DataFlowIssue` — `SharedEnvConfig.getAbsolutePath()` may NPE
  (`integration-tests/.../SharedEnvConfig.java:61`), `AdvertisementSaveService` argument `before`
  might be null (`marketplace-app/.../AdvertisementSaveService.java:69`)
- **Dead code branch (1):** `ConstantValue` — `ids == null` always false in
  `AdvertisementFormOverlayModeHandler.java:316`
- **Deprecated API (6):** Testcontainers `PostgreSQLContainer<?>` (3 uses in
  `AbstractPostgresIntegrationTest.java`), Vaadin `@Theme` (deprecated since 25.0, in
  `AppShell.java` — **carved out into [improvement-116](improvement-116-vaadin-theme-annotation-migration.md)**,
  needs a full visual-regression pass, not a mechanical fix), `setAcceptedFileTypes` (deprecated
  since 25.2, in `AttachmentUploadButton.java`)
- **Null-safety annotation gaps (26):** `NullableProblems` — parameters/methods overriding
  `@NonNull`/`@NullMarked` supertypes without repeating the annotation, across `platform-commons`
  SPI (`AuditDomainHook`), `query-lib` (`OffsetPageable`), `marketplace-app`
  (`RequestCorrelationFilter`), and several `integration-tests` test classes
- **Modernization (20):** `SequencedCollectionMethodCanBeUsed` — `.get(0)` → `.getFirst()`,
  `.add(0, x)` → `.addFirst(x)` (Java 21+ `SequencedCollection`), across `AdvertisementService`,
  `AuditReadService`, and several test classes
- **Dead code (90):** `unused` — unused i18n enum constants (~24 in `I18nKey`), unused methods/
  constructors/fields across `marketplace-app`, `platform-commons`, `taxon`/`user`/`attachment`
  starters. **Note:** a subset are false positives — `*CrudRepository` interfaces
  (`AdvertisementCrudRepository`, `AttachmentCrudRepository`, `TaxonCrudRepository`,
  `UserCrudRepository`, `UserProfileCrudRepository`) are flagged "interface is not implemented"
  because Spring Data JDBC implements them via a runtime proxy IntelliJ can't see; `ArchUnit`
  fields in `ArchitectureRulesTest` are flagged unused because they're only read via ArchUnit's
  own reflection-based test runner, not direct Java references. Both must be excluded, not "fixed."
- **Excess abstraction signal (17):** `SameParameterValue`/`SameReturnValue` — parameters/return
  values that are always one value across all current call sites (e.g.
  `AbstractViewOverlayModeHandler.buildSecondaryTab()`/`buildTertiaryTab()` always return `null`,
  `AuditActivityEnrichHook.entityType()` always returns `ADVERTISEMENT`). Each needs individual
  judgment — some reflect genuine dead flexibility, others are single-implementation SPI methods
  where "always" just means "only one implementation exists today," not a real invariant.
- **Small safe fixes (~40):** `ClassCanBeRecord` (3, needs immutability check per class),
  `Convert2Diamond`/`Convert2MethodRef` (2), `DanglingJavadoc` (1), `EmptyMethod` (2, verify
  intentional no-op interface default before touching), `FieldCanBeLocal` (2),
  `RedundantSuppression`/`RedundantThrows` (3), `SimplifyStreamApiCallChains` (2),
  `UNCHECKED_WARNING` (1), `UNUSED_IMPORT` (10), `UnusedProperty` (2), `UnusedReturnValue` (4,
  verify caller intentionally ignores before removing)

## Suggested fix

Four ordered sub-passes, each compiled/tested before moving to the next:

1. Safe mechanical fixes: unused imports, `SequencedCollectionMethodCanBeUsed`,
   `Convert2Diamond`/`Convert2MethodRef`, `DanglingJavadoc`, `FieldCanBeLocal`,
   `RedundantSuppression`/`RedundantThrows`, `SimplifyStreamApiCallChains`, dead `ConstantValue`
   branch, unused i18n property, `ClassCanBeRecord` (case-by-case)
2. Potential bugs: fix the 2 `DataFlowIssue` NPEs, address the 6 `Deprecation` warnings with
   their recommended replacements
3. `NullableProblems`: add missing `@NonNull`/`@NullMarked` annotations (26 places)
4. Dead code + excess abstraction: exclude the known false positives (CrudRepository proxies,
   ArchUnit fields), remove confirmed-dead code from the rest of `unused` (90), and review
   `SameParameterValue`/`SameReturnValue` (17) one by one — collapse only where the abstraction is
   genuinely unused, not where it's a single-implementation SPI awaiting future consumers

Run `bash scripts/unit-tests.sh` after each sub-pass; full `bash scripts/ci.sh` before closing.

## Related

Source data: IntelliJ IDEA inspection export at `/app/errors/*.xml` (untracked, not part of this
issue's scope to commit — ephemeral analysis input, not a repo artifact).
