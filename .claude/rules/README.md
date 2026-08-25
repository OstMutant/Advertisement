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

## Important — glob matching is not anchored to the repo root

A `paths:` glob like `"scripts/**"` matches **any path containing that segment anywhere**, not
only a path starting with `scripts/` at the repo root. Confirmed directly: reading
`docs/architecture/scripts/generate-architecture-model.sh` loaded `scripts.md`'s content, even
though that file lives under `docs/architecture/scripts/`, nested two levels deep — not the
top-level `scripts/` directory `scripts.md` was actually written for. Write globs narrow enough
that an unrelated directory elsewhere in the repo sharing the same path segment can't trigger an
unintended load.
