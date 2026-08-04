# Architecture & Technical Decisions — platform-commons

---

## ADR-001: Package restructure — core / audit / attachment / user / advertisement
**Status:** Accepted

**Context:** The old flat structure mixed audit, attachment, and shared types in `events.*`
with no clear ownership, making it impossible to tell which package belonged to which subsystem.

**Decision:** Three semantic groups (final layout after 2026-05-18 symmetry cleanup):

```
core            — ComponentFactory (top-level, not a sub-package)
core.config     — CleanupProperties
core.model      — ActionType, ChangeEntry, EntityType, EntityRef
core.spi        — CurrentActorHook
core.validation — ValidRange, ValidRangeValidator

audit.api      — AuditableSnapshot
audit.dto      — AuditActivityItemDto, AuditSnapshotContentDto, AuditTimelineItemDto, AuditTimelineFilterDto
audit.spi      — AuditPort, AuditDomainHook, AuditActivityFieldsHook, AuditActivityEnrichHook

attachment.dto     — AttachmentMediaSummaryDto, AttachmentItemDto, TempAttachmentDto
attachment.model   — AttachmentMediaContentType
attachment.spi     — AttachmentPort, AttachmentAuditPort (AttachmentMediaChangeHook removed, improvement-102;
                     AttachmentAuditHook renamed to AttachmentAuditPort, see ADR-025)
attachment.util    — YoutubeUtil

user.dto       — UserDto, UserFilterDto, UserProfileDto, UserSettingsDto,
                 UserSnapshotDto, SettingsSnapshotDto, SignUpDto
user.model     — Role
user.spi       — UserPort, AuthenticatedPrincipal, UserSettingsChangedHook, UserIdMarker
                 (UserIdMarker moved from user.security, see ADR-025)

advertisement.dto  — AdvertisementInfoDto, AdvertisementFilterDto,
                     AdvertisementSaveDto, AdvertisementSnapshotDto
advertisement.model — AdKind
advertisement.spi  — AdvertisementPort

taxon.dto      — TaxonDto, TaxonTranslationDto, TaxonSnapshotDto (CategoryChangeSnapshotDto deleted,
                 see this file's own entry below on the advertisement-snapshot redesign)
taxon.model    — TaxonType
taxon.spi      — TaxonPort
```

**Consequences:** `core.i18n`, `ui`, `attachment.event`, `attachment.storage` packages removed —
all i18n and UI contracts live in `marketplace-app`; storage lives in `attachment-spring-boot-starter`.
`taxon.*` packages added 2026-06-26 when `taxon-spring-boot-starter` was introduced (ADR-005 update).

---

## ADR-002: Package semantics — api vs spi vs dto
**Status:** Accepted

**Context:** Without a clear split, a reader opening `audit.spi` could find both callable service
facades and passive marker types — two very different things demanding different implementation strategies.

**Decision:** Each sub-package has a distinct role:
- `*.api` — contracts that **marketplace places on its own classes** so the starter can read them
  (marker interfaces, annotations). Only `audit.*` has an `api` package.
- `*.spi` — **extension-point interfaces** declaring a callback boundary between modules.
- `*.dto` — **pure data carriers** with no behavior, named with `Dto` suffix.

**Consequences:**
- Do not add behavior to `*.dto` classes.
- Do not add Spring annotations to `*.api` markers.
- Do not put data records in `*.spi`.

---

## ADR-003: SPI naming convention — Port and Hook suffixes
**Status:** Accepted

**Context:** The initial 7-suffix convention (`*Extension`, `*Consumer`, `*Provider`, `*Resolver`,
`*Checker`, `*Binding`) created too many distinctions with no practical difference in implementation
strategy.

**Decision:** Two suffixes encode the call direction:

| Suffix | Caller → Implementor | Semantic role |
|--------|---------------------|---------------|
| `*Port` | marketplace → starter | Service facade: marketplace issues commands/queries to the starter |
| `*Hook` | starter → marketplace | Starter calls back for domain data, events, or UI contributions |

Current `*Port` interfaces: `AuditPort`, `AttachmentPort`, `UserPort`, `AdvertisementPort`, `TaxonPort`.
Current `*Hook` interfaces: `CurrentActorHook`, `AuditDomainHook`, `AuditActivityFieldsHook`,
`AuditActivityEnrichHook`, `AttachmentMediaChangeHook`, `AttachmentAuditHook`, `UserSettingsChangedHook`.

**Consequences:**
- New suffixes require a DECISIONS.md entry. Existing suffixes must not be repurposed.
- 2026-06-03: `EntityNameHook` merged into `AuditDomainHook`; `AuditFieldLabelHook` merged into
  `AuditActivityFieldsHook`; `AuditActivityRenderHook` merged into `AuditActivityEnrichHook`.
  SPI count reduced from 13 to 10.
- 2026-06-15: `AuditUiPort`, `AuditActivityRowHook`, `AuditHistoryRowActionsHook`,
  `AttachmentGalleryPort` removed — unnecessary indirection since all UI lives in marketplace-app.
- 2026-07-31: `AttachmentAuditHook` renamed to `AttachmentAuditPort` — its call direction
  (marketplace calls the starter) was always the `*Port` semantic, not `*Hook`; see ADR-025.

---

## ADR-004: Shared kernel — contracts only, no implementations
**Status:** Accepted

**Context:** Every module depends on `platform-commons`. If it pulled in Spring Boot or any
framework, every module would inherit that transitive dependency — including `query-lib` which
is intentionally framework-free.

**Decision:** `platform-commons` contains only pure Java: DTOs, SPI interfaces, annotations,
and domain model records. No Spring Boot autoconfiguration, no Spring beans, no framework
annotations beyond `@interface`.

**Consequences:** Rejected: placing conditional Spring annotations in this module — contracts
must be pure Java with no Spring context dependency.

---

## ADR-005: UserPort + AdvertisementPort for domain module extraction
**Status:** Accepted (completed 2026-06-15)

**Context:** Domain module extraction required marketplace-app to call user and advertisement
starters without importing their internals.

**Decision:** Two new `*Port` interfaces added to `platform-commons`:
- `UserPort` (`user.spi`) — marketplace calls user-spring-boot-starter for all user operations.
- `AdvertisementPort` (`advertisement.spi`) — marketplace calls advertisement-spring-boot-starter.

