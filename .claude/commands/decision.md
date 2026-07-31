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
- scripts/ai         → /app/scripts/ai/DECISIONS.md
- integration-tests  → /app/integration-tests/DECISIONS.md
- taxon-starter      → /app/taxon-spring-boot-starter/DECISIONS.md

Note: `user-spring-boot-starter` and `advertisement-spring-boot-starter` have no `DECISIONS.md` of
their own — see root `CLAUDE.md`'s "Architectural Decisions Log" for where their decisions live.

Steps:
1. Parse module and title from $ARGUMENTS; if missing, ask the user
2. Read the target DECISIONS.md to understand existing style and entries; find the highest
   `## ADR-NNN:` already in the file and increment for the new entry's number
3. Draft a new entry: `## ADR-NNN: <title>` followed by `**Status:** Accepted` on its own line,
   then `**Context:**` (what prompted this), `**Decision:**` (what was decided), and optionally
   `**Rejected alternatives:**` — this is the format every existing `DECISIONS.md` entry actually
   uses (also documented in `.claude/commands/sync-docs.md`'s "Documentation Rules") and the one
   `scripts/ai/generate-adr-index.sh` parses; a differently-shaped entry will not appear in
   `docs/ai/adr-index.md`
4. Present the draft and wait for confirmation before writing
5. Insert the new entry at the top of the file, after the `# ...` heading, before existing entries
6. Regenerate `docs/ai/adr-index.md`: `bash scripts/ai/generate-adr-index.sh` — mandatory, same
   change, not a follow-up (see `docs/ai/README.md`)
