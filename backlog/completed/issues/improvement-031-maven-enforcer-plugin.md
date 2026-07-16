# improvement-031: Maven Enforcer plugin — dependency hygiene at build time — ✅ DONE (2026-07-16)

**Type:** improvement — build tooling. Migrated from `backlog/process-improvements.md` Part 2,
item 8.
**Module:** root `pom.xml`
**Priority:** low-medium — no incident depends on this yet; it's a preventive gate for a rule
(`no direct starter→starter imports`) currently enforced by convention/code review only
**When:** independent, no blockers — a few lines of plugin config, no code changes elsewhere

## Problem

Several dependency-hygiene rules are currently convention-only, checked by human review:
- No direct starter→starter dependencies in poms (the module import rule from `rules.md`).
- No guarantee against conflicting transitive dependency versions across the multi-module reactor.
- No enforced minimum Java/Maven version at build time (currently relies on local environment
  matching what CI/deploy scripts assume).

## Suggested fix

Add `maven-enforcer-plugin` to the root `pom.xml` with:
- A custom rule (or `bannedDependencies`) banning direct starter→starter `<dependency>` entries —
  this is the one rule ArchUnit (improvement-030) cannot express, since ArchUnit operates on
  compiled bytecode/classpath, not on Maven's own module graph.
- `dependencyConvergence` — catch conflicting transitive versions across the reactor.
- `requireJavaVersion` / `requireMavenVersion` pinned to what the project actually targets (Java
  25, current Maven).
- Optionally `banDuplicatePomDependencyVersions`.

## Resolution (2026-07-16)

Done essentially as suggested, with `bannedDependencies` activated per-starter rather than at the
root (a root-level ban would also apply to `marketplace-app`/`integration-tests`, which
legitimately depend on starters). Turning the rules on immediately found and fixed two real,
previously-invisible issues: a vestigial `advertisement-spring-boot-starter` → `audit-`/
`attachment-spring-boot-starter` Maven dependency with zero actual Java usage (removed), and a
genuine `dependencyConvergence` conflict on `commons-text` via `liquibase-core`'s two dependency
paths (pinned). `banDuplicatePomDependencyVersions` was not added — not needed once
`dependencyConvergence` was already catching real problems, kept the rule set minimal. Verified
via full `deploy.sh --no-cache` + `bash scripts/playwright.sh e2e --full --ux`, 48/48. Full
writeup: `marketplace-app/DECISIONS.md` ADR-039.

## Related

- `backlog/process-improvements.md` Part 2, item 8 — source item, now superseded by this issue.
- `.claude/rules.md` "Module Import Rules" — the convention this plugin makes build-enforced
  instead of review-enforced.
- [improvement-030](../../issues/improvement-030-archunit-test-module.md) — the sibling tool for rules that
  operate on bytecode rather than the Maven pom graph.
