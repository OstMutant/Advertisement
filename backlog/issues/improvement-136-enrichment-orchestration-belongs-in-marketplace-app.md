# improvement-136: Move cross-domain read-enrichment orchestration out of domain starters, into marketplace-app

**Type:** architecture / tech-debt (no live bug — a module-boundary correction found during
improvement-124 Batch 124-B's design discussion).
**Module:** `advertisement-spring-boot-starter`, `provider-profile-spring-boot-starter`,
`platform-commons` (`AdvertisementPort`/`ProviderProfilePort`), `marketplace-app`.
**Priority:** 🔴 top — ranked ahead of `improvement-135`/`improvement-124`'s remaining batches, per
explicit user request (2026-08-04). Should land before `improvement-124` Batch 124-C (the
`AccountOverlay` UI), so that UI is built against the corrected port contract from the start
instead of being built once and reworked later.
**When:** independent, no blockers. Sequence before improvement-124 Batch 124-C. Worth checking
whether it's efficient to bundle with Batch 124-B2 (already touches `AdvertisementService.java`
for the shared-sanitizer/stale-id fixes) — not decided, flag at planning time for whichever batch
starts first.

## Problem

`AdvertisementEnrichmentService` (`advertisement-spring-boot-starter`) and the just-shipped
`ProviderProfileEnrichmentService` (`provider-profile-spring-boot-starter`, improvement-124 Batch
124-B) both live **inside their own domain starter**, but each orchestrates calls across **three
other starters' ports** (`TaxonPort`, `UserPort`, `AttachmentPort` for Advertisement; `TaxonPort`,
`UserPort` for ProviderProfile) to assemble a UI-ready, `Locale`-resolved display DTO
(`AdvertisementInfoDto`/`ProviderProfileDto` with `categoryNames`/`cityName`/`actorName`/
`actorEmail`/media fields populated) before returning it through `AdvertisementPort`/
`ProviderProfilePort`.

This was raised and discussed live during Batch 124-B's post-implementation review (2026-08-04),
not invented in the abstract. Five concrete pieces of evidence, in the order they came up:

1. **Contradicts each starter's own stated boundary.** Every starter's `CLAUDE.md` "What it owns"
   section describes only its own entity/repository/service/port — none describe "assemble a
   display-ready view spanning 3-4 other domains" as part of that starter's job.
2. **Contradicts the root `CLAUDE.md`'s own assignment of this job to marketplace-app.**
   "UI is a monolith... Marketplace may import from starters only via platform-commons contracts
   (Ports/Hooks/DTOs)... [marketplace-app] stitch[es] things together" — marketplace-app is the
   module explicitly designed and permitted to combine multiple starters' data; a starter reaching
   into three sibling ports to do the same job is the same capability duplicated in the wrong
   layer.
3. **The `Locale` parameter on the port signature is a smoking gun.**
   `AdvertisementPort.getFiltered(..., Sort sort, Locale locale)` and
   `ProviderProfilePort.getFiltered(..., Sort sort, Locale locale)` — a domain port needing a
   `Locale` to do its job is itself evidence that presentation/i18n concerns leaked into the
   domain contract. A true domain contract should return domain data (raw `categoryIds`,
   `cityTaxonId`); resolving those ids into a translated display string for one specific UI
   session's language is a presentation concern.
4. **Inconsistent within the same domain, already.** `AdvertisementAuditEnrichService`
   (`marketplace-app/services/advertisement/`) already does the *same kind* of job — resolving
   `categoryIds`/`cityTaxonId` into `Locale`-aware display names via `ComponentFactory<TaxonPort>`
   — but for **audit-diff rendering**, and it correctly lives in marketplace-app (uses
   `LocaleProvider`/`I18nService`, both marketplace-app-only UI-session infrastructure). The
   list/card-rendering enrichment path does the identical kind of resolution for the identical
   fields, but lives one layer too low. No principled reason justifies the split — it's an
   accident of implementation history, not a deliberate two different mechanisms.
5. **No existing ADR decided this placement question.** Checked directly (subagent research,
   2026-08-04): `platform-commons/DECISIONS.md` ADR-034 (bulk-lookup vs. raw SQL join) and
   `marketplace-app/DECISIONS.md` ADR-068 (`AdvertisementEnrichmentService` extracted into its own
   class) both document *mechanism*, never *"should this live in the starter or marketplace-app"*.
   `platform-commons/DECISIONS.md` ADR-011 ("marketplace is the correct orchestrator") is the
   closest adjacent principle but is scoped to hook-to-hook starter coupling (audit calling
   attachment directly), not a starter self-orchestrating across multiple ports via
   `ComponentFactory`. This is architecture that evolved without an explicit recorded decision on
   the module-placement question itself.

