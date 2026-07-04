# improvement-016: Unused vaadin-core dependency in platform-commons pom

**Type:** improvement — dependency hygiene / stated-architecture violation
**Module:** platform-commons
**Priority:** low — no runtime impact today; contradicts documented architecture
**When:** Week 0 — quick-wins batch (one pom block)

## Problem

`platform-commons/pom.xml` declares:

```xml
<dependency>
    <groupId>com.vaadin</groupId>
    <artifactId>vaadin-core</artifactId>
    <scope>provided</scope>
</dependency>
```

Verified 2026-07-04: **no Java file in platform-commons imports `com.vaadin.*`** — the
dependency is a dead leftover (likely from before UI contracts moved to
`marketplace-app/ui/core`). The module's own pom description even still says "generic UI
contracts", which is equally stale.

This contradicts the documented shared-kernel contract (platform-commons CLAUDE.md: SPI
interfaces "with no Vaadin dependency"; non-UI consumers must be able to depend on `*.spi` /
`*.dto` without pulling Vaadin). `provided` scope limits the practical damage (not transitive
at runtime), but the declaration still misleads readers, adds Vaadin to the module's compile
classpath inviting accidental usage, and was flagged as a kernel-purity violation in an
external architecture review.

## Suggested fix

1. Remove the `vaadin-core` dependency block from `platform-commons/pom.xml`.
2. Fix the pom `<description>` — drop "generic UI contracts" (those live in
   `marketplace-app` `ui.core`).
3. Build the full reactor to confirm nothing referenced it.
4. Follow-up guard: the planned Maven Enforcer / ArchUnit work (process-improvements Part 2)
   should include a banned-dependency rule — no `com.vaadin` outside marketplace-app —
   making this class of leftover impossible to reintroduce silently.
