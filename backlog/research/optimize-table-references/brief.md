# Brief: optimize-table-references

Purpose: what the planner is asked to find out and plan; written by the main thread, read by the planner.

## Goal
Find how to reduce what has to be written, understood, tested and maintained around database tables and cross-module references, without losing readability, explicitness, type safety, testability or the architecture. Topic: entityType+entityId pairs passed as separate parameters where an EntityRef exists, actor columns, polymorphic owner tables, and cleanup code that exists only because the schema does not enforce an invariant. Java code is optimized as code, changelogs as schema, documentation as documentation.

## Task
none yet (related: backlog/tasks/improvement-241-db-integrity-across-modules.md, backlog/tasks/improvement-225-code-reduction-findings.md; no duplicate findings)

## Context
Starters own their tables and changelogs, no cross-starter dependency, no FK across modules (reference by identity), cross-domain use cases live in marketplace-orchestrator (out of scope, never changed). EntityRef(EntityType, Long) exists in platform-commons; attachment repositories/services still pass the pair as two parameters. Do not propose a shared registry table or cross-module FKs (compared and rejected in the research db-normalization-module-decoupling); PostgreSQL stays.

## Where to look in the project
The files below (all outside marketplace-orchestrator/), plus docs/best-practices.md, .claude/rules.md, the relevant .claude/rules/<module>.md, .claude/nav/adr-index.md, backlog/BACKLOG.md and the findings inventory.

