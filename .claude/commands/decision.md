Record a new architectural decision in the appropriate DECISIONS.md file.

Usage: /decision <module> — <title>
Example: /decision platform-commons — Split audit.api from audit.spi

Available modules and their DECISIONS.md paths (if a module isn't listed, check whether it has a
`DECISIONS.md` at all before assuming — this list has gone stale before):
- marketplace-app    → /app/marketplace-app/DECISIONS.md
- audit-starter      → /app/audit-spring-boot-starter/DECISIONS.md
- attachment-starter → /app/attachment-spring-boot-starter/DECISIONS.md
- platform-commons   → /app/platform-commons/DECISIONS.md
- query-lib          → /app/query-lib/DECISIONS.md
- playwright         → /app/playwright/DECISIONS.md
- scripts            → /app/scripts/DECISIONS.md
- scripts/ci         → /app/scripts/ci/DECISIONS.md
- scripts/sonar      → /app/scripts/sonar/DECISIONS.md
- scripts/architecture → /app/scripts/architecture/DECISIONS.md
- integration-tests  → /app/integration-tests/DECISIONS.md
- taxon-starter      → /app/taxon-spring-boot-starter/DECISIONS.md

Note: `user-spring-boot-starter` and `advertisement-spring-boot-starter` have no `DECISIONS.md` of
their own — see root `CLAUDE.md`'s "Architectural Decisions Log" for where their decisions live.

## Before writing: is this actually ADR-worthy?

Write an ADR only if at least one is true:

- Reversing this decision later would be expensive (touches multiple modules, a public
  contract, a schema, or a build-time dependency).
- This decision constrains or informs other future decisions (a pattern other code will
  follow, a rule other modules must respect).
- Someone — human or a future Claude session — is likely to propose an approach already tried
  and rejected here, and would benefit from that history being on record.

Do not write an ADR for: a bug fix, a UI/layout adjustment, a sort-order or formatting change, or
anything whose rationale is self-evident from reading the code change itself. These are ordinary
commits — a good commit message is sufficient, no `DECISIONS.md` entry needed. This applies even
when the tool being changed is itself about architecture or decisions (e.g.
`architecture-map.html`'s own screen/card/group layout, or this very command's own wording) — a
tool's subject matter being "architecture" does not make changes to its own UI exempt from this
gate; judge those changes exactly as you would judge a UI/layout change in any other tool.

If genuinely unsure, prefer *not* writing the ADR and note the decision briefly in the commit
message instead — the cost of a missing ADR is a future "why did we do this?" question; the cost
of an unnecessary one is that this file stops being a reliable signal of what actually matters,
for every reader after it, including future AI context loading it.

Steps:
1. Check the worthiness gate above first — if none of the three conditions hold, stop here; tell
   the user this doesn't meet the bar and suggest a plain commit message instead of an ADR entry
2. Parse module and title from $ARGUMENTS; if missing, ask the user
3. Read the target DECISIONS.md to understand existing style and entries; find the highest
   `## ADR-NNN:` already in the file and increment for the new entry's number
4. Draft a new entry: `## ADR-NNN: <title>` followed by `**Status:** Accepted` on its own line,
   then `**Context:**` (what prompted this), `**Decision:**` (what was decided), and optionally
   `**Rejected alternatives:**` — this is the format every existing `DECISIONS.md` entry actually
   uses (also documented in `.claude/commands/sync-docs.md`'s "Documentation Rules") and the one
   `scripts/ai/generate-adr-index.sh` parses; a differently-shaped entry will not appear in
   `docs/ai/adr-index.md`
5. Present the draft and wait for confirmation before writing
6. Insert the new entry at the top of the file, after the `# ...` heading, before existing entries
7. Regenerate `docs/ai/adr-index.md`: `bash scripts/ai/generate-adr-index.sh` — mandatory, same
   change, not a follow-up (see `docs/ai/README.md`)
