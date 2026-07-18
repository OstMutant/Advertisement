# Architecture & Technical Decisions — marketplace-app

---

## ADR-001: All Vaadin UI consolidated in marketplace-app
**Status:** Accepted

**Context:** Two phases — (1) UI extracted from individual starters into a short-lived
`marketplace-ui` module; (2) `marketplace-ui` merged back into `marketplace-app` (no second
consumer existed, Maven module boundary added cost with no benefit) and domain logic moved to
dedicated starters.

**Decision:** All Vaadin UI lives in `marketplace-app` (`org.ost.marketplace.ui.*`). No starter
contains UI code. `marketplace-app` imports starters only via platform-commons contracts
(`UserPort`, `AdvertisementPort`, `AuditPort`, `AttachmentPort`, `UserDto`, etc.).
`*HookImpl` and `*PortImpl` orchestrators live in `marketplace-app`.

**Consequences:** CSS rule: all CSS lives in `marketplace-app/src/main/frontend/themes/my-app/`
— Vaadin 25 Vite build does not include CSS from `@CssImport` in JAR starters.

---

## ADR-002: No JPA / no Hibernate — JdbcClient + inline SQL
**Status:** Accepted

**Context:** JPA/Hibernate introduces hidden lazy-loading bugs, N+1 queries, and magic that
conflicts with the "explicit over implicit" architecture principle.

**Decision:** All database access via `JdbcClient` with SQL inlined directly in repository methods.
`CrudRepository<T, Long>` only for trivial `save`/`findById`/`deleteById` — never for custom queries.

**Consequences:** Rejected: Spring Data JPA — too much hidden magic.

---

## ADR-003: Inline SQL repository style — no descriptor layer
**Status:** Accepted

**Context:** A prior `SqlEntityDescriptor` / `SqlCommand` descriptor layer required jumping to
constants classes to read any query.

**Decision:** SQL inline per method — text block `"""..."""` for multi-line, single-line string
otherwise. No `TABLE`, `ALIAS`, `SOURCE`, or single-use SQL string constants.
- `@SuppressWarnings("java:S1192")` on every repository class.
- `MapSqlParameterSource` constructed inline per method.
- Dynamic SQL assembled with `.formatted()`, never `+` concatenation.
- `SqlFilterBuilder.build(params, filter, prefix)` — prefix is part of the call.
- `OrderByBuilder.build(sort, map)` returns `" ORDER BY ..."` with leading space.

**Consequences:** The Descriptor layer (`SqlEntityDescriptor`, `SqlCommand`, `SqlDescriptorField`,
`SqlEntityProjection`, `SqlFixedQuery`, `RepositoryCustom`) fully removed from the codebase.
When adding a new repository method, write the full SQL inline. Never extract a SQL fragment to
a constant unless shared across 3+ methods and genuinely non-trivial.

---

## ADR-004: Dependency versions
**Status:** Accepted

**Context:** Spring Boot 4.0.6 is the latest stable patch; Vaadin 25.1.5 aligns with the
Spring Boot 4.x BOM; AWS SDK bumped from 2.25.60 for security patches and API improvements.

**Decision:** Spring Boot 4.0.6, Vaadin 25.1.5, AWS S3 SDK 2.44.4.

**Consequences:** Rejected: Jackson 3 migration (`tools.jackson:jackson-databind:3.1.2`) —
Maven artifacts for the `tools.jackson` groupId are unverified. Revisit when official release
is confirmed.

---

## ADR-005: Modular storage — contract in attachment-starter, not marketplace
**Status:** Accepted

**Context:** Storage only exists to serve attachments — no use case for storage without attachments.

**Decision:** `StorageService` interface and implementations live in `attachment-spring-boot-starter`
(`org.ost.attachment.storage`). UI components use `ObjectProvider.ifAvailable()` to degrade
gracefully when the attachment starter is absent.

**Consequences:** Rejected: keeping `storage-s3-spring-boot-starter` as a separate module —
two modules with a mandatory one-way dependency and no realistic decoupling scenario.

---

## ADR-006: Audit subsystem in audit-spring-boot-starter; domain calls via AuditPort
**Status:** Accepted

**Context:** Audit is infrastructure, not domain. Domain services calling audit internals directly
would couple business logic to audit infrastructure.

**Decision:** The full audit subsystem (write + read sides) lives in `audit-spring-boot-starter`.
All Vaadin audit UI lives in `marketplace-app`. Domain services call `AuditPort` (contract interface).
The starter contains zero advertisement-specific knowledge — all domain coupling expressed through
SPIs (`AuditDomainHook`, `AuditActivityFieldsHook`, `AuditActivityEnrichHook`) implemented in
`marketplace-app`.

**Consequences:** Rejected: conditional Spring annotations in `platform-commons` — contracts must
be Spring-free pure Java.

---

## ADR-007: @EnableMethodSecurity active; @PreAuthorize not at class level on services
**Status:** Accepted

**Context:** Vaadin view beans are initialized on the first HTTP request — before the user
authenticates. Class-level `@PreAuthorize` on services breaks this initialization with
`AuthorizationDeniedException`.

**Decision:** `@EnableMethodSecurity` added to `SecurityConfig`. `@PreAuthorize("isAuthenticated()")`
NOT applied at class level on services. `/health` endpoint is intentionally public.
Future non-public REST endpoints use `@PreAuthorize` at method level on the controller.

**Consequences:** Rejected: class-level `@PreAuthorize` on `AdvertisementService`,
`AuditReadService`, `UserSettingsService` — confirmed broken via smoke tests.

---

## ADR-008: Double-click guard on save buttons via setEnabled
**Status:** Accepted, mechanism superseded by ADR-020 (2026-07-01, corrected 2026-07-16)

**Context:** Rapid double-click submits the form twice, causing duplicate saves.

**Decision:** Save buttons are disabled on click to prevent a double submit.

**Consequences (updated 2026-07-16 — verified against current code, original text was stale):**
The guard no longer lives in each handler's `activate()` with a `finally` block re-enable, as
originally described here. It now lives in shared
`AbstractFormOverlayModeHandler.wireSaveGuard()` (`saveBtn.setEnabled(false)` on click, no
`finally` block anywhere in this flow) — re-enabling happens instead via `afterSave(boolean)` →
`updateButtons()` in each concrete handler (e.g. `AdvertisementFormOverlayModeHandler
.afterSave()`), called unconditionally from every branch of `AbstractEntityOverlay.handleSave()`.
This is ADR-020's later, more general lifecycle mechanism — this entry should have been marked
Superseded when ADR-020 landed and wasn't; corrected here instead of duplicating ADR-020's fuller
description.

---

## ADR-009: ValidRange.List without @Constraint; ValidRangeValidator caches field lookups
**Status:** Accepted

**Context:** Jakarta Bean Validation 3.0 spec says container annotations should carry
`@Constraint(validatedBy = {})`, but Hibernate Validator (Spring Boot 4.0.6) does NOT support
this for class-level (`ElementType.TYPE`) constraints — adding it causes `HV000030: No validator
could be found` at runtime.

**Decision:** The inner `@List` annotation on `@ValidRange` intentionally omits
`@Constraint(validatedBy = {})`. `ValidRangeValidator` caches reflected fields in a static
`ConcurrentHashMap` keyed by `FieldKey(class, start, end)`.

**Consequences:** Rejected: adding `@Constraint(validatedBy = {})` to `ValidRange.List` —
confirmed broken at runtime via smoke tests.

---

## ADR-010: Restore semantics — restore TO snapshot, not BEFORE snapshot
**Status:** Accepted

**Context:** The previous behavior restored to the state *before* a change, not *to* that
snapshot — inverting the UX expectation.

**Decision:** Clicking a restore button restores the entity to the state captured in the clicked
entry's snapshot (`getSnapshotContent`). `getPreviousSnapshotContent` was reserved for diff
display only, and has since been removed entirely (see `audit-spring-boot-starter/DECISIONS.md`
ADR-008's amendment) — diff display now works directly from snapshot pairs.

**Consequences (corrected 2026-07-13 — method names had drifted):** all restore flows call
`AuditPort.getSnapshotContent(snapshotId, entityType)`. Current entry points:
`AdvertisementFormOverlayModeHandler.handleRestoreFromActivity`,
`UserFormOverlayModeHandler`'s equivalent, `SettingsFormModeHandler.handleRestoreFromActivity`,
and `TaxonFormOverlayModeHandler`'s own restore flow (added when taxon-spring-boot-starter
landed, not present when this ADR was originally written). The originally-cited method names
(`UserOverlay.handleRestoreUser`, `AdvertisementService.restoreToSnapshot`) do not exist — only
`UserService.restoreToSnapshot` (user-spring-boot-starter) matches that exact name today.

---

## ADR-011: OverlayFormBinder used directly — no AuditUiPort
**Status:** Accepted

**Context:** `AuditUiPort` was removed 2026-06-15 as unnecessary indirection — all Vaadin UI
lives in marketplace-app, so there is no second consumer that would require the SPI.

**Decision:** `OverlayFormBinder` (corrected 2026-07-13 — named `AuditSnapshotBinder` originally,
which no longer exists anywhere in the codebase; `OverlayFormBinder` is the class performing this
role today) used directly in marketplace-app UI components.

**Consequences:** Do not re-introduce `AuditUiPort`.

---

## ADR-012: No shared UI module — plain-class pattern for future sharing
**Status:** Accepted

**Context:** No actual cross-module UI duplication exists as of 2026-05-26. `PaginationBar` and
`EmptyStateView` exist only in `marketplace-app`. Multiplying modules has real cost.

**Decision:** No new UI module (`advertisement-ui-core` or similar). If cross-module sharing ever
becomes real, move the component to `platform-commons` as a plain class (no `@SpringComponent`,
no `@Scope`). Each consuming module declares its own `@Bean @Scope("prototype")` in a local
`@Configuration`.

**Consequences:** Rejected: separate `advertisement-ui-core` module.
Rejected: making `platform-commons` a starter — commons has no Vaadin dependency.
Prerequisite for any component moved to commons: remove all marketplace-specific imports
(`I18nKey`, `I18nParams`, `PaginationDefaults`) — pass as constructor parameters instead.

---

## ADR-013: AbstractViewOverlayModeHandler — template method for tabbed view overlays
**Status:** Accepted

**Context:** `AdvertisementViewOverlayModeHandler` and `UserViewOverlayModeHandler` had identical
tab-switching and lazy-loading boilerplate (~15 lines each).

**Decision:** All "view mode" overlay handlers extend `AbstractViewOverlayModeHandler`. The base
class provides `final activate(OverlayLayout)` that assembles the tab layout. Only two methods
are truly `abstract` (corrected 2026-07-13 — not "five abstract methods" as originally written):
`buildPrimaryContent()` and `buildHeaderActions()`. `tabsCssClass()` (default `""`),
`buildSecondaryTab()`, and `buildTertiaryTab()` (both default `null`) have default bodies and are
overridden only when needed.

`SecondaryTabDef` record `(Tab tab, String cssClass, Supplier<Component> loader)` represents the
optional second tab. Returning `null` from `buildSecondaryTab()` produces a single-tab layout.

**Addition (2026-07-13, previously undocumented):** a `TertiaryTabDef` record of the same shape
plus a `buildTertiaryTab()` hook were added since this ADR was written, supporting a third tab —
used by `TaxonFormOverlayModeHandler`/`ReferenceDataView`'s sub-tabs (taxon-spring-boot-starter
landed after this ADR). Same override-for-null-default pattern as `SecondaryTabDef`.

**Consequences:** Rejected: `TabbedOverlayContent` as a Spring `@Prototype` `Configurable` bean
— passing live UI components as `Parameters` violates the convention that Parameters carry
data/config, not pre-built component trees.

---

## ADR-014: Audit writes belong in the service layer, not the UI
**Status:** Accepted

**Context:** `SettingsOverlay.handleSave()` was calling `auditPortFactory.captureUpdate(...)`
directly — the only place in the entire UI layer that fired an audit write. All other entities
correctly placed audit calls inside the service.

**Decision:** `AuditPort.captureUpdate` / `captureCreation` / `captureDeletion` must only be
called from `*Service` classes — never from UI overlays, view components, or `*HookImpl` classes.
`UserSettingsService.save()` now loads old settings, saves, publishes the domain event, and
captures the audit entry.

**Consequences:** When a save/update/delete operation needs to be audited, put the `AuditPort`
call at the end of the corresponding `*Service.save()` / `*Service.delete()` method. The UI
layer calls the service; the service handles persistence + audit atomically.

---

## ADR-015: *HookImpl — no snapshot inspection, pure delegation
**Status:** Accepted

**Context:** `AuditDomainHookImpl.resolveDisplayName()` was directly accessing `ad.title()` and
`u.name()` from snapshot DTOs and applying a null guard — business logic embedded in a hook.

**Decision:** `*HookImpl` methods must not access fields of snapshot DTOs or apply any
formatting/resolution logic. Each switch branch must contain exactly one service call.
Entity-type routing (a `switch` over `EntityType`) is the only permitted logic in a `*HookImpl`.

**Consequences:** If a `*HookImpl` method needs entity-specific data, add a method to the
corresponding `*Service` and delegate.

---

## ADR-016: Full user decoupling — marketplace no longer imports org.ost.user.* internals
**Status:** Accepted

**Context:** marketplace-app previously imported `User` entity, `UserService`, `UserSettingsService`,
and `UserPrincipal` directly — violating the module import rule.

**Decision:**
- `AuthenticatedPrincipal` SPI added to `platform-commons` (`org.ost.platform.user.spi`).
- `UserPrincipal` (user-starter) implements `AuthenticatedPrincipal.toUserDto()`.
- `AuthContextService` rewritten: reads `AuthenticatedPrincipal` via pattern matching; returns `Optional<UserDto>`.
- `UserPort` gains `save(UserProfileDto, Long actingUserId)` and `refreshCurrentUserInContext(Long userId)`.
- All 22 files now use `UserDto`/`UserPort` from platform-commons exclusively.

Key design — `refreshCurrentUserInContext` in `UserPort`: updating the Spring Security principal
requires `UserPrincipal(User)` — which needs the password hash (not in `UserDto`). Moving this
responsibility into `UserPortImpl` avoids exposing the `User` entity or password hash across the
module boundary.

