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
```

**Consequences:** `core.i18n`, `ui`, `attachment.event`, `attachment.storage` packages removed —
all i18n and UI contracts live in `marketplace-app`; storage lives in `attachment-spring-boot-starter`.

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

Current `*Port` interfaces: `AuditPort`, `AttachmentPort`, `UserPort`, `AdvertisementPort`.
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

## ADR-006: ComponentFactory<T> — InjectionPoint-resolved prototype factory
**Status:** Accepted

**Context:** Optional starter dependencies in marketplace-app need typed, ergonomic access
without unchecked casts. Raw `ObjectProvider<T>` doesn't know about the `Configurable<T, P>`
protocol.

**Decision:** `ComponentFactory<T>` wraps `ObjectProvider<T>` and resolves the concrete type `T`
by inspecting `InjectionPoint` at wiring time:
```java
@Bean @Scope("prototype")
public ComponentFactory<?> componentFactory(InjectionPoint ip, ConfigurableListableBeanFactory bf) { ... }
```
Exposes: `get()`, `build(P params)` (for `Configurable<T,P>` prototypes), `getIfAvailable()`,
`ifAvailable(Consumer<T>)`.

**Consequences:**
- All optional starter components in marketplace-app use `ComponentFactory<T>` injection.
- Direct `ObjectProvider<T>` fields are not used for this purpose.
- Rejected: singleton factory with `<T> T get(Class<T> type)` — pushes type token to every call site,
  requires unchecked cast, unsound at compile time.

---

## ADR-007: StorageService moved out of platform-commons into attachment-starter
**Status:** Accepted

**Context:** `StorageService` lived in `attachment.storage` (contracts) but had no cross-module
consumer — only `attachment-spring-boot-starter` referenced it.

**Decision:** `StorageService` and `@ConditionalOnStorageEnabled` moved to
`attachment-spring-boot-starter` (`org.ost.attachment.storage`). The `attachment.storage` package
no longer exists in platform-commons.

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
**Status:** Accepted

**Context:** Not all snapshot types represent a restorable entity state. Category-change snapshots
record a taxonomy assignment event — restoring to that "snapshot" makes no domain sense.

**Decision:** `AuditableSnapshot` gained `default boolean isRestorable() { return true; }`.
`CategoryChangeSnapshotDto` overrides it to return `false`.

**Consequences:** Any new `AuditableSnapshot` implementation that represents a metadata/event
record (not a full entity state) must override `isRestorable()` to return `false`.

---

## ADR-010: Attachment lifecycle — SPI ports replace ApplicationEvents
**Status:** Accepted

**Context:** Events were a one-way pipe with no return value, forcing the starter to denormalize
derived fields into the event payload and the domain to listen and translate.

**Decision:** Dropped `AdvertisementDeletedEvent`, `AdvertisementRestoredEvent`,
`AdvertisementMediaUpdatedEvent`. Cross-module attachment lifecycle now carried by SPIs:
- **`AttachmentPort`** (domain → starter): `softDeleteAll`, `restoreToSnapshot`, `getMediaSummary`.
- **`AttachmentMediaChangeHook`** (starter → domain): `onMediaChanged(EntityType, Long)`.
- **`AttachmentMediaSummaryDto`** (`attachment.dto`) — display-ready record from `getMediaSummary`.

**Consequences:** Rejected: keeping events alongside the SPI — splits the contract surface.
The starter speaks SPI and only SPI.

---

## ADR-011: Audit decoupled from attachment via AuditActivityEnrichHook
**Status:** Accepted

**Context:** The audit starter called `AttachmentAuditHook` (an `attachment.spi` interface) directly
— starter-to-starter coupling. Marketplace is the correct orchestrator.

**Decision:** `AuditActivityEnrichHook` SPI added to `audit.spi`. Audit starter calls it via
`ObjectProvider`. Marketplace implements `ActivityEnrichHookImpl`, which delegates to
`ObjectProvider<AttachmentAuditHook>`.

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
`AuditSnapshotBinder` directly via `ComponentFactory<AuditPort>`.

**Consequences:** Do not re-introduce `AuditUiPort` or `AttachmentGalleryPort`.

---

## ADR-015: MediaSummary reclassified as DTO
**Status:** Accepted

**Context:** `MediaSummary` was a return-type record exposed by `AttachmentPort` but lived under
`attachment.spi` — wrong package for a data carrier.

**Decision:** Moved to `attachment.dto`, renamed `AttachmentMediaSummaryDto`.

**Consequences:** `*.spi` is for interfaces and extension points; data records belong in `*.dto`.
