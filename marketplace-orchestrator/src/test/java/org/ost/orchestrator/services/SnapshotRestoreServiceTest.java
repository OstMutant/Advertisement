package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.advertisement.dto.AdvertisementSaveDto;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.advertisement.model.AdKind;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditSnapshotContentDto;
import org.ost.platform.core.StaleWriteException;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSnapshotDto;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.ost.platform.taxon.dto.TaxonSnapshotDto;
import org.ost.platform.taxon.dto.TaxonTranslationDto;
import org.ost.platform.user.dto.SettingsSnapshotDto;
import org.ost.platform.user.dto.UserProfileDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.platform.user.dto.UserSnapshotDto;
import org.ost.platform.user.model.Role;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for {@link SnapshotRestoreService}: snapshot values are mapped onto each domain's save DTO and saved through its normal path. */
@ExtendWith(MockitoExtension.class)
class SnapshotRestoreServiceTest {

    private static final Long ACTOR_ID = 10L;
    private static final Long RESOURCE_ID = 7L;
    private static final Long SNAPSHOT_ID = 5L;
    private static final Long VERSION = 3L;

    @Mock private AuditQueryService auditQueryService;
    @Mock private AdvertisementSaveService advertisementSaveService;
    @Mock private ProviderProfileSaveService providerProfileSaveService;
    @Mock private ProviderProfileReadService providerProfileReadService;
    @Mock private UserProfileService userProfileService;
    @Mock private TaxonCatalogService taxonCatalogService;
    @Mock private AttachmentMediaService attachmentMediaService;

    @Captor private ArgumentCaptor<AdvertisementSaveDto> advertisementCaptor;
    @Captor private ArgumentCaptor<ProviderProfileSaveDto> providerProfileCaptor;
    @Captor private ArgumentCaptor<UserProfileDto> userProfileCaptor;
    @Captor private ArgumentCaptor<UserSettingsDto> settingsCaptor;
    @Captor private ArgumentCaptor<Map<Locale, TaxonTranslationDto>> translationsCaptor;

    private SnapshotRestoreService service;

    @BeforeEach
    void setUp() {
        service = new SnapshotRestoreService(auditQueryService, advertisementSaveService, providerProfileSaveService,
                providerProfileReadService, userProfileService, taxonCatalogService, attachmentMediaService);
    }

    // ── restoreAdvertisement ─────────────────────────────────────────────

    @Test
    void restoreAdvertisement_savesSnapshotValuesThroughSaveService() {
        AdvertisementSnapshotDto snapshot = new AdvertisementSnapshotDto("Original", "First description", AdKind.OFFER,
                List.of(2L, 1L), 5L, null);
        stubSnapshot(EntityType.ADVERTISEMENT, RESOURCE_ID, snapshot, AdvertisementSnapshotDto.class);
        when(advertisementSaveService.save(any(), any(), any())).thenReturn(RESOURCE_ID);

        Long savedId = service.restoreAdvertisement(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, VERSION);

        verify(advertisementSaveService).save(advertisementCaptor.capture(), eq(ACTOR_ID), any());
        AdvertisementSaveDto dto = advertisementCaptor.getValue();
        assertThat(savedId).isEqualTo(RESOURCE_ID);
        assertThat(dto.id()).isEqualTo(RESOURCE_ID);
        assertThat(dto.title()).isEqualTo("Original");
        assertThat(dto.description()).isEqualTo("First description");
        assertThat(dto.adKind()).isEqualTo(AdKind.OFFER);
        assertThat(dto.categoryIds()).isEqualTo(Set.of(1L, 2L));
        assertThat(dto.cityTaxonId()).isEqualTo(5L);
        assertThat(dto.version()).isEqualTo(VERSION);
        verifyNoInteractions(attachmentMediaService);
    }

