# feature-007: Taxonomy — Generic Category/Classifier System — ✅ DONE

**Type:** feature — new domain starter, condensed from the original `taxonomy/SPEC.md` +
`DESIGN.md` + `PLAN.md` (pre-issue-file convention).
**Module:** `taxon-spring-boot-starter` (new), `platform-commons` (SPI), `marketplace-app` (UI).
**Status:** done and long-since stable — this is now a fully documented, actively-used domain
starter. **Current authoritative documentation lives in `taxon-spring-boot-starter/CLAUDE.md`** —
this entry preserves only the original problem framing and resolved design questions, not
implementation detail that's better read from the live starter's own docs.

## Problem

Advertisements needed many-to-many category tags (Electronics, Real Estate, Services, ...),
manageable at runtime by moderators/admins rather than hard-coded, with the underlying machinery
generic enough to support future classifier types (tags, statuses, condition labels) without a
new starter each time.

## Goals delivered

- Zero-to-many categories per advertisement, attached/detached from the edit form.
- A dedicated Reference Data admin view (ADMIN/MODERATOR only), full CRUD incl. soft-delete/restore.
- Mandatory per-locale (`uk`/`en`) name+description; missing-translation fallback to `en`.
- Category CRUD and assignment changes flow through the audit subsystem.
- Soft-delete preserves historical assignments; deleted entries hidden from selectors but still
  resolvable by id in audit history.
- Zero categories is a valid, intentional advertisement state — no auto-assignment, no seeded
  default entry.
- Advertisement list filter panel + card chip strip both show category data.
- Delivered as a standalone, fully decoupled starter — removing `taxon-spring-boot-starter` from
  `marketplace-app/pom.xml` must not break compilation, packaging, or startup; only taxonomy UI
  surfaces silently disappear.

## Key resolved design questions

| Question | Answer |
|---|---|
| One starter for categories only, or generic taxonomy? | Generic — one starter manages multiple taxon types via a closed `TaxonType` enum (`platform-commons`), same pattern as `Role`/`EntityType`. Only *entries* are user-creatable at runtime; adding a new *type* is a release-level change. |
| Where does the many-to-many link live? | In the starter, as a generic `taxon_assignment(entity_type, entity_id, taxon_id)` table — same decoupling shape as the attachment starter. |
| UI components — starter or marketplace-app? | marketplace-app entirely. The starter is pure domain/data, no Vaadin dependency; no `TaxonUiPort` SPI needed, UI talks to `TaxonPort` directly. |
| Default locale for fallback? | `en`, configurable via `taxon.default-locale`. |

## Non-goals (still true today)

Hierarchical (parent/child) categories, per-advertisement category ordering, free-text taxon
search, public taxonomy-browsing routes, translation workflow (drafts/approvals), bulk import.

## Related

- `taxon-spring-boot-starter/CLAUDE.md` — current, authoritative documentation of everything this
  feature built (schema, `TaxonPort`/`TaxonAuditHook` contracts, `DefaultTaxonPort` coordination
  layer, optimistic locking via `Taxon.version`).
- [feature-004](feature-004-category-ids-in-snapshot.md), [feature-002](feature-002-advertisement-snapshot-redesign.md) —
  later redesigns of how advertisement snapshots reference taxon data.