`UserPort.findActorNames` is called by advertisement-starter (not marketplace) to enrich
`AdvertisementInfoDto` with creator display names without a SQL JOIN on `user_information`.

**Consequences:** Starters may call other starters' ports from platform-commons — this is the
correct pattern. Direct starter-to-starter imports remain forbidden.

---

## ADR-006: ComponentFactory<T> — typed wrapper over ObjectProvider<T>
**Status:** Accepted (mechanism changed since original write-up — see correction)

**Context:** Optional starter dependencies in marketplace-app need typed, ergonomic access
without unchecked casts. Raw `ObjectProvider<T>` doesn't know about the `Configurable<T, P>`
protocol.

**Correction (verified 2026-07-13):** the original text described `ComponentFactory<T>` resolving
its concrete type `T` by inspecting Spring's `InjectionPoint` at wiring time via a single generic
`@Bean ComponentFactory<?> componentFactory(InjectionPoint ip, ...)` method. That design is not
what exists in code — `grep -rn "InjectionPoint"` across the whole reactor returns zero hits.
`ComponentFactory<T>` (`platform-commons/.../core/ComponentFactory.java`) is instead a plain
class taking a constructor-injected `ObjectProvider<T>`, with **one explicit `@Bean` method per
concrete type**, hand-written in each consuming config class (`marketplace-app/config/
ComponentFactoryConfig.java` has 17 such methods, one per optional port/component type — was "20+"
in an earlier count, corrected 2026-07-27). `build(P
params)` lives on the marketplace-app subclass `UiComponentFactory<T>`, not on the base
`ComponentFactory<T>` itself — the base class only exposes `get()`, `getIfAvailable()`,
`findIfAvailable()`, `ifAvailable(Consumer<T>)`.

**Decision:** `ComponentFactory<T>` wraps `ObjectProvider<T>` for typed, ergonomic access to
optional starter-provided beans, declared per-type as an explicit `@Bean` (not resolved
generically via reflection/`InjectionPoint`).

**Consequences:**
- All optional starter components in marketplace-app use `ComponentFactory<T>` injection.
- Direct `ObjectProvider<T>` fields are not used for this purpose.
- Rejected: singleton factory with `<T> T get(Class<T> type)` — pushes type token to every call site,
  requires unchecked cast, unsound at compile time.
- Also effectively rejected in practice (though not originally planned that way): a single generic
  `InjectionPoint`-resolved factory bean — replaced by one explicit `@Bean` per type, which is more
  boilerplate but fully type-safe and requires no reflection.

---

## ADR-007: StorageService moved out of platform-commons into attachment-starter
**Status:** Accepted

**Context:** `StorageService` lived in `attachment.storage` (contracts) but had no cross-module
consumer — only `attachment-spring-boot-starter` referenced it.

**Decision:** `StorageService` moved to `attachment-spring-boot-starter`, at
`org.ost.attachment.services` (verified 2026-07-13 — not `org.ost.attachment.storage` as
originally written here; that package does not exist, the module's actual top-level packages are
`config, entities, repository, services, spi, util`). `@ConditionalOnStorageEnabled` was dropped
entirely rather than relocated — zero references anywhere in the current codebase. The
`attachment.storage` package no longer exists in platform-commons, which remains accurate.

**Consequences:** platform-commons is reserved for types crossed by ≥2 modules.
Rejected: keeping SPI in contracts "in case" — speculative.

---

## ADR-008: AuditableSnapshot uses Id.NAME polymorphism; no subtype registration in starter
**Status:** Accepted

**Context:** `Id.CLASS` embeds fully-qualified class names into stored JSON — any package rename
or class move silently breaks deserialization of all existing snapshot rows.

**Decision:** `@JsonTypeInfo` on `AuditableSnapshot` uses `Id.NAME`. Concrete snapshot DTOs live
in marketplace-app and carry `@JsonTypeName("stable_short_name")`. Subtype registration happens
in `marketplace-app/JacksonConfig` via `@PostConstruct registerAuditSnapshotSubtypes(auditObjectMapper)`.

**Consequences:**
- platform-commons must never import or reference concrete `AuditableSnapshot` implementations.
- Subtype registration always stays in the consuming application.
- Changing `@JsonTypeName` values requires a DB migration.

---

## ADR-009: AuditableSnapshot.isRestorable() default method
**Status:** Superseded (2026-07-03, snapshot-cleanup)

**Context:** Not all snapshot types represent a restorable entity state. Category-change snapshots
record a taxonomy assignment event — restoring to that "snapshot" makes no domain sense.

**Decision:** `AuditableSnapshot` gained `default boolean isRestorable() { return true; }`.
`CategoryChangeSnapshotDto` overrides it to return `false`.

**Superseded:** advertisement-snapshot-redesign deleted `CategoryChangeSnapshotDto` (the only
overrider), leaving every implementation on the default `true`; snapshot-cleanup then removed
`isRestorable()` (and the analogous `isVisible()`) from `AuditableSnapshot` entirely as dead
code. If a metadata/event snapshot type ever reappears, reintroduce the flag with it — do not
add it preemptively. See `backlog/completed/issues/feature-006-snapshot-cleanup.md`.

---

## ADR-010: Attachment lifecycle — SPI ports replace ApplicationEvents
**Status:** Accepted

**Context:** Events were a one-way pipe with no return value, forcing the starter to denormalize
derived fields into the event payload and the domain to listen and translate.

**Decision:** Dropped `AdvertisementDeletedEvent`, `AdvertisementRestoredEvent`,
`AdvertisementMediaUpdatedEvent`. Cross-module attachment lifecycle now carried by SPIs:
- **`AttachmentPort`** (domain → starter): `softDeleteAll(EntityRef, Long actorId)`,
  `getMediaSummary(EntityRef)`, and restore via `restoreToUrls(EntityType, Long, String[])`/
  `restoreToUrlsAndCapture(...)` (corrected 2026-07-13 — originally written as a single
  `restoreToSnapshot` method, which does not exist on `AttachmentPort`; that name exists only on
  the unrelated `UserPort`).
