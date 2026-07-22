# improvement-029: Docs-drift guard + incremental-compile Claude Code hooks

**Type:** improvement — process/tooling. Migrated and **merged** from `backlog/process-
improvements.md` Part 1 item 5 ("Docs-drift guard") and Part 2 item 11 ("Claude Code hooks") —
both items described the same changelog→CLAUDE.md hook independently in the source document; this
issue consolidates them into one so the duplication doesn't propagate into the issue tracker too.
**Module:** repo-wide (`.claude/settings.json` hooks, no application code)
**Priority:** medium — proven need, not speculative: the 2026-07-03/04 audit found three stale
docs (wrong table names in `user-spring-boot-starter/CLAUDE.md` and `audit-spring-boot-starter/
CLAUDE.md`, a resolved-but-still-open finding in `docs/architecture/06-coupling-analysis.md`), and
`/sync-docs` is manual and demonstrably under-used
**When:** independent, no blockers — hook infrastructure already exists and works (the
commit-approval `PreToolUse` hook, see `.claude/settings.json`)

## Problem

1. **Docs drift** — Liquibase changelog edits (`*/db/**changelog**`) are not required to come with
   a matching update to the owning starter's `CLAUDE.md` "Schema"/"Tables:" section. Proven
   concretely: two starter CLAUDE.md files already drifted from their actual schema before this
   was caught by a manual audit, and a coupling-analysis doc kept a finding open after it was
   already resolved in code.
2. **Slow compile feedback** — a Java file edit currently has no automatic incremental compile
   signal; errors surface only at the next full deploy/build, not within seconds of the edit.

## Suggested fix

Two separate hooks, both `PostToolUse`:
1. **changelog→docs guard:** fires on edits under `*/db/**changelog**`. Warns (or blocks the next
   commit, mirroring the existing commit-approval hook's blocking style) until the owning
   starter's `CLAUDE.md` "Schema" section is touched in the same change. Alternative considered in
   the source audit: a CI check comparing Liquibase `tableName=` values against each starter's
   CLAUDE.md "Tables:" line — do the hook first (immediate, local), add the CI check later
   (improvement-028) as a backstop for anyone bypassing the hook.
2. **incremental compile:** fires on `*.java` edits → `mvn compile -pl <module> -q`, surfacing
   compilation errors within seconds of a save instead of at the next full deploy.

## Related

- `backlog/process-improvements.md` Part 1 item 5 and Part 2 item 11 — both source items, now
  superseded by this single issue.
- `backlog/issues/improvement-028-minimal-ci-pipeline.md` — CI-level backstop for the same
  docs-drift problem.
- `docs/architecture/06-coupling-analysis.md` — the doc that had a stale, already-resolved finding
  when this gap was first proven.
