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
audit.spi      — AuditPort, AuditDomainHook, AuditActivityEnrichHook

attachment.dto     — AttachmentMediaSummaryDto, AttachmentItemDto, TempAttachmentDto
attachment.model   — AttachmentMediaContentType
attachment.spi     — AttachmentPort, AttachmentAuditPort (AttachmentMediaChangeHook does not exist;
                     AttachmentAuditPort's call direction is the *Port semantic, see ADR-025)
attachment.util    — YoutubeUtil

user.dto       — UserDto, UserFilterDto, UserProfileDto, UserSettingsDto,
                 UserSnapshotDto, SettingsSnapshotDto, SignUpDto
user.model     — Role
user.spi       — UserPort, UserAccountPort, UserAuthorizationPort, UserPreferencesPort,
                 AuthenticatedPrincipal, UserSettingsChangedHook

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
`taxon.*` packages added 2026-06-26 when `taxon-spring-boot-starter` was introduced.

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

Current `*Port` interfaces: `AuditPort`, `AttachmentPort`, `AttachmentAuditPort`, `UserPort`,
`UserAccountPort`, `UserAuthorizationPort`, `UserPreferencesPort`, `AdvertisementPort`, `TaxonPort`,
`ProviderProfilePort`.
Current `*Hook` interfaces: `CurrentActorHook`, `AuditDomainHook`, `AuditActivityEnrichHook`,
`UserSettingsChangedHook`.

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

## ADR-006: ComponentFactory<T> — typed wrapper over ObjectProvider<T>
**Status:** Accepted

**Context:** Optional starter dependencies in marketplace-app need typed, ergonomic access
without unchecked casts. Raw `ObjectProvider<T>` doesn't know about the `Configurable<T, P>`
protocol.

**Decision:** `ComponentFactory<T>` (`platform-commons/.../core/ComponentFactory.java`) is a plain
class taking a constructor-injected `ObjectProvider<T>`, with **one explicit `@Bean` method per
concrete type**, hand-written in each consuming config class — not resolved generically via
reflection or `InjectionPoint`. The base class exposes `get()`, `getIfAvailable()`,
`findIfAvailable()`, `ifAvailable(Consumer<T>)`; `build(P params)` lives only on the
marketplace-app subclass `UiComponentFactory<T>`.

**This `@Bean` must exist on every consuming config class that holds a mandatory field of that
port type, not just the port's defining starter** — an easy gap to miss since nothing fails at
compile time, only at runtime once the dependent starter is actually absent from the build.

**Consequences:**
- All optional starter components in marketplace-app use `ComponentFactory<T>` injection; direct
  `ObjectProvider<T>` fields are not used for this purpose.
- Rejected: singleton factory with `<T> T get(Class<T> type)` — pushes a type token to every call
  site, requires an unchecked cast, unsound at compile time.
- Rejected: a single generic `InjectionPoint`-resolved factory bean — one explicit `@Bean` per
  type is more boilerplate but fully type-safe and requires no reflection.

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
overrider), leaving every implementation on the default `true`; a later cleanup pass then removed
`isRestorable()` (and the analogous `isVisible()`) from `AuditableSnapshot` entirely as dead
code. If a metadata/event snapshot type ever reappears, reintroduce the flag with it — do not
add it preemptively.

---

## ADR-010: Attachment lifecycle — SPI ports replace ApplicationEvents
**Status:** Accepted

**Context:** Events were a one-way pipe with no return value, forcing the starter to denormalize
derived fields into the event payload and the domain to listen and translate.

**Decision:** Dropped `AdvertisementDeletedEvent`, `AdvertisementRestoredEvent`,
`AdvertisementMediaUpdatedEvent`. Cross-module attachment lifecycle is carried by SPIs instead:
`AttachmentPort` (domain → starter) exposes `softDeleteAll(EntityRef, Long actorId)`,
`getMediaSummary(EntityRef)`, and restore via `restoreToUrls(EntityType, Long, String[])`/
`restoreToUrlsAndCapture(...)`; `AttachmentMediaSummaryDto` (`attachment.dto`) is the
display-ready record `getMediaSummary` returns. `AttachmentMediaChangeHook` (the starter → domain
direction this ADR originally also introduced) does not exist — it had zero implementations and
was deleted entirely rather than kept as dead API surface (see
`attachment-spring-boot-starter/CLAUDE.md`).

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
  `currentActorHook` only. `AttachmentMediaChangeHook` itself was deleted entirely (see ADR-010
  above), not just left unimplemented, so there is no exception left to document — this ADR's rule
  holds without carve-outs today.

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

**Context:** Optimistic locking (`@Version`) was added to `Advertisement`, `User`,
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

---

## ADR-021: `AuditTimelineItemDto.expandedChanges()` — a narrow, documented exception to "`*.dto` has no behavior"

**Status:** Accepted

**Context:** The same three-line "if there's a snapshot, expand the changes against it; otherwise return the
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