**Consequences:** `UserSortMeta` and `UserQueryConfig`: `User.Fields.*` replaced with string
literals (field names are stable). `UserMapper` maps `UserDto → UserEditDto`.

---

## ADR-017: Decoupling debt at the time of writing — all items since resolved
**Status:** Resolved (was undated/open at write time; every item below closed by 2026-06-26 —
verified 2026-07-13: `org.ost.attachment.*`/`org.ost.user.*` internal imports in marketplace-app
both return zero grep hits. This ADR previously had no `Status:` line at all despite every listed
item already being marked resolved inline — the one structural inconsistency found across this
file's 33 ADRs.)

**Architecture rule (2026-06-15):** marketplace-app UI is a monolith — decoupling is required
only at the service ↔ UI boundary (starters vs marketplace-app). Within marketplace-app, UI
components may reference each other freely. UI ports/hooks (`AuditUiPort`, `AttachmentGalleryPort`,
`AuditActivityRowHook`) were removed as unnecessary indirection.

### ✅ Resolved — attachment UI boundary violations (2026-06-26)

`MediaContentTypeUtil` merged into `AttachmentMediaContentType` enum (platform-commons). All attachment UI components now import only from `platform-commons`. No `org.ost.attachment.*` imports remain in marketplace-app.

→ [improvement-001-attachment-ui-boundary-violation](../backlog/completed/issues/improvement-001-attachment-ui-boundary-violation.md) (completed)

→ [improvement-004-accessevaluator-boundary-violation](../backlog/completed/issues/improvement-004-accessevaluator-boundary-violation.md) (completed)

### ✅ Resolved — marketplace-app → org.ost.user.* internals (2026-06-15)

All 22 files now use `UserDto`/`UserPort` from platform-commons exclusively. See ADR-016.

### ✅ Resolved — org.ost.marketplace.security.* uses User entity (2026-06-15)

`OwnershipChecker`, `RoleChecker`, `AccessEvaluator` updated. `AuthContextService.getCurrentUser()`
returns `Optional<UserDto>`. `AuthenticatedPrincipal` SPI is the boundary.

---

## ADR-018: Top-level Timeline tab
**Status:** Accepted (done 2026-06-23)

**Context:** Per-overlay timeline queried by `actor_id` only. A top-level tab with proper filters
gives full audit context without navigating into individual overlays.

**Decision:** Added a dedicated **Timeline** navigation tab (alongside Listings and Users) with
filter, sort, and pagination. Inline timeline tabs removed from `UserOverlay` and `SettingsOverlay`.

Components: `TimelineView` (`@UIScope`), `TimelineQueryBlock`, `AuditTimelineListRenderer`,
`AuditTimelineRowRenderer`, `PaginationBar`. Visibility gated by `access.canView()`.
USER role: only own activity (actor filter forced server-side by `AccessEvaluator`).

**Consequences:** Key lesson: `TimelineView` must override `setVisible(boolean)` to call
`refreshFeed()` — tab switching uses `setVisible()`, not component detach/attach, so
`@PostConstruct` alone produces stale data after mutations.

---

---

## ADR-019: Taxon/Category domain extracted as standalone starter
**Status:** Accepted (done 2026-06-26)

**Context:** Advertisement categories (and future tags) are a classification taxonomy that:
- spans multiple entity types (`ADVERTISEMENT`, potentially `USER`, etc.)
- requires soft-delete + restore (same as advertisement/user)
- needs multilingual translations (locale-keyed)
- must be audited (category assignments recorded in audit_log)

Embedding this inside `advertisement-spring-boot-starter` would couple two distinct domains and
prevent reuse across other entity types.

**Decision:** New `taxon-spring-boot-starter` module owns:
- `taxon`, `taxon_translation`, `taxon_assignment` tables (Liquibase changelog at `db/taxon-changelog/`)
- `Taxon`, `TaxonTranslation`, `TaxonAssignment` entities
- `TaxonService`, `TaxonAssignmentService` — business logic
- `DefaultTaxonPort` — coordination layer (not pure delegation: it resolves translations and builds DTOs)
- `TaxonProperties` — configurable `defaultLocale` for translation fallback

SPI contracts in `platform-commons`:
- `TaxonPort` (marketplace → starter) — CRUD, assignment management, batched queries

Marketplace-app additions:
- `TaxonManagementView` + `TaxonOverlay` + `TaxonFormOverlayModeHandler` + `TaxonViewOverlayModeHandler`
- `ReferenceDataView` — tab container for taxon management (nested sub-tabs)
- **Resolved (2026-07-17, improvement-058):** this ADR originally required a dedicated
  `TaxonAuditHook` → `TaxonAuditHookImpl` → `TaxonActivityService` chain recording category
  assignment changes to the audit log independently. That chain was never built (`TaxonAuditHook`
  had zero implementations; `TaxonActivityService` never existed). Investigation found the
  underlying need was already met differently: every taxon assignment change in this codebase
  happens exclusively inside an advertisement save/delete (`AdvertisementSaveService`/
  `AdvertisementService`, both via `TaxonPort.replaceAssignments()`), which already produces its
  own audit snapshot capturing the before/after category set
  (`AdvertisementSnapshotDto.categoryIds`). The actual, narrower bug was that the Timeline tab
  rendered raw taxon ids instead of resolved names in that diff (the Activity tab already resolved
  them) — fixed in `AdvertisementEnrichService` (see below). `TaxonAuditHook` was removed entirely
  rather than implemented, along with the unused `TaxonPort.assign()`/`unassign()`/`findByCode()`
  methods (zero callers). See
  [improvement-058](../backlog/issues/improvement-058-taxon-assignment-audit-trail-missing.md)
  (not yet closed — pending Playwright verification).

`TaxonType` enum (in `platform-commons`) — closed set: currently `CATEGORY`. Adding a new type
is a release-level change requiring UI, audit translations, and seed entries.

Advertisement filtering by category: `AdvertisementRepository` calls
`TaxonPort.findEntityIdsWithAnyTaxon()` to translate taxon ids into entity ids — no direct
join to `taxon_assignment` table from advertisement code.

**Consequences:**
- `EntityType.TAXON` added to `platform-commons` for audit records of taxon entity changes.
- `ReferenceDataView` added as a new top-level navigation tab with sub-tabs per taxon type.
- `DefaultTaxonPort` is permitted to contain coordination logic (translation fallback chain,
  DTO assembly) because the alternative would require exposing TaxonTranslation internals
  to marketplace-app — that would be a worse boundary violation.

---

---

## ADR-020: AbstractEntityOverlay<H> — currentFormHandler and save/cancel lifecycle in base class
**Status:** Accepted (done 2026-07-01)

**Context:** Concrete overlay classes (`AdvertisementOverlay`, `UserOverlay`, `TaxonOverlay`,
`SettingsOverlay`) each duplicated the same `currentFormHandler` field, `handleSave()` try/catch
block, `doCancel()` dispatch, and `hasUnsavedChanges()` check. Any fix in one overlay had to be
applied to all others.

**Decision:** `AbstractEntityOverlay<H extends AbstractFormOverlayModeHandler<?>>` now owns:
- `protected H currentFormHandler` — typed field for the active form handler
- `protected final handleSave()` — shared save lifecycle (try/catch, `afterSave(bool)`, `proceed()`)
- `protected final doCancel()` — calls `currentFormHandler.discardChanges()`, then `afterDiscard()`
- `protected final hasUnsavedChanges()` — null-safe check via `currentFormHandler.hasChanges()`

Concrete overlays implement abstract hooks: `saveConfig()`, `proceed()`, `afterDiscard()`.

**Consequences:**
- `switchTo()` must reset `currentFormHandler = null` before the switch expression — without this,
  after `VIEW → EDIT → VIEW` the handler remains non-null and `hasUnsavedChanges()` returns `true`.
- `SaveConfig` record `(I18nKey success, I18nKey validFailed, I18nKey saveError, I18nKey conflict)`
  declares error key mapping per overlay type — `conflict` added by ADR-029 (optimistic-locking
  UI) after this ADR was written; noted here 2026-07-13 since this ADR's body was never amended.

---

## ADR-021: QuillEditor — custom Vaadin web component for rich-text editing
**Status:** Accepted (done 2026-07-01)

**Context:** Advertisement descriptions require rich-text formatting (bold, italic, links, paragraph
blocks). Vaadin's built-in `TextArea` is plain-text only; embedding Quill as a web component
gives native Vaadin field integration with value binding.

**Decision:** `QuillEditor` extends `AbstractSinglePropertyField<QuillEditor, String>`, registered
via `@Tag("quill-editor")`, `@NpmPackage(value="quill", version="2.0.3")`, and `@JsModule("./quill-editor.js")`.
Implements `HasSize` and `HasLabel` for standard Vaadin field behavior. The custom element handles
Quill initialization and bidirectional value sync.

**Consequences:**
- `quill-editor.js` lives in `marketplace-app/src/main/frontend/` (Vaadin Vite entry).
- HTML output is sanitized server-side in `AdvertisementService` using OWASP HTML Sanitizer
  (`Sanitizers.FORMATTING.and(LINKS).and(BLOCKS)`) — server never trusts raw HTML from the client.
- Used in `AdvertisementFormOverlayModeHandler` and `TaxonFormOverlayModeHandler` for description fields.

**Update (2026-07-04) — false-dirty-state bug and its proper fix:** editing an advertisement
whose description contained rich HTML made Save/Discard appear active immediately on opening
the form, with no actual edit. Root cause: `quill-editor.js` loaded/restored content via direct
`this.__quill.root.innerHTML = ...` assignment, bypassing Quill's API entirely. Quill's own
MutationObserver-based change detection treats any DOM mutation it didn't originate through its
own API as an external, `'user'`-sourced change — so loading a value re-triggered Quill's HTML
serializer (`getSemanticHTML()`), which normalizes rich HTML (quotes/attributes/tag structure)
differently byte-for-byte from what was stored, and dispatched a spurious `value-changed` event
that Vaadin's `Binder` picked up as a real edit (`Binder` has no `fromClient`-aware guard against
this — confirmed against decompiled `flow-data` bytecode).

An earlier, undocumented attempt to fix this (`awaitingNormalization` flag + `setPresentationValue`/
`setModelValue` overrides in `QuillEditor.java`, added 2026-07-03 in commit `94647d9e` under an
unrelated "docs sync" commit message) did not work, because it assumed `Binder` inspects
`fromClient` on the changed binding — it doesn't.

**Proper fix:** use Quill's own native `source` parameter instead of custom bookkeeping. All
programmatic content loads (`connectedCallback()` initial load, `set value()`) now go through
`quill.setContents(delta, 'silent')` — Quill's `'silent'` source never fires `text-change` at
all, so there is no echo to filter. The `text-change` listener needs no source check: since our
own writes never reach it, anything that does fire there is a genuine change (real typing,
toolbar clicks, or an external API call like `quill.setContents(delta)` without an explicit
source — which Quill tags `'api'`, not `'user'`; an earlier draft of this fix filtered on
`source === 'user'` and broke Playwright's rich-text test helper, which calls `setContents`
directly without a source argument). The dead `awaitingNormalization` mechanism in
`QuillEditor.java` was removed as part of this fix — see the corresponding commit for both files.
Confirmed this is also how Vaadin's own (commercial, CVALv3-licensed) `RichTextEditor` add-on
solves the identical problem internally (its Quill-wrapping mixin uses the same `source`
tagging), validating the approach against a production-proven precedent without needing the
paid component itself.

---

## ADR-022: isCurrentState — criteria for "Current state" badge vs Restore button
**Status:** Accepted

**Context:** The audit activity panel shows a "Current state" badge on historical versions that
match the current entity state, and a "Restore" button on versions that differ. The criteria for
equality must cover all user-visible fields so that the badge only appears when restoring would
have no effect.

**Decision:** A historical snapshot is considered "current state" when its full
`AdvertisementSnapshotDto` record equals the current one via `Objects.equals` — covering `title`,
`description`, `categoryIds`, and (since improvement's advertisement-snapshot-redesign,
corrected here 2026-07-16) `attachmentSnapshotId`, a soft reference to the attachment gallery's
current snapshot id. Media is no longer compared via a separate hook call — it's folded into the
same single record-equality check as every other field, because `attachmentSnapshotId` lives
directly on `AdvertisementSnapshotDto`. `AuditActivityRowRenderer` computes the badge with exactly
one line: `Objects.equals(h.snapshotData(), cfg.currentSnapshot())`.

If any of these differ, the "Restore" button is shown. If all are identical, the badge is shown.

`AdvertisementSnapshotDto` stores `categoryIds` as a `List<Long>` of numeric category ids, sorted
at construction time (corrected 2026-07-13 — originally written as "a sorted, comma-joined string
of category names"; verified directly in `AdvertisementSnapshotDto.java`: the field is
`List<Long> categoryIds`, compared and diffed via `Objects.equals`/`FieldChange`, never a
name-based string — the diff display formats ids to a comma-separated string only for the UI
diff view, the stored/compared value itself is numeric ids). `Objects.equals` on the full record
covers title + description + categoryIds + attachmentSnapshotId in one comparison (see the
corrected Decision above — `AuditActivityEnrichHook` has no `matchesCurrent`/`mediaMatchCurrent`
method; that mechanism was removed by advertisement-snapshot-redesign, not just renamed).

Update (2026-07-03, snapshot-cleanup): `CategoryChangeSnapshotDto` and the
`AuditableSnapshot.isVisible()` mechanism were removed entirely — after
advertisement-snapshot-redesign no snapshot type ever returned `false`, making the visibility
filter in `AuditActivityPanel` a no-op. Category change information rides in the main
`AdvertisementSnapshotDto` diff; no row-hiding machinery exists anymore
(see `backlog/completed/issues/feature-006-snapshot-cleanup.md`).

---

## ADR-023: TransactionTemplate orchestrator — preferred pattern for multi-step backend operations
**Status:** Accepted

**Context:** Some save flows require calling multiple ports in a specific order within one
transaction. The canonical example: `advertisementPort.save()` → `captureSnapshot` →
`taxonPort.replaceAssignments()`. With `@Transactional` on service methods, the order is
controlled by implementation details buried inside each starter's service — invisible to the
caller. Self-invocation tricks (inner `@Component`, self-injection) work but obscure intent.

