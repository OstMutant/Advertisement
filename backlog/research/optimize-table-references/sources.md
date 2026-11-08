# Sources: optimize-table-references

Purpose: every source the planner used, written with add-source.sh.
Level: 0 is a direct source, 1 and 2 are reached by following references when sources conflict.

| link | taken | confirms or contradicts | date | type | level | confidence |
|---|---|---|---|---|---|---|
| https://docs.liquibase.com/change-types/drop-column.html | dropColumn attributes tableName/columnName; no automatic rollback, so a rollback block with addColumn is needed | confirms | 2026-11-07 | official-doc | 0 | high |
| https://martinfowler.com/bliki/DataClump.html | values always passed together (entityType+entityId) should become one object used wherever the clump appears | contradicts | 2026-11-07 | blog | 0 | high |
| https://refactoring.guru/introduce-parameter-object | Introduce Parameter Object / Preserve Whole Object replace a repeating parameter group; EntityRef already exists | contradicts | 2026-11-07 | other | 0 | medium |
| https://refactoring.guru/smells/dead-code | unused code, parameters and fields are removed (Remove Parameter, delete unused method) | confirms | 2026-11-07 | other | 0 | medium |
| https://www.postgresql.org/docs/current/ddl-constraints.html | ON DELETE CASCADE fits a table that is a component of the referenced one and cannot exist alone (user_preferences of a user, rating/reply of a feedback) | contradicts | 2026-11-07 | official-doc | 0 | high |
