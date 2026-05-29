# Architecture & Technical Decisions — platform-commons

---

## 2026-05-22 — Package semantics: `api` vs `spi` vs `dto`

**Decision:** Three sub-packages inside each subsystem namespace carry distinct roles:

- `*.api` — contracts that **marketplace places on its own classes** so the starter can read them: marker interfaces (`AuditableSnapshot`) and annotations (`@AuditedField`, `@ConditionalOnAuditEnabled`). Only `audit.*` has an `api` package; attachment needs no marker contracts from marketplace.
- `*.spi` — **extension-point interfaces** declaring a callback boundary between modules. Call direction and semantic role are encoded in the interface suffix (see the 7-suffix convention below).
- `*.dto` — **pure data carriers** crossing the module boundary, named with the `Dto` suffix. No behavior, no framework annotations.

**Why:** Without this split, a reader opening `audit.spi` would find both callable service facades and passive marker types — two very different things that demand different implementation strategies. The `api` / `spi` / `dto` labels make the reader's job explicit before they open a single file.

**Rule:** do not add behavior to `*.dto` classes; do not add Spring annotations to `*.api` markers; do not put data records in `*.spi`.

---

## 2026-05-22 — SPI interface naming convention: Port and Hook

**Decision:** Each suffix in `platform-commons/*.spi` encodes the call direction and semantic role:

| Suffix | Caller → Implementor | Semantic role |
|--------|---------------------|---------------|
| `*Port` | marketplace → starter | Service facade: marketplace issues commands/queries to the starter |
| `*Hook` | starter → marketplace | Starter calls back for domain data, events, or UI contributions |

Current assignments: `AuditPort`, `AttachmentPort`, `AuditUiPort`, `AttachmentGalleryPort` (`*Port`); `CurrentActorHook`, `EntityNameHook`, `AuditDomainHook`, `ActivityFieldsHook`, `ActivityRowHook`, `ActivityRenderHook`, `MediaChangeHook`, `AttachmentAuditHook` (`*Hook`).

**Why:** The initial 7-suffix convention (`*Extension`, `*Consumer`, `*Provider`, `*Resolver`, `*Checker`, `*Binding`) created too many distinctions with no practical difference in implementation strategy. All "starter → marketplace" callbacks were consolidated under `*Hook`; the two directions (marketplace→starter and starter→marketplace) are the only distinctions that matter.

**Rule:** new suffixes require a DECISIONS.md entry. Existing suffixes must not be repurposed for a different direction or role.

---

## 2026-05-19 — `MediaSummary` reclassified as DTO

**Decision:** `MediaSummary` was a return-type record exposed by `AttachmentPort.getMediaSummary(...)`. It lived under `attachment.spi` but has no behavior — it is a display-ready data carrier. Moved to `attachment.dto` and renamed `MediaSummaryDto` for symmetry with `audit.dto.ActivityItemDto` / `EntityHistoryDto`.

**Why:** `*.spi` is for interfaces and extension points; data records belong in `*.dto`. The `Dto` suffix makes the role obvious at the import site (no need to open the file to learn that this is a value object, not a callback).

**Rejected:** Keeping `MediaSummary` next to its SPI for proximity — proximity is not a package-layout principle; semantic role is. Both `attachment.spi.AttachmentPort` and `attachment.dto.MediaSummaryDto` are tiny single-file packages today; that is fine.

---

## 2026-05-19 — Storage SPI moved out of contracts

**Decision:** `StorageService` and `@ConditionalOnStorageEnabled` were removed from `platform-commons` (package `attachment.storage`) and moved into `attachment-spring-boot-starter` (package `org.ost.attachment.storage`). The `attachment.storage` package no longer exists in contracts; the `attachment.*` contract surface is now SPI-only (`attachment.spi`).

**Why:** No module outside `attachment-spring-boot-starter` referenced `StorageService` or the conditional. They were contracts in name only — no cross-module consumer. `platform-commons` is reserved for types crossed by ≥2 modules; single-consumer interfaces belong with their consumer.

**Superseded (2026-05-23):** `@ConditionalOnAttachmentEnabled` and the `attachment.enabled` property were removed entirely. There is no scenario where the starter jar is present but the subsystem should be disabled — presence in the classpath is the toggle. UI components degrade via `ObjectProvider.ifAvailable()` without needing a flag.