**Decision:** Use `TransactionTemplate` in a dedicated `*SaveService` (or `*Orchestrator`) class
in `marketplace-app` whenever a save flow requires:
- calling two or more ports in a controlled order, AND/OR
- mixing non-transactional work (S3, external calls) with transactional DB writes

Pattern:
```java
@Service
@RequiredArgsConstructor
class FooSaveService {
    private final TransactionTemplate tx;

    public Long save(FooSaveDto dto, Long actorId,
                     Runnable preCommitExternal,  // S3, external — outside TX
                     Runnable captureSnapshot) {  // DB only — inside TX
        preCommitExternal.run();                  // outside transaction

        return tx.execute(status -> {
            Long savedId = fooPort.get().save(dto, actorId);
            captureSnapshot.run();                // before side-effects that shift audit window
            barPort.ifAvailable(p -> p.doSideEffect(savedId, dto));
            return savedId;
        });
    }
}
```

`TransactionTemplate` is auto-configured by Spring Boot from `PlatformTransactionManager` —
inject directly, no `@Bean` declaration needed. Inner port `@Transactional` methods join the
existing TX via default `REQUIRED` propagation.

**Consequences:**
- Transaction boundary is explicit and visible in code — not hidden behind `@Transactional` on a
  deeply nested service method.
- Non-transactional work (S3) is clearly separated from DB work by position in the method.
- Order of operations is enforced structurally, not by convention.
- Rejected: inner `@Component` static class — workaround that obscures why the extra class exists.
- Rejected: self-injection (`@Lazy @Autowired FooService self`) — same workaround category.
- Rejected: two separate `@Transactional` service classes — atomicity not guaranteed across calls.

---

## ADR-024: Jsoup-based, defense-in-depth description length validation
**Status:** Accepted (done 2026-07-04)

**Context:** `AdvertisementSaveDto.DESCRIPTION_MAX_LENGTH = 2000` existed as a constant but was
enforced only by a client-reachable, regex-based binder validator
(`html.replaceAll("<[^>]+>", "")` + length check) that tag-spam could bypass — formatting tags
like `<b></b>` survive the OWASP sanitizer (it preserves allowed tags), so thousands of empty
ones pass the stripped-text check while still bloating the stored HTML
(`backlog/completed/issues/issue-description-length-tag-spam.md`). No server-side length
guard existed at all — a direct port call bypassing the UI could persist an unbounded
description.

**Decision:** three-layer validation, none of them removable without reopening a gap:
1. **Raw-size cap** — `@Size(max = DESCRIPTION_RAW_MAX_LENGTH = 20_000)` on
   `AdvertisementSaveDto.description` (`platform-commons`) — plain Bean Validation, no new
   dependency, bounds worst-case payload size before any parsing.
2. **UI content check** — `AdvertisementFormOverlayModeHandler`'s binder validator replaced
   the regex with `Jsoup.parse(html).text().length() <= DESCRIPTION_MAX_LENGTH`.
3. **Service-level guard** — `AdvertisementService.sanitizeHtml()` calls a new
   `validateDescriptionLength()` using the same Jsoup check on the *sanitized* HTML, throwing
   `IllegalArgumentException` on overflow. No new exception-handling plumbing was needed:
   `AbstractEntityOverlay.handleSave()` already has a generic `catch (Exception e)` that shows
   `e.getMessage()` via `NotificationService`.

`org.jsoup:jsoup:${jsoup.version}` (`1.22.1`, new root-pom property, same pattern as
`aws-s3-sdk.version`) was added directly to `marketplace-app` and
`advertisement-spring-boot-starter` — **not** to `platform-commons`, keeping the shared kernel
dependency-free per its governance rules. The one-line Jsoup check is intentionally duplicated
in both layers (UI for fast feedback, service for real enforcement) rather than factored into
a shared utility — a single line of logic does not justify a cross-module abstraction, and the
two layers have no natural common module to live in without violating the starter/UI
boundary.

**Consequences:**
- `improvement-006` (Quill UI character counter + `advertisement.description` DB column limit)
  was unblocked by this ADR and has since been **completed** (closed 2026-07-13 via ADR-031 —
  updated here since this ADR still said "remains open" after that closure).
- Any future field with the same "rich HTML but bounded visible text" shape should follow the
  same three-layer pattern rather than reaching for `@Size` directly on the HTML string.

---

## ADR-025: Deny-by-default at the Spring Security URL layer is incompatible with this app's Vaadin routing model

**Status:** Accepted

**Context:** `improvement-020` proposed hardening `SecurityConfig` from `anyRequest().permitAll()`
to `anyRequest().denyAll()` (permitting only `vaadinInternalRequestMatcher` and `/health`), to
guard against future public REST endpoints being accidentally exposed. Deployed and tested via
the full Playwright e2e suite: **0/46 tests passed** — the root page itself never rendered.

Root cause, confirmed via `HandlerHelper.isFrameworkInternalRequest`'s actual implementation:
it matches only Vaadin's internal AJAX/RPC traffic (UIDL, heartbeat, push, upload, dynamic
resource requests) — **not** the root page bootstrap (`GET /`) that serves the application shell.
Since `MainView` is the sole `@Route("")` and the entire app is one Vaadin SPA behind that single
route, `anyRequest().denyAll()` blocks the application shell itself, not just hypothetical future
REST endpoints.

This is consistent with ADR-007: access control for Vaadin views in this app is enforced *inside*
the view (`AccessEvaluator`, hidden tabs), not via Spring Security URL matching — a direct
consequence of Vaadin initializing view beans before authentication completes.

**Decision:** Reverted to `anyRequest().permitAll()`. Deny-by-default at the URL layer does not
apply to a monolithic Vaadin SPA; instead, each future non-Vaadin REST controller (webhooks,
sitemap, etc.) must declare its own explicit `requestMatchers(...)` rule ahead of the Vaadin
catch-all, as a discipline/process rule rather than a global switch.

**Consequences:**
- `SecurityConfig` keeps `anyRequest().permitAll()`; only `/health` and the Vaadin internal
  matcher get explicit rules.
- Any new REST controller added later must add its own security rule *before* the catch-all —
  reviewers should treat a missing explicit rule on a new controller as a blocker.
- → [improvement-020-security-baseline-before-public-endpoints](../backlog/completed/issues/improvement-020-security-baseline-before-public-endpoints.md)

---

## ADR-026: Rate limiting counts only real failures, never successful attempts

**Status:** Accepted

**Context:** `improvement-020` also added Caffeine-based rate limiting to `AuthService.login()`
(`org.ost.marketplace.services.auth` — corrected 2026-07-13; `AuthService` lives in
marketplace-app, not `org.ost.user.services`) and `UserService.register()` (`org.ost.user.services`,
correct as originally written). The first version incremented the attempt counter on *every* call
regardless of outcome. This broke the Playwright e2e suite:
all signups run from the same client IP (the test-runner container), and the 6th successful
signup within the 15-minute window was rejected as "too many attempts" — the generic
`catch (Exception ex)` in `SignUpDialog` then misreported it as "email already registered",
masking the real cause.

**Decision:** Both limiters now increment only on an actual failure:
- `AuthService.login()` — increments on `BadCredentialsException`, invalidates the cache entry
  on success (unchanged from the original design — this one was correct from the start).
- `UserService.register()` — increments only on `DuplicateKeyException` from `repository.save()`
  (a TOCTOU race past the client-side email-uniqueness check); the client-side binder validator
  already rejects duplicate emails before `register()` is ever called, so this path is rare by
  design. Successful registrations never count toward the limit.

Both dialogs (`LoginDialog`, `SignUpDialog`) catch `IllegalStateException` (thrown when the
limit is exceeded) separately from the generic exception handler, showing a dedicated
"too many attempts, try again later" notification (`LOGIN_ERROR_TOO_MANY_ATTEMPTS`,
`SIGNUP_ERROR_TOO_MANY_ATTEMPTS`) instead of a misleading generic error.

**Consequences:**
- e2e coverage: `02-marketplace-authentication-flow.spec.js` — `rateLimitUser exceeds login
  attempts` test signs up a dedicated throwaway user and drives 5 wrong-password attempts + a
  6th blocked attempt, to avoid locking out shared `TEST_USERS` accounts used by later specs.
- No equivalent e2e test exists for the registration limiter's `DuplicateKeyException` path —
  it is not reachable through the normal UI flow (client-side check intercepts first) and would
  require bypassing that check to force a real race.
- → [improvement-020-security-baseline-before-public-endpoints](../backlog/completed/issues/improvement-020-security-baseline-before-public-endpoints.md)

---

## ADR-027: forward-headers-strategy so registration rate limiting sees the real client IP

**Status:** Accepted

**Context:** An external audit pass (round 7) found that `UserService.register()`'s rate
limiter (ADR-026) keys solely on `clientIp` (`request.getRemoteAddr()` from
`SignUpDialog`), with no email component (unlike the login limiter, which keys on
`remoteAddr + "|" + email` and stays scoped to one target account even if `remoteAddr`
collapses). The project's README documents the deployed target as Render, which — like
essentially all PaaS providers — terminates the connection at its own edge and forwards to the
app instance over its internal network. `server.forward-headers-strategy` was not configured
anywhere, so `request.getRemoteAddr()` returned Render's internal proxy address for every
user, not the real client IP. This collapsed the registration limiter into one shared bucket
for the entire platform: 5 failed registrations from anyone (even organic
`DuplicateKeyException` races) would lock out registration for everyone for 15 minutes.

**Decision:** `application-prod.yml` — added `server.forward-headers-strategy: framework`, so
Spring's `ForwardedHeaderFilter` translates `X-Forwarded-For` into the request's apparent
remote address before the app sees it. This assumes Render forwards this header (standard
PaaS behavior) — not independently verifiable from this dev environment; needs confirming
once actually deployed.

A coarser global (IP-independent) backstop limiter was considered and rejected: registration
failures don't have a natural key to count against the way login does (a login failure is
always a guess against one specific existing account; a registration failure is a random
collision with whatever email happened to already be taken, essentially never the same email
retried), so a second counter added complexity without a clean justification for this
codebase's actual threat model. Fixing the IP resolution itself is the real fix.

**Consequences:**
- The login limiter (`AuthService`) was not touched — its compound key already scopes lockout
  to one target account even under a fully collapsed `remoteAddr`.
- → [improvement-022-registration-rate-limit-shared-proxy-ip](../backlog/completed/issues/improvement-022-registration-rate-limit-shared-proxy-ip.md)

---

## ADR-028: SettingsPaginationService — per-user ownership on bindings, detach-based cleanup

**Status:** Accepted

**Context:** `SettingsPaginationService` is a singleton `@Component` holding a
`CopyOnWriteArrayList<BindingEntry>` shared across every user's UI session. Two problems found
via external audit + internal verification:
1. **Cross-session bleed (bug):** `onSettingsChanged(userId, settings)` only checked whether
   the *current thread's* user matched `userId`, then pushed the new page size to **every**
   registered `PaginationBar` across **all** sessions — so when user X changed their page size,
   users Y and Z had their live grids silently resized to X's value until they next reloaded
   the view. Invisible in single-user e2e (only one logged-in user drives each test).
2. **UI-reference leak risk:** the singleton held strong references to `PaginationBar` (→ the
   whole UI subtree) with cleanup relying entirely on views calling `unregister()` from
   `@PreDestroy`; any path that skips that callback pins a dead UI tree in the service forever.

**Decision:** `BindingEntry` now carries the owning `userId` (captured from
`AuthContextService` at `register()` time). `onSettingsChanged` filters `entries` by
`entry.userId().equals(userId)` instead of gating on the current thread's user — this fixes the
bleed regardless of which thread fires the hook, and drops the now-redundant
`authContextService.getCurrentUser()` pre-filter entirely. `register()` also adds
`bar.addDetachListener(_ -> unregister(bar))`, so cleanup no longer depends solely on
`@PreDestroy` being called correctly on every path; `unregister()` remains for the explicit
call.

**Consequences:**
- `SettingsPaginationBinding` and the three call sites (`AdvertisementsView`, `TimelineView`,
  `UserView`) are unchanged — same `register`/`unregister` signatures.
- e2e coverage: extended `05-seed-filter-sort-pagination.spec.js` — `adminEn changes page
  sizes...` test now opens a second browser context logged in as `userEn` right after `adminEn`
  changes their own page size, and asserts `userEn`'s Advertisements grid still shows the
  default page size (unaffected).
- → [improvement-018-settings-pagination-cross-session-bleed](../backlog/completed/issues/improvement-018-settings-pagination-cross-session-bleed.md)

---

## ADR-029: Optimistic locking via a stored version column — no auto-reload on conflict

**Status:** Accepted

**Context:** No entity carried a version field; two concurrent edits of the same advertisement,
user, or taxon resulted in silent last-write-wins — the second save overwrote the first with no
error, audit anomaly, or warning to either editor (improvement-015). With the planned community
migration (24k members, moderators/owners editing shared listings) this stops being a rare
accident. Several alternatives were discussed and rejected before settling on this design:
- Computing a "version" on the fly from `audit_log` (the way the audit timeline already does via
  a `ROW_NUMBER()`/`COUNT(*)` window function, see audit-spring-boot-starter ADR-020) — rejected:
  audit is optional (`ComponentFactory<AuditPort>.ifAvailable()`), so core save correctness can't
  depend on it, and not every bespoke UPDATE writes an audit row.
- Comparing `updated_at` instead of adding a column — rejected: equality on a high-precision
  timestamp across a DB → Java `Instant` → JSON/DTO round trip is fragile (rounding, truncation);
  an integer counter never has that class of bug, which is why every mainstream ORM (Rails
  `lock_version`, Hibernate/JPA `@Version`, EF Core `rowversion`) uses one.
- A full-row compare (`WHERE title = :old AND description = :old ...`) — avoids a new column and
  isn't precision-fragile, but flags a conflict on *any* field touched by someone else, even one
  the current editor never looked at. Rejected in favor of the simpler, standard column.

**Decision:** `version BIGINT NOT NULL DEFAULT 0` added directly to the existing
`01-advertisement-schema.xml`, `01-user-schema.xml`, and `001-taxon.xml` changesets (DB not yet
in production — no new migration file, per the same rationale as the `taxon.deleted_by` column;
requires `deploy.sh --reset` locally). `@Version private Long version;` added to `Advertisement`,
`User`, `Taxon`.