    @Test
    void restoreAdvertisement_withGallerySnapshot_restoresTheGallery() {
        AdvertisementSnapshotDto snapshot = new AdvertisementSnapshotDto("Original", "First description", AdKind.OFFER,
                List.of(1L), 5L, 9L);
        stubSnapshot(EntityType.ADVERTISEMENT, RESOURCE_ID, snapshot, AdvertisementSnapshotDto.class);
        when(attachmentMediaService.getUrlsBySnapshotId(9L)).thenReturn(new String[]{"u1"});
        when(attachmentMediaService.getLatestSnapshotId(EntityType.ADVERTISEMENT, RESOURCE_ID)).thenReturn(11L);
        when(advertisementSaveService.save(any(), any(), any())).thenAnswer(invocation -> {
            invocation.<Function<EntityRef, Long>>getArgument(2).apply(new EntityRef(EntityType.ADVERTISEMENT, RESOURCE_ID));
            return RESOURCE_ID;
        });

        service.restoreAdvertisement(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, VERSION);

        verify(attachmentMediaService).restoreToUrls(EntityType.ADVERTISEMENT, RESOURCE_ID, new String[]{"u1"});
        verify(attachmentMediaService).captureSnapshot(EntityType.ADVERTISEMENT, RESOURCE_ID);
    }