- advertisement-spring-boot-starter/src/main/java/org/ost/advertisement/repository/AdvertisementRepository.java
- advertisement-spring-boot-starter/src/main/resources/db/advertisement-changelog/changes/01-advertisement-schema.xml
- apikey-spring-boot-starter/src/main/java/org/ost/apikey/entity/ApiKey.java
- apikey-spring-boot-starter/src/main/java/org/ost/apikey/repository/ApiKeyRepository.java
- apikey-spring-boot-starter/src/main/java/org/ost/apikey/services/ApiKeyService.java
- apikey-spring-boot-starter/src/main/java/org/ost/apikey/spi/ApiKeyPortImpl.java
- apikey-spring-boot-starter/src/main/resources/db/apikey-changelog/changes/01-apikey-schema.xml
- attachment-spring-boot-starter/src/main/java/org/ost/attachment/entities/Attachment.java
- attachment-spring-boot-starter/src/main/java/org/ost/attachment/repository/AttachmentRepository.java
- attachment-spring-boot-starter/src/main/java/org/ost/attachment/repository/AttachmentSnapshotRepository.java
- attachment-spring-boot-starter/src/main/java/org/ost/attachment/services/AttachmentService.java
- attachment-spring-boot-starter/src/main/java/org/ost/attachment/services/AttachmentSnapshotService.java
- attachment-spring-boot-starter/src/main/java/org/ost/attachment/spi/AttachmentAuditPortImpl.java
- attachment-spring-boot-starter/src/main/java/org/ost/attachment/spi/DefaultAttachmentPort.java
- attachment-spring-boot-starter/src/main/resources/db/attachment-changelog/changes/01-attachment-schema.xml
- audit-spring-boot-starter/src/main/java/org/ost/audit/repository/AuditLogProjection.java
- audit-spring-boot-starter/src/main/java/org/ost/audit/repository/AuditLogRepository.java
- audit-spring-boot-starter/src/main/java/org/ost/audit/services/AuditReadService.java
- audit-spring-boot-starter/src/main/java/org/ost/audit/services/DefaultAuditPort.java
- audit-spring-boot-starter/src/main/resources/db/audit-changelog/changes/01-audit-schema.xml
- contact-spring-boot-starter/src/main/java/org/ost/contact/entity/ContactInfo.java
- contact-spring-boot-starter/src/main/java/org/ost/contact/entity/ContactView.java
- contact-spring-boot-starter/src/main/java/org/ost/contact/repository/ContactRepository.java
- contact-spring-boot-starter/src/main/java/org/ost/contact/services/ContactService.java
- contact-spring-boot-starter/src/main/java/org/ost/contact/spi/ContactPortImpl.java
- contact-spring-boot-starter/src/main/resources/db/contact-changelog/changes/01-contact-schema.xml
- contact-spring-boot-starter/src/main/resources/db/contact-changelog/changes/03-contact-view-entity-remarks.xml
- feedback-spring-boot-starter/src/main/java/org/ost/feedback/entity/FeedbackAggregate.java
- feedback-spring-boot-starter/src/main/java/org/ost/feedback/entity/Feedback.java
- feedback-spring-boot-starter/src/main/java/org/ost/feedback/repository/FeedbackRepository.java
- feedback-spring-boot-starter/src/main/java/org/ost/feedback/services/FeedbackService.java
- feedback-spring-boot-starter/src/main/java/org/ost/feedback/spi/FeedbackPortImpl.java
- feedback-spring-boot-starter/src/main/resources/db/feedback-changelog/changes/01-feedback-schema.xml
- integration-tests/src/main/java/org/ost/integrationtests/UserTestFixtures.java
- integration-tests/src/test/java/org/ost/integrationtests/level1/advertisement/AdvertisementRepositoryTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level1/apikey/ApiKeyRepositoryTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level1/attachment/AttachmentRepositoryTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level1/attachment/AttachmentServiceTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level1/audit/AuditLogRepositoryTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level1/contact/ContactRepositoryTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level1/contact/ContactServiceTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level1/feedback/FeedbackCommentSnapshotDtoTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level1/feedback/FeedbackRepositoryTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level1/feedback/FeedbackServiceTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level1/feedback/FeedbackSnapshotDtoTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level1/promotion/PromotionPlacementRepositoryTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level1/providerprofile/ProviderProfileRepositoryTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level1/providerprofile/ProviderProfileServiceTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level1/taxon/TaxonAssignmentRepositoryTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level1/taxon/TaxonPortResolveCategoryAndCityFilterTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level1/user/UserPreferencesRepositoryTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level1/user/UserServiceTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level2/orchestrator/FeedbackAccessServiceTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level2/orchestrator/FeedbackCommentWriteServiceTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level3/restapi/AuditTimelineMatrixScenarioTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level3/restapi/ProviderProfileAuthorizationScenarioTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level3/restapi/ProviderProfilePaginationScenarioTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level3/restapi/UserAdminScenarioTest.java
- integration-tests/src/test/java/org/ost/integrationtests/level3/restapi/UserSettingsScenarioTest.java
- marketplace-app/src/main/java/org/ost/marketplace/services/i18n/I18nKey.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/components/attachment/AttachmentGallery.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/components/attachment/AttachmentGalleryService.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/components/audit/AuditActivityListRenderer.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/components/audit/AuditActivityPanel.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/components/audit/AuditActivityRowRenderer.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/components/audit/AuditTimelineListRenderer.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/components/audit/AuditTimelineRowRenderer.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/components/audit/EntityActivityOverlay.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/components/ContactRevealPanel.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/components/FeedbackPanel.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/header/account/AccountNameFormModeHandler.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/header/account/ProviderProfileFormOverlayModeHandler.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/header/account/ProviderProfileViewModeHandler.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/header/settings/SettingsFormModeHandler.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/tabs/advertisements/AdvertisementCardView.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/tabs/advertisements/overlay/modes/AdvertisementFormOverlayModeHandler.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/tabs/advertisements/overlay/modes/AdvertisementViewOverlayModeHandler.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/tabs/providers/overlay/ProviderProfileCatalogViewModeHandler.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/tabs/referencedata/overlay/modes/AbstractTaxonFormOverlayModeHandler.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/tabs/timeline/query/TimelineFilterMeta.java
- marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/tabs/timeline/query/TimelineQueryBlock.java
- marketplace-mcp/src/main/java/org/ost/mcp/tools/AccountTools.java
- marketplace-mcp/src/main/java/org/ost/mcp/tools/AuditTools.java
- marketplace-mcp/src/test/java/org/ost/mcp/tools/AuditToolsTest.java
- marketplace-rest-api/src/main/java/org/ost/restapi/api/AdvertisementApiController.java
- marketplace-rest-api/src/main/java/org/ost/restapi/api/AdvertisementMediaApiController.java
- marketplace-rest-api/src/main/java/org/ost/restapi/api/ApiKeyController.java
- marketplace-rest-api/src/main/java/org/ost/restapi/api/audit/ActivityHistorySupport.java
- marketplace-rest-api/src/main/java/org/ost/restapi/api/AuditApiController.java
- marketplace-rest-api/src/main/java/org/ost/restapi/api/ContactApiController.java
- marketplace-rest-api/src/main/java/org/ost/restapi/api/FeedbackApiController.java
- marketplace-rest-api/src/main/java/org/ost/restapi/api/ModerationApiController.java
- marketplace-rest-api/src/main/java/org/ost/restapi/api/ProviderProfileApiController.java
- marketplace-rest-api/src/main/java/org/ost/restapi/api/TaxonApiController.java
- marketplace-rest-api/src/main/java/org/ost/restapi/api/UserApiController.java
- marketplace-rest-api/src/main/java/org/ost/restapi/config/ApiWriteRateLimitFilter.java
- marketplace-rest-api/src/test/java/org/ost/restapi/api/audit/ActivityHistorySupportTest.java
- marketplace-rest-api/src/test/java/org/ost/restapi/api/AuditApiControllerTest.java
- marketplace-rest-api/src/test/java/org/ost/restapi/api/error/ApiExceptionHandlerTest.java
- marketplace-rest-api/src/test/java/org/ost/restapi/api/FeedbackApiControllerTest.java
- marketplace-rest-api/src/test/java/org/ost/restapi/api/ModerationApiControllerTest.java
- marketplace-rest-api/src/test/java/org/ost/restapi/api/support/RestApiMockMvcTestSupport.java
- marketplace-rest-api/src/test/java/org/ost/restapi/config/ApiWriteRateLimitFilterTest.java
- platform-commons/src/main/java/org/ost/platform/advertisement/dto/AdvertisementSnapshotDto.java
- platform-commons/src/main/java/org/ost/platform/apikey/spi/ApiKeyPort.java
- platform-commons/src/main/java/org/ost/platform/attachment/spi/AttachmentAuditPort.java
- platform-commons/src/main/java/org/ost/platform/attachment/spi/AttachmentPort.java
- platform-commons/src/main/java/org/ost/platform/audit/api/AuditableSnapshot.java
- platform-commons/src/main/java/org/ost/platform/audit/dto/AuditActivityItemDto.java
- platform-commons/src/main/java/org/ost/platform/audit/dto/AuditTimelineFilterDto.java
- platform-commons/src/main/java/org/ost/platform/audit/dto/AuditTimelineItemDto.java
- platform-commons/src/main/java/org/ost/platform/audit/spi/AuditActivityEnrichHook.java
- platform-commons/src/main/java/org/ost/platform/audit/spi/AuditDomainHook.java
- platform-commons/src/main/java/org/ost/platform/audit/spi/AuditPort.java
- platform-commons/src/main/java/org/ost/platform/contact/dto/ContactInfoDto.java
- platform-commons/src/main/java/org/ost/platform/contact/spi/ContactPort.java
- platform-commons/src/main/java/org/ost/platform/core/model/EntityRef.java
- platform-commons/src/main/java/org/ost/platform/feedback/dto/FeedbackAggregateDto.java
- platform-commons/src/main/java/org/ost/platform/feedback/dto/FeedbackCommentSnapshotDto.java
- platform-commons/src/main/java/org/ost/platform/feedback/dto/FeedbackDto.java
- platform-commons/src/main/java/org/ost/platform/feedback/dto/FeedbackSaveDto.java
- platform-commons/src/main/java/org/ost/platform/feedback/dto/FeedbackSnapshotDto.java
- platform-commons/src/main/java/org/ost/platform/feedback/dto/ModerationQueueItemDto.java
- platform-commons/src/main/java/org/ost/platform/feedback/spi/FeedbackPort.java
- platform-commons/src/main/java/org/ost/platform/promotion/dto/PromotionPlacementDto.java
- platform-commons/src/main/java/org/ost/platform/promotion/spi/PromotionPlacementPort.java
- platform-commons/src/main/java/org/ost/platform/providerprofile/dto/ProviderProfileDto.java
- platform-commons/src/main/java/org/ost/platform/providerprofile/dto/ProviderProfileSnapshotDto.java
- platform-commons/src/main/java/org/ost/platform/providerprofile/spi/ProviderProfilePort.java
- platform-commons/src/main/java/org/ost/platform/taxon/dto/TaxonSnapshotDto.java
- platform-commons/src/main/java/org/ost/platform/taxon/spi/TaxonPort.java
- platform-commons/src/main/java/org/ost/platform/user/dto/SettingsSnapshotDto.java
- platform-commons/src/main/java/org/ost/platform/user/dto/UserSnapshotDto.java
- promotion-spring-boot-starter/src/main/java/org/ost/promotion/repository/PromotionPlacementRepository.java
- promotion-spring-boot-starter/src/main/java/org/ost/promotion/services/PromotionPlacementService.java
- promotion-spring-boot-starter/src/main/java/org/ost/promotion/spi/PromotionPlacementPortImpl.java
- promotion-spring-boot-starter/src/main/resources/db/promotion-changelog/changes/01-promotion-schema.xml
- provider-profile-spring-boot-starter/src/main/java/org/ost/provider/entity/ProviderProfile.java
- provider-profile-spring-boot-starter/src/main/java/org/ost/provider/repository/ProviderProfileRepository.java
- provider-profile-spring-boot-starter/src/main/java/org/ost/provider/services/ProviderProfileService.java
- provider-profile-spring-boot-starter/src/main/java/org/ost/provider/spi/ProviderProfilePortImpl.java
- provider-profile-spring-boot-starter/src/main/resources/db/provider-profile-changelog/changes/01-provider-profile-schema.xml
- query-lib/src/test/java/org/ost/query/filter/SqlOperatorTest.java
- taxon-spring-boot-starter/src/main/java/org/ost/taxon/entities/TaxonAssignment.java
- taxon-spring-boot-starter/src/main/java/org/ost/taxon/repository/TaxonAssignmentRepository.java
- taxon-spring-boot-starter/src/main/java/org/ost/taxon/repository/TaxonRepository.java
- taxon-spring-boot-starter/src/main/java/org/ost/taxon/services/DefaultTaxonPort.java
- taxon-spring-boot-starter/src/main/java/org/ost/taxon/services/TaxonAssignmentService.java
- taxon-spring-boot-starter/src/main/java/org/ost/taxon/services/TaxonService.java
- taxon-spring-boot-starter/src/main/resources/db/taxon-changelog/changes/001-taxon.xml
- user-spring-boot-starter/src/main/java/org/ost/user/repository/UserPreferencesRepository.java
- user-spring-boot-starter/src/main/resources/db/user-changelog/changes/01-user-schema.xml

## Completion criterion
Every file listed is read, or listed as not read with the reason. Findings are concrete (file, symbol, excerpt, estimate, risks, tests), ranked by net benefit and risk, each checked against the backlog, improvement-241 and the ADR index; rejected ideas are listed with reasons.

## Decisions already taken
Application code is never edited during discovery; nothing is committed; marketplace-orchestrator/ is out of scope. Never change module boundaries, dependency direction or layers; never remove a port, hook or interface only for line count; never add a dependency or reflection; never change behavior, contracts, authorization, validation, transaction boundaries, exception semantics or side effects.

## What the researcher may decide
Nothing beyond the goal; conflicts with best practice or with the project's practice are highlighted, the decision is left to the user.
