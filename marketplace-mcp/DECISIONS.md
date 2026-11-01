# marketplace-mcp — Decisions (generated index)

This module has no `DECISIONS.md` of its own — decisions about it are recorded in
other modules' files and cross-listed here via their own `**Also affects:**` tag.
Do not hand-edit this file — add `**Also affects:** marketplace-mcp` to the real ADR in its
home file instead, then regenerate via `bash docs/architecture/scripts/generate-architecture-model.sh`.

- [ADR-004 (docs/api-collections)](../docs/api-collections/DECISIONS.md) — MCP flows are a separate Postman collection of plain HTTP JSON-RPC requests, run by the shared runner on their own page, with a coverage check against the live tools/list
- [ADR-003 (docs/api-collections)](../docs/api-collections/DECISIONS.md) — The flow runner page is served only behind a property, from the REST module's classpath, and is never switched on by a file a real deployment reuses
- [ADR-092 (marketplace-app)](../marketplace-app/DECISIONS.md) — The MCP server is a separate module with no project dependencies that wraps the REST API over HTTP
- [ADR-080 (marketplace-app)](../marketplace-app/DECISIONS.md) — External REST API list endpoints — filter/sort bind onto the existing domain DTOs, pagination uses RFC 8288 `Link` + `X-Total-Count` headers, never an envelope or Spring HATEOAS
- [ADR-078 (marketplace-app)](../marketplace-app/DECISIONS.md) — External REST API authentication — long-lived bearer API-key, not OAuth2
