# improvement-181: Module-list config/auto-discovery for build scripts

**Type:** improvement — dev-experience/tooling, no live bug
**Module:** `scripts/build-and-test/build.sh`, `integration-tests/run.sh`,
`marketplace-app/src/test/java/org/ost/marketplace/architecture/ArchitectureRulesTest.java`,
root `pom.xml`
**Priority:** ⚪ lowest
**When:** independent, no blockers

## Current state

Adding a new Maven module currently requires manually updating a hand-maintained module-name list
in at least three separate non-`pom.xml` places, in addition to `pom.xml` itself:
- `scripts/build-and-test/build.sh`'s `UNIT_MODULES` string (plus a second, parallel `for m in ...`
  loop a few lines below it that copies Surefire reports) — a module left out here silently never
  has its tests executed by `--unit`, with no error or warning.
- `integration-tests/run.sh`'s `STARTER_MODULES` string, used for staleness auto-detection before
  reinstalling starter JARs.
- `ArchitectureRulesTest`'s `STARTER_PACKAGES` constant, plus separate inline
  `resideInAnyPackage(...)` package lists in at least two individual `@ArchTest` rules.

Confirmed directly (improvement-073, 2026-09-03): adding two new modules
(`apikey-spring-boot-starter`, `marketplace-rest-api`) required editing all of the above by hand;
the `build.sh` list was missed on the first pass, so the new module's own unit tests
(`ApiKeyAuthenticationFilterTest`, `ApiKeyControllerTest`) compiled successfully but were never
actually executed until the gap was noticed and fixed separately.

## Why change

Every one of these lists can silently drift from the real module set with no build failure to
catch it — a forgotten update doesn't error, it just quietly stops covering the new module (skipped
tests, skipped architecture checks, or a stale staleness-check). This is exactly the class of
problem `docs/architecture/scripts` and `scripts/sonar` already solve for their own module-list
needs (self-describing `pom.xml` properties / auto-validated against `pom.xml` before every run —
see `.claude/nav/adr-index.md`), just not yet applied to these three.

## Expected benefit

Adding a future module only requires updating the one place Maven already requires (root
`pom.xml`'s `<modules>`) — the scripts/test derive their module list from it automatically, so a
forgotten manual update can no longer silently skip a module's tests or checks.

## Approach

1. **Auto-discovery from `pom.xml`** — parse root `pom.xml`'s `<modules>` list at runtime in
   `build.sh`/`integration-tests/run.sh` instead of hardcoding the module name string, mirroring
   the existing `docs/architecture/scripts`/`scripts/sonar` precedent. `build.sh`'s `UNIT_MODULES`
   would need an additional marker to distinguish "has its own unit tests" modules from starter
   modules whose tests all live in `integration-tests` — e.g. a dedicated `pom.xml` property, same
   shape as the already-existing `<architecture.boundedContext>` property every starter's `pom.xml`
   already carries. For `ArchitectureRulesTest`'s `STARTER_PACKAGES`, the `boundedContext=starter`
   property would need to be readable from the test's own runtime classpath (e.g. baked into a
   properties resource at build time), since raw `pom.xml` isn't naturally on a test classpath.
   More robust, but real engineering effort and a new convention to design and document.
2. **Checklist only** — no structural change; add a single doc (e.g. an "adding a new module" note)
   listing every file that needs a manual touch. Cheap, but still relies on remembering to follow
   it — doesn't actually prevent the silent-skip failure mode confirmed above.

## Related

- Surfaced while implementing the REST-API module split — see
  `backlog/issues/improvement-073-rest-endpoint-infrastructure-test-seeding.md` (or its
  `backlog/completed/issues/` location once that issue closes).
- `.claude/nav/adr-index.md` — `docs/architecture/scripts` and `scripts/sonar` already solve this
  same class of problem for their own module lists.