    @Test
    void restoreAdvertisement_snapshotMissing_throwsNotFound() {
        when(auditQueryService.getSnapshotContent(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, EntityType.ADVERTISEMENT, AdvertisementSnapshotDto.class))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restoreAdvertisement(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, VERSION))
                .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(advertisementSaveService);
    }

    @Test
    void restoreAdvertisement_snapshotNotVisible_propagatesAccessDenied() {
        when(auditQueryService.getSnapshotContent(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, EntityType.ADVERTISEMENT, AdvertisementSnapshotDto.class))
                .thenThrow(new AccessDeniedException("snapshot not visible"));

        assertThatThrownBy(() -> service.restoreAdvertisement(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, VERSION))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(advertisementSaveService);
    }

    // ── restoreProviderProfile ───────────────────────────────────────────

    @Test
    void restoreProviderProfile_savesContactChannelsAgainstTheOwner() {
        ProviderProfileSnapshotDto snapshot = new ProviderProfileSnapshotDto(ProviderKind.MASTER, "About", List.of(3L), 5L,
                "+380501234567", "plumber_master", "+380501234568");
        stubSnapshot(EntityType.PROVIDER_PROFILE, RESOURCE_ID, snapshot, ProviderProfileSnapshotDto.class);
        when(providerProfileReadService.findById(RESOURCE_ID))
                .thenReturn(Optional.of(ProviderProfileDto.builder().actorId(40L).build()));
        when(providerProfileSaveService.save(any(), any(), any())).thenReturn(RESOURCE_ID);

        service.restoreProviderProfile(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, VERSION);

        verify(providerProfileSaveService).save(providerProfileCaptor.capture(), eq(40L), eq(ACTOR_ID));
        ProviderProfileSaveDto dto = providerProfileCaptor.getValue();
        assertThat(dto.id()).isEqualTo(RESOURCE_ID);
        assertThat(dto.kind()).isEqualTo(ProviderKind.MASTER);
        assertThat(dto.about()).isEqualTo("About");
        assertThat(dto.categoryIds()).isEqualTo(Set.of(3L));
        assertThat(dto.cityTaxonId()).isEqualTo(5L);
        assertThat(dto.phone()).isEqualTo("+380501234567");
        assertThat(dto.telegram()).isEqualTo("plumber_master");
        assertThat(dto.viber()).isEqualTo("+380501234568");
        assertThat(dto.version()).isEqualTo(VERSION);
    }

    @Test
    void restoreProviderProfile_unknownProfile_throwsNotFound() {
        ProviderProfileSnapshotDto snapshot = new ProviderProfileSnapshotDto(ProviderKind.MASTER, "About", List.of(), null);
        stubSnapshot(EntityType.PROVIDER_PROFILE, RESOURCE_ID, snapshot, ProviderProfileSnapshotDto.class);
        when(providerProfileReadService.findById(RESOURCE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restoreProviderProfile(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, VERSION))
                .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(providerProfileSaveService);
    }

    // ── restoreUser ──────────────────────────────────────────────────────

    @Test
    void restoreUser_savesNameAndRoleOnly() {
        UserSnapshotDto snapshot = new UserSnapshotDto("Alice", "alice@example.com", "USER");
        stubSnapshot(EntityType.USER, RESOURCE_ID, snapshot, UserSnapshotDto.class);

        service.restoreUser(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, VERSION);

        verify(userProfileService).save(userProfileCaptor.capture(), eq(ACTOR_ID));
        UserProfileDto dto = userProfileCaptor.getValue();
        assertThat(dto.id()).isEqualTo(RESOURCE_ID);
        assertThat(dto.name()).isEqualTo("Alice");
        assertThat(dto.role()).isEqualTo(Role.USER);
        assertThat(dto.version()).isEqualTo(VERSION);
    }

    // ── restoreSettings ──────────────────────────────────────────────────

    @Test
    void restoreSettings_savesAllPageSizesAndVersion() {
        SettingsSnapshotDto snapshot = new SettingsSnapshotDto(30, 31, 32, 33, 34, 35);
        stubSnapshot(EntityType.USER_SETTINGS, RESOURCE_ID, snapshot, SettingsSnapshotDto.class);

        service.restoreSettings(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, VERSION);

        verify(userProfileService).saveSettings(eq(RESOURCE_ID), settingsCaptor.capture());
        UserSettingsDto dto = settingsCaptor.getValue();
        assertThat(dto.getAdsPageSize()).isEqualTo(30);
        assertThat(dto.getUsersPageSize()).isEqualTo(31);
        assertThat(dto.getTimelinePageSize()).isEqualTo(32);
        assertThat(dto.getProviderProfilesPageSize()).isEqualTo(33);
        assertThat(dto.getFeedbackPageSize()).isEqualTo(34);
        assertThat(dto.getModerationPageSize()).isEqualTo(35);
        assertThat(dto.getVersion()).isEqualTo(VERSION);
    }

    // ── restoreTaxon ─────────────────────────────────────────────────────

    @Test
    void restoreTaxon_savesEnglishAndUkrainianTranslations() {
        TaxonSnapshotDto snapshot = new TaxonSnapshotDto("Plumbing", "Plumbing services", "Сантехніка", "Сантехнічні послуги");
        stubSnapshot(EntityType.TAXON, RESOURCE_ID, snapshot, TaxonSnapshotDto.class);

        service.restoreTaxon(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, VERSION);

        verify(taxonCatalogService).update(eq(RESOURCE_ID), translationsCaptor.capture(), eq(ACTOR_ID), eq(VERSION));
        Map<Locale, TaxonTranslationDto> translations = translationsCaptor.getValue();
        assertThat(translations.get(Locale.ENGLISH).getName()).isEqualTo("Plumbing");
        assertThat(translations.get(Locale.ENGLISH).getDescription()).isEqualTo("Plumbing services");
        assertThat(translations.get(Locale.forLanguageTag("uk")).getName()).isEqualTo("Сантехніка");
        assertThat(translations.get(Locale.forLanguageTag("uk")).getDescription()).isEqualTo("Сантехнічні послуги");
    }

    @Test
    void restoreTaxon_nullSnapshotText_usesEmptyStrings() {
        TaxonSnapshotDto snapshot = new TaxonSnapshotDto("Plumbing", "Plumbing services", null, null);
        stubSnapshot(EntityType.TAXON, RESOURCE_ID, snapshot, TaxonSnapshotDto.class);

        service.restoreTaxon(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, VERSION);

        verify(taxonCatalogService).update(eq(RESOURCE_ID), translationsCaptor.capture(), eq(ACTOR_ID), eq(VERSION));
        TaxonTranslationDto uk = translationsCaptor.getValue().get(Locale.forLanguageTag("uk"));
        assertThat(uk.getName()).isEmpty();
        assertThat(uk.getDescription()).isEmpty();
    }

    // ── helpers ──────────────────────────────────────────────────────────

    // ── cases shared by every restore: not visible, stale, rejected by the save path ──

    @Test
    void restoreProviderProfile_snapshotNotVisible_propagatesAccessDenied() {
        when(auditQueryService.getSnapshotContent(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, EntityType.PROVIDER_PROFILE, ProviderProfileSnapshotDto.class))
                .thenThrow(new AccessDeniedException("snapshot not visible"));

        assertThatThrownBy(() -> service.restoreProviderProfile(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, VERSION))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(providerProfileSaveService);
    }

    @Test
    void restoreUser_snapshotNotVisible_propagatesAccessDenied() {
        when(auditQueryService.getSnapshotContent(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, EntityType.USER, UserSnapshotDto.class))
                .thenThrow(new AccessDeniedException("snapshot not visible"));

        assertThatThrownBy(() -> service.restoreUser(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, VERSION))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(userProfileService);
    }

    @Test
    void restoreSettings_snapshotNotVisible_propagatesAccessDenied() {
        when(auditQueryService.getSnapshotContent(ACTOR_ID, ACTOR_ID, SNAPSHOT_ID, EntityType.USER_SETTINGS, SettingsSnapshotDto.class))
                .thenThrow(new AccessDeniedException("snapshot not visible"));

        assertThatThrownBy(() -> service.restoreSettings(ACTOR_ID, ACTOR_ID, SNAPSHOT_ID, VERSION))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(userProfileService);
    }

    @Test
    void restoreTaxon_snapshotNotVisible_propagatesAccessDenied() {
        when(auditQueryService.getSnapshotContent(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, EntityType.TAXON, TaxonSnapshotDto.class))
                .thenThrow(new AccessDeniedException("snapshot not visible"));

        assertThatThrownBy(() -> service.restoreTaxon(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, VERSION))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(taxonCatalogService);
    }

    @Test
    void restoreAdvertisement_staleVersion_propagatesTheSaveFailure() {
        stubSnapshot(EntityType.ADVERTISEMENT, RESOURCE_ID,
                new AdvertisementSnapshotDto("Original", "First description", AdKind.OFFER, List.of(1L), 5L, null), AdvertisementSnapshotDto.class);
        when(advertisementSaveService.save(any(), any(), any())).thenThrow(new StaleWriteException("changed by someone else"));

        assertThatThrownBy(() -> service.restoreAdvertisement(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, VERSION))
                .isInstanceOf(StaleWriteException.class);
    }

    @Test
    void restoreTaxon_staleVersion_propagatesTheUpdateFailure() {
        stubSnapshot(EntityType.TAXON, RESOURCE_ID, new TaxonSnapshotDto("Plumbing", "d", "Сантехніка", "о"), TaxonSnapshotDto.class);
        doThrow(new StaleWriteException("changed by someone else")).when(taxonCatalogService).update(any(), any(), any(), any());

        assertThatThrownBy(() -> service.restoreTaxon(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, VERSION))
                .isInstanceOf(StaleWriteException.class);
    }

    @Test
    void restoreUser_roleChangeRejectedBySavePath_propagatesAccessDenied() {
        stubSnapshot(EntityType.USER, RESOURCE_ID, new UserSnapshotDto("Name", "name@example.com", "ADMIN"), UserSnapshotDto.class);
        doThrow(new AccessDeniedException("only an administrator may change a role")).when(userProfileService).save(any(), eq(ACTOR_ID));

        assertThatThrownBy(() -> service.restoreUser(ACTOR_ID, RESOURCE_ID, SNAPSHOT_ID, VERSION))
                .isInstanceOf(AccessDeniedException.class);
    }

    private <T extends AuditableSnapshot> void stubSnapshot(EntityType entityType, Long entityId, T snapshot, Class<T> type) {
        when(auditQueryService.getSnapshotContent(ACTOR_ID, entityId, SNAPSHOT_ID, entityType, type))
                .thenReturn(Optional.of(new AuditSnapshotContentDto<>(snapshot, 1)));
    }
}
