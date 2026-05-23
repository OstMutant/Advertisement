Record a new architectural decision in the appropriate DECISIONS.md file.

Usage: /decision <module> — <title>
Example: /decision platform-commons — Split audit.api from audit.spi

Available modules and their DECISIONS.md paths:
- marketplace-app    → /app/marketplace-app/DECISIONS.md
- audit-starter      → /app/audit-spring-boot-starter/DECISIONS.md
- attachment-starter → /app/attachment-spring-boot-starter/DECISIONS.md
- platform-commons   → /app/platform-commons/DECISIONS.md
- sql-engine         → /app/sql-engine/DECISIONS.md
- playwright         → /app/playwright/DECISIONS.md
- scripts            → /app/scripts/DECISIONS.md

Steps:
1. Parse module and title from $ARGUMENTS; if missing, ask the user
2. Read the target DECISIONS.md to understand existing style and entries
3. Draft a new entry with today's date: `## YYYY-MM-DD — <title>`
   Structure: **Decision** (what was decided), **Why** (motivation/constraints), **Rejected** (alternatives considered)
4. Present the draft and wait for confirmation before writing
5. Insert the new entry at the top of the file, after the `# ...` heading, before existing entries