- **`AttachmentMediaChangeHook`** (starter → domain): `onMediaChanged(EntityRef entity)` (corrected
  2026-07-13 — signature was `(EntityType, Long)` when this ADR was written, superseded by a later
  refactor introducing `EntityRef` to collapse `(EntityType, Long)` pairs across attachment SPIs;
  the ADR was never updated for it). **Removed entirely, improvement-102, corrected 2026-07-27** —
  the interface no longer exists anywhere in `attachment.spi`; it had zero implementations and was
  deleted rather than kept as dead API surface (see `advertisement-spring-boot-starter/CLAUDE.md`
  and `marketplace-app/DECISIONS.md` ADR-035). Any reference to this hook elsewhere in this file is
  historical only.
- **`AttachmentMediaSummaryDto`** (`attachment.dto`) — display-ready record from `getMediaSummary`.

**Consequences:** Rejected: keeping events alongside the SPI — splits the contract surface.
The starter speaks SPI and only SPI.

---

## ADR-011: Audit decoupled from attachment via AuditActivityEnrichHook
**Status:** Accepted — see `audit-spring-boot-starter/DECISIONS.md` ADR-010 for the same decision
from the audit starter's side (corrected together, 2026-07-16 — the two entries had drifted to
describe different, both-inaccurate mechanisms with no cross-reference between them)

**Context:** The audit starter called `AttachmentAuditHook` (an `attachment.spi` interface) directly
— starter-to-starter coupling. Marketplace is the correct orchestrator.

**Decision (corrected 2026-07-16):** `AuditActivityEnrichHook` SPI added to `audit.spi`.
`AuditReadService` (audit starter) injects it as `List<AuditActivityEnrichHook>` — a plain
required field per ADR-012's rule, not `ObjectProvider`. Marketplace implements
`ActivityEnrichHookImpl`, which delegates directly to `AdvertisementEnrichService` — it has no
`ObjectProvider<AttachmentAuditHook>` reference at all; that class doesn't call into the
attachment starter through this hook.

**Consequences:** Audit starter must never import from `attachment.*` packages. Any enrichment
from outside the audit domain flows through a hook in `audit.spi`.

---

## ADR-012: Starters inject hooks as required beans; marketplace uses ObjectProvider
**Status:** Accepted

**Context:** A starter is only on the classpath when a consuming application (marketplace-app or
equivalent) is present — that application is always responsible for providing all hook implementations.
Using `ObjectProvider` in a starter implies the hook is optional, which is architecturally false.

**Decision:**
- Starters: inject `*Hook` implementations as plain required fields (`@RequiredArgsConstructor`).
  No `ObjectProvider`, no `required = false`.
- Marketplace: `ObjectProvider` for all starter ports and components — starters are genuinely optional.

**Consequences:**
- `private final CurrentActorHook currentActorHook;` — correct inside a starter.
- `private final ObjectProvider<AuditPort> auditPort;` — correct inside marketplace-app.
- **Documented exception, now moot (found 2026-07-16, resolved 2026-07-27):** this entry used to
  describe `AttachmentService` (attachment-spring-boot-starter) injecting `AttachmentMediaChangeHook`
  as `ObjectProvider<AttachmentMediaChangeHook>` as a real, live exception to this ADR's rule.
  Confirmed by direct read of `AttachmentService`: that field no longer exists — the class's actual
  fields are `storageService`, `attachmentRepository`, `attachmentSnapshotService`,
  `currentActorHook` only. `AttachmentMediaChangeHook` itself was deleted entirely (improvement-102,
  see ADR-010 above), not just left unimplemented, so there is no exception left to document — this
  ADR's rule holds without carve-outs today.

---

## ADR-013: User-domain knowledge purged; actor-centric naming
**Status:** Accepted

**Context:** "User" is a marketplace concept. Starters referencing `userId` cannot be reused
in systems where the acting principal is an agent, robot, or workflow.

**Decision:** All user-domain types removed from platform-commons. SPIs renamed to actor-centric
vocabulary:
- `AuditUserProvider` / `CurrentUserProvider` → `CurrentActorHook`
- Every `userId` parameter in attachment API → `actorId`
- `Role` enum promoted to `platform-commons/user.model` (2026-06-13) when user-starter extracted

**Consequences:** Rejected: keeping `UserSnapshotState` — locks the contract surface to a specific
subject shape. Rejected: keeping `userId` aliases — would perpetuate user-domain vocabulary forever.

---

## ADR-014: Activity decoration via SPI (AuditUiPort + AuditActivityRowHook)
**Status:** Deprecated — superseded 2026-06-15

**Context (historical):** Profile activity panels were built through `AuditUiPort` with an
`AuditActivityRowHook` SPI list for per-row UI decoration.

**Decision (superseded):** `AuditUiPort`, `AuditActivityRowHook`, and `AuditHistoryRowActionsHook`
removed from platform-commons (2026-06-15). All Vaadin UI lives in marketplace-app — UI ports/hooks
are unnecessary indirection with no cross-module consumer. Marketplace UI components use
`OverlayFormBinder` directly via `ComponentFactory<AuditPort>` (corrected 2026-07-16 —
`AuditSnapshotBinder`, named here originally, does not exist anywhere in the codebase; this exact
staleness was already caught and fixed once in `audit-spring-boot-starter/DECISIONS.md` ADR-008
but the correction was never propagated to this entry).

**Consequences:** Do not re-introduce `AuditUiPort` or `AttachmentGalleryPort`.

---

## ADR-015: MediaSummary reclassified as DTO
**Status:** Accepted

**Context:** `MediaSummary` was a return-type record exposed by `AttachmentPort` but lived under
`attachment.spi` — wrong package for a data carrier.

**Decision:** Moved to `attachment.dto`, renamed `AttachmentMediaSummaryDto`.

**Consequences:** `*.spi` is for interfaces and extension points; data records belong in `*.dto`.

---

## ADR-016: Role and ownership checks exposed via UserPort
**Status:** Accepted

**Context:** `AccessEvaluator` in marketplace-app imported `org.ost.user.security.RoleChecker`
and `OwnershipChecker` directly — internal user-starter classes, violating module boundaries
(tracked in improvement-004).

**Decision:** Added `isAdmin`, `isModerator`, `isOwner` methods to `UserPort` (platform-commons).
`UserPortImpl` delegates to the existing internal `RoleChecker` / `OwnershipChecker` beans.
`AccessEvaluator` now depends only on `UserPort` — a platform-commons contract.

