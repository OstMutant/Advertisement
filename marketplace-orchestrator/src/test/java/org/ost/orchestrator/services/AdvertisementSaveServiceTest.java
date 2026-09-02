package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.dto.AdvertisementSaveDto;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.advertisement.model.AdKind;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * {@link AdvertisementSaveService#save} orchestrates the create/update transaction: it decides
 * {@code captureCreation} vs {@code captureUpdate}, and has a non-obvious
 * {@code attachmentSnapshotId} fallback that preserves the previous media-snapshot reference when
 * the gallery itself wasn't touched during this save.
 */
@ExtendWith(MockitoExtension.class)
class AdvertisementSaveServiceTest {

    private static final Long ACTOR_ID = 10L;

    @Mock private TransactionTemplate tx;
    @Mock private ComponentFactory<AdvertisementPort> advertisementPortFactory;
    @Mock private ComponentFactory<AuditPort> auditPortFactory;
    @Mock private AdvertisementPort advertisementPort;
    @Mock private AuditPort auditPort;
    @Mock private TaxonLookupService taxonLookupService;
    @Mock private TaxonAssignmentWriteService taxonAssignmentWriteService;
    @Mock private AttachmentSnapshotReaderService attachmentSnapshotReaderService;
    @Mock private AttachmentSoftDeleteService attachmentSoftDeleteService;
    @Mock private SitemapService sitemapService;

    private AdvertisementSaveService service;

    @BeforeEach
    void setUp() {
        service = new AdvertisementSaveService(tx, advertisementPortFactory, auditPortFactory,
                taxonLookupService, taxonAssignmentWriteService, attachmentSnapshotReaderService, attachmentSoftDeleteService,
                sitemapService);
        lenient().when(tx.execute(this.<Long>callback())).thenAnswer(inv -> {
            TransactionCallback<Long> callback = inv.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        lenient().doAnswer(inv -> {
            Consumer<TransactionStatus> callback = inv.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(tx).executeWithoutResult(any());
        lenient().when(advertisementPortFactory.get()).thenReturn(advertisementPort);
        lenient().when(taxonLookupService.getForEntity(any(), any(), any())).thenReturn(List.of());
    }

    @SuppressWarnings("unchecked")
    private <T> TransactionCallback<T> callback() {
        return any(TransactionCallback.class);
    }

    private static <T> void stubAvailable(ComponentFactory<T> factory, T component) {
        lenient().when(factory.findIfAvailable()).thenReturn(Optional.of(component));
        lenient().doAnswer(inv -> {
            Consumer<T> consumer = inv.getArgument(0);
            consumer.accept(component);
            return null;
        }).when(factory).ifAvailable(any());
    }

    @Test
    void save_newAdvertisement_capturesCreationNotUpdate() {
        AdvertisementSaveDto dto = new AdvertisementSaveDto(null, "Title", "Desc", AdKind.OFFER, Set.of(1L, 2L), null, null);
        when(advertisementPort.save(dto)).thenReturn(100L);
        when(advertisementPort.findById(100L)).thenReturn(Optional.of(
                AdvertisementInfoDto.builder().id(100L).title("Title").description("Desc").build()));
        stubAvailable(auditPortFactory, auditPort);

        Long id = service.save(dto, ACTOR_ID, ref -> null);

        assertThat(id).isEqualTo(100L);
        ArgumentCaptor<AuditableSnapshot> afterCaptor = ArgumentCaptor.forClass(AuditableSnapshot.class);
        verify(auditPort).captureCreation(eq(100L), afterCaptor.capture(), eq(ACTOR_ID));
        verify(auditPort, never()).captureUpdate(any(), any(), any());
        AdvertisementSnapshotDto after = (AdvertisementSnapshotDto) afterCaptor.getValue();
        assertThat(after.title()).isEqualTo("Title");
        assertThat(after.categoryIds()).containsExactly(1L, 2L);
    }

    @Test
    void save_existingAdvertisement_capturesUpdateWithAfter() {
        Long adId = 42L;
        AdvertisementSaveDto dto = new AdvertisementSaveDto(adId, "New Title", "New Desc", AdKind.OFFER, Set.of(3L), null, 5L);
        AdvertisementInfoDto beforeInfo = AdvertisementInfoDto.builder().id(adId).title("Old Title").description("Old Desc").build();
        AdvertisementInfoDto afterInfo = AdvertisementInfoDto.builder().id(adId).title("New Title").description("New Desc").build();

        when(advertisementPort.findById(adId)).thenReturn(Optional.of(beforeInfo), Optional.of(afterInfo));
        when(advertisementPort.save(dto)).thenReturn(adId);
        stubAvailable(auditPortFactory, auditPort);

        Long id = service.save(dto, ACTOR_ID, ref -> null);

        assertThat(id).isEqualTo(adId);
        ArgumentCaptor<AuditableSnapshot> afterCaptor = ArgumentCaptor.forClass(AuditableSnapshot.class);
        verify(auditPort).captureUpdate(eq(adId), afterCaptor.capture(), eq(ACTOR_ID));
        verify(auditPort, never()).captureCreation(any(), any(), any());
        assertThat(((AdvertisementSnapshotDto) afterCaptor.getValue()).title()).isEqualTo("New Title");
    }

    @Test
    void save_existingAdvertisementConcurrentlyDeleted_throwsOptimisticLockingFailure() {
        Long adId = 42L;
        AdvertisementSaveDto dto = new AdvertisementSaveDto(adId, "New Title", "New Desc", AdKind.OFFER, Set.of(), null, 5L);

        when(advertisementPort.findById(adId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(dto, ACTOR_ID, ref -> null))
                .isInstanceOf(OptimisticLockingFailureException.class);
        verify(advertisementPort, never()).save(any());
    }

    @Test
    void save_galleryTouched_usesGallerySnapshotIdRegardlessOfPreviousOne() {
        Long adId = 42L;
        AdvertisementSaveDto dto = new AdvertisementSaveDto(adId, "T", "D", AdKind.OFFER, Set.of(), null, 5L);
        AdvertisementInfoDto info = AdvertisementInfoDto.builder().id(adId).title("T").description("D").build();
        when(advertisementPort.findById(adId)).thenReturn(Optional.of(info));
        when(advertisementPort.save(dto)).thenReturn(adId);
        stubAvailable(auditPortFactory, auditPort);
        when(attachmentSnapshotReaderService.getLatestSnapshotId(EntityType.ADVERTISEMENT, adId)).thenReturn(777L);

        service.save(dto, ACTOR_ID, ref -> 999L);

        ArgumentCaptor<AuditableSnapshot> afterCaptor = ArgumentCaptor.forClass(AuditableSnapshot.class);
        verify(auditPort).captureUpdate(eq(adId), afterCaptor.capture(), eq(ACTOR_ID));
        assertThat(((AdvertisementSnapshotDto) afterCaptor.getValue()).attachmentSnapshotId()).isEqualTo(999L);
    }

    @Test
    void save_galleryNotTouched_fallsBackToPreviousAttachmentSnapshotId() {
        Long adId = 42L;
        AdvertisementSaveDto dto = new AdvertisementSaveDto(adId, "T", "D", AdKind.OFFER, Set.of(), null, 5L);
        AdvertisementInfoDto info = AdvertisementInfoDto.builder().id(adId).title("T").description("D").build();
        when(advertisementPort.findById(adId)).thenReturn(Optional.of(info));
        when(advertisementPort.save(dto)).thenReturn(adId);
        stubAvailable(auditPortFactory, auditPort);
        when(attachmentSnapshotReaderService.getLatestSnapshotId(EntityType.ADVERTISEMENT, adId)).thenReturn(777L);

        service.save(dto, ACTOR_ID, ref -> null);

        ArgumentCaptor<AuditableSnapshot> afterCaptor = ArgumentCaptor.forClass(AuditableSnapshot.class);
        verify(auditPort).captureUpdate(eq(adId), afterCaptor.capture(), eq(ACTOR_ID));
        assertThat(((AdvertisementSnapshotDto) afterCaptor.getValue()).attachmentSnapshotId()).isEqualTo(777L);
    }

    @Test
    void save_optionalAuditPortAbsent_completesWithoutException() {
        AdvertisementSaveDto dto = new AdvertisementSaveDto(null, "T", "D", AdKind.OFFER, null, null, null);
        when(advertisementPort.save(dto)).thenReturn(1L);
        when(advertisementPort.findById(1L)).thenReturn(Optional.of(
                AdvertisementInfoDto.builder().id(1L).title("T").description("D").build()));
        // auditPortFactory left unstubbed -- ObjectProvider-absent shape.

        Long id = service.save(dto, ACTOR_ID, ref -> null);

        assertThat(id).isEqualTo(1L);
    }

    @Test
    void delete_existingAdvertisement_capturesDeletionWithSnapshotReadBeforeDeleting() {
        Long adId = 42L;
        Long version = 5L;
        when(advertisementPort.findById(adId)).thenReturn(Optional.of(
                AdvertisementInfoDto.builder().id(adId).title("Deleted Title").description("Deleted Desc").build()));
        stubAvailable(auditPortFactory, auditPort);

        service.delete(adId, ACTOR_ID, version);

        verify(advertisementPort).delete(adId, ACTOR_ID, version);
        verify(taxonAssignmentWriteService).clear(EntityType.ADVERTISEMENT, adId);
        verify(attachmentSoftDeleteService).softDeleteAll(any(), eq(ACTOR_ID));
        ArgumentCaptor<AuditableSnapshot> snapshotCaptor = ArgumentCaptor.forClass(AuditableSnapshot.class);
        verify(auditPort).captureDeletion(eq(adId), snapshotCaptor.capture(), eq(ACTOR_ID));
        assertThat(((AdvertisementSnapshotDto) snapshotCaptor.getValue()).title()).isEqualTo("Deleted Title");
    }

    @Test
    void delete_nonExistentAdvertisement_stillCallsPortButNeverCascadesOrCapturesDeletion() {
        Long adId = 99L;
        when(advertisementPort.findById(adId)).thenReturn(Optional.empty());
        stubAvailable(auditPortFactory, auditPort);

        service.delete(adId, ACTOR_ID, null);

        verify(advertisementPort).delete(adId, ACTOR_ID, null);
        verify(taxonAssignmentWriteService, never()).clear(any(), any());
        verify(attachmentSoftDeleteService, never()).softDeleteAll(any(), any());
        verify(auditPort, never()).captureDeletion(any(), any(), any());
    }

    @Test
    void delete_optionalAuditPortAbsent_completesWithoutException() {
        Long adId = 42L;
        when(advertisementPort.findById(adId)).thenReturn(Optional.of(
                AdvertisementInfoDto.builder().id(adId).title("T").description("D").build()));
        // auditPortFactory left unstubbed -- ObjectProvider-absent shape.

        service.delete(adId, ACTOR_ID, 1L);

        verify(advertisementPort).delete(adId, ACTOR_ID, 1L);
    }
}
