# improvement-034: Feature-workflow standardization — issue-file template + `/feature` skill — ✅ DONE (2026-07-16)

**Type:** improvement — process tooling. Migrated from `backlog/process-improvements.md` Part 2,
item 12 (template portion only — see Explicitly not migrated below for the rest of that item).
**Module:** `.claude/` skills, `backlog/` conventions
**Priority:** low — convenience/consistency, no correctness or safety impact
**When:** independent, no blockers

## Correction (2026-07-16): `backlog/<name>/SPEC.md` is not what's actually in use — retarget to `backlog/issues/`

The original wording proposed formalizing a `backlog/<name>/SPEC.md` shape, citing
`backlog/entity-extensions/SPEC.md` as an example. **Confirmed neither exists anymore** — no
`SPEC.md` file exists anywhere in the repo today, and no `backlog/<name>/` directories exist besides
`issues/`/`completed/`. The convention that actually won out in practice (every issue filed this
session, and the existing ones before it) is one markdown file per issue directly in
`backlog/issues/<prefix>-NNN-<slug>.md`, with a consistent header block (`**Type:**`/`**Module:**`/
`**Priority:**`/`**When:**`) followed by `## Problem` / `## Suggested fix` (→ `## Resolution` once
done) / `## Related` — this is the shape to formalize, not the SPEC.md-in-its-own-directory one.

## Problem

There is no template or skill scaffolding a new issue into the shape that's already emerged in
practice (see correction above) — each one is written from scratch, and consistency (which
sections exist, in what order, what the header block contains) depends entirely on whoever's
writing it remembering the pattern from other files.

## Suggested fix

- A template capturing the shape already in consistent use across `backlog/issues/*.md`: the
  `# <prefix>-NNN: <title>` heading, `**Type:**`/`**Module:**`/`**Priority:**`/`**When:**` header
  block, `## Problem`, `## Suggested fix`, `## Related`.
- A `/feature <name>` skill (or `/issue <name>`, naming TBD — "feature" undersells that this covers
  bugs/tech-debt too, not just new features) that scaffolds a new `backlog/issues/<prefix>-NNN-<slug>.md`
  from that template, auto-filling the next available issue number, so every new issue starts with
  the right shape rather than being copy-pasted from whichever existing file happens to be open.

## Explicitly not migrated from the source item

The source process-improvements.md item 12 also mentioned **OpenRewrite**, explicitly marked
"deferred" there: worth adopting only if mechanical mass-refactors become a regular occurrence
(official recipes exist for Spring Boot version upgrades; custom recipes possible for conversions
like improvement-025's leaf-widget refactor). Not given its own issue — it has no concrete trigger
yet, same reasoning the source document already applied; revisit if a third mechanical
mass-refactor of similar shape comes up.

Also explicitly rejected in the source item (not migrated, kept only as a historical note): PIT
mutation testing (premature without a unit layer — see improvement-027), Error Prone/NullAway
(duplicates Lombok `@NonNull` at doubtful gain), Checkstyle (SonarQube already covers style, see
improvement-032).

## Resolution (2026-07-16)

Added `.claude/commands/feature.md` — a `/feature <title>` skill that scaffolds a new
`backlog/issues/<prefix>-NNN-<slug>.md` from the shape formalized above (auto-numbered by scanning
both `backlog/issues/` and `backlog/completed/issues/` for the highest existing number across all
prefixes), fills the template from conversation context (reading source first when the discussion
doesn't already have enough concrete detail, matching how every issue this session was actually
filed), and inserts a ranked row into `BACKLOG.md`'s priority table in the same operation — per the
`.claude/rules.md` "Issue Lifecycle" rule requiring every new issue to be ranked immediately, not
left for later triage. Registered in the root `CLAUDE.md` "Slash commands available" list.

No separate `SPEC.md` template/directory scaffolding was built — see the Correction above for why
that part of the original idea didn't match reality.

## Related

- `backlog/process-improvements.md` Part 2, item 12 — source item, now superseded by this issue
  (partially — see above for what was deliberately not migrated).
- `.claude/commands/feature.md` — the implemented skill.
- `.claude/rules.md` "Issue Lifecycle" — the mandatory-priority-and-ranking rule this skill enforces
  automatically instead of relying on remembering to do it by hand.
