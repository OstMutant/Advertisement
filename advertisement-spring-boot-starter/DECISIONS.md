# advertisement-spring-boot-starter — Decisions (generated index)

This module has no `DECISIONS.md` of its own — decisions about it are recorded in
other modules' files and cross-listed here via their own `**Also affects:**` tag.
Do not hand-edit this file — add `**Also affects:** advertisement-spring-boot-starter` to the real ADR in its
home file instead, then regenerate via `bash docs/architecture/scripts/generate-architecture-model.sh`.

- [ADR-024 (marketplace-app)](../marketplace-app/DECISIONS.md) — Jsoup-based, defense-in-depth description length validation
- [ADR-034 (marketplace-app)](../marketplace-app/DECISIONS.md) — No raw cross-starter SQL joins — bulk-lookup port + service-level enrichment; actor-reference columns follow Taxon's naming convention
- [ADR-064 (marketplace-app)](../marketplace-app/DECISIONS.md) — `advertisement` → `user_information` hard FK coupling removed — last one between starters
- [ADR-036 (platform-commons)](../platform-commons/DECISIONS.md) — `TaxonPort` exposes a typed SQL predicate; the default-method allowance stays for calls to the interface's own methods
- [ADR-032 (platform-commons)](../platform-commons/DECISIONS.md) — `TaxonPort.resolveCategoryAndCityFilter` as a default method — a narrow, bounded exception to "no business logic in platform-commons"
- [ADR-006 (taxon-spring-boot-starter)](../taxon-spring-boot-starter/DECISIONS.md) — Category and city filter is a Taxon-built SQL predicate the owner splices into its own statement