For `Advertisement` and `Taxon`, saves go through `CrudRepository.save()`, so Spring Data JDBC's
`@Version` handling applies natively — it appends `WHERE version = ?` and throws
`OptimisticLockingFailureException` on a mismatch. Two places rebuild the entity via `Builder`
before saving and were missed on the first pass because they don't automatically carry the field
forward: `AdvertisementService.buildEntity()` and `TaxonService.update()` — both now explicitly
forward the version from the incoming DTO / port parameter (the value the caller last read),
**not** a version re-fetched inside the same save method (which would just match itself and
detect nothing).

`User`'s real edit path (`UserService.save()` → `UserRepository.updateProfile()`) originally
bypassed `CrudRepository` entirely via hand-written SQL, so `@Version` alone did nothing there.
`updateProfile()` did the check by hand: `SET ..., version = version + 1 WHERE id = :id AND
version = :version`, throwing `OptimisticLockingFailureException` manually when zero rows match.

**Update (2026-07-14) — `User` moved onto native `CrudRepository.save()` too:** a second, narrower
entity, `UserProfileUpdate` (`id`, `name`, `role`, `updatedAt`, `version` — deliberately excludes
`email`/`passwordHash`), mapped to the same `user_information` table via its own
`UserProfileCrudRepository`, replaces the hand-written SQL in `UserRepository.updateProfile()`.
This was chosen over mirroring `AdvertisementService.buildEntity()`/`TaxonService.update()`
(rebuild the full entity via `Builder`, forwarding every unedited field from `before`) precisely
because that pattern's known failure mode — forgetting to forward a field — is far more dangerous
for `User` than for `Advertisement`/`Taxon`: dropping `passwordHash` or `email` silently breaks
login or notifications, not just a lock-check regression. Since `passwordHash`/`email` are not
mapped properties on `UserProfileUpdate`, the generated `UPDATE` cannot reference them regardless
of builder mistakes — the risk is closed at the type level, not by discipline. See
`user-spring-boot-starter/CLAUDE.md` and
[improvement-024](../backlog/completed/issues/improvement-024-user-save-via-crudrepository.md).

`softDelete` on `Advertisement` and `Taxon` also got the same manual guard (an admin/owner
deleting a listing while someone else is mid-edit should not silently win); `updateMedia`,
`updateLocale`, and `TaxonRepository.restore()` were left unguarded — none of them represent a
user-authored edit that competes with a live form.

The version travels end-to-end: `AdvertisementInfoDto`/`UserDto`/`TaxonDto` (read side, RowMapper
+ SELECT column added where hand-rolled) → `AdvertisementEditDto`/`UserEditDto` (MapStruct maps
the field by name automatically) → `AdvertisementSaveDto`/`UserProfileDto` (write side) →
`TaxonPort.update()`/`softDelete()` and `AdvertisementPort.delete()` gained a trailing `version`
parameter since they had no DTO to carry it (see platform-commons ADR-019).

`AbstractEntityOverlay.handleSave()` gained a `catch (OptimisticLockingFailureException)` before
the generic `catch (Exception)`, showing a dedicated conflict notification
(`*_NOTIFICATION_CONFLICT` i18n keys, one per domain) instead of the generic save-error message.
`SaveConfig` record gained a fourth `conflict` component; `SettingsOverlay` (which has no version
field) passes `null` for it, same as its existing `null` validation/save-error keys.

**Deliberately not done:** no automatic form reload on conflict. Silently replacing the editor's
in-progress form with fresh server data would destroy their unsaved changes without them
noticing — a different flavor of the same "silent data loss" bug this feature exists to fix. The
user sees the conflict notification and must manually cancel/reopen to get a fresh version and
retry — safer than a clever auto-merge for a first version of this feature.

**Consequences:**
- e2e coverage: new test in `04-marketplace-advertisement-flow.spec.js` — two browser contexts
  (`userEn`, `moderatorEn`) open the same advertisement for edit before either saves; the first
  save succeeds, the second (stale) save shows the conflict notification instead of overwriting.
- → [improvement-015-optimistic-locking](../backlog/completed/issues/improvement-015-optimistic-locking.md)

---

## ADR-030: Field labels applied uniformly across every Activity/Timeline rendering path

**Status:** Accepted

**Context:** `AuditTimelineRowRenderer` has two private helpers that both render a list of
`ChangeEntry` into a `Div`: `buildActivityChangesDiv()` (used only when an entity type has an
`AuditActivityFieldsHook` but no `AuditActivityEnrichHook`) and `buildEntityChangesDiv()` (used
by the enrich-hook branch of the cross-entity Timeline, and unconditionally by the overlay
Activity-tab overload `buildActivityFieldsList(AuditActivityItemDto, EntityRef)`). Only the
former applied `labelHook.labelFor(field)` to each `ChangeEntry.FieldChange`; the latter never
did. Since `ADVERTISEMENT` has both hooks registered (`ActivityEnrichHookImpl` for media state,
`AdvertisementActivityFieldsHookImpl` for labels), its enrich-hook branch always won and always
skipped labeling — and every overlay's own Activity tab (Advertisement, User, Taxon,
UserSettings alike) went through `buildEntityChangesDiv()` unconditionally, so it never applied
labels either. The label mappings themselves (`AdvertisementActivityFieldsHookImpl`,
`TaxonActivityFieldsHookImpl`, `UserSettingsActivityFieldsHookImpl`) were already complete —
only the wiring was missing (improvement-013).

**Decision:** `buildEntityChangesDiv()` now takes the resolved `AuditActivityFieldsHook` for the
entity type as a parameter and applies it via a shared `applyLabel(entry, labelHook)` helper
(extracted from the logic `buildActivityChangesDiv()` already had) before rendering each
`FieldChange`. Both call sites — the Timeline enrich-hook branch and the overlay Activity-tab
overload — now resolve `fieldsProviders.get(entityType)` and pass it through. `labelHook` is
nullable-safe (falls back to the raw field key) since `AuditActivityFieldsHook.labelFor()` has a
default no-op implementation, so this doesn't require every entity type to register one.

**Consequences:**
- No changes needed to any `*ActivityFieldsHookImpl` — their `labelFor()` mappings were already
  correct; only `AuditTimelineRowRenderer` needed the wiring fix.
- e2e coverage: `05-seed-filter-sort-pagination.spec.js` — `adminEn changes page sizes...` test
  assertions updated from the old raw-field-name-tolerant regex (`/adsPageSize|Оголошень/i`) to
  the actual humanized label (`/Ads per page|Оголошень/i`), which now proves the fix rather than
  merely tolerating the old bug.
- → [improvement-013-raw-field-names-in-activity-diff](../backlog/completed/issues/improvement-013-raw-field-names-in-activity-diff.md)

---

## ADR-031: QuillEditor character counter measures visible text; DB column sized to the raw-HTML cap, not the visible-text cap

**Status:** Accepted

**Context:** `QuillEditor` had no visible character counter, unlike Vaadin's `TextArea`/`TextField`
which show one automatically when `maxLength` is set (improvement-006). Separately,
`advertisement.description` was still `TEXT` (unbounded) in the schema, with no DB-level cap
matching the two limits already established in ADR-024: `DESCRIPTION_MAX_LENGTH = 2000`
(visible text, Jsoup-measured) and `DESCRIPTION_RAW_MAX_LENGTH = 20_000` (raw HTML, Bean
Validation `@Size`).

**Decision:**
1. `QuillEditor` gained a `setMaxLength(int)` method (sets a `maxlength` attribute, mirrored in
   `quill-editor.js` via `observedAttributes`). The counter reads `quill.getText().length - 1`
   (Quill's `getText()` always ends in `\n`) on every `text-change`, displaying `"N / max"` —
   this measures the same thing the server does (visible text), not raw HTML size.
   `AdvertisementFormOverlayModeHandler` wires it with
   `descriptionField.setMaxLength(AdvertisementSaveDto.DESCRIPTION_MAX_LENGTH)` (2000).
2. `advertisement.description` changed from `TEXT` to `VARCHAR(20000)` — edited directly into
   the existing `01-advertisement-schema.xml` changeset (DB not yet in production, same
   rationale as prior direct-edit changes this cycle).

**Why the DB column is 20000, not 2000:** the column stores raw HTML (with formatting tags),
not visible text. A description at exactly the 2000-visible-character limit that uses bold,
lists, or headers can easily produce well over 2000 raw characters — legitimate content that
already passes both the UI counter and the server-side Jsoup check. Capping the column at 2000
(matching only the visible-text limit) would reject that already-valid content at the DB layer
for no reason connected to any actual limit anyone agreed to. `20000` reuses the raw-size
ceiling already established and enforced in ADR-024 (`DESCRIPTION_RAW_MAX_LENGTH`) — not a new
number, just the column matching a limit the application already enforces one layer up.

**Consequences:**
- No change to `DESCRIPTION_MAX_LENGTH` (2000) — the visible-text limit users actually see and
  are validated against is unchanged.
- Full e2e suite 48/48 green; counter visually confirmed via Playwright screenshot
  (`adv-useren-create-form-filled`, showing "85 / 2000").
- → [improvement-006-quill-description-counter-and-db-limit](../backlog/completed/issues/improvement-006-quill-description-counter-and-db-limit.md)

---

## ADR-032: Request correlation id via SLF4J MDC, plus closing silent-service logging gaps

**Status:** Accepted

**Context:** Log lines from a single HTTP request/UI action carried no shared identifier —
reconstructing "everything that happened for one save click" across threads meant matching on
timestamps and guessing. `Advertisement.version`/`User.version`/`Taxon.version`
(improvement-015) were considered and rejected as a substitute: a version is unique only within
one row, many requests touch zero or several entities, and a failed/conflicting request has no
version to log against at all (improvement-023).

Separately, a review of `log.` coverage across all services found several completely silent
classes — mutating operations with no log line at all, which would leave the new correlation id
with nothing to correlate: `TaxonService` (create/update/softDelete/restore), `AuthService`
(login/logout — a security-relevant gap), `AttachmentService` (upload/delete/addVideo/
softDeleteAll), `TaxonAssignmentService` (assign/unassign/replaceAssignments),
`AttachmentSnapshotService` (captureAndGetId), `UserSettingsService` (save),
`AdvertisementSaveService` (the marketplace-app-level save transaction, distinct from
`AdvertisementService.save()` which already logged), and both cleanup services
(`AuditCleanupService`/`AdvertisementService.cleanup()`, which discarded the deleted-row count).
`LoginDialog` also lacked the generic catch-all exception log that `SignUpDialog` already had.

**Decision:**
1. `RequestCorrelationFilter` (`marketplace-app/config`, extends `OncePerRequestFilter`) puts a
   fresh `UUID.randomUUID()` in MDC under key `requestId` at the start of every HTTP request,
   removed in a `finally` block. Auto-registered by Spring Boot (any `Filter` bean); no
   `SecurityConfig` changes needed.
2. `application.yml`'s console logging pattern gained `%.8X{requestId}` (first 8 hex chars —
   enough to eyeball-correlate without cluttering the console with a full UUID).
3. Alternative considered: `micrometer-tracing` (auto-configured MDC insertion, full
   distributed-tracing spans) — rejected as heavier than needed for a single-instance monolith
   with no downstream services to trace across; revisit if the app ever splits into multiple
   services.
4. Added `log.info`/`log.warn` to every silent mutating method listed above, following the
   existing convention (`"<Entity> <action>: id={}"`). `deleteOlderThan()` in both
   `AuditLogRepository` and `AdvertisementRepository` changed from `void` to `int` (returns the
   JDBC update count) so cleanup services can log how many rows were actually deleted, not just
   that cleanup ran.

**Vaadin-specific caveat:** a single Vaadin UI session spans many HTTP requests (one per server
round-trip), so `requestId` correlates *one round-trip* (e.g. one save-click), not "the whole
time the user had the form open" — that's expected and matches the actual unit anyone wants to
correlate.

**Consequences:**
- Purely additive: no schema changes, no port/hook signature changes except the two
  `deleteOlderThan()` return-type widenings (both had exactly one caller each, both updated).
- Verified end-to-end: `docker logs` shows a distinct `requestId` per login request during the
  e2e run (e.g. `[d96a341c]`, `[121b21da]`, one per HTTP round-trip).
- Full e2e suite 48/48 green.
- → [improvement-023-request-correlation-id-via-mdc](../backlog/completed/issues/improvement-023-request-correlation-id-via-mdc.md)

---

---

## ADR-033: Optional-port UI components use `ComponentFactory<Port>`, never `@ConditionalOnBean` on the component class

**Status:** Accepted

**Context:** `AttachmentGalleryService`, `AttachmentGallery`, and `AuditActivityPanel` inject
`AttachmentPort`/`AuditPort` directly as hard constructor parameters. In a genuinely optional
future starter (payment-, telegram-, ai-spring-boot-starter per the roadmap), this would crash
Spring bean construction the moment the starter is removed from the classpath. improvement-011
proposed adding `@ConditionalOnBean(AttachmentPort.class)` / `@ConditionalOnBean(AuditPort.class)`
directly on these three `@SpringComponent` classes so the bean definition itself would not exist
when the port is unavailable ("Option A/C").

This was implemented, deployed, and **empirically broke the app** even with every starter present
and every port genuinely available: the full Playwright e2e suite went from 48/48 to 5 failed / 35
skipped. Root cause, confirmed via container logs and source inspection: `@ConditionalOnBean` on a
`@ComponentScan`-discovered class evaluates during regular bean-definition registration, which
happens *before* `@AutoConfiguration` classes register their beans (`AuditPort`'s real
implementation, `DefaultAuditPort`, is registered by `AuditAutoConfiguration`, an autoconfiguration
class). At the point Spring evaluates the condition for `AuditActivityPanel`, `AuditPort`'s bean
definition does not exist yet — the condition fails, `AuditActivityPanel` is silently never
registered, and `SettingsFormModeHandler.activate()`'s `auditActivityPanelFactory.findIfAvailable()`
guard (line 119) silently omits the Activity tab with no exception. The very first e2e test
(admin sign-up, settings check) failed on that missing tab; because that spec file runs in serial
mode, every later test in the file was skipped, and several e2e users were never created — causing
a cascade of unrelated-looking "Invalid email or password" failures in later spec files that
merely tried to log in as those never-created users. One bug looked like five.