**Rejected:** Keeping the SPI in contracts "in case" a future module wanted a `StorageService` — speculative. If a non-attachment module ever needs blob storage, the interface can be promoted back at that time.

---

## 2026-05-29 — `AuditableSnapshot` uses `Id.NAME` polymorphism; no subtype registration in starter

**Decision:** `@JsonTypeInfo` on `AuditableSnapshot` uses `Id.NAME` (not `Id.CLASS`). Concrete snapshot DTOs live in marketplace-app and carry `@JsonTypeName("stable_short_name")`. The `platform-commons` interface only declares the type-info policy — it never registers subtypes. Registration happens in `marketplace-app/JacksonConfig` via `@PostConstruct registerAuditSnapshotSubtypes(auditObjectMapper)`.

**Why:** The interface in `platform-commons` must not know about its implementations (those live in the consuming application). `Id.CLASS` couples the stored JSON to the class path — a class rename or package move silently breaks existing DB rows. `Id.NAME` with short stable names makes the discriminator an explicit contract, not an implementation detail.

**Rule:** `platform-commons` must never import or reference concrete `AuditableSnapshot` implementations. Subtype registration always stays in the consuming application.

---

## Ongoing — Shared kernel: contracts, not implementations

**Decision:** `platform-commons` contains only pure Java: DTOs, domain events, SPI interfaces, annotations, and conditional markers. No Spring Boot autoconfiguration, no Spring beans, no framework annotations beyond `@interface`.

**Why:** Every module depends on `platform-commons`. If it pulled in Spring Boot or any framework, every module would inherit that transitive dependency — including `sql-engine` which is intentionally framework-free. Keeping contracts pure Java preserves the dependency hierarchy.

**Rejected:** Placing `@ConditionalOnAuditEnabled` in Spring config classes inside this module — confirmed that `@ConditionalOnProperty` requires Spring context, which contracts must not have. (`@ConditionalOnAttachmentEnabled` was relocated to `attachment-spring-boot-starter` itself on 2026-05-19 — see top entry.)

---

## 2026-05-29 — Starters inject hooks as required beans; only marketplace uses ObjectProvider

**Decision:** Starters inject `*Hook` implementations as plain required fields (direct `@RequiredArgsConstructor`). `ObjectProvider` and `@Autowired(required = false)` are forbidden inside starters for hook dependencies.

**Why:** The module dependency is strictly one-directional — `marketplace-app` depends on starters, never the reverse. A starter can only be on the classpath when `marketplace-app` (or an equivalent consuming application) is present. That application is always responsible for providing all hook implementations. There is no runtime scenario where a starter is loaded but a required hook bean is absent. Using `ObjectProvider` in a starter implies the hook is optional, which is architecturally false and misleads readers.

`marketplace-app`, by contrast, uses `ObjectProvider` for all starter ports and components — because starters are genuinely optional and the app must degrade gracefully when they are absent.

**Rule:**
- Starters: `private final CurrentActorHook currentActorHook;` — no `ObjectProvider`, no `required = false`.
- Marketplace: `private final ObjectProvider<AuditPort> auditPort;` — always `ObjectProvider` for starter dependencies.

---

## Ongoing — SPI pattern for cross-module extension

**Decision:** Cross-module extension points are defined as SPI interfaces in `platform-commons` (e.g. `AttachmentGalleryPort`, `MediaChangeHook`, `ActivityRowHook`, `AttachmentPort`, `AuditUiPort`). Each starter provides its own implementation; `marketplace-app` calls them via `ObjectProvider.ifAvailable()`. (`StorageService` was an SPI candidate but had no cross-module consumer — moved into `attachment-spring-boot-starter` on 2026-05-19.)

**Why:** Modules must not know about each other — only about contracts. The SPI pattern lets `attachment-spring-boot-starter` extend audit history without `audit-spring-boot-starter` importing attachment types.

---

## 2026-05-18 — Attachment lifecycle: SPI ports replace ApplicationEvents

**Decision:** Dropped the three advertisement-scoped events (`AdvertisementDeletedEvent`, `AdvertisementRestoredEvent`, `AdvertisementMediaUpdatedEvent`) along with the `attachment.event` package. Cross-module attachment lifecycle is now carried by SPIs:

