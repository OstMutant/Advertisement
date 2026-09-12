# marketplace-rest-api — Decisions (generated index)

This module has no `DECISIONS.md` of its own — decisions about it are recorded in
other modules' files and cross-listed here via their own `**Also affects:**` tag.
Do not hand-edit this file — add `**Also affects:** marketplace-rest-api` to the real ADR in its
home file instead, then regenerate via `bash docs/architecture/scripts/generate-architecture-model.sh`.

- [ADR-081 (marketplace-app)](../marketplace-app/DECISIONS.md) — `GET /api/taxons` filter/sort/pagination reverted — ADR-080's Taxon mandate was applied without checking UI parity, no real caller ever needed it
- [ADR-080 (marketplace-app)](../marketplace-app/DECISIONS.md) — External REST API list endpoints — filter/sort bind onto the existing domain DTOs, pagination uses RFC 8288 `Link` + `X-Total-Count` headers, never an envelope or Spring HATEOAS