**Consequences:** `RoleChecker` and `OwnershipChecker` remain internal to user-starter.
No new SPI interfaces or suffixes introduced — role/ownership checks are user-domain queries,
fitting naturally on the existing `UserPort`.

---

## ADR-017: Taxon SPI contracts added — TaxonPort and TaxonAuditHook
**Status:** Accepted (done 2026-06-26); `TaxonAuditHook` half **removed** 2026-07-17 (see note below)

**Context:** Introduction of `taxon-spring-boot-starter` required new cross-module contracts. UI and
services in marketplace-app must reach taxon functionality without importing starter internals.

**Decision:** Two new SPI interfaces added to `platform-commons`:
- `TaxonPort` (`taxon.spi`) — marketplace → starter; CRUD, assignment management, batched entity-id queries
- ~~`TaxonAuditHook` (`taxon.spi`) — starter → marketplace; fired when taxon assignments change~~

New DTOs in `taxon.dto`: `TaxonDto`, `TaxonTranslationDto`, `TaxonSnapshotDto` (`CategoryChangeSnapshotDto`,
listed here originally, was deleted in the advertisement-snapshot redesign — see ADR-001's package
listing above).
New enum in `taxon.model`: `TaxonType` — was a closed set of just `CATEGORY` when this ADR was
written; `CITY` was added since (F-02).

**Consequences:** `EntityType.TAXON` added to `core.model.EntityType` to allow taxon entities to be
audited. `ActionType.RESTORED` added to `core.model.ActionType` to distinguish restore events from
updates — used by `AuditPort.captureRestore()` and written to `audit_log.action_type`.

**Note (2026-07-17, improvement-058):** `TaxonAuditHook` was removed entirely — it never gained an
implementation, and both of its call sites already sit inside an advertisement save/delete that
produces its own audit snapshot, making a separate assignment-event trail redundant. `TaxonPort`
itself is unaffected and remains as originally decided (minus `assign()`/`unassign()`/
`findByCode()`, also removed as zero-caller dead API surface in the same pass). See
`marketplace-app/DECISIONS.md` ADR-019 and ADR-043 for the full resolution.

---

## ADR-018: ActionType.RESTORED — explicit enum value for soft-delete restore
**Status:** Accepted (done 2026-06-26)

**Context:** Before this change, restoring a soft-deleted entity called `AuditPort.captureUpdate()`,
which stored the action as `UPDATED` in `audit_log`. This made it impossible to distinguish between
a genuine field edit and a restore from deletion by querying the action type alone.

**Decision:** `ActionType.RESTORED` added as a fourth enum value (alongside `CREATED`, `UPDATED`,
`DELETED`). `AuditPort.captureRestore()` method added to the port interface. `DefaultAuditPort`
implements it by writing `ActionType.RESTORED` to `audit_log`. Services that restore entities
(e.g. `TaxonService.restore()`) call `captureRestore`, not `captureUpdate`.

**Consequences:**
- `audit_log.action_type` column CHECK constraint must allow `'RESTORED'` — covered by the existing
  VARCHAR column without an enum constraint in PostgreSQL.
- CSS modifier classes in UI: `--restored` added alongside `--created`, `--updated`, `--deleted`
  (see audit-spring-boot-starter ADR-007).
- Any UI that renders action types must handle `RESTORED`.

---

## ADR-019: version parameter added to AdvertisementPort.delete() and TaxonPort.update()/softDelete()

**Status:** Accepted

**Context:** improvement-015 adds optimistic locking (`@Version`) to `Advertisement`, `User`,
`Taxon`. For `AdvertisementPort.save()` the version already travels through
`AdvertisementSaveDto`, but `delete()` had no DTO to carry it. Same for `TaxonPort.update()` and
`softDelete()` — translations are passed as a bare `Map`, with no carrier for the version the
caller last read.

**Decision:** Added a trailing `Long version` parameter to `AdvertisementPort.delete(id,
actingUserId, version)`, `TaxonPort.update(id, translations, actorId, version)`, and
`TaxonPort.softDelete(id, actorId, version)`. Callers pass the version from the DTO they already
have in hand (`AdvertisementInfoDto.getVersion()` / `TaxonDto.getVersion()`) — the object
displayed in the card/grid at the moment the action was triggered. A stale value causes the
starter's repository to throw `OptimisticLockingFailureException`.

**Consequences:**
- `UserPort` was not touched the same way — `UserService.save()` already receives the full
  `UserProfileDto` (which now carries `version`), so no signature change was needed there.
- See `marketplace-app/DECISIONS.md` ADR-029 for the full cross-module design (why `@Version` on
  the entity is not enough by itself, the manual guard needed for `User`, and the UI conflict
  handling).
- → [improvement-015-optimistic-locking](../backlog/completed/issues/improvement-015-optimistic-locking.md)

---

## ADR-020: `AuditTimelineFilterDto.actorId` (`Long`) → `actorIds` (`Set<Long>`)

**Status:** Accepted

**Context:** [improvement-075](../backlog/completed/issues/improvement-075-timeline-actor-filter-multi-select.md)
— the Timeline actor filter needed to match "any of N selected actors" in one query instead of one
actor at a time. `actorId` was the only scalar field on this DTO; `entityTypes`/`actionTypes`
already use the `Set<T>` shape this change brings `actorIds` in line with.

**Decision:** Renamed and retyped the field. Every consumer updated in the same change: `AuditLogRepository`'s
binding (`equalsTo` → `anyOf`, see `query-lib/DECISIONS.md` ADR-005), `TimelineFilterMeta.ACTOR`
(`UserDto → Long` mapping became `Set<UserDto> → Set<Long>`), and `TimelineView.refresh()`'s
non-privileged-viewer self-scoping (`.actorId(userId)` → `.actorIds(Set.of(userId))`) — the latter
needed an explicit null guard (`Set.of(null)` throws `NullPointerException`, unlike a plain
`Long`-typed builder setter accepting `null` silently), caught via a full Playwright run that
failed application startup entirely until fixed.

**Consequences:** No other module reads or writes this field outside `audit-spring-boot-starter`
and `marketplace-app`'s timeline package — confirmed by a full-repo grep before making the change,
so this is a clean rename with no compatibility shim needed.

---

## ADR-021: `AuditTimelineItemDto.expandedChanges()` — a narrow, documented exception to "`*.dto` has no behavior"