- **`AttachmentPort`** (domain → starter): `softDeleteAll(EntityType, Long, Long)`, `restoreToSnapshot(EntityType, Long, int, Long)`, `getMediaSummary(EntityType, Long)`. Symmetric with `AuditPort`. `NoOpAttachmentPort` activates when storage is disabled.
- **`MediaChangeHook`** (starter → domain): `onMediaChanged(EntityType, Long)`. The starter calls it via `ObjectProvider.ifAvailable()`; consumers query current state via `AttachmentPort.getMediaSummary(...)` and update their own denormalized columns.
- **`MediaSummaryDto(displayUrl, contentType, count)`** (`attachment.dto`) — display-ready record returned by `getMediaSummary`; the starter resolves YouTube → thumbnail URL, generic embed → `null`, image/uploaded-video → original URL.

**Why:** Events were a one-way pipe with no return value, forcing the starter to denormalize derived fields (URLs, counts) into the event payload and the domain to listen and translate. The SPI surface is symmetric with `AuditPort`, gives the caller a direct return path, and keeps derived fields inside the starter where the truth lives. The `MediaChangeConsumer` SPI intentionally carries only `(entityType, entityId)` so the contract surface does not leak attachment internals.

**Rejected:** Keeping events alongside the SPI — splits the contract surface and makes "which path does the starter use" ambiguous.

---

## 2026-05-19 — User-domain knowledge purged from contracts; actor-centric naming

**Decision:** All user-domain types removed from `platform-commons`; SPIs renamed so the audit/attachment surface speaks about "actors" (whoever performs the action) and "subjects" (whoever is observed), not "users".

- `Role` enum → moved to `marketplace-app` (`org.ost.marketplace.entities`). It was never used by either starter — only by domain code.
- `UserSettings` → moved to `marketplace-app`. Contracts no longer ship a settings record.
- `UserSnapshotState` → deleted from `audit.dto`. The starter does not parse user payloads; consumers (`marketplace-app`) read raw `SnapshotContent` via `AuditPort.getSnapshotContent` / `getPreviousSnapshotContent` and deserialize their own `UserSnapshot`.
- `AuditPort.getUserStateBefore(Long)` / `getUserStateAt(Long)` → deleted. Replaced by the generic pair `getSnapshotContent(Long, EntityType)` / `getPreviousSnapshotContent(Long, EntityType)`.
- `AuditUserProvider` / `CurrentUserProvider` → renamed to `CurrentActorHook` (`getCurrentActorId(): Optional<Long>`). "Actor" is the audit-side concept; "user" leaked the marketplace domain.
- `UserActivityExtension` → renamed to `ActivityFeedExtension` → subsequently consolidated into `ActivityRowHook`. `AdvertisementHistoryExtension` → renamed to `MediaHistoryExtension` → removed when attachment-audit coupling was replaced by `AttachmentAuditHook`.
- Attachment public API: every `userId` parameter renamed to `actorId` (no domain semantics).

**Why:** "User" is a marketplace concept. Starters that talk about `userId` cannot be reused in a CRM, ticketing, or content system where the acting principal is an agent, robot, or workflow. The actor/subject vocabulary is generic; consumers map it onto their own domain.

**Rejected:** Keeping `UserSnapshotState` as a "typed convenience" — locks the contract surface to a specific subject shape (name/email/role) and forces every consumer through the same fields.

---

## 2026-05-19 — Activity decoration via SPI: `ActivityRowHook` + `AuditUiPort.buildProfileActivityPanel`

**Decision:** Profile activity panels (per-subject feeds) are now built through `AuditUiPort.buildProfileActivityPanel(ProfileActivityParams)`. Consumers pass in a list of `ActivityRowHook` — an SPI with `entityType()` + `decorate(ActivityItemDto): Component` — to attach per-row UI (e.g. "current state" badges, "restore" buttons) without the starter understanding the snapshot shape.

`SnapshotBinder<T>` (in `audit-spring-boot-starter`) is the canonical generic implementation: it deserializes `ActivityItemDto.snapshotData` into the consumer-provided `Class<T>`, checks a consumer-provided `Predicate<T>` for "is current", and optionally fires a consumer-provided `BiConsumer<Long, T>` for restore. Marketplace consumers (`SettingsOverlay`, `UserViewOverlayModeHandler`) build one `SnapshotBinder<SettingsSnapshotDto>` and/or `SnapshotBinder<UserSnapshotDto>` per panel.