**Amendment (same precedent, `core.model` this time):** `ChangeEntry` (`core.model`, not `*.dto`)
gained the same shape of method twice — `replaceIfField()` then `mapField()` — both pure
derivations over the record's own fields, no external dependencies. `platform-commons/CLAUDE.md`'s
"Narrow exception" note was reworded to state the principle package-agnostically instead of
literally scoped to `*.dto`, since this ADR's own reasoning already treated it that way.

---

## ADR-023: `Class<T> targetClass` type-token added to `AuditPort.getSnapshotContent()` / `AuditDomainHook.castIfKnown()`

**Status:** Accepted

**Context:** `AuditDomainHookImpl.castIfKnown()` used a `switch` over the four known snapshot DTO
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
- The next new snapshot-bearing domain (F-04's `ActorProfileSnapshotDto`) must
  add its own `schemaVersion` record component + `SCHEMA_VERSION` constant, following this pattern
  — not a `@SchemaVersion` annotation (removed, no longer exists).

---

## ADR-025: Batch G governance cleanup — DTO boundary, Hook→Port rename

**Status:** Accepted

**Context:** A repo-wide SOLID/DRY review surfaced two independent `platform-commons` governance
findings, grouped together since both touch this module's own naming/package rules.

**Decision:**
- `SettingsSnapshotDto.from(UserSettingsDto settings)` was removed — it reached into a sibling
  DTO's fields to construct itself, exceeding the `*.dto` package's "pure derivation over its own
  fields" exception (ADR-002/ADR-021). Moved to
  `UserSettingsService.toSettingsSnapshot(UserSettingsDto)` in `user-spring-boot-starter`, as a
  genuine instance method — `UserService` holds `UserSettingsService` as a constructor-injected
  field and calls it like any other collaborator; safe because `UserSettingsService`'s own
  constructor deps (`UserSettingsRepository`, `ComponentFactory<UserSettingsChangedHook>`,
  `ComponentFactory<AuditPort>`) never reference `UserService`, so no circular dependency results.
- `AttachmentAuditHook` renamed to `AttachmentAuditPort` (and its implementation,
  `AttachmentAuditHookImpl` → `AttachmentAuditPortImpl`). Its call direction was always marketplace
  calling into the attachment starter (`AdvertisementAuditEnrichService` calls it), which per
  ADR-003's own table is the `*Port` semantic, not `*Hook` — the interface had carried the wrong
  suffix since it was introduced.

**Consequences:** Pure renames/moves, no behavior change — same method signatures on
`AttachmentAuditPort`.

## ADR-026: One starter, multiple `*Port` interfaces — `UserPort` split into 4

**Status:** Accepted

**Also affects:** user-spring-boot-starter

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

## ADR-027: `ProviderProfilePort` added — F-04 Batch B, `provider-profile-spring-boot-starter`

**Status:** Accepted

**Also affects:** provider-profile-spring-boot-starter

**Context:** F-04 adds a "provider profile" concept — any actor can optionally
describe themselves as a service provider (`MASTER`/`SHOP`/`SUPPORT`). The original single-table
design (merging this with locale/settings into one `actor_profile` row) was superseded before
implementation by a 2026-07-31 reconsideration: three tables, not one — `user_information`
(auth, unchanged), `user_preferences` (locale/settings, Batch A, already shipped), and a new
standalone `provider_profile` table/module (this ADR).

**Decision:** New package `org.ost.platform.providerprofile` (`model.ProviderKind`,
`dto.ProviderProfileDto`/`ProviderProfileSaveDto`/`ProviderProfileFilterDto`/
`ProviderProfileSnapshotDto` — `schemaVersion` per ADR-024 — `spi.ProviderProfilePort`), plus
`EntityType.PROVIDER_PROFILE`. `ProviderProfilePort`'s shape mirrors `AdvertisementPort` closely
(`getFiltered`/`count`/`findById`/`save`/`delete`/`findExistingIds`/`findOwnerIds`) — deliberate
symmetry with the established starter pattern, not an accident (see `docs/ai/adr-index.md` for the
"isn't this just a copy?" discussion this raised during implementation). Backed by
a new `provider-profile-spring-boot-starter` module owning `ProviderProfile` entity/repository/
service/port-impl/autoconfiguration — this batch is backend-only, no UI, no audit-write path yet
(that's a later batch).

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
  orchestration service — writes category assignments directly via `TaxonPort.replaceAssignments()`.
  This batch has no marketplace-app "SaveService" yet
  (that's a later batch, alongside the actual `AuditPort.record()` call), so there was no other layer
  to put it in.

**Found and fixed during `/code-review`'s 8-angle pass (Batch B):** `ProviderProfileFilterDto
.cityTaxonId` was declared but never wired into the repository's `SqlFilterBuilder` (a dead filter
field — fixed, now bound to `pp.city_taxon_id`); `ProviderProfileService.delete()` unconditionally
cleared taxon assignments before checking the row existed, unlike `AdvertisementService.delete()`'s
existence-guarded pattern (fixed, same guard added); an unnecessary empty-set defensive check in
`ProviderProfileEnrichmentService.findCities()` was removed (`TaxonPort.findByIds()`'s own contract
already handles an empty input set). Two further real findings — `HTML_SANITIZER`/`sanitizeHtml()`
duplication with `AdvertisementService`, and the shared "stale id during concurrent delete" edge
case both `AdvertisementService.save()` and `ProviderProfileService.save()` have — were kept out of
this batch (they require touching `advertisement-spring-boot-starter`, outside Batch B's own
scope) and filed as a follow-up batch, at the user's explicit direction, rather than the generic
deferred-findings bucket.

**Consequence:** `EntityType.USER_SETTINGS` keeps being used unchanged for the Settings tab
(preferences never merged into `provider_profile`, so the earlier "keep as historical tag or
migrate" open question from the superseded single-table design is moot). The next batch must land
before the unified "My Account" overlay batch, per the updated gate tracked in the backlog.
Starters may call other starters' `*Port`s via `platform-commons` — this is the correct pattern
for cross-domain SPI composition; direct starter-to-starter imports remain forbidden regardless.

---

## ADR-028: `AdvertisementPort`/`ProviderProfilePort` drop `Locale` from `getFiltered`/`findById`/`findByActorId`
**Status:** Accepted

**Context:** Both ports took a `Locale` parameter on their read methods solely to pass it through
to `TaxonPort.getForEntities(..., locale)` for translated category/city names — the enrichment
logic living inside `AdvertisementEnrichmentService`/`ProviderProfileEnrichmentService`, both
starter-internal at the time. `Locale` is a presentation concern; nothing else in either port's SQL
query or persisted-column shape ever used it. See `marketplace-orchestrator/DECISIONS.md` ADR-001
for the extraction this is part of.

**Decision:** Once the enrichment services moved to `marketplace-orchestrator`, `Locale` moved with
them — `AdvertisementPort.getFiltered()`/`findById()` and `ProviderProfilePort.getFiltered()`/
`findById()`/`findByActorId()` no longer take a `Locale` parameter at all. Callers that need
translated display fields call the orchestrator's `AdvertisementDisplayEnrichmentService`/
`ProviderProfileDisplayEnrichmentService` afterward, passing `Locale` directly to that call instead.

**Consequences:** Every real call site across both starters and `marketplace-app` (Views,
`OgMetaRequestListener`, `SitemapController`, the moved `AdvertisementSaveService`) stopped passing
the now-removed parameter — a mechanical but repo-wide ripple, not a behavior change. Both ports'
raw return DTOs (`AdvertisementInfoDto`/`ProviderProfileDto`) are unchanged in shape — their
composition-enriched fields (category/city/actor names, media summary) simply arrive unset until
the caller explicitly enriches, matching the DTOs' pre-existing hybrid nature (domain-owned fields
+ composition-enriched fields in one flat class, unchanged by this ADR).

## ADR-029: `UiLabelHook`/`SessionActorHook` forwarder SPIs do not live in `platform-commons`

**Status:** Accepted

**Context:** Moving `AuditDomainHookImpl`/`CurrentActorHookImpl` (and, later, `ActivityEnrichHookImpl`)
out of `marketplace-app/spi` and into `marketplace-orchestrator` needed a way for those classes to
reach two UI-shell resources — translations and the current actor's session id — without
`marketplace-orchestrator` importing `marketplace-app` types directly (the dependency direction
only ever runs `marketplace-app -> marketplace-orchestrator`, never the reverse). Moving the whole
300-key `I18nKey` enum (or `AuthContextService`) into `platform-commons` was rejected: the
overwhelming majority of those keys are `marketplace-app`-only Vaadin UI strings, unrelated to any
Hook, and relocating them would contradict `marketplace-app/CLAUDE.md`'s "all UI i18n lives here"
rule for a handful of keys.

**Decision:** `UiLabelHook`/`SessionActorHook` (and a later addition, `CurrentLocaleHook`) are
forwarder SPIs — but they do **not** live in `platform-commons`, unlike every other `*Hook` in this
project. Since `marketplace-app` already legally depends on `marketplace-orchestrator`, any type
`marketplace-orchestrator` defines is already visible to `marketplace-app`; the
*Hook-must-live-in-platform-commons rule exists specifically for starter optionality, which never
applied to this pair — no starter calls either interface, and `marketplace-orchestrator` is a
mandatory, never-optional dependency of `marketplace-app`. All three forwarder SPIs live in
`org.ost.orchestrator.spi` instead, implemented by thin `*Impl` classes in `marketplace-app/spi`
wrapping `I18nService`/`AuthContextService`/`LocaleProvider` — see
`marketplace-orchestrator/DECISIONS.md` and `marketplace-orchestrator/CLAUDE.md`'s "Forwarder SPI
pattern" for the full design and its evolution.

**Consequences:** `platform-commons/CLAUDE.md`'s `*Hook` row names `marketplace-orchestrator` as a
second legitimate `*Hook` caller alongside "starter," but this specific pair contributes **zero
new types** to `platform-commons` itself. `ArchitectureRulesTest
.marketplace_app_must_not_depend_on_platform_commons_spi_directly` carries a named allow-list
entry for these forwarder SPIs living outside `platform-commons`.