**Decision:** `@ConditionalOnBean` must never be used on a `@ComponentScan`-discovered UI component
class (marketplace-app) that depends on a bean registered by a starter's `@AutoConfiguration`
class — the ordering is not guaranteed and the failure mode is silent (no exception, just an
absent tab/feature). Reverted all three `@ConditionalOnBean` additions. Instead, applied the
already-established pattern from `marketplace-app/CLAUDE.md` ("Use `ComponentFactory<T>` for
optional singleton services/ports"):
- `AuditActivityPanel`: hard field `AuditPort auditPort` → `ComponentFactory<AuditPort>
  auditPortFactory`, resolved via `.get()` inside `configure()`.
- `AttachmentGalleryService` / `AttachmentGallery`: hard field `AttachmentPort attachmentPort` →
  `ComponentFactory<AttachmentPort> attachmentPortFactory`; `AttachmentGallery` resolves it once in
  `@PostConstruct init()` into a cached field (kept the rest of its ~15 call sites unchanged).
  `ObjectProvider<T>` (which `ComponentFactory<T>` wraps) is always injectable regardless of
  whether a `T` bean exists — Spring never fails constructor injection on it, deferring the
  "is it actually there" check to whenever `.get()`/`.ifAvailable()`/`.findIfAvailable()` is
  actually called.
- The *availability gate* moved up one level, from checking the wrapping UI component's factory to
  checking the **port's own** `ComponentFactory` — because once the component classes above no
  longer carry `@ConditionalOnBean`, their own `UiComponentFactory<X>.findIfAvailable()` would
  always resolve non-empty regardless of whether the port truly exists, silently defeating
  graceful degradation. Fixed in every call site that previously gated on the wrapping factory:
  `SettingsFormModeHandler`, `UserFormOverlayModeHandler`, `TaxonFormOverlayModeHandler` (gate
  changed from `auditActivityPanelFactory.findIfAvailable()` to `auditPortFactory.findIfAvailable()`
  — `auditPortFactory` already existed as a field in all three, just unused for this purpose), and
  `AdvertisementFormOverlayModeHandler`, `AdvertisementViewOverlayModeHandler`,
  `AdvertisementCardView` (gate changed from `galleryServiceFactory.ifAvailable(...)` to a new
  `ComponentFactory<AttachmentPort> attachmentPortFactory` field's `.ifAvailable(...)`/
  `.findIfAvailable()`).

**Consequences:**
- No `@ConditionalOnBean` remains on any marketplace-app UI component class targeting a
  starter-provided port — establishes the pattern for future genuinely-optional starters.
- Two pre-existing instances of the *same* wrong-level gate (`auditActivityPanelFactory
  .findIfAvailable()` instead of `auditPortFactory.findIfAvailable()`) were found and fixed in
  `TaxonFormOverlayModeHandler` and `UserFormOverlayModeHandler` during this pass — they had not
  yet caused a visible failure only because `AuditActivityPanel` had never carried a conditional
  annotation before improvement-011, so `findIfAvailable()` on it always happened to resolve
  correctly by accident, not by design.
- Full e2e suite verified 48/48 green after the corrected fix (was 8/48 with the
  `@ConditionalOnBean` approach).
- → [improvement-011-unguarded-port-injection-in-ui-components](../backlog/completed/issues/improvement-011-unguarded-port-injection-in-ui-components.md)

---

## ADR-034: No raw cross-starter SQL joins — bulk-lookup port + service-level enrichment; actor-reference columns follow Taxon's naming convention

**Status:** Accepted

**Context:** `AdvertisementRepository.findAdvertisementById()`/`findByFilter()` did
`FROM advertisement a LEFT JOIN user_information u ON a.created_by_user_id = u.id`, hardcoding
`user-spring-boot-starter`'s table and column names (`user_information`, `u.name`, `u.email`)
inside `advertisement-spring-boot-starter`. This is a stronger violation of "starters must not
depend on each other" (`.claude/rules.md`) than a Java import: there is no class-level
dependency for ArchUnit (improvement-030) to detect, only a raw SQL string — a rename in
`user-spring-boot-starter` would break `advertisement-spring-boot-starter` at runtime with zero
compile-time warning. Separately, `advertisement`'s actor-reference columns
(`created_by_user_id`/`last_modified_by_user_id`/`deleted_by_user_id`) embedded the word "user"
for no functional reason, unlike `taxon`'s already-established `created_by`/`updated_by`/
`deleted_by` convention (same kind of value — an opaque `User.id` populated via
`AuditorAware<Long>`).

**Decision:** Mirrors the already-completed `TaxonPort.findByIds()` pattern
(improvement-007/015), applied to `UserPort`:
- `UserPort.findByIds(Set<Long>) -> Map<Long, UserDto>` added to `platform-commons`;
  `UserPortImpl.findByIds()` is pure delegation to `UserService.findByIds()`, which calls a new
  `UserRepository.findByIds(Long[])` (`SELECT ... WHERE id = ANY(:ids)`, same shape as the
  existing `findActorNames()`/`findExistingIds()` bulk lookups).
- `AdvertisementRepository` no longer joins `user_information` at all — `findAdvertisementById()`
  and `findByFilter()` select only `advertisement.created_by` (the FK id, still present as a
  plain column). The three dead `ORDER BY` alias entries for `u.id`/`u.name`/`u.email` were
  removed together with the join — verified dead: `AdvertisementSortMeta` only exposes
  `TITLE`/`CREATED_AT`/`UPDATED_AT`, so no UI path ever built a `Sort` reaching those aliases.
- `AdvertisementService.enrichWithActorInfo(ads)` (new, mirrors `enrichWithCategories()` exactly,
  same `ComponentFactory<UserPort>` optional-dependency shape as `ComponentFactory<TaxonPort>`)
  merges `createdByUserName`/`createdByUserEmail` into `AdvertisementInfoDto` after the repository
  call, in `getFiltered()` and `findById()`. `UserPortImpl`/`AdvertisementPortImpl` stay pure
  delegation — the merge logic lives only in the service, per `platform-commons/CLAUDE.md`'s
  `*PortImpl` rule.
- Renamed `advertisement`'s actor-reference columns to match `taxon`: `created_by_user_id` →
  `created_by`, `last_modified_by_user_id` → `updated_by`, `deleted_by_user_id` → `deleted_by`
  (direct edit of `01-advertisement-schema.xml` — DB not yet in production, same practice as
  every prior schema edit — requires `deploy.sh --reset` for the Liquibase checksum). Matching
  Java field renames: `Advertisement.createdByUserId`→`createdBy` (`@CreatedBy`),
  `.lastModifiedByUserId`→`updatedBy` (`@LastModifiedBy`); `AdvertisementInfoDto.createdByUserId`
  (platform-commons) and `AdvertisementEditDto.createdByUserId`/`.lastModifiedByUserId`
  (marketplace-app) renamed to match — `createdByUserName`/`createdByUserEmail` keep their names
  since they describe enrichment output, not a `_user_id`-suffixed column.

**Sort-by-author, if ever requested:** do not reintroduce a JOIN (recreates this exact violation)
and do not sort in memory after `enrichWithActorInfo()` (pagination `LIMIT`/`OFFSET` runs in SQL
*before* enrichment, so an in-memory sort would only order the current page, silently wrong for
any page beyond the first). The correct fix is the same pattern already used for
`media_url`/`media_content_type`: denormalize `created_by_user_name` onto `advertisement`, synced
via a typed hook fired on user name changes — not a query-time join.

**Explicitly not touched:** the FK constraints in `01-advertisement-schema.xml`
(`referencedTableName="user_information"`) still reference the other starter's table by name —
this is a deeper, separate schema-level coupling (referential integrity inherently requires
knowing the referenced table) that this ADR does not attempt to resolve.

**Consequences:**
- Full e2e suite must stay green — the advertisement card's author-email display
  (`AdvertisementCardView.java:187`, `ad.getCreatedByUserEmail()`) and the owner-only edit/delete
  button visibility (`getOwnerUserId()`, used in `AdvertisementCardView`,
  `AdvertisementFormOverlayModeHandler`, `AdvertisementViewOverlayModeHandler`) are the concrete
  regression detectors.
- → [improvement-041-advertisement-user-sql-join-and-column-naming](../backlog/completed/issues/improvement-041-advertisement-user-sql-join-and-column-naming.md)

---

## ADR-035: `advertisement` stores no denormalized attachment columns — media summary enriched at read time via a bulk `AttachmentPort` lookup

**Status:** Accepted

**Context:** `advertisement` had three columns — `media_url`, `media_content_type`, `media_count`
— caching a summary of the entity's attachments, written by
`AdvertisementRepository.updateMedia()`, triggered by `MediaChangeHookImpl.onMediaChanged()`
whenever `AttachmentService` fired `AttachmentMediaChangeHook`. An earlier pass on this review
dismissed these columns as "fine as-is" because the sync mechanism (a hook) was clean — that
answered "is the sync mechanism clean?" (yes) instead of "does the coupling exist?" (also yes,
independently of the mechanism): three columns named with attachment-domain vocabulary, living on
`advertisement`'s own row, in a different starter's schema. `backlog/entity-extensions/SPEC.md`
(deleted 2026-07-13) had already named this exact coupling as a motivating problem; its proposed
fix (genericize into a `media JSONB` column) was rejected — re-encoding the same data on the same
row removes type safety without removing the coupling itself.

**Decision:** Same shape as ADR-034 (User) and the completed improvement-007 (Taxon): a bulk
lookup replaces the denormalized cache.
- `AttachmentPort.getMediaSummaries(EntityType, Set<Long>) -> Map<Long, AttachmentMediaSummaryDto>`
  added to `platform-commons`, alongside the existing single-entity `getMediaSummary(EntityRef)`.
  `DefaultAttachmentPort` stays pure delegation to a new `AttachmentService.getMediaSummaries()`,
  which calls a new `AttachmentRepository.loadMediaStats(EntityType, Set<Long>)` — one SQL query
  using `ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY created_at ASC)` to pick each entity's
  earliest attachment as its "main" one, plus `COUNT(*) OVER (PARTITION BY entity_id)`, matching
  the existing single-entity method's semantics exactly (`loadMediaStats(EntityType, Long)`,
  unchanged, still used by the single-entity path).
- `AdvertisementService.enrichWithMediaSummary(ads)` (new, same shape as `enrichWithCategories()`/
  `enrichWithActorInfo()`) merges `mediaUrl`/`mediaContentType`/`mediaCount` into
  `AdvertisementInfoDto` at read time in `getFiltered()`/`findById()`, using the already-existing
  `ComponentFactory<AttachmentPort> attachmentPortFactory` field. Entities with zero attachments
  fall back to `AttachmentMediaSummaryDto.empty()`.
- The three columns were removed from `advertisement` (`01-advertisement-schema.xml`, direct
  edit — DB not yet in production, `deploy.sh --reset` required), along with
  `AdvertisementRepository.updateMedia()` and the three dead `ORDER BY` sort-alias entries for
  them (confirmed unreachable — `AdvertisementSortMeta` never exposed a media-related sort).
- **The write-triggered sync path was deleted entirely, not just emptied**: `MediaChangeHookImpl`
  (the only implementation of `AttachmentMediaChangeHook`), `AdvertisementService
  .onMediaChanged(Long)`, and `AdvertisementPort.onMediaChanged(Long)` (confirmed unused by any
  marketplace-app call site) are all gone — there is nothing left to update once no column caches
  the data. `AttachmentService` still fires `AttachmentMediaChangeHook` on every media change (the
  interface itself stays in `platform-commons` as a generic, still-meaningful extension point for
  any future starter that wants to react to media changes) — it now simply has zero listeners,
  which is the same valid, gracefully-degraded state every other optional SPI in this codebase
  already tolerates.

**Tradeoff accepted explicitly:** one more bulk `AttachmentPort` query per advertisement list
render — the same cost class already accepted twice (Taxon categories, User author info) for the
same real decoupling benefit.

**Consequences:**
- Full e2e suite must stay green — `AdvertisementCardView.java`'s media thumbnail/badge rendering
  (reads `ad.getMediaUrl()`/`getMediaContentType()`/`getMediaCount()`) is the concrete regression
  detector; behavior is unchanged since `AdvertisementInfoDto` still carries these fields, just
  populated by enrichment instead of a stored column.
- → [improvement-042-advertisement-media-denormalized-columns](../backlog/completed/issues/improvement-042-advertisement-media-denormalized-columns.md)

---

## ADR-036: `AdvertisementRepository.buildIdClause()` binds a plain array, not a `Set`, for the category-filter id list

**Status:** Accepted

**Context:** `AdvertisementService.resolveCategoryFilter()` calls `TaxonPort
.findEntityIdsWithAnyTaxon()` to get the set of advertisement ids matching the selected
categories — the bulk-lookup pattern ADR-034 already established, kept exactly as-is here (not
revisited). The gap was one level down: `AdvertisementRepository.buildIdClause()` bound that
`Set<Long>` directly to `WHERE a.id IN (:allowedIds)`. Spring's `NamedParameterJdbcTemplate`
expands a `Collection`-typed bind value into one `?` placeholder per element for an `IN` clause —
unbounded for a popular category's advertisement count, and the SQL text itself changes shape
(different placeholder count) for every differently-sized result, defeating Postgres's query-plan
cache on top of the parameter-count risk. Filed as improvement-050 item 2.

Three fixes were considered:
1. **Leave as-is** — rejected for a fresh review, but was the standing default for a while: no
   evidence this app's real category sizes are anywhere near the risk zone (current seed/test data
   is ~10 ads/category), and the project avoids designing for hypothetical future load. Revisit
   trigger: a real category size approaching the thousands.
2. **Defensively cap `allowedIds` size** — rejected: silently wrong results (arbitrarily dropping
   matches) or an opaque failure, neither actually fixes the scaling problem, just delays it.
3. **JOIN to `taxon_assignment` directly** — rejected: reverses ADR-034's own decision (no raw
   cross-starter SQL joins) without a compelling reason to, since a much smaller fix closes the
   actual problem without touching that boundary at all (see Decision below).

**Decision:** Bind a plain `Long[]` array instead of the `Set<Long>`, and compare with
`= ANY(:allowedIds)` instead of `IN (:allowedIds)`:
```java
params.addValue("allowedIds", ids.toArray(new Long[0]));
return " AND a.id = ANY(:allowedIds)";
```
Spring only expands `Collection`-typed values into multiple placeholders — a native array is
passed through as a single bind parameter, and the PostgreSQL JDBC driver binds it natively as one
`bigint[]` value. This is not a new pattern: `AdvertisementRepository.findExistingIds(Long[] ids)`
already does exactly this (`WHERE id = ANY(:ids)`) a few lines below `buildIdClause()` — the fix
is applying the class's own existing, already-proven convention consistently, not introducing a
new one.

