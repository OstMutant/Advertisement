# platform-commons

The shared kernel — cross-module contracts (`*Port`/`*Hook` SPIs, DTOs, domain enums) every
starter and `marketplace-app`/`marketplace-orchestrator` compile against, with zero dependency on
any of them. No business logic lives here — only the shapes other modules agree to.

## What it provides

- **`core.*`** — cross-cutting types shared by every module: `ComponentFactory` (top-level),
  `core.model` (`ActionType`, `ChangeEntry`, `EntityRef`, `EntityType`), `core.config`
  (`CleanupProperties`), `core.spi` (`CurrentActorHook`), `core.validation` (`ValidRange`).
- **`audit.*`** — the audit subsystem's contracts: `audit.api` (`AuditableSnapshot`), `audit.dto`
  (`AuditActivityItemDto`, `AuditSnapshotContentDto`, `AuditTimelineItemDto`,
  `AuditTimelineFilterDto`), `audit.spi` (`AuditPort`, `AuditDomainHook`,
  `AuditActivityEnrichHook`).
- **`attachment.*`** — `attachment.spi` (`AttachmentPort`, `AttachmentAuditPort`), `attachment.dto`
  (`AttachmentMediaSummaryDto`, `AttachmentItemDto`, `TempAttachmentDto`), `attachment.model`
  (`AttachmentMediaContentType`).
- **`user.*`** — `user.spi` (`UserPort`/`UserAccountPort`/`UserAuthorizationPort`/
  `UserPreferencesPort` — one logical domain split across four narrow interfaces, plus
  `AuthenticatedPrincipal`, `UserSettingsChangedHook`), `user.dto` (`UserDto`, `UserFilterDto`,
  `UserProfileDto`, `UserSettingsDto`, `UserSnapshotDto`, `SettingsSnapshotDto`, `SignUpDto`),
  `user.model` (`Role`).
- **`apikey.*`** — `apikey.spi` (`ApiKeyPort`), `apikey.dto` (`ApiKeySummaryDto`).
- **`advertisement.*`** — `advertisement.spi` (`AdvertisementPort`), `advertisement.dto`
  (`AdvertisementInfoDto`, `AdvertisementFilterDto`, `AdvertisementSaveDto`,
  `AdvertisementSnapshotDto`), `advertisement.model` (`AdKind`).
- **`taxon.*`** — `taxon.spi` (`TaxonPort`), `taxon.dto` (`TaxonDto`, `TaxonTranslationDto`,
  `TaxonSnapshotDto`), `taxon.model` (`TaxonType`).
- **`providerprofile.*`** — `providerprofile.spi` (`ProviderProfilePort`), `providerprofile.dto`
  (`ProviderProfileDto`, `ProviderProfileSaveDto`, `ProviderProfileFilterDto`,
  `ProviderProfileSnapshotDto`), `providerprofile.model` (`ProviderKind`).

## Package structure

Every domain package follows the same three-way split: `<domain>.spi` (interfaces a starter
implements and `marketplace-orchestrator`/`marketplace-app` call through), `<domain>.dto`
(request/response/snapshot records crossing module boundaries), `<domain>.model` (narrow domain
enums). This split — not a single flat package per domain — is what lets a consumer depend on just
the DTO shapes without pulling in SPI contracts it doesn't need, and is why `platform-commons`
itself carries no Spring Boot starter dependency: it's pure contracts, auto-configured nowhere.

## Dependencies

- None on any sibling `org.ost.*` module — this is the one module every starter,
  `marketplace-orchestrator`, and `marketplace-app` can safely depend on without risking a cycle.
- Jackson (`jackson-annotations`/`jackson-databind`), `jakarta.validation-api`, Lombok (optional),
  `spring-data-commons` — DTO serialization/validation/paging types only, never a Spring Boot
  starter or autoconfiguration dependency, since this module is never itself autoconfigured.