**Why:** The previous pattern parsed snapshot JSON inside the starter to decide rendering — coupling the starter to specific user/settings shapes. With `ActivityRowHook`, the starter only knows: "for this row's entityType, ask the hook what (if any) decoration to attach." The shape of the snapshot stays in the consumer.

**Rejected:**
- Decorator wrapper around `ProfileActivityPanel` — adds an extra layer with no payoff; the SPI list is enough.
- Abstract `ActivityRowDecorator` requiring subclasses per shape — duplicates the Builder+Parameters pattern already used everywhere else (`Configurable<T, P>` per CLAUDE.md).

---

## 2026-05-18 — Audit/attachment contract symmetry cleanup

**Decision:** Reorganised cross-module SPIs so audit and attachment contracts now mirror each other exactly:

- **Audit-only SPIs** (`AuditActorNameResolver`, `AuditEntityExistenceChecker`, `ActivityItemFieldsProvider`, `UserActivityExtension`, `AdvertisementHistoryExtension`) moved from `core.spi/` to `audit.spi/`. Subsequently folded into the `*Hook` convention and renamed (see 2026-05-22 SPI naming decision).
- **`AuditPort`** moved from `audit.api/` to `audit.spi/` — symmetric with `attachment.spi.AttachmentPort`. `audit.api/` now holds only annotations and snapshot markers (`AuditableSnapshot`, `AuditedField`, `@ConditionalOnAuditEnabled`).
- **`CurrentUserProvider`** (new, in `core.spi/`) replaced both `audit.spi.AuditUserProvider` and `attachment.spi.AttachmentCurrentUserProvider`. Subsequently renamed to `CurrentActorHook` (see 2026-05-19 actor-centric naming decision).
- **`AttachmentEntityDisplayNameResolver`** deleted — was dead code; `EntityNameHook` (`core.spi/`) is the single canonical form.

**Why:** Incremental growth left asymmetric layouts (two ports under different package families, two identical user-provider SPIs, an audit-only SPI living in `core.spi/`). A reader skimming `core.spi/` could not tell which interfaces were cross-cutting vs audit-only. The cleaned-up layout makes ownership obvious: `core.spi/` = used by ≥2 subsystems, `audit.spi/` = audit only, `attachment.spi/` = attachment only.

**Rejected:** Renaming both user providers to `*CurrentUserProvider` and keeping them as separate interfaces — keeps duplicated implementation in marketplace-app while fixing only the cosmetic asymmetry.

---

## 2026-05-16 — Package restructure: core / audit / attachment

**Decision:** Replaced flat scattered packages (`events/`, `events/dto/`, `events/spi/`, `events/model/`, `audit/`, `config/`, `dto/`, `entities/`, `i18n/`, `ui/rules/`, `spi/storage/`) with three semantic groups (final layout after the 2026-05-18 symmetry cleanup):

```
core.config    — CleanupProperties
core.i18n      — I18nService, InstantFormatter, LocaleProvider, TranslationKey
core.model     — ActionType, ChangeEntry, EntityType
core.spi       — CurrentActorHook, EntityNameHook
ui             — Configurable, ComponentBuilder, Initialization, Provider

audit.api      — AuditableSnapshot, AuditedField, @ConditionalOnAuditEnabled
audit.codec    — SnapshotCodec
audit.dto      — ActivityItemDto, EntityHistoryDto, SnapshotContentDto, SnapshotPayloadDto
audit.spi      — AuditPort, AuditUiPort, AuditDomainHook,
                 ActivityFieldsHook, ActivityRowHook, ActivityRenderHook

attachment.dto     — MediaSummaryDto
attachment.model   — MediaContentType
attachment.spi     — AttachmentPort, AttachmentGalleryPort,
                     MediaChangeHook, AttachmentAuditHook
```

The `attachment.event` package was removed on 2026-05-18 — see the SPI-replaces-events entry above. The `attachment.storage` package was removed on 2026-05-19 — `StorageService` and the subsystem conditional moved into `attachment-spring-boot-starter`.

`PaginationDefaults` moved to `marketplace-app` (only used there).

**Why:** The old structure mixed audit, attachment, and shared types in `events.*` with no clear ownership. The new structure makes it immediately obvious which package belongs to which subsystem and where SPIs live.
