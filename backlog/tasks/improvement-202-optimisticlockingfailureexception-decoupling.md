# improvement-202: `OptimisticLockingFailureException` couples every layer to Spring Data's exception vocabulary

**Type:** improvement — architecture/decoupling (deferred-findings bucket, carved out of
`improvement-201` Part 4 once sized for real work).
**Module:** platform-commons (`AdvertisementPort`/`ProviderProfilePort`/`TaxonPort` Javadoc
contracts) + advertisement/provider-profile/taxon/user-spring-boot-starter (repositories) +
marketplace-orchestrator (`AdvertisementSaveService`/`ProviderProfileSaveService`) +
marketplace-app (`AbstractEntityOverlay`) + marketplace-rest-api (`ApiExceptionHandler`).
**Priority:** 🔴 Top — placed above every other backlog item per explicit user direction,
2026-09-23.
**When:** Not yet started. Independent of every other Top item — no shared files.

## Current state

Verified end-to-end against current source, 2026-09-23: `org.springframework.dao.
OptimisticLockingFailureException` (a Spring Data framework type, not a project-owned type) is
the de facto cross-cutting "stale write" signal used everywhere in the reactor:

- Declared as part of the public contract in `platform-commons`'s own `*Port` interfaces (Javadoc
  `@throws`-style documentation, not a formal `throws` clause since these are unchecked): `
  AdvertisementPort.java:29`, `ProviderProfilePort.java:35`, `TaxonPort.java:96,100`.
- Actually thrown in 5 places: `AdvertisementRepository.java:131`,
  `ProviderProfileRepository.java:117`, `TaxonRepository.java:112`,
  `UserPreferencesRepository.java:78` (real `@Version` conflicts from Spring Data JDBC), plus
  `AdvertisementSaveService.java:56` and `ProviderProfileSaveService.java:47` in
  `marketplace-orchestrator` (a synthetic guard for "the row was deleted between read and write" —
  a different real-world scenario reusing the same exception type and the same generic message,
  see `marketplace-orchestrator/DECISIONS.md` ADR-006).
- Caught/handled in 2 places: `marketplace-app/.../AbstractEntityOverlay.java:78` (Vaadin UI —
  shows a conflict notification) and `marketplace-rest-api/.../ApiExceptionHandler.java:31-35`
  (REST — maps to HTTP 412 Precondition Failed).
- Referenced directly in at least 4 existing test files (`TaxonApiControllerTest`,
  `ProviderProfileApiControllerTest`, `AdvertisementApiControllerTest`,
  `ApiExceptionHandlerTest`), which would need updating alongside any type change.

`ApiExceptionHandler` follows the same shape for several other JDK/Spring generic exception types
(`DuplicateKeyException`, `IllegalStateException`, `NoSuchElementException`,
`IllegalArgumentException`) — this is a deliberate, consistently-applied project convention, not
an isolated oversight in one class. `marketplace-orchestrator/DECISIONS.md` ADR-006 already
records why `OptimisticLockingFailureException` specifically was chosen for the synthetic
concurrent-delete guard ("already the proven, correct shape").

## Why change

Every consuming layer (UI, REST, every domain starter, the shared-kernel `*Port` contracts
themselves) is coupled to Spring Data's own exception hierarchy as the vocabulary for "this write
conflicts with newer state" — a persistence-framework type leaking into the shared domain
contract (`platform-commons`) and every layer built on top of it, rather than a project-owned
type. Separately, the two real-world scenarios that throw it today — a genuine `@Version`
conflict vs. a row deleted out from under an in-flight edit — are currently indistinguishable to
any caller, both carrying the same generic message.

## Expected benefit

Primarily architectural-purity: `platform-commons` contracts stop naming a framework-internal
type; a project-owned type could carry richer, structured semantics (e.g. distinguish the two
scenarios above) if that's ever needed. **Explicitly not** a user-facing or external-API-facing
benefit — `marketplace-rest-api` already never serializes the Java exception type itself, only the
mapped `ErrorResponse` + HTTP 412, so no external contract changes either way. See the discussion
in this task's own originating conversation (`improvement-201` Part 4) for the full honest
cost/benefit weighing before starting this.

## Approach

1. Define a project-owned unchecked exception (e.g. `StaleWriteException`) in `platform-commons`
   (`core` package, alongside `TooManyAttemptsException` — the existing precedent for a
   project-owned cross-layer exception type living in the shared kernel).
2. Replace all 5 real throw sites (4 repositories + `AdvertisementSaveService`/
   `ProviderProfileSaveService`) and the 3 `*Port` Javadoc references.
3. Update both catch/handler sites (`AbstractEntityOverlay`, `ApiExceptionHandler`) and the 4
   existing tests that stub the old type.
4. Decide whether to preserve the current single-type-two-scenarios shape or give the new type a
   distinguishing field/subtype for the "concurrent delete" case — a design choice to make before
   implementing, not during.
5. Update `marketplace-orchestrator/DECISIONS.md` ADR-006 (annotate as superseded/updated, per
   this project's own "update the existing entry rather than only adding a new one" rule) plus any
   other `DECISIONS.md`/`.claude/rules/*.md` that names the old type.
6. Full `scripts/ci.sh` pass — this touches every domain's repository layer plus UI and REST error
   handling.