**Why not `unnest()`:** `WHERE a.id IN (SELECT unnest(:allowedIds))` is a documented, equally valid
alternative for the same underlying reason (a single array bind instead of N placeholders) — not
chosen here only because `= ANY()` was already an established, tested pattern in this exact class,
so it carries less risk than introducing new SQL syntax the codebase hasn't used before.

**On Postgres coupling:** `= ANY()` is Postgres-specific syntax (not ANSI SQL), same as `unnest()`
would have been. Not treated as a new category of risk — this codebase already commits to
Postgres-specific features throughout (`JSONB` columns with `::jsonb` casts, `ROW_NUMBER() OVER
(PARTITION BY ...)`, `(created_at, id) <=` tuple comparisons — see ADR-020 and the audit tiebreak
fix in improvement-050 item 4), and portability to another RDBMS has never been a stated goal.

**On the array-size limit:** binding a native array removes the *parameter-count* limit entirely
(always exactly one bind parameter, regardless of how many ids). A different limit still exists —
PostgreSQL's per-value TOAST size cap (~1 GB) — but for a `bigint[]`, that allows tens of millions
of elements before it would ever matter, several orders of magnitude past any realistic category
size for this app.

**Consequences:**
- `AdvertisementRepository.findByFilter()`/`countByFilter()` — the only two callers of
  `buildIdClause()` — are otherwise unchanged.
- New regression coverage: `AdvertisementRepositoryTest
  .findByFilter_allowedIdsRestrictsToMatchingRows` /
  `.countByFilter_allowedIdsRestrictsCount` — the existing test class had zero coverage of the
  non-null `allowedIds` path before this (every prior test passed `null`).
- → [improvement-050-toctou-scalability-locale-audit-tiebreak](../backlog/issues/improvement-050-toctou-scalability-locale-audit-tiebreak.md)
  item 2.

---

## ADR-037: Raw `new Button(...)` spots converted to `Ui*Button` wrappers — every consumer is itself a bean injecting its own factory, never a pre-built widget through a constructor

**Status:** Accepted

**Context:** `HeaderBar`, `PaginationBar`, the attachment lightbox/gallery family
(`CardMediaLightbox`, `AttachmentLightbox`, `CardLightboxViewer`, `AttachmentThumbnail`,
`AttachmentGallery`), `AuditActivityRowRenderer`'s restore button, and `UserPickerField`'s
clear/open buttons all hand-built raw `Button`/`ButtonVariant` calls instead of reusing the
existing `UiPrimaryButton`/`UiTertiaryButton`/`UiIconButton` wrappers — five of these spots applied
**no theme variant at all** (a real visible UX bug in the header's 4 auth buttons, not just
duplication). Filed as improvement-026.

**Design question that came up mid-implementation:** how should a plain (non-Spring) helper class
obtain a `Ui*Button` instance? Two designs were tried and rejected before landing on the final
one:
1. Pass a `UiComponentFactory<UiIconButton>` into a plain class's constructor — rejected: a
   Spring factory has no business being injected into a class the container doesn't manage.
2. Pass a pre-built `Ui*Button` widget into the constructor (asymmetric: some call sites via
   constructor, others via a static `open()` factory method) — rejected: inconsistent construction
   shape across sibling classes, and passing *views* into a constructor is backwards regardless —
   constructors should receive **beans**, not pre-built widgets.

A full-tree survey (every class anywhere that consumes a `Ui*Button`/`*ActionButton` type) found
**zero exceptions** to one pattern: the consumer is always itself a real `@SpringComponent
@Scope("prototype")` bean, and it injects its own `UiComponentFactory<T>` directly.

**Decision:** Apply that same pattern everywhere raw buttons were converted. Where a plain class
didn't already carry Spring wiring (`AttachmentLightbox`, `CardLightboxViewer`,
`AttachmentThumbnail` — previously plain classes or partially so), it was promoted to a full
`@SpringComponent @Scope("prototype")` bean specifically so it could inject
`UiComponentFactory<UiIconButton>` itself, matching every other consumer in the codebase. Done in
four phased batches (full e2e run after each, per the project's established discipline for
wide-blast-radius UI refactors):
1. **`HeaderBar`** — 4 auth buttons → `UiPrimaryButton`, all CSS classes (`header-*-button`)
   preserved exactly — these are selected by nearly every Playwright spec's login-check.
2. **`PaginationBar`** — 4 nav icon buttons → `UiIconButton`.
3. **Attachment lightboxes/gallery** — `CardMediaLightbox`, `AttachmentLightbox`,
   `CardLightboxViewer`, `AttachmentThumbnail` all promoted to beans as described above; new
   `ATTACHMENT_LIGHTBOX_*_TOOLTIP` i18n keys added (these buttons previously had no tooltip at
   all).
4. **`AuditActivityRowRenderer`**'s restore button → `UiTertiaryButton` + `.addThemeVariants
   (LUMO_SMALL)` layered on top (the established "wrapper + one extra variant" technique, same as
   `ConfirmActionDialog`'s `LUMO_ERROR` layering); `UserPickerField`'s clear/open buttons →
   `UiIconButton`, with two new tooltip keys (`USER_PICKER_CLEAR_TOOLTIP`/`_OPEN_TOOLTIP`) since
   neither button had one before.

**Deliberately excluded, each for a documented reason:**
- `query/elements/action/QueryActionButton.java` — a legitimate sibling wrapper (its own
  `Configurable` component, SVG icons, `ButtonVariant` passed as an explicit parameter) used only
  by `QueryActionBlock`. Architecturally inconsistent with the `Ui*Button` family
  (variant-as-parameter vs. variant-hardcoded-per-class) but that's a design-unification question,
  not raw-`Button` duplication.
- `services/NotificationService.java`'s close button — a plain `@Service`, not a Vaadin bean, with
  a raw Lumo font glyph icon (not `VaadinIcon`); converting would mean a non-UI-scoped service
  reaching into `UiComponentFactory` machinery and would visibly change the rendered icon, not a
  pure refactor. Extracted to its own decision issue:
  [improvement-057](../backlog/issues/improvement-057-notificationservice-close-button-decision.md).
- `UserPickerField`'s inline search button (a `TextField` suffix component using
  `LUMO_TERTIARY_INLINE`, a variant `UiIconButton` doesn't support) — forcing it through
  `UiIconButton`'s hardcoded `LUMO_TERTIARY+LUMO_ICON` baseline risked a visual regression for zero
  UX benefit. Extracted, along with an unrelated pagination-correctness bug found in the same file
  while scoping this batch, to
  [improvement-056](../backlog/issues/improvement-056-userpickerfield-inline-button-gap-and-pagination-bug.md).
- `AttachmentUploadButton` — wraps Vaadin's `Upload` component, a materially different widget, not
  a `Button` at all.

**Regression found and fixed along the way:** adding a real tooltip to `UserPickerField`'s clear
button (previously it had none) broke a shared Playwright helper,
`e2e/_flows/filter.flow.js`'s `clearFilter()`/`applyFilter()` — both used
`${blockSelector} vaadin-button[title*="Clear"/"Apply"]`, a selector scoped to the whole query
block, not just the block's own Apply/Clear buttons. Once the picker's clear button also carried a
"Clear"-containing title, the selector matched two elements (strict-mode violation) inside
`TimelineQueryBlock` specifically (the only query block that embeds a `UserPickerField`). Fixed by
scoping both helpers to `.query-action-block` — the CSS class `QueryActionBlock` already applies to
itself around its own Apply/Clear buttons — rather than the wider block selector.

**Consequences:**
- All four batches verified independently with a full `deploy.sh` + `bash scripts/playwright.sh
  e2e --full --ux` run — 48/48 green each time.
- `marketplace-app/CLAUDE.md`'s "Configurable prototype beans" pattern now has three more
  real-world instances of the "promote a plain class to a bean solely to inject a `UiComponentFactory`"
  shape — if [improvement-025](../backlog/issues/improvement-025-leaf-ui-components-plain-classes.md)
  (leaf widgets → plain classes) is picked up later, these three new beans
  (`AttachmentLightbox`/`CardLightboxViewer`/`AttachmentThumbnail`) become new candidates for
  reverting back to plain classes once `UiIconButton` itself stops being a Spring bean — flagged
  directly in that issue's own investigation notes, not lost.
- → [improvement-026-duplicate-raw-buttons-instead-of-ui-button-wrappers](../backlog/completed/issues/improvement-026-duplicate-raw-buttons-instead-of-ui-button-wrappers.md).

---

## ADR-038: Theme CSS colors tokenized into named custom properties — fixes the WCAG AA contrast failure and builds the prerequisite dark-mode needs

**Status:** Accepted

**Context:** `marketplace-app/src/main/frontend/themes/my-app/` (21 CSS files) used hardcoded hex
color literals throughout — 49 unique values, ~180 occurrences, the same handful of colors
copy-pasted into 9-15 different files each, zero `:root`/custom-property infrastructure. Two
concrete problems, filed as improvement-037 (accessibility) and improvement-039 (dark mode):
1. `.header-auth-row span` used `#94a3b8` (~2.5:1 contrast on white) — fails WCAG AA's 4.5:1
   minimum for body text.
2. Dark mode is structurally impossible without a token layer — Lumo ships a dark palette, but
   only components already using `var(--lumo-*)`/custom-property tokens pick it up automatically;
   swapping 180 hardcoded literals in place isn't a real option.

Both issues' own text called out that they share the exact same prerequisite (hardcoded hex →
named tokens) and recommended doing both in one pass over the theme files rather than two separate
touches of the same CSS. improvement-039's actual dark-mode toggle (`prefers-color-scheme` +
switcher UI) is explicitly deferred to a later pass — this ADR covers only the token
infrastructure + the accessibility fix riding on top of it.

**Decision:** Every hex value was named as a semantic custom property in a new `:root` block in
`styles.css` (`--app-*` prefix, to stay visually distinct from Lumo's own `--lumo-*` tokens), then
every occurrence across all 21 files was replaced with `var(--app-*)`. Values map almost exactly
onto Tailwind's default palette (`#94a3b8` = slate-400, `#3b82f6` = blue-500, etc.) — not a
coincidence, the app's colors were already implicitly drawn from that palette; tokenizing just
gives the existing (if undocumented) design system real names instead of inventing a new one.

Token groups, by role (full list with inline comments: `styles.css` lines 41-118):
- **Text** (`--app-text-primary/-secondary/-tertiary/-muted`) — 4-tier slate text hierarchy.
- **Borders/surfaces** (`--app-border-*`, `--app-surface-*`) — dividers, card/page backgrounds.
- **Primary accent** (`--app-accent-primary*`, 10 shades) — the blue family: links, focus
  outlines, query-block chrome, active tab.
- **Gallery accent** (`--app-accent-gallery*`, 6 shades) — indigo, `attachment-gallery.css` only.
- **Violet accent** (`--app-accent-violet*`) — the `USER_SETTINGS` activity-type badge and one
  card gradient partner.
- **Status colors** — separate bg/text token pairs per badge kind: success (created action,
  active user role), info (updated action, reuses `--app-accent-primary-bg`), the
  `advertisement` entity-type pill, restored action, moderator role, danger (deleted action).

**The WCAG fix, specifically:** `--app-text-muted` is defined as `#64748b` (the color that was
*already* used, and already measured ~4.76:1 — passes AA with margin) and now serves **both** the
former `#64748b` call sites and the former `#94a3b8` (failing) call sites — the two were
functionally the same "de-emphasized text" role, just drifted to two different grays over time.
Merging them into one compliant value is not a workaround; it's the actual fix — you cannot keep a
visually-distinct "extra light" gray tier that also passes 4.5:1, `#94a3b8` was failing precisely
*because* it was lighter than the compliant tier. This was the **only** value intentionally
changed during tokenization — every other token preserves its exact prior hex value, so the rest
of this refactor has zero visual delta by construction.

**RGB-triplet companion tokens:** three colors (`--app-accent-primary`, `--app-status-warning-accent`,
`--app-status-danger-outline`) also appeared as `rgba(r,g,b,alpha)` literals for shadow/highlight
glows (`highlight.css`, query-block focus rings). Added `--app-accent-primary-rgb: 59, 130, 246`-style
companions (raw comma-separated channels, since `var()` can't extract channels out of a hex
custom property) so those `rgba()` calls became `rgba(var(--app-accent-primary-rgb), 0.07)` instead
of a second, disconnected copy of the same color.

**Deliberately left as literals (documented scope boundary, not an oversight):**
- Pure black/white shadow and overlay alphas (`rgba(0,0,0,0.04)` box-shadows,
  `rgba(0,0,0,0.92)` lightbox backdrop, `rgba(255,255,255,0.15)` lightbox control hover) — these
  are an elevation/shadow system, not brand colors; tokenizing two dozen distinct one-off alpha
  values (each hand-tuned for a specific shadow depth) would add indirection with no reuse value.
  Revisit only if/when the dark-mode toggle actually ships and shadows need to invert.
- `query-block.css:22`'s `rgba(239, 246, 255, 0.9)` — a near-white tint close to but not identical
  to `--app-accent-primary-bg-hover`, single occurrence, not worth a dedicated token.

**Consequences:**
- `styles.css`'s `:root` block is now the single source of truth for every brand/status color in
  the app — changing a color means editing one line, not grep-and-replace across up to 15 files.
- improvement-039 (dark mode) is now unblocked at the infrastructure level: a dark palette is a
  second `:root` (or `[data-theme="dark"]`) block redefining the same custom-property names — no
  further CSS file touches required for the color layer itself.
- Verified via full `deploy.sh` + `bash scripts/playwright.sh e2e --full --ux` — a pure CSS-value
  substitution carries no logic-level regression risk, but the file count (21) and occurrence
  count (~180) made a full visual smoke pass worth doing rather than assuming.
- improvement-037's two remaining items (not part of the tokenization itself, done as a follow-up
  in the same pass) — `UiIconButton.configure()` now sets `aria-label` alongside `title` (an
  icon-only button's `title` attribute alone isn't a reliably announced accessible name across
  screen readers), which fixes every icon-only button app-wide in one shared-component edit
  (lightbox prev/next/close, pagination nav, attachment-thumbnail delete, `UserPickerField`
  clear/open) rather than per-call-site; the category chip list
  (`AdvertisementViewOverlayModeHandler`) got `role="list"`/`role="listitem"` +
  `aria-label` for correct screen-reader grouping (no "city chips" exist in the codebase yet — that
  feature is still on the private roadmap, nothing to fix there); a `.primary-button/.tertiary-button
  /.icon-button:focus-visible` rule was added to `styles.css`, matching the pre-existing
  `.advertisement-card:focus-visible` treatment, since no explicit focus style existed for the
  `Ui*Button` family before. Full e2e suite re-verified 48/48 after these additions.
