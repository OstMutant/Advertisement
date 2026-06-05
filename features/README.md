# Features

This directory tracks **feature-level specifications and implementation plans** for substantial work that spans multiple modules, takes more than one session to deliver, or warrants explicit design discussion.

It complements per-module `DECISIONS.md` files:
- A `DECISIONS.md` entry records **one architectural decision** scoped to one module.
- A `features/<name>/` folder records **the full lifecycle of one feature** that may produce many decisions across modules.

## Structure

```
features/
├── README.md                 ← this file: convention
└── <feature-name>/
    ├── SPEC.md               ← what + why
    ├── DESIGN.md             ← how (architecture)
    └── PLAN.md               ← step-by-step plan + status
```

`<feature-name>` is `kebab-case`, short, and stable (rename only at significant scope change).

## File responsibilities

### `SPEC.md` — Requirements & scope
- Problem statement (in user's own words is fine)
- Goals / non-goals
- Resolved open questions (Q → A pairs)
- Out-of-scope items that came up but were deferred

**Audience:** anyone wanting to understand *why this feature exists* without reading code.
**Updated when:** scope changes, new questions arise, deferred items are reactivated.

### `DESIGN.md` — Architecture
- Data model (tables, entities, relationships)
- SPI / API contracts and their direction
- Module placement (which module owns what)
- Integration with existing subsystems (audit, attachments, query)
- UI patterns and component structure
- Cross-cutting concerns: i18n, security, soft-delete, etc.

References to module `DECISIONS.md` entries are encouraged for low-level rationale.

**Audience:** an engineer who needs to extend the feature later.
**Updated when:** architectural approach changes, never for cosmetic edits.

### `PLAN.md` — Step-by-step execution
- Numbered steps, each scoped to one commit (ideally)
- For each step:
  - **Status:** `[ ] todo` / `[~] in progress` / `[x] done`
  - **Prompt:** the exact prompt that will execute the step
  - **Notes:** anything learned during execution (gotchas, deviations from plan)
- Step status updates happen during work; the plan itself may be revised if reality differs.

**Audience:** the person (or agent) executing the feature.
**Updated when:** before each step (refine the prompt) and after each step (note outcome).

## Relationship to `DECISIONS.md`

| Where it lives | What it records |
|---|---|
| `features/<name>/DESIGN.md` | Feature-wide architectural choices (whole feature view) |
| `<module>/DECISIONS.md` | Decisions scoped to one module — typically discovered during implementation |

Example: while implementing the taxonomy feature, we choose to store translations as a separate normalized table rather than a JSON column. That belongs in `taxon-spring-boot-starter/DECISIONS.md` (module-specific). The decision that "categories are a special case of a generic taxonomy mechanism" belongs in `features/taxonomy/DESIGN.md` (feature-wide).

## Lifecycle

1. **Drafting** — create folder, write `SPEC.md`, sketch `DESIGN.md`.
2. **Planning** — fill `PLAN.md` with concrete steps.
3. **Execution** — work through `PLAN.md`, update statuses, record module decisions in `DECISIONS.md` as they occur.
4. **Completed** — when all steps are `[x] done`, the folder remains as historical record.

There is no "archive" step. Completed features stay in place; their `PLAN.md` becomes a reference for how the feature was actually built.

## When NOT to create a feature folder

- Single-commit fixes or small refactors → just commit.
- One-module change with a clear architectural question → `DECISIONS.md` entry is enough.
- Exploratory spike → keep in a chat session; promote to a feature folder only if it becomes real work.
