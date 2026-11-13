# marketplace-rest-api — Decisions (generated index)

This module has no `DECISIONS.md` of its own — decisions about it are recorded in
other modules' files and cross-listed here via their own `**Also affects:**` tag.
Do not hand-edit this file — add `**Also affects:** marketplace-rest-api` to the real ADR in its
home file instead, then regenerate via `bash docs/architecture/scripts/generate-architecture-model.sh`.

- [ADR-003 (docs/api-collections)](../docs/api-collections/DECISIONS.md) — The flow runner page is served only behind a property, from the REST module's classpath, and is never switched on by a file a real deployment reuses
- [ADR-093 (marketplace-app)](../marketplace-app/DECISIONS.md) — External REST API carries no hypermedia — no per-resource links, no action list; agents learn the caller from `getMe` and the operations from the MCP tool list
- [ADR-091 (marketplace-app)](../marketplace-app/DECISIONS.md) — External REST API limits write requests per account in a bearer-chain filter
- [ADR-090 (marketplace-app)](../marketplace-app/DECISIONS.md) — An administrator cannot delete their own account
- [ADR-089 (marketplace-app)](../marketplace-app/DECISIONS.md) — External REST API partial updates use JSON Merge Patch applied over the PUT write record
- [ADR-087 (marketplace-app)](../marketplace-app/DECISIONS.md) — External REST API accepts HTTP Basic only on API-key issuance, in its own security chain, with a failed-login limit
- [ADR-081 (marketplace-app)](../marketplace-app/DECISIONS.md) — `GET /api/taxons` filter/sort/pagination reverted — ADR-080's Taxon mandate was applied without checking UI parity, no real caller ever needed it
- [ADR-080 (marketplace-app)](../marketplace-app/DECISIONS.md) — External REST API list endpoints — filter/sort bind onto the existing domain DTOs, pagination uses RFC 8288 `Link` + `X-Total-Count` headers, never an envelope or Spring HATEOAS
- [ADR-035 (platform-commons)](../platform-commons/DECISIONS.md) — Promotion placement is its own starter keyed by entity type, id and reason; each reason has one writer
