# Architecture & Technical Decisions — platform-commons

---

## ADR-001: Package restructure — core / audit / attachment / user / advertisement
**Status:** Accepted

**Context:** The old flat structure mixed audit, attachment, and shared types in `events.*`
with no clear ownership, making it impossible to tell which package belonged to which subsystem.

**Decision:** Three semantic groups (final layout after 2026-05-18 symmetry cleanup):

```
core.config    — CleanupProperties
core.model     — ActionType, ChangeEntry, EntityType, EntityRef
core.spi       — CurrentActorHook

audit.api      — AuditableSnapshot
audit.dto      — AuditActivityItemDto, AuditSnapshotContentDto, AuditTimelineItemDto, AuditTimelineFilterDto
audit.spi      — AuditPort, AuditDomainHook, AuditActivityFieldsHook, AuditActivityEnrichHook

attachment.dto     — AttachmentMediaSummaryDto, AttachmentItemDto, TempAttachmentDto
attachment.model   — AttachmentMediaContentType
attachment.spi     — AttachmentPort, AttachmentMediaChangeHook, AttachmentAuditHook
attachment.util    — YoutubeUtil

user.dto       — UserDto, UserFilterDto, UserProfileDto, UserSettingsDto,
                 UserSnapshotDto, SettingsSnapshotDto, SignUpDto
user.model     — Role
user.spi       — UserPort, AuthenticatedPrincipal, UserSettingsChangedHook

advertisement.dto  — AdvertisementInfoDto, AdvertisementFilterDto,
                     AdvertisementSaveDto, AdvertisementSnapshotDto
advertisement.spi  — AdvertisementPort

taxon.dto      — TaxonDto, TaxonTranslationDto, TaxonSnapshotDto, CategoryChangeSnapshotDto
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
ComponentFactoryConfig.java` has 20+ such methods, one per optional port/component type). `build(P
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
  the ADR was never updated for it).
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
- **Documented exception (found 2026-07-16, not previously noted):** `AttachmentService`
  (attachment-spring-boot-starter) injects `AttachmentMediaChangeHook` as
  `ObjectProvider<AttachmentMediaChangeHook>`, not a plain required field — its own javadoc calls
  this intentional ("Optional — attachment-starter injects via `ObjectProvider`"). This is a real,
  live exception to this ADR's rule, not a violation to silently fix — `AttachmentMediaChangeHook`
  currently has zero implementations anywhere (the old `MediaChangeHookImpl` was deleted, see
  ADR-035 in `marketplace-app/DECISIONS.md`), so a required field would fail to autowire. Whether
  to keep this exception long-term or restore a listener is a decision for whoever revisits that
  hook, not something to resolve via documentation alone.

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

New DTOs in `taxon.dto`: `TaxonDto`, `TaxonTranslationDto`, `TaxonSnapshotDto`, `CategoryChangeSnapshotDto`.
New enum in `taxon.model`: `TaxonType` (closed set; currently `CATEGORY`; adding a value is a release-level change).

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
