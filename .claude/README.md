# `.claude/` — AI-guidance tooling structure and loading mechanics

This file documents how the pieces under `.claude/` actually get into Claude's context — verified
directly against this harness's real behavior, not assumed from documentation. Behavioral rules
themselves live in `.claude/rules.md`; this file covers the loading *mechanism*, not the rules.

## What's here

- `commands/*.md` — slash-command definitions (`/feature`, `/deploy-and-run`, etc.)
- `skills/*/SKILL.md` — skill definitions, invoked via the Skill tool
- `rules.md` — the main standing behavioral rules file, `@`-imported by root `CLAUDE.md`
- `rules/*.md` — path-scoped rule files, one per module; see `rules/README.md` for their own
  format and a load-bearing nuance about how their `paths:` glob actually matches

## Two loading mechanisms, verified directly

**Eager (`@import`):** any `@path` line in root `CLAUDE.md` — including
`@.claude/rules.md` and, formerly, one `@<module>/CLAUDE.md` line per module — loads that file's
full content unconditionally, every session, regardless of which module the task actually
touches. Confirmed directly: a session's opening context contained the full text of every
`@`-imported file before any file was read and before any module-specific task was stated.

**Path-scoped (`.claude/rules/*.md` + `paths:` frontmatter):** a file here loads only when a tool
call reads or edits a file matching its own `paths:` glob. Confirmed directly with a live test
(a temporary probe rule + a matching dummy file) — the mechanism works exactly as documented, not
merely assumed to. See `rules/README.md` for the glob-matching nuance (not restated here).

**Subagents inherit the full eager-loaded set too.** A minimal `Agent`-tool call, given zero
instructions about it, correctly reported back root `CLAUDE.md`'s content, `.claude/rules.md`'s
first heading, several module summaries, and the memory index — confirmed with zero tool calls on
the agent's part, meaning it came from the agent's own initial context injection, not something it
had to go fetch.

**The auto-memory index (`MEMORY.md`, outside this repo) is also eager-loaded**, the same way
`.claude/rules.md` is — every session, regardless of task relevance. Individual memory files
underneath it are not — those load only when Claude actively reads one.

## Why split it this way

A rule tied to a specific file type or module directory (e.g. a starter's own schema/constraints)
only matters when that module is actually being touched — eager-loading it every session is pure
waste for every other task. A rule with no natural file-path trigger (e.g. the Approval Rule,
which governs a decision, not a file type) has no safe path-scoped home — moving it out of eager
loading would mean it's silently absent exactly when it matters. Keeping both mechanisms, and
routing each rule to the one that actually fits its trigger shape, is what makes the split pay for
itself instead of just moving the same cost around.