- → [improvement-037-accessibility-contrast-and-aria](../backlog/completed/issues/improvement-037-accessibility-contrast-and-aria.md)
  (done), [improvement-039-dark-mode-lumo-tokens](../backlog/issues/improvement-039-dark-mode-lumo-tokens.md)
  (still open — only its own prerequisite shipped here).

---

## ADR-039: Maven Enforcer — dependency hygiene enforced at build time, starter-to-starter ban activated per-starter not at the root

**Status:** Accepted

**Context:** Several dependency-hygiene rules were convention-only, checked by human review: no
direct starter→starter Maven dependencies (the module import rule from `.claude/rules.md`), no
guard against conflicting transitive dependency versions across the reactor, no enforced minimum
Java/Maven version at build time. Filed as improvement-031.

**Decision:**
- Root `pom.xml`: `maven-enforcer-plugin` added to an active `<build><plugins>` block (inherited by
  every module) with `dependencyConvergence`, `requireJavaVersion` (`[25,)`), and
  `requireMavenVersion` (`[3.9,)`).
- The starter→starter ban (`bannedDependencies`) is **not** in the root's active plugins — a
  project-wide ban would also apply to `marketplace-app`/`integration-tests`, which legitimately
  depend on starters. Instead, each of the 5 starter poms
  (`audit-`/`attachment-`/`user-`/`advertisement-`/`taxon-spring-boot-starter`) activates the same
  rule individually in its own `<build><plugins>`, banning the other 4 starter artifacts.

**Found and fixed while turning this on (not just theoretical):**
1. `advertisement-spring-boot-starter/pom.xml` declared `audit-spring-boot-starter` and
   `attachment-spring-boot-starter` as `<optional>true</optional>` Maven dependencies with **zero**
   Java source anywhere in the module importing from either (`org.ost.audit.*`/`org.ost.attachment.*`
   — confirmed via full-module grep before removing). Vestigial cruft, removed; all real
   cross-starter wiring already went through `platform-commons`' SPI types via `ComponentFactory<T>`,
   which needed no Maven dependency at all.