**Status:** Accepted

**Context:** [improvement-104](../backlog/completed/issues/improvement-104-expandactivityfields-feature-envy.md)
— the same three-line "if there's a snapshot, expand the changes against it; otherwise return the
changes as-is" check was independently copy-pasted four times: inline in
`TaxonActivityFieldsHookImpl` and `AdvertisementActivityFieldsHookImpl` (marketplace-app), and
routed through `UserService.expandActivityFields()` → `UserPort.expandActivityFields()` for
`UserActivityFieldsHookImpl`/`UserSettingsActivityFieldsHookImpl` — despite nothing about the
logic being user-domain-specific. It operates purely on `AuditTimelineItemDto`'s own
`snapshotData()`/`changes()` fields.

**Decision:** Added `expandedChanges()` as a default-shaped instance method directly on
`AuditTimelineItemDto` (`platform-commons/*.dto`), and deleted `UserService
.expandActivityFields()` / `UserPort.expandActivityFields()` / `UserPortImpl
.expandActivityFields()` entirely — all four call sites now read `item.expandedChanges()`. This is
a `*.dto` class gaining a method, which `platform-commons/CLAUDE.md`'s package-semantics rule
forbids ("`*.dto` — plain value objects with no behavior"). Justified as a narrow exception: the
same file already has an identically-shaped precedent (`withChanges()`, a wither over the record's
own fields), and the method calls no service, holds no dependency, and branches only on the
record's own fields — the same "pure derivation" spirit that lets `diff()` live on
`AuditableSnapshot` (a `*.api` marker, not `*.dto`, but the same category of exception). See
`platform-commons/CLAUDE.md`'s "Narrow exception" note under Package Semantics for the boundary
this does not extend past (no service calls, no cross-DTO production, no domain branching beyond
this record's own fields).

**Consequences:** `UserActivityFieldsHookImpl`/`UserSettingsActivityFieldsHookImpl` no longer need
a `UserPort` dependency at all for this method — removed the now-unused field from both.

---

## ADR-022: Dead SPI parameters and methods removed — `AuditPort`, `AuditActivityEnrichHook`, `AuditDomainHook`, `UserPort`, `AttachmentPort`

**Status:** Accepted

**Context:** [improvement-115](../backlog/issues/improvement-115-intellij-inspection-cleanup-pass.md)'s
dead-code sub-pass found several SPI methods/parameters with zero real consumers across every
current implementation and call site — not "single implementation that might grow," but params
threaded through and never read anywhere, or methods with no caller at all:

- `AuditPort.captureUpdate(entityId, before, after, actorId)` — `before` was never read by
  `DefaultAuditPort` (only `after` gets persisted; diffing happens at read time via
  `AuditableSnapshot.diff()` against the previous log row, per `audit-spring-boot-starter/CLAUDE.md`).
  Every caller (`AdvertisementSaveService`, `TaxonService`, `UserService` ×2, `UserSettingsService`)
  was computing/fetching a "before" snapshot for this one unused argument.
- `AuditActivityEnrichHook.merge(subjects, base)` / `.enrichActivity(entityRef, items)` — both
  `subjects`/`entityRef` were dead-forwarded from `AuditReadService` (`List<EntityRef> noSubjects
  = List.of()` — always empty) to the sole implementation (`ActivityEnrichHookImpl`), which
  ignored them.
- `AuditDomainHook.resolveDisplayName(entityType, snapshot)` — `entityType` unused in the sole
  implementation (`AuditDomainHookImpl`); `AuditableSnapshot.displayName()` already resolves
  per-type display logic polymorphically, making the extra parameter redundant.
- `UserPort.restoreToSnapshot()` (+ `UserService.restoreToSnapshot()`/`applyUserRestore()`) — no UI
  caller exists; both `AdvertisementFormOverlayModeHandler` and `UserFormOverlayModeHandler`'s
  restore-from-activity flows fetch the snapshot directly via `AuditPort.getSnapshotContent()` and
  populate the edit form locally, letting the user's own "Save" click persist it — neither goes
  through this port method. `UserServiceRestoreTest.java` (its only caller, existing purely to
  test it) was deleted alongside it.
- `AttachmentPort.getMediaSummary(EntityRef)` (single-entity) — every real caller already uses the
  bulk `getMediaSummaries(EntityType, Set<Long>)` (see ADR-035 in `marketplace-app/DECISIONS.md`).
  `AttachmentRepository.loadMediaStats(EntityType, Long)`, the repository method it delegated to,
  was kept — it has its own direct repository-level test coverage independent of this port method.

**Decision:** Removed each dead parameter/method from the SPI interface, its implementation(s),
and every call site (compiler-verified — no call site could silently keep passing a stale value).
Not treated as "maybe a future entity type needs `subjects`" speculative API surface: each was
already threaded through a real caller today and provably unused end-to-end, not merely
single-implementation.

**Consequences:**
- `AdvertisementSaveService.save()`'s update branch still computes its own `before` snapshot
  locally (needed for the `attachmentSnapshotId` fallback and the concurrent-delete guard added in
  the same pass — see that file) — only the `AuditPort.captureUpdate` argument was dropped, not
  the local computation.
- `TaxonService.update()` no longer fetches `beforeTranslations`/`beforeSnapshot` at all (was
  otherwise unused after the arg removal); `UserSettingsService.save()` no longer calls
  `repository.load(userId)` for the same reason.
- If a genuine future need for a "before" snapshot at audit-write time arises, re-add the
  parameter then with a concrete consumer — do not restore it preemptively.

## ADR-023: `Class<T> targetClass` type-token added to `AuditPort.getSnapshotContent()` / `AuditDomainHook.castIfKnown()`

**Status:** Accepted

**Context:** [improvement-072](../backlog/issues/improvement-072-uicomponentfactory-generics-design-debt.md)
item 3 — `AuditDomainHookImpl.castIfKnown()` used a `switch` over the four known snapshot DTO
types to confirm `content.snapshotData()` was *one of* them, then did an unchecked cast to the
caller-inferred `T` without ever checking it matched *that specific* `T`. A caller inferring
`T = TaxonSnapshotDto` when the actual runtime data was `AdvertisementSnapshotDto` would still
match a `switch` case arm and silently return mismatched-type content.

**Decision:** Added a `@NonNull Class<T> targetClass` parameter to both
`AuditPort.getSnapshotContent(Long snapshotId, EntityType entityType, Class<T> targetClass)` and
`AuditDomainHook.castIfKnown(AuditSnapshotContentDto<? extends AuditableSnapshot> content, Class<T>
targetClass)`. `AuditDomainHookImpl.castIfKnown()` now does `targetClass.cast(content.snapshotData())`
inside a `try`/`catch (ClassCastException)` — a real, JVM-verified generic downcast via the reified
`Class<T>` token, with zero `instanceof`, zero `switch`, and zero `@SuppressWarnings("unchecked")`
(`Class.cast()` is checked by construction; no erasure ambiguity remains once a concrete `Class<T>`
is in hand). `DefaultAuditPort.getSnapshotContent()` forwards `targetClass` straight through to
`auditDomainHook.castIfKnown()`. The four UI call sites (`AdvertisementFormOverlayModeHandler`,
`UserFormOverlayModeHandler`, `TaxonFormOverlayModeHandler`, `SettingsFormModeHandler`) pass a
`.class` literal (e.g. `AdvertisementSnapshotDto.class`) instead of the old
`port.<AdvertisementSnapshotDto>getSnapshotContent(...)` type-witness syntax.

**Consequences:**
- The four known snapshot DTOs no longer need to be named in `AuditDomainHookImpl` at all — the
  `switch`'s enumeration of `AdvertisementSnapshotDto`/`UserSnapshotDto`/`SettingsSnapshotDto`/
  `TaxonSnapshotDto` is gone; adding a fifth snapshot DTO in the future needs no change here.
- A caller that passes the wrong `Class<T>` for the entity type it queried now gets a caught
  `ClassCastException` (logged, returns `Optional.empty()`) instead of silently succeeding with
  mismatched data — closes the actual correctness gap item 3 identified.
- Item 1 (`UiComponentFactory<T extends Configurable<T, ?>>` bound) and item 3 above are resolved;
  item 2 (`AuditReadService`'s raw `List`/`AuditActivityEnrichHook` dispatch) was investigated and
  **deliberately kept as the raw-type + `@SuppressWarnings` idiom** — verified directly (not just
  in theory) that no wildcard-capture-helper or `Class<T>`-token variant can eliminate the cast
  there without moving it somewhere worse: the dispatch loop correlates two independently-wildcarded
  values (a heterogeneous `hook` and a heterogeneous `items` list) whose actual-type relationship is
  an external runtime invariant (`hook.entityType() == entityType`), not something Java's wildcard
  capture can express — capture-helper only unifies types when *one* wildcard-typed expression is
  reused for multiple parameters of the same call, not two independently-wildcarded values. Pushing
  the reinterpretation down into `AdvertisementEnrichService` (tried and reverted) just moved an
  unchecked cast into a class that previously needed none, since that class already receives a
  properly `T`-typed list via the hook interface's own generic parameter.

---

## ADR-024: Snapshot schema versioning — a real `schemaVersion` field everywhere, no reflection
**Status:** Accepted

**Context:** Field renames or type changes in `AuditableSnapshot` implementations (and in the
other two JSON-persisted blobs in the system) caused silent data loss on read — Jackson's
`FAIL_ON_UNKNOWN_PROPERTIES = false` setting hides additions/removals, but a rename or type change
just deserializes to `null` with no warning at all.

**Decision:** Every JSON-persisted blob in the system (three total, found by grepping every
Liquibase changelog for a JSONB/JSON column) gets a genuine, Jackson-bound `schemaVersion` field —
no annotations, no reflection, no raw-JSON tree-parsing anywhere. `AuditableSnapshot.schemaVersion()`
is a plain abstract interface method, satisfied by each implementation's own record component:

- `audit_log.snapshot_data` — `AdvertisementSnapshotDto`/`TaxonSnapshotDto`/`UserSnapshotDto`/
  `SettingsSnapshotDto` each gain `int schemaVersion` as their **last canonical record component**,
  plus `public static final int SCHEMA_VERSION = 1` and a second, non-canonical constructor
  matching the DTO's *old* parameter list that delegates to the canonical one with `SCHEMA_VERSION`
  — every existing call site across the codebase (`AdvertisementSaveService`, `UserService`,
  `TaxonService`, every `*SnapshotDtoTest`, ...) keeps compiling unchanged. Java records have no
  concept of an optional/default constructor parameter (unlike Lombok `@Builder.Default`, which
  `UserSettingsDto` below actually gets to use) — a second delegating constructor is the standard,
  idiomatic way to add a component to a record without touching its existing callers.
- `user_information.settings` — `UserSettingsDto` (a Lombok `@Value`/`@Builder` class, not a
  record) gets a `@Builder.Default int schemaVersion = SCHEMA_VERSION` field directly — no
  delegating constructor needed here, `@Builder.Default` already covers the "existing callers
  don't need to change" case for a builder-backed class. Separate from the existing `version`
  field (that one is optimistic-locking, a different concern).
- `attachment_snapshot.changes_summary` — considered wrapping the whole `List<AttachmentMediaChange>`
  in an envelope object (`{"schemaVersion":1,"changes":[...]}`) so the list itself could carry a
  sibling version field, but that changes the column's wire shape from a bare JSON array to an
  object — a bigger, needless change. Instead `AttachmentMediaChange` itself (the record already
  stored, one per array element) gets the same real-record-component + delegating-constructor
  treatment as the four `AuditableSnapshot` DTOs above. The column stays a bare array; each element
  self-describes its own version; `findChangesById()` checks the first element's `schemaVersion()`
  after an unchanged single `readValue()` call.

**Rejected along the way — an earlier draft of this ADR, both since reverted:**
1. `AuditableSnapshot.schemaVersion()` as a `@JsonProperty default` method computed via
   `getClass().getAnnotation(SchemaVersion.class)` reflection, rather than a real record component.
   Reverted for internal inconsistency — `UserSettingsDto` right next to it uses a genuine bound
   field, so the two blobs looked like they were solving the same problem two different ways for
   no real reason. A real field is simpler to read and, unlike the reflection default, its value
   after deserialization genuinely reflects what was in the stored JSON rather than always
   trivially equalling whatever the current class declares.
2. Reading every blob via `ObjectMapper.readTree()`/`JsonNode` before converting, specifically to
   distinguish "key genuinely missing from the raw JSON" from "key present and equal to the
   default" — relevant only if real pre-existing rows in the old shape needed to be told apart
   from current ones. Reverted: **this app has never run in production**, so there is no real
   deployed data in an old shape to protect this distinction against. All three read paths just
   deserialize normally (one call, identical in shape to the pre-issue code) and compare the
   resulting object's own field.

**Explicitly out of scope: no migration.** Detection only — a mismatch is logged (attachment/user-
settings paths; the polymorphic `audit_log` path has no single "current expected version" to
compare a deserialized instance against without reintroducing either reflection or a switch over
the four known DTOs, so it stays a plain, unchecked `readValue()` — the field is still stamped on
write, available for manual inspection or a future migration tool), then deserialization proceeds
exactly as it would have before. Converting an old-shape JSON into a new shape before
deserializing, if ever needed for a real field rename with real data to protect, is a separate
future task.

**Consequences:**
- A schema drift that previously failed silently now at least appears in the logs where a
  meaningful single-target comparison is possible (`user_information.settings`,
  `attachment_snapshot.changes_summary`) — at the cost of one extra `if` per read path, no new
  parsing step, no new wrapper class.
- The next new snapshot-bearing domain (F-04 / `improvement-124`'s `ActorProfileSnapshotDto`) must
  add its own `schemaVersion` record component + `SCHEMA_VERSION` constant, following this pattern
  — not a `@SchemaVersion` annotation (removed, no longer exists).

---

## ADR-025: Batch G governance cleanup — DTO boundary, Hook→Port rename, UserIdMarker package

**Status:** Accepted

**Context:** [improvement-132](../backlog/completed/issues/improvement-132-full-repo-solid-dry-review-2026-07-29.md)
Batch G, items 20-22 — three independent `platform-commons` governance findings from the repo-wide
review, grouped together since all three touch this module's own naming/package rules.

**Decision:**
- **Item 20:** `SettingsSnapshotDto.from(UserSettingsDto settings)` was removed — it reached into a
  sibling DTO's fields to construct itself, exceeding the `*.dto` package's "pure derivation over
  its own fields" exception (ADR-002/ADR-021). Moved to
  `UserSettingsService.toSettingsSnapshot(UserSettingsDto)` in `user-spring-boot-starter`, as a
  genuine instance method — `UserService` holds `UserSettingsService` as a constructor-injected
  field and calls it like any other collaborator; safe because `UserSettingsService`'s own
  constructor deps (`UserSettingsRepository`, `ComponentFactory<UserSettingsChangedHook>`,
  `ComponentFactory<AuditPort>`) never reference `UserService`, so no circular dependency results.
  Corrected from an initial `public static` cross-class-call draft, which this change's own
  `/code-review` pass flagged as bypassing the codebase's constructor-injection convention.
- **Item 21:** `AttachmentAuditHook` renamed to `AttachmentAuditPort` (and its implementation,
  `AttachmentAuditHookImpl` → `AttachmentAuditPortImpl`). Its call direction was always marketplace
  calling into the attachment starter (`AdvertisementAuditEnrichService` calls it), which per
  ADR-003's own table is the `*Port` semantic, not `*Hook` — the interface had carried the wrong
  suffix since it was introduced.
- **Item 22:** `UserIdMarker` moved from `org.ost.platform.user.security` to
  `org.ost.platform.user.spi` — `user.security` was never a documented package role (only
  `api`/`spi`/`dto`, per ADR-002), and `UserIdMarker` is exactly a `*.spi` marker: implemented by
  domain types, consumed across the module boundary (`UserPort`, `OwnershipChecker`,
  `AccessEvaluator`).

**Consequences:**
- `user.security` package no longer exists in `platform-commons`; any future marker/contract that
  isn't a `Port`/`Hook`/`Dto` still needs a governance call, not a new ad hoc package.
- Pure renames/moves, no behavior change — same method signatures on `AttachmentAuditPort`.

## ADR-026: One starter, multiple `*Port` interfaces — `UserPort` split into 4 (improvement-124 Batch A2)

**Status:** Accepted

**Context:** Every `*Port` in this codebase to date maps one-to-one to one starter module
(`TaxonPort` alone covers `taxon`/`taxon_translation`/`taxon_assignment` — 3 tables, one
interface). `UserPort` had grown to 19 methods spanning 4 unrelated concerns — query
(`getFiltered`/`findById`/`findByIds`/...), account mutation (`save`/`delete`/`register`/
`refreshCurrentUserInContext`), authorization (`isAdmin`/`isModerator`/`isOwner` ×2), and
preferences (`loadSettings`/`saveSettings`/`updateLocale`) — after `user-spring-boot-starter`
picked up preferences methods over several passes. Grep against every real consumer confirmed
most inject only one slice: `AccessEvaluator` uses only the 4 authorization methods;
`UserDeleteService` only `delete`; `UserPickerField`/`UserView` only the query methods;
`SettingsFormModeHandler`/`SettingsPaginationService` only the preferences methods. A few
consumers (`LocaleSelectorComponent`, `SignUpDialog`, `UserFormOverlayModeHandler`) genuinely
span two concerns and inject two ports — not a sign the split is wrong, just that those specific
flows touch two bounded contexts in one user action.

**Decision:** `UserPort` (platform-commons) split into 4 interfaces, same `org.ost.platform.user.spi`
package: `UserPort` (narrowed to query only), `UserAccountPort` (save/delete/register/refresh),
`UserAuthorizationPort` (isAdmin/isModerator/isOwner), `UserPreferencesPort` (settings/locale, plus
a new `findLocale(Long)` for a future admin-views-another-user case). `UserPortImpl` (starter) split
into 4 correspondingly-named thin-delegation impl classes; `UserAutoConfiguration` gained 3 more
`ComponentFactory<...>` beans alongside the existing `userPortFactory`. Every real consumer was
repointed to inject only the port(s) it actually calls, verified against the actual codebase (not
assumed) before editing.

**This deliberately breaks the "one starter = one `*Port`" precedent — the trigger is interface
cohesion, not runtime toggleability.** All 4 ports are always implemented by the same
`user-spring-boot-starter` module; there is no `ObjectProvider`-optionality benefit from the split
the way there would be if, say, preferences moved to its own starter. The `*Port` suffix's existing
semantic (marketplace → starter, commands/queries) is unchanged — this is not a new suffix, no
table update needed beyond adding the 3 new names to the Examples column. **Do not treat "many
methods" alone as sufficient reason to split a future `*Port`** — split only when grep against real
consumers shows the interface's methods cluster into genuinely separate concerns that different
callers use independently, the same evidence-first approach used here, not a size threshold.

**Consequence:** `TaxonPort`/`AdvertisementPort`/`AttachmentPort`/`AuditPort` are unaffected and
remain single interfaces — they don't (yet) show the same multi-concern consumer pattern `UserPort`
did. If one of them grows a similarly-mixed consumer profile later, this ADR is the precedent to
cite, with the same consumer-grep-first discipline, not a rubber stamp for splitting on sight.

## ADR-027: `ProviderProfilePort` added — F-04 Batch 124-B, `provider-profile-spring-boot-starter`

**Status:** Accepted

**Context:** F-04 (improvement-124) adds a "provider profile" concept — any actor can optionally
describe themselves as a service provider (`MASTER`/`SHOP`/`SUPPORT`). The original single-table
design (merging this with locale/settings into one `actor_profile` row) was superseded before
implementation by a 2026-07-31 update to the issue: three tables, not one — `user_information`
(auth, unchanged), `user_preferences` (locale/settings, Batch 124-A, already shipped), and a new
standalone `provider_profile` table/module (this ADR). See `backlog/issues/improvement-124-provider-profile.md`'s
"Update 2026-07-31 — module/table split reconsidered" for the full rationale.

**Decision:** New package `org.ost.platform.providerprofile` (`model.ProviderKind`,
`dto.ProviderProfileDto`/`ProviderProfileSaveDto`/`ProviderProfileFilterDto`/
`ProviderProfileSnapshotDto` — `schemaVersion` per ADR-024 — `spi.ProviderProfilePort`), plus
`EntityType.PROVIDER_PROFILE`. `ProviderProfilePort`'s shape mirrors `AdvertisementPort` closely
(`getFiltered`/`count`/`findById`/`save`/`delete`/`findExistingIds`/`findOwnerIds`) — deliberate
symmetry with the established starter pattern, not an accident (see `marketplace-app/DECISIONS.md`
ADR-072 for the "isn't this just a copy?" discussion this raised during implementation). Backed by
a new `provider-profile-spring-boot-starter` module owning `ProviderProfile` entity/repository/
service/port-impl/autoconfiguration — this batch is backend-only, no UI, no audit-write path yet
(that's Batch 124-C).

**Deliberate divergences from `AdvertisementPort`'s shape, each grounded in a real difference:**
- `kind` is `NOT NULL` and the row is created **lazily** (only on first "become a provider" save) —
  unlike `advertisement`, there is no "every actor gets one eagerly at registration" concept.
- `city_taxon_id` is a **plain column** on `provider_profile`, not a `taxon_assignment` row like
  `advertisement`'s city/category handling — a provider has exactly one city, so a scalar column is
  the simpler, correct shape; only `categoryIds` (many-to-many) goes through
  `TaxonPort.replaceAssignments()`.
- `delete()` is a **real `DELETE`**, not a soft-delete — `provider_profile` carries no
  `deleted_at`/`deleted_by` columns, so there is no "restore a deleted provider profile" concept in
  this design.
- `findOwnerIds()` exists (mirrors `AdvertisementPort`'s created_by-purge-block precedent) but
  **no `clearActorReferences()`** — `advertisement` needs it to null its nullable `updated_by`/
  `deleted_by` audit columns on user purge; `provider_profile` has no nullable actor-reference
  columns to null, `actor_id` is the sole, non-nullable owner reference, so `findOwnerIds()` alone
  (block purge while a profile exists) is sufficient.
- `ProviderProfileService.save()` enforces `kind == SUPPORT` requires an `actingUserIsPrivileged`
  boolean the caller (marketplace-app) computes via `AccessEvaluator` and passes in — the **one**
  authorization-shaped rule this starter enforces server-side, an explicit, deliberate exception to
  the "authorization lives only in marketplace-app" convention (confirmed via `/code-review`
  verification against the approved issue plan during this batch — REFUTED as an architecture
  violation, since the plan calls it out by name as "the one real authorization rule this feature
  adds"). Treat it as a data-integrity guarantee (like `AdvertisementService`'s server-side
  description-length enforcement), not a precedent for adding general authorization logic to
  starters.
- Unlike `advertisement`/`taxon`, `ProviderProfileService`'s own service — not a marketplace-app
  orchestration service — writes category assignments directly via `TaxonPort.replaceAssignments()`
  (see the issue's Part 1 technical plan). This batch has no marketplace-app "SaveService" yet
  (that's Batch 124-C, alongside the actual `AuditPort.record()` call), so there was no other layer
  to put it in.

**Found and fixed during `/code-review`'s 8-angle pass (Batch 124-B):** `ProviderProfileFilterDto
.cityTaxonId` was declared but never wired into the repository's `SqlFilterBuilder` (a dead filter
field — fixed, now bound to `pp.city_taxon_id`); `ProviderProfileService.delete()` unconditionally
cleared taxon assignments before checking the row existed, unlike `AdvertisementService.delete()`'s
existence-guarded pattern (fixed, same guard added); an unnecessary empty-set defensive check in
`ProviderProfileEnrichmentService.findCities()` was removed (`TaxonPort.findByIds()`'s own contract
already handles an empty input set). Two further real findings — `HTML_SANITIZER`/`sanitizeHtml()`
duplication with `AdvertisementService`, and the shared "stale id during concurrent delete" edge
case both `AdvertisementService.save()` and `ProviderProfileService.save()` have — were kept out of
this batch (they require touching `advertisement-spring-boot-starter`, outside Batch 124-B's own
scope) and filed as Batch 124-B2 in `backlog/issues/improvement-124-provider-profile.md`, at the
user's explicit direction, rather than the generic `improvement-133` bucket.

**Consequence:** `EntityType.USER_SETTINGS` keeps being used unchanged for the Settings tab
(preferences never merged into `provider_profile`, so the earlier "keep as historical tag or
migrate" open question from the superseded single-table design is moot). The next batch (124-B2)
must land before 124-C, per the updated gate in the issue file.
