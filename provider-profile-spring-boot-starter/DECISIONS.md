# provider-profile-spring-boot-starter — Decisions (generated index)

This module has no `DECISIONS.md` of its own — decisions about it are recorded in
other modules' files and cross-listed here via their own `**Also affects:**` tag.
Do not hand-edit this file — add `**Also affects:** provider-profile-spring-boot-starter` to the real ADR in its
home file instead, then regenerate via `bash docs/architecture/scripts/generate-architecture-model.sh`.

- [ADR-075 (marketplace-app)](../marketplace-app/DECISIONS.md) — Providers public catalog — OG/sitemap/deep-link pattern applied to a second domain, view-only catalog overlay
- [ADR-036 (platform-commons)](../platform-commons/DECISIONS.md) — `TaxonPort` exposes a typed SQL predicate; the default-method allowance stays for calls to the interface's own methods
- [ADR-035 (platform-commons)](../platform-commons/DECISIONS.md) — Promotion placement is its own starter keyed by entity type, id and reason; each reason has one writer
- [ADR-032 (platform-commons)](../platform-commons/DECISIONS.md) — `TaxonPort.resolveCategoryAndCityFilter` as a default method — a narrow, bounded exception to "no business logic in platform-commons"
- [ADR-031 (platform-commons)](../platform-commons/DECISIONS.md) — Provider profile city storage unified to `taxon_assignment`, matching Advertisement — scalar shape kept, not converted to a list
- [ADR-027 (platform-commons)](../platform-commons/DECISIONS.md) — `ProviderProfilePort` added — F-04 Batch B, `provider-profile-spring-boot-starter`
- [ADR-030 (platform-commons)](../platform-commons/DECISIONS.md) — `ProviderProfilePort.save()` gains `targetUserId`; category-assignment write and audit capture move to `marketplace-orchestrator`
- [ADR-006 (taxon-spring-boot-starter)](../taxon-spring-boot-starter/DECISIONS.md) — Category and city filter is a Taxon-built SQL predicate the owner splices into its own statement
