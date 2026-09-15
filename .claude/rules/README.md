# `.claude/rules/` — path-scoped rule files

Each file here loads into context only when a tool call reads or edits a file matching that
file's own `paths:` frontmatter glob. This is a different mechanism from root `CLAUDE.md`'s
`@import` lines (and `.claude/rules.md`, which root `CLAUDE.md` also `@`-imports): an `@import` is
unconditional — the whole file loads every session regardless of which module the task touches.
A path-scoped file here loads conditionally — only the module(s) actually relevant to the current
task pay their own cost, instead of every module's full guidance loading on every session
regardless of relevance.

## Format

```
---
paths: ["<module-directory>/**"]
---

<the module's own guidance>
```

## What lives here today

One file per module — the same guidance that used to live in that module's own `CLAUDE.md`, moved
here so it stops loading eagerly. Root `CLAUDE.md` keeps a one-line, always-loaded pointer to each
file; the full detail only loads once a file inside that module's own directory is actually
touched.

- [`advertisement-spring-boot-starter.md`](advertisement-spring-boot-starter.md) — `advertisement-spring-boot-starter/**`
- [`apikey-spring-boot-starter.md`](apikey-spring-boot-starter.md) — `apikey-spring-boot-starter/**`
- [`attachment-spring-boot-starter.md`](attachment-spring-boot-starter.md) — `attachment-spring-boot-starter/**`
- [`audit-spring-boot-starter.md`](audit-spring-boot-starter.md) — `audit-spring-boot-starter/**`
- [`html-sanitizer-lib.md`](html-sanitizer-lib.md) — `html-sanitizer-lib/**`
- [`integration-tests.md`](integration-tests.md) — `integration-tests/**`
- [`marketplace-app.md`](marketplace-app.md) — `marketplace-app/**`
- [`marketplace-orchestrator.md`](marketplace-orchestrator.md) — `marketplace-orchestrator/**`
- [`marketplace-rest-api.md`](marketplace-rest-api.md) — `marketplace-rest-api/**`
- [`platform-commons.md`](platform-commons.md) — `platform-commons/**`
- [`playwright.md`](playwright.md) — `playwright/**`
- [`provider-profile-spring-boot-starter.md`](provider-profile-spring-boot-starter.md) — `provider-profile-spring-boot-starter/**`
- [`query-lib.md`](query-lib.md) — `query-lib/**`
- [`scripts.md`](scripts.md) — `scripts/**` (see "Important" below — the glob matches more broadly than just the top-level `scripts/` directory)
- [`taxon-spring-boot-starter.md`](taxon-spring-boot-starter.md) — `taxon-spring-boot-starter/**`
- [`user-spring-boot-starter.md`](user-spring-boot-starter.md) — `user-spring-boot-starter/**`

## Important — glob matching is not anchored to the repo root

A `paths:` glob like `"scripts/**"` matches **any path containing that segment anywhere**, not
only a path starting with `scripts/` at the repo root. Confirmed directly: reading
`docs/architecture/scripts/generate-architecture-model.sh` loaded `scripts.md`'s content, even
though that file lives under `docs/architecture/scripts/`, nested two levels deep — not the
top-level `scripts/` directory `scripts.md` was actually written for. Write globs narrow enough
that an unrelated directory elsewhere in the repo sharing the same path segment can't trigger an
unintended load.

A full mechanical sweep (`bash .claude/nav/scripts/check-rule-path-globs.sh`) confirmed this isn't
an isolated case — 8 of the 16 rule files here have at least one unintended match today:
`scripts.md` also matches `.claude/nav/scripts/` (a second, previously-undocumented instance,
alongside the `docs/architecture/scripts/` one above); `html-sanitizer-lib.md`,
`integration-tests.md`, `marketplace-app.md`, `marketplace-orchestrator.md`,
`marketplace-rest-api.md`, `playwright.md`, and `query-lib.md` all also match their own
module-named subdirectories under `scripts/build-and-test/reports/**/surefire/` (per-module
Surefire report output) and, for `integration-tests`/`playwright`, under `scripts/logs/` too. No
anchored-glob syntax to avoid this is currently known to exist for the `paths:` field — the
official docs' own examples (e.g. `src/**/*` described as "all files under `src/`") imply
root-relative matching is the intent, but the actually-observed behavior in this installed version
matches anywhere in the tree, and no alternate anchoring prefix is documented. Treat every instance
above as low-severity (a matching path is a generated build-report/log artifact directory, not real
module source — the cost is an unnecessary extra rule file loading into context, not a correctness
bug) rather than attempting an unverified glob-syntax workaround. Re-run the checker script
periodically (e.g. whenever a new `.claude/rules/*.md` file is added) rather than assuming this
list stays complete by hand.

## Important — path-scoped rules load on Read, not on Write

Per Claude Code's own documentation: "Path-scoped rules trigger when Claude reads files matching
the pattern, not on every tool use." Creating a brand-new file with `Write` in a module that has no
file read yet in the current session does **not** load that module's `.claude/rules/<module>.md` —
only a `Read` of a matching path does. In practice this rarely bites for edits to *existing* files
(the `Edit` tool already requires a prior `Read` of the same file), but a genuinely new file in a
module — e.g. the first class in a brand-new package — can be written before its module's own
rules ever entered context. No mitigation currently in place beyond awareness: read an existing
sibling file in the target module first when creating something genuinely new there.