**Consequence of leaving it as-is:** every new domain that needs cross-referenced display data
(provider-profile just did) re-implements the same starter-side orchestration pattern from
scratch, instead of there being one consistent, reusable place (marketplace-app) that already
knows how to resolve taxon/actor names for a given `Locale`. The pattern will keep spreading with
every new domain unless corrected now, before Batch 124-C adds a third occurrence
(`ProviderProfileOverlay`'s own enrichment needs) and before a public Providers catalog (Batch
124-D) locks in the current shape.

## Explicitly not a "let's split into two DTOs" issue

A related but different question was raised and **rejected** in the same discussion: whether
`AdvertisementInfoDto`/`ProviderProfileDto` should be split into a "raw repository row" DTO and a
"fully enriched" DTO, because the repository-returned instance is momentarily half-populated
before enrichment runs. That was rejected because, **today**, the half-populated state never
crosses a module boundary — `AdvertisementService.getFiltered()`/`findById()` always call all
enrichment steps before `AdvertisementPort` returns to any external caller, so no real consumer
ever observes the half-built state.

**This issue changes that fact, on purpose.** Once enrichment moves to marketplace-app, the
half-populated DTO *does* cross the port boundary (marketplace-app receives it from the port
*before* running its own enrichment step) — so the earlier "no real leak, don't bother splitting"
reasoning no longer applies once this issue ships. Whether to reuse one DTO shape end-to-end
(the port returns a partially-null instance, marketplace-app's new service fills the rest via
`toBuilder()`) or introduce a second DTO type is an open technical question for this issue's
implementation — see "Open design question" below. Do not resolve it by assumption; ground it in
which shape keeps the diff smallest and the port contract least surprising to a caller that never
calls the marketplace-app enrichment step.

## Not a slide into "the monolith swallows everything"

The user raised this concern directly and it deserves a direct answer, not a hand-wave: **does
moving orchestration into marketplace-app just recreate the same "one class knows about
everything" problem, one layer up?**

