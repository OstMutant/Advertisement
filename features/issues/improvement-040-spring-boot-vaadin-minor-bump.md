# improvement-040: Spring Boot 4.0.6→4.1.0 + Vaadin 25.1.5→25.2.1 minor version bump

**Type:** improvement — dependency maintenance. Migrated from the remainder of `features/process-
improvements.md` Part 1, item 2 (the owasp-sanitizer bump from that same item was already done —
see `improvement-006`/BACKLOG "Week 0" — this issue is specifically the still-open Spring Boot/
Vaadin part).
**Module:** root `pom.xml` (`spring-boot-starter-parent` version, `vaadin.version` property)
**Priority:** low-medium — minor version bumps, not security-critical (unlike the already-done
owasp-sanitizer bump); routine maintenance
**When:** independent — re-check current available versions before starting, since the source
scan is from 2026-07-04; verified during this migration (2026-07-13) that the repo is still on
`4.0.6`/`25.1.5`, but did not re-scan whether newer minor versions have since been released

## Problem

Root `pom.xml` currently pins `spring-boot-starter-parent` at `4.0.6` and `vaadin.version` at
`25.1.5` (both confirmed still current as of 2026-07-13). The 2026-07-04 dependency scan
(`./mvnw versions:display-property-updates` / `display-parent-updates` /
`display-dependency-updates`) found `4.1.0`/`25.2.1` available at that time, pulling current
Jackson/Logback/Spring Security transitively.

## Suggested fix

- Bump `spring-boot-starter-parent` to `4.1.0` and `vaadin.version` to `25.2.1` together in one PR
  (the source plan explicitly groups them, since Vaadin's Spring Boot integration is
  version-sensitive) + full e2e run.
- Ignore transitive noise from the Spring Boot BOM unrelated to this app (elasticsearch/kotlin/
  pulsar dependencies flagged by the scanner — not used directly).
- Before starting: re-run `./mvnw versions:display-property-updates` to confirm `4.1.0`/`25.2.1`
  are still the latest minors (9+ days have passed since the original scan) and pick up the
  process addition below at the same time.

## Process addition (from the source item, still relevant)

Run `./mvnw versions:display-property-updates` monthly, or wire it into CI (improvement-028) once
that exists, so a security-relevant dependency can't silently fall years behind the way
owasp-java-html-sanitizer had before that bump was caught.

## Related

- `features/process-improvements.md` Part 1, item 2 — source item; the owasp-sanitizer portion is
  already done (see `features/completed/issues/` — was bundled with the tag-spam validator fix,
  commit a9ed6d7e per `BACKLOG.md` "Week 0"), this issue covers the remaining, still-open part.
- `features/issues/improvement-028-minimal-ci-pipeline.md` — where the monthly dependency-check
  process addition would actually get automated.
