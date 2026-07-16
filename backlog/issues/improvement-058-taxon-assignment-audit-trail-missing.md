# improvement-058: Category/taxon assignment changes are not recorded to the audit log

**Type:** bug fix — a stated architectural goal (ADR-019: "must be audited") was never actually
implemented, found during a full `/sync-docs --full-audit` pass, 2026-07-16.
**Module:** `taxon-spring-boot-starter` (`TaxonAssignmentService`), `platform-commons`
(`TaxonAuditHook` SPI), `marketplace-app` (missing implementation).
**Priority:** medium — no data-loss risk, but a real, silent gap in the audit trail for a
user-facing action (assigning/removing categories on an advertisement) that the domain's own
founding ADR explicitly required.
**When:** independent, no blockers.

## Problem

`taxon-spring-boot-starter`'s `TaxonAssignmentService.assign()`/`.unassign()`
(`TaxonAssignmentService.java`) fire `TaxonAuditHook.onAssignmentChanged(...)` via
`auditHook.ifAvailable(...)` — correct, graceful, optional-SPI wiring on the starter side. The
problem is on the marketplace-app side: **no implementation of `TaxonAuditHook` exists anywhere in
the codebase.** `services/audit/taxon/` (marketplace-app) is an empty package. `TaxonAuditHookImpl`
and `TaxonActivityService`, both referenced by `marketplace-app/DECISIONS.md` ADR-019 ("Taxon
audited via `TaxonAuditHookImpl` → delegates to `TaxonActivityService`") and by
`taxon-spring-boot-starter/CLAUDE.md` ("`TaxonAuditHookImpl` in marketplace-app records it to the
audit log via `TaxonActivityService`"), were apparently planned but never written.

**Concrete consequence:** when a moderator/admin adds or removes a category on an advertisement,
that change does not appear anywhere in the advertisement's activity/timeline feed — contrary to
ADR-019's own stated goal ("must be audited... category assignments recorded in audit_log") and
contrary to what both `marketplace-app/DECISIONS.md` and `taxon-spring-boot-starter/CLAUDE.md`
currently document as already working. Advertisement title/description/media changes ARE captured
correctly (via `AdvertisementSnapshotDto.categoryIds`, part of the main save-flow snapshot per
`feature-002`/ADR — see Related) — but that only captures categories as a *side effect* of an
advertisement save; a taxon-side audit trail of category assignment history specifically
(independent of any single advertisement save) does not exist.

**Note on scope overlap:** this is *not* the same gap as improvement-002 (snapshot schema
versioning, deferred until the first new snapshot-bearing domain) — that's about audit_log schema
evolution generally. This is specifically about the missing `TaxonAuditHook` implementation.

## Suggested fix

1. Decide whether a dedicated taxon-assignment audit trail is still wanted at all, given
   `AdvertisementSnapshotDto.categoryIds` already captures category state as part of every
   advertisement save/restore cycle (i.e. confirm what additional value a separate
   `TaxonAuditHook`-driven trail would add before implementing it — don't build it just because an
   old ADR said to).
2. If still wanted: implement `TaxonAuditHookImpl` (marketplace-app,
   `org.ost.marketplace.services.audit.taxon`) delegating to a small service (or directly to
   `AuditPort`) that records assignment changes, following the same thin-delegation pattern
   `*HookImpl` classes use elsewhere (see `platform-commons/CLAUDE.md`'s "Hook and Port
   Implementation Rules" — pure delegation, no business logic in the impl itself).
3. If not wanted: update `marketplace-app/DECISIONS.md` ADR-019 and
   `taxon-spring-boot-starter/CLAUDE.md` to explicitly state this is a deliberately unimplemented
   optional SPI (same documented shape as `AttachmentMediaChangeHook`'s current "fires, currently
   has no implementation, valid gracefully-degraded state" — see ADR-035), closing this issue
   without writing new code.

## Related

- `marketplace-app/DECISIONS.md` ADR-019 — the original decision this issue's problem was found
  while correcting (2026-07-16 full-audit pass).
- [feature-002](../completed/issues/feature-002-advertisement-snapshot-redesign.md) —
  `AdvertisementSnapshotDto.categoryIds` already captures category state per advertisement save,
  the "note on scope overlap" above.
- `platform-commons/DECISIONS.md` — Hook and Port Implementation Rules (pure-delegation pattern
  any new `TaxonAuditHookImpl` should follow).