No, provided the fix keeps **one enrichment service per domain**, matching the package convention
`marketplace-app/CLAUDE.md` already documents (`services/advertisement/`, and a new
`services/providerprofile/` following the same shape) — mirroring the already-existing
`AdvertisementAuditEnrichService` precedent exactly, not a single shared "enrich anything" god
service. `marketplace-app` is already explicitly declared a monolith **by design** for UI
(`CLAUDE.md`: "UI is a monolith... within marketplace-app, UI components may freely reference each
other — no ports, no hooks, no indirection needed") — moving this orchestration there does not
introduce new coupling into the system, it relocates already-existing coupling into the one layer
the architecture already designates for holding it. The actual monolith risk to guard against
during implementation is centralizing *multiple domains'* enrichment into one shared class — avoid
by keeping domain-scoped services, one per domain, same as today's `AdvertisementAuditEnrichService`
already does for the audit-diff case.

## Suggested fix

1. **`platform-commons`:** drop the `Locale` parameter from `AdvertisementPort.getFiltered()`/
   `findById()` and `ProviderProfilePort.getFiltered()`/`findById()`/`findByActorId()`. The port's
   job becomes: return domain data (raw `categoryIds`/`cityTaxonId`/`actorId`), full stop — no
   i18n awareness in the contract at all.
2. **`advertisement-spring-boot-starter`:** delete `AdvertisementEnrichmentService`. `AdvertisementService.getFiltered()`/`findById()` stop calling it — they return whatever the
   repository gives them (already-correct raw shape, per `AdvertisementRepository`'s `ROW_MAPPER`).
3. **`provider-profile-spring-boot-starter`:** same — delete `ProviderProfileEnrichmentService`,
   `ProviderProfileService.getFiltered()`/`findById()`/`findByActorId()` stop calling it.
4. **`marketplace-app`:** new `services/advertisement/AdvertisementViewEnrichService` (name TBD at
   implementation time — must not collide with the existing `AdvertisementAuditEnrichService`) and
   new `services/providerprofile/ProviderProfileViewEnrichService`, each taking
   `ComponentFactory<TaxonPort>`/`ComponentFactory<UserPort>`/(`ComponentFactory<AttachmentPort>`
   for advertisement only) + `LocaleProvider`, mirroring `AdvertisementAuditEnrichService`'s
   existing shape (bulk resolve, single resolve, degrade gracefully when a sibling starter is
   absent). Every current call site that goes through `AdvertisementPort`/`ProviderProfilePort`
   (`AdvertisementsView`, `AdvertisementCardView`, the future `ProvidersView`/`AccountOverlay`)
   calls the new marketplace-app-side service immediately after the port call, same shape as
   today's two-line "get + enrich" pattern, just relocated.
5. **Open design question, resolve during implementation, not here:** does the enriched result
   reuse `AdvertisementInfoDto`/`ProviderProfileDto` (same `platform-commons` class, built via
   `toBuilder()` in the new marketplace-app service) or does marketplace-app need its own
   "view"/"card" DTO distinct from the port's return type? Ground the answer in real call-site
   review (how many places actually consume the enriched fields today, whether any code holds a
   reference to the *unenriched* port result and would break if the type stayed the same) rather
   than assuming either answer.
6. Repoint every real caller (grep first, don't assume): `AdvertisementsView`,
   `AdvertisementCardView`, `AdvertisementQueryConfig`/query layer, `OgMetaRequestListener`,
   `SitemapController`, and whatever Batch 124-C/D's `AccountOverlay`/`ProvidersView` end up being
   at the time this issue is picked up.

## Testing strategy

- Unit: new marketplace-app-side enrichment services get the same Mockito-based coverage
  `AdvertisementEnrichmentService`/`ProviderProfileEnrichmentService` had (bulk vs. single, graceful
  degradation when a sibling starter's `ComponentFactory` is empty).
- Integration: `AdvertisementRepositoryTest`/`ProviderProfileRepositoryTest` unaffected (repository
  layer doesn't change) — but any integration test currently asserting on a *fully enriched*
  `AdvertisementInfoDto`/`ProviderProfileDto` coming back from the port/service needs updating to
  assert the now-raw shape instead (grep `getCategoryNames()`/`getCityName()`/`getCreatedByUserName()`/
  `getActorName()` assertions against `*Port`/`*Service` return values specifically, not against the
  new marketplace-app service, which gets its own coverage).
- Playwright: full `e2e --full --ux` — this changes a read path every card/list view depends on,
  not a cosmetic change; verify advertisement cards, the Timeline audit-diff display (make sure
  `AdvertisementAuditEnrichService` — unaffected by this issue — still renders correctly alongside
  the new list-side service), and any provider-profile UI that exists at the time this ships.

## Out of scope

- `TaxonPort`'s own coordination-layer nature (`DefaultTaxonPort` is explicitly documented as "a
  coordination layer, not pure delegation" in `taxon-spring-boot-starter/CLAUDE.md`) — that's
  `TaxonPort` orchestrating *within its own domain* (translations, active-record filtering), not
  reaching into sibling starters' ports. Not the same problem, not touched here.
- Redesigning `AdvertisementFilterDto`/`ProviderProfileFilterDto` or the filter/pagination
  mechanism itself — only the enrichment/orchestration step and the `Locale` parameter are in
  scope.
- Any change to `AdvertisementAuditEnrichService` itself — it's already correctly placed; cited
  here only as precedent.

## Related

- `improvement-124` (F-04, provider-profile) — Batch 124-B just shipped
  `ProviderProfileEnrichmentService` mirroring the pattern this issue corrects; Batch 124-C
  (`AccountOverlay`) should land after this issue, not before, to avoid building UI against a port
  contract that's about to change.
- `platform-commons/DECISIONS.md` ADR-034 (bulk-lookup-over-join), ADR-035 (media summary
  enrichment), ADR-068 (`AdvertisementEnrichmentService` extraction) — document the *mechanism*
  this issue's fix reuses; none of them decided the module-placement question.
- `platform-commons/DECISIONS.md` ADR-011 / `audit-spring-boot-starter/DECISIONS.md` ADR-010 —
  closest existing precedent for "marketplace is the correct orchestrator," scoped to hook-to-hook
  coupling, not multi-port self-orchestration inside one starter.
- `marketplace-app/services/advertisement/AdvertisementAuditEnrichService.java` — the existing,
  correctly-placed precedent this issue's fix mirrors.
