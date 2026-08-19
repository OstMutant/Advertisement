# advertisement-spring-boot-starter — Decisions (generated index)

This module has no `DECISIONS.md` of its own — decisions about it are recorded in
other modules' files and cross-listed here via their own `**Also affects:**` tag.
Do not hand-edit this file — add `**Also affects:** advertisement-spring-boot-starter` to the real ADR in its
home file instead, then regenerate via `bash docs/architecture/scripts/generate-architecture-model.sh`.

- [ADR-024 (marketplace-app)](../marketplace-app/DECISIONS.md) — Jsoup-based, defense-in-depth description length validation
- [ADR-034 (marketplace-app)](../marketplace-app/DECISIONS.md) — No raw cross-starter SQL joins — bulk-lookup port + service-level enrichment; actor-reference columns follow Taxon's naming convention
- [ADR-050 (marketplace-app)](../marketplace-app/DECISIONS.md) — Advertisement delete-side audit capture moves into `AdvertisementSaveService`, matching save's existing orchestration
- [ADR-064 (marketplace-app)](../marketplace-app/DECISIONS.md) — `advertisement` → `user_information` hard FK coupling removed — last one between starters
- [ADR-005 (platform-commons)](../platform-commons/DECISIONS.md) — UserPort + AdvertisementPort for domain module extraction