2. `dependencyConvergence` immediately caught a real, previously invisible conflict:
   `liquibase-core:5.0.2` depends on `commons-text:1.15.0` directly, and also on
   `opencsv:5.12.0` → `commons-text:1.13.1` transitively — two versions of the same artifact
   resolving inconsistently depending on classpath order. Pinned to `1.15.0` (matching
   liquibase-core's own direct requirement) in the root `dependencyManagement`.

**Follow-up (2026-07-16, improvement-059):** the same rule caught a second, equivalent
`commons-io` conflict once something actually ran `mvn -pl integration-tests test` for the first
time (`testcontainers`'s transitive `2.20.0` vs. `liquibase-core`'s transitive `2.21.0`) —
`liquibase-core` bumped to `5.0.3` (from `5.0.2` above) and `commons-io` pinned to `2.22.0` to
match. Full rationale in `scripts/ci/DECISIONS.md` ADR-001.

**Consequences:**
- A future starter→starter Maven dependency (optional or not) now fails the build immediately,
  not just a code-review catch.
- Verified via full `deploy.sh --no-cache` (all 5 starters + marketplace-app rebuild from empty
  cache, exercising every enforcer rule) + `bash scripts/playwright.sh e2e --full --ux`, 48/48.
- → [improvement-031-maven-enforcer-plugin](../backlog/completed/issues/improvement-031-maven-enforcer-plugin.md).

---

## ADR-040: `NotificationService`'s close button converted to `UiIconButton` despite being a plain `@Service`, not a Vaadin bean

**Status:** Accepted

**Context:** `NotificationService.createLayout()`'s close button was the one raw-`Button` spot
improvement-026 deliberately excluded from its mechanical conversion, because
`NotificationService` is a plain `@Service` (not a `@SpringComponent` Vaadin bean like every other
`Ui*Button` consumer) and its icon was a raw Lumo font glyph (`new Icon("lumo", "cross")`, not
`VaadinIcon`) — a conversion would be a small visible icon change, not a pure refactor. Filed as
improvement-057 to decide explicitly rather than leave unresolved.

**Decision:** Convert anyway, in favor of full consistency over the narrow exception. Neither
concern held up as a real blocker on inspection:
- No rule in this codebase (`.claude/rules.md`, `platform-commons/CLAUDE.md`) forbids a `@Service`
  from depending on `UiComponentFactory<T>` — it simply hadn't been done before.
  `NotificationService` already lives in the UI-layer package (`ui.views.services`), so a
  singleton service reaching into `UiComponentFactory<UiIconButton>` (itself backed by
  `ObjectProvider<UiIconButton>`, exactly the mechanism Spring provides for a singleton to obtain
  fresh prototype-scoped instances) is architecturally unremarkable, not a scope violation.
- The icon swap (Lumo glyph → `VaadinIcon.CLOSE_SMALL`) is visually negligible — an "X" in either
  icon set — and now every icon-only button in the app renders through the same wrapper, with no
  documented exception for a future `improvement-030` (ArchUnit) rule to special-case.

New `NOTIFICATION_CLOSE_TOOLTIP` i18n key added (the raw glyph never had a tooltip/aria-label
before; `UiIconButton.configure()` now sets both `title` and `aria-label` per ADR-037's
improvement-037 follow-up).

**Follow-up fix (2026-07-16, found via direct visual screenshot inspection, not caught by any
Playwright assertion):** `UiIconButton`'s default tertiary-theme icon color is Lumo's primary
blue (`rgb(0, 95, 219)`) — correct on the white/neutral surfaces every other `UiIconButton`
consumer renders on, but wrong here: `Notification`'s `LUMO_ERROR`/`LUMO_SUCCESS` variants render
white text (`rgb(255, 255, 255)`, confirmed via computed-style inspection) on red/green
backgrounds, so the close icon rendered as a mismatched blue glyph on a colored background.
Added `.notification-close-btn { color: var(--app-surface-white); }` in a new
`notification.css` (the `notification-*` CSS classes had no theme file at all before this —
purely inline/inherited styling). Confirmed visually via direct screenshot before and after.

**Consequences:**
- Zero remaining raw `new Button(...)` spots from the original improvement-026 audit — every
  identified spot is now either converted or explicitly, permanently out of scope
  (`QueryActionButton`, `AttachmentUploadButton` — see improvement-026's own "Do NOT touch" list).
- Verified via full `deploy.sh` + `bash scripts/playwright.sh e2e --full --ux`, 48/48 — the two
  existing Playwright selectors touching the notification close button
  (`e2e/_helpers.js`'s `closeNotification()`, `_flows/category.flow.js`) both use the generic
  `vaadin-notification-card vaadin-button` pattern, unaffected by the icon/class change.
- → [improvement-057-notificationservice-close-button-decision](../backlog/completed/issues/improvement-057-notificationservice-close-button-decision.md).

---

## ADR-041: ArchUnit codifies cross-module architecture rules — lives in `marketplace-app/src/test`, not a new module

**Status:** Accepted

**Context:** Every cross-module and intra-module architecture rule in this project (module import
restrictions, no-Vaadin-in-starters, Port/Hook placement, `@PreAuthorize` placement, `Optional`-
parameter ban, `config` vs `configuration` naming, `*PortImpl`/`*HookImpl` delegation-only) lived
as prose in `CLAUDE.md`/`rules.md`, checked by human review only. Two real violations
(improvement-011's unguarded port injection, improvement-010's view deviating from its own
`refresh()` pattern) reached working code before being caught by manual audit rather than a build
failure.

**Decision:** Added ArchUnit (`com.tngtech.archunit:archunit-junit5:1.4.2`, test-scope) directly to
`marketplace-app`'s existing test tree — `marketplace-app/src/test/java/org/ost/marketplace/
architecture/ArchitectureRulesTest.java` — rather than a new dedicated module. `marketplace-app`
already depends on every starter + `platform-commons` + `query-lib` (confirmed directly), so its
test classpath already has every compiled class ArchUnit needs to check cross-module rules (e.g.
"no Vaadin in starters" needs starter classes; "UI must not call repositories directly" needs
marketplace-app's own `ui` package) — no new module, no new dependency wiring. This also means
these checks run automatically as part of the existing `scripts/unit-tests.sh`/`scripts/ci.sh
--unit` stage, with zero new script/CI plumbing.

Seven rules implemented, mapping directly to the seven prose rules identified in improvement-030:
UI-must-not-call-repositories, no-Vaadin-in-starters, Ports/Hooks-live-only-in-platform-commons
(split into two separate rules, one per suffix, rather than one combined `.or()` rule — simpler
and less error-prone than chaining ArchUnit's fluent predicate combinators), no-class-level-
`@PreAuthorize`-on-services, no-`Optional`-method-parameters (a custom `ArchCondition` — ArchUnit
has no built-in predicate for this), no-`configuration`-packages, and `*PortImpl`/`*HookImpl`-
delegation-only (no `java.util.stream`/Jackson dependencies).

**The delegation-only rule needed no explicit exception list for `DefaultTaxonPort`/
`DefaultAuditPort`/`DefaultAttachmentPort`** (all three are documented, deliberate
coordination-layer exceptions — see e.g. `taxon-spring-boot-starter/CLAUDE.md`, and `DefaultTaxonPort`
does genuinely import `java.util.stream.Collectors`) — the rule only targets the `*PortImpl`/
`*HookImpl` simple-name suffix, and none of the three `Default*Port`-named classes match that
suffix. The existing `Default*Port` vs. `*PortImpl` naming convention
(`platform-commons/CLAUDE.md` "Hook and Port Implementation Rules") already draws exactly the
line this rule needs, for free.

**Consequences:**
- All 7 rules (8 `@ArchTest` fields — Port/Hook split into two) pass cleanly against the current
  codebase on first run — the rules describe discipline already actually followed, now enforced by
  a build failure instead of a code-review judgment call. Verified via `bash scripts/unit-tests.sh
  ArchitectureRulesTest` (8/8 passed) and a full `bash scripts/unit-tests.sh` run (all suites still
  green) to confirm the new test-scope dependency didn't disturb anything else.
- → [improvement-030-archunit-test-module](../backlog/completed/issues/improvement-030-archunit-test-module.md).

---

## ADR-042: `UserPickerField` gains an offset-based lookup (`OffsetPageable`) instead of deriving a page number from Vaadin's raw offset

**Status:** Accepted

**Context:** `UserPickerField` is the only place in `marketplace-app` using Vaadin's native lazy
`CallbackDataProvider`/`DataProvider.fromFilteringCallbacks` — every other grid uses the app's own
`PaginationBar` with explicit page-state tracking. Its data provider derived a page number from
Vaadin's raw row offset via `query.getOffset() / query.getLimit()`, which is only correct when the
offset happens to be an exact multiple of the limit — not guaranteed by Vaadin's `Grid`/
`DataCommunicator` under fast/jump scrolling. A non-page-aligned fetch (e.g. `offset=137,
limit=50`) silently computed the wrong page, returning duplicated, skipped, or wrong rows. Went
untriggered by existing Playwright coverage because the seed spec used exactly 50 users, matching
Vaadin `Grid`'s default page size — the entire result set fit in one always-aligned fetch
(`offset=0`). Filed as improvement-056 alongside a related, smaller gap: the picker's inline
search button used a raw `Button` instead of `UiIconButton`, since `UiIconButton` had no variant
for Vaadin's `LUMO_TERTIARY_INLINE` (the suffix/prefix-slot button treatment).

**Decision:**
- Added `OffsetPageable` (`query-lib/src/main/java/org/ost/query/sort/OffsetPageable.java`) — a
  `record` implementing Spring Data's `Pageable` directly, carrying an arbitrary raw `offset`
  instead of deriving `offset = page * size` the way `PageRequest.of(page, size)` always does.
  Chosen over reworking `UserPort.getFiltered()`'s existing page-based contract (used by every
  `PaginationBar`-style caller) — introducing a second, offset-shaped `Pageable` implementation
  is additive and keeps the page-based contract untouched for its existing callers.
- Added `UserPort.getFilteredByOffset(filter, offset, limit, sort)`, delegating through
  `UserPortImpl` → `UserService.getFilteredByOffset()` → `UserRepository.findByFilter(filter, new
  OffsetPageable(offset, limit, sort))`. The repository itself needed **no changes** — its SQL
  already used `pageable.getOffset()` (via `PaginationSqlBuilder.pageLimit()` →
  `LIMIT :limit OFFSET :offset`), so it was already offset-correct; only the `Pageable` it
  received was wrong.
- `UserPickerField`'s data provider now calls `userPort.getFilteredByOffset(filter,
  query.getOffset(), query.getLimit(), sort)` directly — no more `offset/limit` division.
- `UiIconButton.Parameters` gained a `boolean inline` field (default `false`); `configure()` now
  applies `LUMO_TERTIARY_INLINE` instead of `LUMO_TERTIARY` when `inline` is set, moved out of
  `init()` (which previously hardcoded the non-inline variant unconditionally). The picker's
  search button now goes through `UiIconButton` with `inline(true)`.

**Verification:** the existing Playwright seed volume (50 users) could never have exercised the
bug (see Context) — bumped `05-seed-filter-sort-pagination.spec.js`'s `SEED_COUNT` from 50 to 60
specifically so the picker's underlying grid needs a second Vaadin-internal data-provider page,
and retargeted the timeline actor-filter test to pick a name-sorted user past the first page
(`'Seed User 60'` instead of `adminEn`, who previously sorted within the first page and never
exercised the bug either). `fillActorPicker` in `timeline.flow.js` gained a grid-scroll step
(`grid.scrollToIndex(...)`) to actually reach that row. Full `bash scripts/playwright.sh e2e --full
--ux` run: 48/48 passed, confirming both the fix and that the seed-count bump didn't regress any
existing pagination assertion (`verifyPagination()`'s hardcoded 3-page structure, `1-20`/`21-40`/
`41-{total}`, holds for 60 exactly as cleanly as for 50 — 60 divides evenly into three full pages
of 20 rather than 50's partial third page of 10).

**Consequences:**
- `OffsetPageable` is a new, general-purpose `query-lib` type available to any future caller that
  needs to pass a Vaadin-native (or otherwise raw) offset through to a `Pageable`-based repository
  method without page-number derivation.
- → [improvement-056-userpickerfield-inline-button-gap-and-pagination-bug](../backlog/completed/issues/improvement-056-userpickerfield-inline-button-gap-and-pagination-bug.md).

---

## ADR-043: Timeline tab resolves category names via a typed `prevSnapshotData` on `AuditTimelineItemDto`; `ChangeEntry.replaceIfField()` consolidates the one unavoidable type check

**Status:** Accepted

**Context:** The Timeline tab (global activity feed) rendered category changes as raw taxon ids
(`Category: 3, 5 → 3, 5, 7`), while the per-advertisement Activity tab already resolved the same
data to names (`Category: Electronics, Books → ...`) via `AdvertisementEnrichService
.enrichActivityItems()`. Root cause: `AuditTimelineItemDto` (unlike `AuditActivityItemDto`) carried
only the current snapshot, not the previous one — `AuditReadService.toTimelineItem()` computed the
diff via `row.snapshot().diff(row.prevSnapshot())` but only passed `row.snapshot()` into the DTO,
discarding `row.prevSnapshot()` even though `AuditLogProjection` already had it.

**Decision:**
- Added `T prevSnapshotData` to `AuditTimelineItemDto` (`platform-commons`) and populated it from
  `AuditLogProjection.prevSnapshot()` (already available, previously unused) in
  `AuditReadService.toTimelineItem()`. Only two constructor call sites existed for this record
  (`toTimelineItem()` and `withChanges()`), both updated — no other consumer constructs it.
- `AdvertisementEnrichService.mergeMediaChanges()` (Timeline) and `enrichActivityItems()` (Activity)
  now share the same fully-typed `resolveCategories()` helper, reading `List<Long>` directly from
  both DTOs' `categoryIds()` — no string parsing of the diff's own rendered values (an earlier,
  rejected draft parsed `ChangeEntry.FieldChange`'s `from`/`to` strings via
  `split(",\\s*")`; discarded once the DTO gained a typed previous snapshot, making that
  unnecessary).
- `AdvertisementEnrichService`'s `mergeMediaChanges`/`enrichActivityItems` dropped unused
  parameters (`List<EntityRef> subjects`, `EntityRef entityRef`) that only existed to mirror
  `AuditActivityEnrichHook`'s interface signature — they are plain internal service methods, not
  the hook's own override (that's `ActivityEnrichHookImpl`, which still needs the full interface
  signature and now simply doesn't forward the unused arguments).
- `ChangeEntry.replaceIfField(String fieldName, UnaryOperator<String> fromFn, UnaryOperator<String>
  toFn)` — a new default method on the `ChangeEntry` sealed interface — consolidates the one
  instanceof-check on `FieldChange` that Java's type erasure makes genuinely unavoidable (some
  identifying key — here, the existing field-name constant already used everywhere else in this
  codebase for the same purpose — is required to know *which* generic `FieldChange` to transform).
  Every consumer needing to conditionally rewrite one field's value calls this instead of writing
  its own `instanceof`/`switch`; it is now the only such check in the entire codebase.
  `AdvertisementEnrichService.resolveCategories()` uses it instead of a hand-rolled pattern match.

**Rejected alternatives (considered and discarded during design):**
- A new sealed `ChangeEntry` subtype (e.g. `CategoryFieldChange`) carrying typed `List<Long>`
  values directly, matched via `instanceof CategoryFieldChange` instead of a field-name string.
  Rejected: `ChangeEntry` has several **exhaustive** switches with no `default` branch across
  `AuditChangeFormatter` and `AuditTimelineRowRenderer` (rendering code for every entity type, not
  just advertisements) — adding a third permitted subtype would require a new case in each one,
  a "sealed tax" repeating for every future field needing the same treatment. Also would have
  required carrying the `T`-erased value out of the entry itself, reintroducing exactly the
  unchecked cast this design avoids.
- A generic `FieldChange<T>` carrying a `ValueFormatter<T>` alongside typed `from`/`to`. Same
  rendering-code blast radius as above, plus every renderer would need to call `.fromDisplay()`/
  `.toDisplay()` instead of pattern-matching `from`/`to` directly — a larger, orthogonal refactor
  of code this fix didn't need to touch.

**Consequences:**
- `TaxonAuditHook` (SPI) removed entirely, along with `TaxonPort.assign()`/`unassign()`/
  `findByCode()`, `TaxonService.findByCode()`, and `TaxonRepository.findByTypeAndCode()` — all
  confirmed zero callers via direct trace, not assumption. See ADR-019's updated resolution note
  and `taxon-spring-boot-starter/DECISIONS.md` ADR-004 (marked superseded).
- `integration-tests/.../taxon/TaxonRepositoryTest`'s `findByTypeAndCode_*` tests removed along
  with the repository method they covered (a deliberate soft-delete-filter regression test from
  improvement-045, item 4/5 — traded for keeping the method-removal clean per explicit direction,
  not an oversight).
- → [improvement-058-taxon-assignment-audit-trail-missing](../backlog/issues/improvement-058-taxon-assignment-audit-trail-missing.md)
  (not yet closed — pending Playwright verification that the Timeline tab now shows resolved
  category names).

---

## ADR-044: `UserSettingsRepository` optimistic-locking version lives inside the `settings` JSONB blob, not a new SQL column

**Status:** Accepted

**Context:** `UserSettingsRepository.save()` had no conflict detection at all — a bare
`UPDATE user_information SET settings = :settings::jsonb WHERE id = :userId`, unlike every other
mutable entity in this codebase (`Advertisement`, `Taxon`, `user_information` name/role via
`UserProfileUpdate` — all `@Version`-column optimistic locking per ADR-029). Two browser tabs of
the same user editing settings would silently clobber each other: the second tab's save overwrites
the *entire* JSONB blob with its own stale copy, discarding whatever the first tab's save had just
written, with no error and no conflict notification. Traced the full call chain
(`SettingsOverlay` → `AuthContextService.getCurrentUser()`) and confirmed there is exactly one
write path (the current user's own settings, via the header settings icon) — no admin-edits-
another-user's-settings path, no background writer — so the only realistic conflict is the same
user's own concurrent tabs. → [improvement-066](../backlog/issues/improvement-066-usersettingsrepository-missing-version-check.md).

**Decision:**
- `UserSettingsDto` (`platform-commons`) gained a `long version` field. `UserSettingsRepository`
  already serializes/deserializes the *entire* DTO directly into/from the `settings` JSONB column
  (`mapper.writeValueAsString`/`readValue`), so the version travels for free through the same
  round-trip — no new SQL column, no entity restructuring.
- `save()` increments `version` on the DTO being written and adds
  `AND (settings->>'version')::bigint = :expectedVersion` to the `UPDATE`'s `WHERE` clause (Postgres
  JSONB `->>` operator extracts the field as text, cast to `bigint` for the comparison); 0 affected
  rows throws `OptimisticLockingFailureException` — same exception type and UI conflict-handling
  path ADR-029 already standardized on, so no new UI code was needed for the failure case itself.
- UI (`SettingsEditDto`, `SettingsFormModeHandler`) threads `version` through every lifecycle path
  (`activate`, `save`, `discardChanges`, `handleRestoreFromActivity`, `loadRestored`) so it is never
  silently re-derived from a stale read mid-flow — the same discipline ADR-029 requires for
  `Advertisement.buildEntity()`/`Taxon.update()`. `handleRestoreFromActivity()` is the one
  deliberate exception: it fetches the *current* DB version via `userPort.loadSettings()` rather
  than trusting the audit snapshot (which predates this field and carries no version), since a
  restore only stages values into the form — the eventual save still has to check against whatever
  is actually current in the row, not what the snapshot happened to hold.
- Schema default (`01-user-schema.xml`'s `settings` column) updated to
  `{"adsPageSize":20,"usersPageSize":20,"timelinePageSize":20,"version":0}` so freshly-registered
  users start at version 0 rather than a missing JSON key (which would make
  `(settings->>'version')::bigint` never match any expected value on a user's first save).

**Rejected alternatives:**
- **A dedicated new SQL column** (e.g. `settings_version BIGINT`), matching the shape of a real
  `@Version` field. Rejected as unnecessary ceremony: it would require restructuring
  `UserSettingsRepository` to read/write the column separately from the JSONB blob, when the whole
  point of that repository is that it treats `settings` as one opaque serialized DTO.
- **Reusing the row's existing `user_information.version`** (the one `UserProfileUpdate`/
  `UserProfileCrudRepository` already uses for name/role edits). Rejected: it would couple two
  functionally independent parts of the same row — a settings save in one tab and a profile-name
  edit in another tab would spuriously conflict with each other, which is not a real data race
  (they touch disjoint columns) and would surface confusing, unwarranted conflict errors.
- **A formal new Liquibase changeset** (`02-user-settings-default-version`) to fix the live dev
  database's column default, mirroring how every other schema change in this codebase is applied.
  Explicitly skipped per direct instruction, since this application is not yet in production — the
  dev DB's actual column default was fixed directly via a one-off `ALTER TABLE ... ALTER COLUMN
  settings SET DEFAULT ...`. **A proper Liquibase changeset is still required before any real
  deploy** — editing `01-user-schema.xml`'s `defaultValue` (even with `validCheckSum ANY`) has no
  effect on an already-migrated database; Liquibase does not re-execute a changeset once applied,
  it only suppresses the checksum-mismatch error on future runs.

**Consequences:**
- No new SQL column, no new Liquibase changeset (see rejected alternative above — a real gap, not
  an oversight, tracked for whenever this app approaches a production deploy).
- `integration-tests/.../user/UserSettingsRepositoryTest` (new) covers fresh-user version-0 start,
  stale-version save throwing `OptimisticLockingFailureException`, and current-version save
  succeeding + incrementing — verified against real Postgres.

---

## ADR-045: New `QueryLongField` (text-backed) replaces `NumberField` (`Double`-backed) for the user id range filter — neither Vaadin numeric field type actually fits a `Long`

**Status:** Accepted

**Context:** `UserFilterMeta.ID_MIN`/`ID_MAX` used Vaadin's `NumberField` (`Double`-backed) with
`SupportUtil.toLong(Double)` (`value.longValue()`) converting to the `Long` the filter DTO actually
needs. Entering a fractional value (e.g. `123.99`) was silently truncated to `123` with no
validation error — a real, silent filter-correctness bug, since the truncated `Long` is what the
UI's own conversion pipeline (`FilterProcessor.register()`'s value-change listener calls the
setter, which runs `toLong()`, *before* any validation predicate ever runs against the DTO) writes
into the DTO. The originating issue's own suggested fix — add a whole-number check to the DTO-level
`idValid` predicate — turned out to be structurally impossible: that predicate only ever sees the
already-truncated `Long` field on the DTO, by which point the fractional part is already gone.
→ [improvement-061](../backlog/completed/issues/improvement-061-supportutil-tolong-silent-truncation-id-filter.md).

**Decision:** Neither of Vaadin's built-in numeric field types is actually the right fit for a
`user_information.id` (`BIGSERIAL`, a true 64-bit `Long`) filter: `NumberField` is `Double`-backed
(safe integer range ~53 bits, plus the truncation-on-convert problem above), and `IntegerField` is
32-bit — narrower than the real column's range, an artificial UI-level ceiling below what the
schema actually allows (confirmed every domain table in this project uses `BIGSERIAL`, not
`SERIAL`, consistently — this isn't a `user`-specific choice to special-case around). Vaadin has no
built-in `Long`-backed field. New `org.ost.marketplace.ui.query.elements.fields.QueryLongField`
(mirrors `QueryNumberField`'s `Configurable` structure exactly, but extends `TextField` instead) —
raw text parsed directly via `SupportUtil.toLongOrNull(String)` (`Long.parseLong`, catches
`NumberFormatException` → `null`). Un-parseable input (fractional, non-numeric) is flagged via the
component's own native `setInvalid`/`setErrorMessage` (Vaadin's `HasValidation`), independent of —
and confirmed not conflicting with — `HighlighterUtil`'s separate dirty/changed CSS-class styling
(`resetHighlightClasses()` only touches `CLEAN`/`DIRTY`/`CHANGED`/`INVALID` class names, never
Vaadin's own `invalid` attribute). `SupportUtil.toLong(Double)` removed outright — confirmed zero
other callers before deleting.

**Rejected alternative — `IntegerField` swap.** Considered first (matches the originating issue's
own "alternative considered"), but rejected once the actual `id` column type was checked: 32-bit
would be a real, if narrow, regression against the 64-bit `BIGSERIAL` range the filter is
supposed to express, for no benefit over the text-field approach.

**Consequences:**
- No `Double` anywhere in the id-filter pipeline anymore — component, setter, and DTO are all
  `String`/`Long` end to end.
- New `UiComponentFactory<QueryLongField>` bean (`MarketplaceUiConfiguration`) and `@Uses` (`MainView`),
  mirroring `QueryNumberField`'s exact wiring — `QueryLongField` is currently only used by
  `UserQueryBlock`'s two id fields, but is a generic, reusable component like its siblings.
- New `I18nKey.USER_FILTER_ID_INVALID_NUMBER` ("Must be a whole number" / uk equivalent).
- Covered by `SupportUtilTest` (8 cases: whole number, negative, whitespace-trimmed, fractional →
  null, non-numeric → null, blank → null, null → null, beyond-`Long`-range → null) and a new
  Playwright assertion in spec 05 (`05-seed-filter-sort-pagination.spec.js`): typing `1.5` into the
  ID-min field sets the Vaadin `invalid` attribute, typing `1` clears it. Full suite verified 48/48
  green.

→ [goal-001-activity-field-visibility-by-role](../backlog/issues/goal-001-activity-field-visibility-by-role.md)
