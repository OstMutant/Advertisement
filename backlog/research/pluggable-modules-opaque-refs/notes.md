# Notes: pluggable-modules-opaque-refs

Purpose: findings, the comparison of variants and the differences from the project's own practice.

## Findings

- Measured (PG 15, 1M ads, 2.58M assignments, files in measurements/): per list page variant A costs 163 ms at 6.3k ids, 556 ms at 31k, 1240 ms at 242k; variant B 45 / 211 / 512 ms. B loses on a rare category (A 168 ms vs B 329 ms: backward scan on created_at). COUNT dominates both variants above ~30k ids.
- A's extra cost is (1) the taxon query run twice per page (getFiltered and count each resolve), (2) planning time of a large bound array (138-161 ms at 242k), (3) moving ids to Java (psql client fetch of 242k ids 300-400 ms; JDBC not measured). A (taxon_id, entity_type, entity_id) index halves the taxon query (43->22 ms at 31k, ~12->2.8 ms rare).
- Pluggability at the database level already holds: zero cross-starter FK, view or function in any changelog; each starter runs its own SpringLiquibase. At the bean level it likely does not: ComponentFactory<XPort> beans are declared only by the starter that provides XPort (except TaxonPort), while taxon/user starters inject ComponentFactory<AuditPort> and feedback injects ComponentFactory<UserPort>. Not run; RepositoryTestSupport's own empty factories show the gap.

## Variants and comparison

- A current (Taxon owns taxon_assignment, ids through Port); A+ (A plus one Taxon-local index and one resolution per page); B (advertisement_taxon / provider_profile_taxon owned by the entity module); C-rejected: Taxon-supplied SQL fragment or cross-starter EXISTS (hidden join, forbidden by the marketplace-app no-cross-starter-join decision).

## Differences from the project

- None in the direction of the hypothesis: the project already uses opaque ids, module-owned changelogs and no cross-module FK, matching Spring Modulith and DDD reference-by-identity. The hypothesis' 'module owns its relations' (B) conflicts with taxon ADR-001, commons ADR-031/032 and app ADR-079; it is presented as a decision with a measured trigger, not adopted.
