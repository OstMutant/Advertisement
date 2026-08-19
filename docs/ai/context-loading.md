# Context loading by task type

What to consult beyond what's already loaded, for a given kind of task. Scope is deliberately
narrow: root and module `CLAUDE.md` are unconditionally `@`-imported into every session by root
`CLAUDE.md` regardless of task type — that isn't a choice this table can influence, so it isn't
listed below. `.claude/skills/deep-review/` (per-module subagents, each given that module's own
`CLAUDE.md`/`DECISIONS.md`) and `.claude/commands/sync-docs.md` (its own changed-file→doc-target
mapping) already have their own internal context strategy — this table complements them for
everyday work that goes through neither, it does not redefine either.

Use [adr-index.md](adr-index.md) to find a specific `DECISIONS.md` entry by module/status instead
of opening a full file speculatively. Once the index narrows it down to one or a few ADR ids in
one module, read just those via
`node docs/architecture/scripts/md-to-decisions-json.js --extract <module> <ADR-NNN>[,<ADR-NNN>...]`
(prints the requested ADR(s) as raw markdown — see [README.md](README.md)) instead of opening the
whole `DECISIONS.md` file. Open the whole file directly only when most of it is actually relevant
(e.g. the "Architectural change" row below) — extraction is for the common case of needing a
handful of known ids out of a file that can run to thousands of lines.

| Task type | Consult | Usually skip |
|---|---|---|
| Trivial fix (typo, one-line, no behavior change) | nothing beyond what's already loaded | `DECISIONS.md`, backlog, `docs/architecture/` |
| Bug fix, single module | filter [adr-index.md](adr-index.md) by module first, then `--extract` just the matching id(s); `backlog/completed/issues/` for a prior fix of the same shape | other modules' `DECISIONS.md`, `docs/architecture/` |
| Local refactor, single class/package | same module's `DECISIONS.md` — filter the index, `--extract` the matching id(s) | cross-module docs |
| Feature, single module | filter the index for related ADRs and `--extract` them if only a few match, `backlog/BACKLOG.md` for related open work | `docs/architecture/` unless the feature touches a documented bounded-context boundary |
| Cross-module feature | `adr-index.md` filtered to every touched module + `platform-commons`, `docs/architecture/architecture-map.html` (Diagrams › Bounded Contexts) | — |
| Architectural change (new SPI, new `*Port`/`*Hook`, schema change touching ownership/FKs) | `platform-commons/DECISIONS.md` in full, `docs/architecture/architecture-map.html` (Module Dependencies, SPI Map, Database ERD), `ArchitectureRulesTest` | — |
| Architecture audit / repo-wide review | use `.claude/skills/deep-review` full mode directly (see [flows.md](flows.md)) — do not hand-load module `CLAUDE.md`/`DECISIONS.md` yourself, it already does this per module | — |

See [flows.md](flows.md) for *which command/skill* handles a given situation — this table only
answers *what to read*, not *what to run*.
