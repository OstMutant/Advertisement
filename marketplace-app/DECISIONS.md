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
**Status:** Accepted

**Context:** Rapid double-click submits the form twice, causing duplicate saves.

**Decision:** Save buttons in `AdvertisementFormOverlayModeHandler`, `UserFormOverlayModeHandler`,
and `SettingsOverlay` are guarded with `setEnabled(false)` at the start of the click listener
and re-enabled in a `finally` block.

**Consequences:** The guard lives in `activate()` of the FormModeHandler (not in the Overlay)
because the save button is a Spring-injected field of the handler.

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
entry's snapshot (`getSnapshotContent`). `getPreviousSnapshotContent` is reserved for diff
display only.

**Consequences:** All restore flows (`UserOverlay.handleRestoreUser`,
`SettingsOverlay.loadAndShowSettingsRestore`, `AdvertisementService.restoreToSnapshot`) must
call `AuditPort.getSnapshotContent(snapshotId, entityType)`.

---

## ADR-011: AuditSnapshotBinder used directly — no AuditUiPort
**Status:** Accepted

**Context:** `AuditUiPort` was removed 2026-06-15 as unnecessary indirection — all Vaadin UI
lives in marketplace-app, so there is no second consumer that would require the SPI.

**Decision:** `AuditSnapshotBinder` used directly in marketplace-app UI components.

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
class provides `final activate(OverlayLayout)` that assembles the tab layout; subclasses implement
five abstract methods: `tabsCssClass()`, `buildPrimaryTab()`, `buildPrimaryContent()`,
`buildSecondaryTab()`, `buildHeaderActions()`.

`SecondaryTabDef` record `(Tab tab, String cssClass, Supplier<Component> loader)` represents the
optional second tab. Returning `null` from `buildSecondaryTab()` produces a single-tab layout.

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

## ADR-017: Known decoupling debt — open boundary violations

**Architecture rule (2026-06-15):** marketplace-app UI is a monolith — decoupling is required
only at the service ↔ UI boundary (starters vs marketplace-app). Within marketplace-app, UI
components may reference each other freely. UI ports/hooks (`AuditUiPort`, `AttachmentGalleryPort`,
`AuditActivityRowHook`) were removed as unnecessary indirection.

### Open violations

### ✅ Resolved — attachment UI boundary violations (2026-06-26)

`MediaContentTypeUtil` merged into `AttachmentMediaContentType` enum (platform-commons). All attachment UI components now import only from `platform-commons`. No `org.ost.attachment.*` imports remain in marketplace-app.

→ [improvement-001-attachment-ui-boundary-violation](../features/completed/issues/improvement-001-attachment-ui-boundary-violation.md) (completed)

→ [improvement-004-accessevaluator-boundary-violation](../features/completed/issues/improvement-004-accessevaluator-boundary-violation.md) (completed)

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
- `TaxonAuditHook` (starter → marketplace) — fires when assignments change

Marketplace-app additions:
- `TaxonAuditHookImpl` → delegates to `TaxonActivityService.recordAssignmentChange()`
- `TaxonManagementView` + `TaxonOverlay` + `TaxonFormOverlayModeHandler` + `TaxonViewOverlayModeHandler`
- `ReferenceDataView` — tab container for taxon management (nested sub-tabs)
- `TaxonActivityService` in `services/audit/taxon/`

`TaxonType` enum (in `platform-commons`) — closed set: currently `CATEGORY`. Adding a new type
is a release-level change requiring UI, audit translations, and seed entries.

Advertisement filtering by category: `AdvertisementRepository` calls
`TaxonPort.findEntityIdsWithAnyTaxon()` to translate taxon ids into entity ids — no direct
join to `taxon_assignment` table from advertisement code.

**Consequences:**
- `EntityType.TAXON` added to `platform-commons` for audit records of taxon entity changes.
- `ReferenceDataView` added as a new top-level navigation tab with sub-tabs per taxon type.
- Taxon CRUD triggers `TaxonAuditHook.onAssignmentChanged()` from `TaxonAssignmentService`.
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
- `SaveConfig` record `(I18nKey success, I18nKey validFailed, I18nKey saveError)` declares error
  key mapping per overlay type.

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

**Decision:** A historical snapshot is considered "current state" when ALL of the following match:
- `title` — advertisement title
- `description` — advertisement body (rich HTML)
- `categories` — sorted, comma-separated names of assigned categories
- `media` — attachment filenames at that version (via `AuditActivityEnrichHook.matchesCurrent`)

If any of these differ, the "Restore" button is shown. If all are identical, the badge is shown.

`AdvertisementSnapshotDto` stores `categories` as a sorted, comma-joined string of category names
captured at save time (locale: English). `Objects.equals` on the full record covers title +
description + categories; `mediaMatchCurrent` via `AuditActivityEnrichHook` covers media.

Update (2026-07-03, snapshot-cleanup): `CategoryChangeSnapshotDto` and the
`AuditableSnapshot.isVisible()` mechanism were removed entirely — after
advertisement-snapshot-redesign no snapshot type ever returned `false`, making the visibility
filter in `AuditActivityPanel` a no-op. Category change information rides in the main
`AdvertisementSnapshotDto` diff; no row-hiding machinery exists anymore
(see `features/completed/snapshot-cleanup/SPEC.md`).

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
(`features/completed/issues/issue-description-length-tag-spam.md`). No server-side length
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
  remains open but is now unblocked — the counter and the validators agree on how "length" is
  measured.
- Any future field with the same "rich HTML but bounded visible text" shape should follow the
  same three-layer pattern rather than reaching for `@Size` directly on the HTML string.

---

## [OPEN GOAL] Activity field visibility — filter by viewer's role

→ [goal-001-activity-field-visibility-by-role](../features/issues/goal-001-activity-field-visibility-by-role.md)
