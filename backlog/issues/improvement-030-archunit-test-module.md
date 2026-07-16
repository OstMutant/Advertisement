# improvement-030: ArchUnit test module — prose architecture rules become build-breaking tests

**Type:** improvement — architecture consistency/tooling. Migrated from `backlog/process-
improvements.md` Part 2, item 7 — flagged there as "Highest ROI of Part 2."
**Module:** new module (e.g. `architecture-tests`) or a test-only package inside `marketplace-app`
**Priority:** medium-high — the architecture rules this codifies are currently enforced by
discipline and code review only; the source audit found two concrete violations
(improvement-011's unguarded port injection, improvement-010's view deviating from its own
`refresh()` pattern) that survived multiple sessions unnoticed under the current prose-only
approach
**When:** independent, no blockers — ~1 day of work per the original estimate, enforced forever
after

## Problem

Every cross-module and intra-module architecture rule in this project (module import
restrictions, no-Vaadin-in-starters, Port/Hook placement, `@PreAuthorize` placement,
`Optional`-parameter ban, package naming) lives as prose in `CLAUDE.md`/`rules.md` and is checked
by human review only. This has already failed twice in ways that reached working code before
being caught by a manual audit rather than a build failure.

## Suggested fix

One new ArchUnit test module, with a direct rule mapping already worked out in the source
document:

| Existing prose rule | ArchUnit rule |
|---|---|
| UI must not call repositories directly | `noClasses().that().resideInAPackage("..ui..").should().dependOnClassesThat().resideInAPackage("..repository..")` |
| No Vaadin in starters | `noClasses().that().resideInAPackage("org.ost.(audit\|attachment\|user\|advertisement\|taxon)..").should().dependOnClassesThat().resideInAPackage("com.vaadin..")` |
| Ports/Hooks live in platform-commons only | `classes().that().haveSimpleNameEndingWith("Port").and().areInterfaces().should().resideInAPackage("org.ost.platform..")` |
| No `@PreAuthorize` at class level on services | custom condition over class annotations |
| No `Optional` method parameters | custom condition over method signatures |
| `config` (not `configuration`) packages | naming rule |
| `*PortImpl`/`*HookImpl` delegation-only | partial: forbid Jackson/stream imports in `*.spi` impl classes |

**Explicitly not needed:** Spring Modulith — Maven multi-module already gives compile-time
isolation between starters; ArchUnit is specifically for the intra-module rules Maven cannot
express (e.g. "no Vaadin inside a starter" is a dependency-direction rule Maven's own module
graph doesn't capture, since Vaadin is a valid transitive dependency of the reactor as a whole).

## Related

- `backlog/process-improvements.md` Part 2, item 7 — source item, now superseded by this issue.
- `backlog/completed/issues/improvement-011-unguarded-port-injection-in-ui-components.md` — one
  of the two concrete violations that motivated this (would have been caught immediately by the
  "no raw port field on a `@SpringComponent` without a `ComponentFactory` wrapper" rule, had it
  existed as a test rather than a review-time judgment call).
- `backlog/issues/improvement-010-advertisements-view-refresh-error-notification.md` — the
  second concrete violation (a view deviating from the documented `refresh()` guard pattern),
  still open (Deferred bucket, batch into any nearby UI-touching PR).
- `backlog/issues/improvement-028-minimal-ci-pipeline.md` — where these tests actually get
  enforced on every push, not just locally.
