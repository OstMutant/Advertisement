package org.ost.marketplace.services.advertisement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.dto.AdvertisementSaveDto;
import org.ost.platform.advertisement.dto.AdvertisementSnapshotDto;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.spi.TaxonPort;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link AdvertisementSaveService#save} orchestrates the create/update transaction: it decides
 * {@code captureCreation} vs {@code captureUpdate}, and has a non-obvious
 * {@code attachmentSnapshotId} fallback that preserves the previous media-snapshot reference when
 * the gallery itself wasn't touched during this save (improvement-048).
 */
@ExtendWith(MockitoExtension.class)
class AdvertisementSaveServiceTest {

    private static final Long ACTOR_ID = 10L;

    @Mock private TransactionTemplate tx;
    @Mock private ComponentFactory<AdvertisementPort> advertisementPortFactory;
    @Mock private ComponentFactory<AttachmentPort> attachmentPortFactory;
    @Mock private ComponentFactory<TaxonPort> taxonPortFactory;
    @Mock private ComponentFactory<AuditPort> auditPortFactory;
    @Mock private AdvertisementPort advertisementPort;
    @Mock private AuditPort auditPort;
    @Mock private AttachmentPort attachmentPort;

    private AdvertisementSaveService service;

    @BeforeEach
    void setUp() {
        service = new AdvertisementSaveService(tx, advertisementPortFactory, attachmentPortFactory,
                taxonPortFactory, auditPortFactory);
        when(tx.execute(this.<Long>callback())).thenAnswer(inv -> {
            TransactionCallback<Long> callback = inv.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        lenient().when(advertisementPortFactory.get()).thenReturn(advertisementPort);
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
        AdvertisementSaveDto dto = new AdvertisementSaveDto(null, "Title", "Desc", Set.of(1L, 2L), null);
        when(advertisementPort.save(dto, ACTOR_ID)).thenReturn(100L);
        when(advertisementPort.findById(100L)).thenReturn(Optional.of(
                AdvertisementInfoDto.builder().id(100L).title("Title").description("Desc").build()));
        stubAvailable(auditPortFactory, auditPort);
        stubAvailable(taxonPortFactory, mock(TaxonPort.class));

        Long id = service.save(dto, ACTOR_ID, ref -> null);

        assertThat(id).isEqualTo(100L);
        ArgumentCaptor<AuditableSnapshot> afterCaptor = ArgumentCaptor.forClass(AuditableSnapshot.class);
        verify(auditPort).captureCreation(eq(100L), afterCaptor.capture(), eq(ACTOR_ID));
        verify(auditPort, never()).captureUpdate(any(), any(), any(), any());
        AdvertisementSnapshotDto after = (AdvertisementSnapshotDto) afterCaptor.getValue();
        assertThat(after.title()).isEqualTo("Title");
        assertThat(after.categoryIds()).containsExactly(1L, 2L);
    }

    @Test
    void save_existingAdvertisement_capturesUpdateWithBeforeAndAfter() {
        Long adId = 42L;
        AdvertisementSaveDto dto = new AdvertisementSaveDto(adId, "New Title", "New Desc", Set.of(3L), 5L);
        AdvertisementInfoDto beforeInfo = AdvertisementInfoDto.builder().id(adId).title("Old Title").description("Old Desc").build();
        AdvertisementInfoDto afterInfo = AdvertisementInfoDto.builder().id(adId).title("New Title").description("New Desc").build();

        when(advertisementPort.findById(adId)).thenReturn(Optional.of(beforeInfo), Optional.of(afterInfo));
        when(advertisementPort.save(dto, ACTOR_ID)).thenReturn(adId);
        stubAvailable(auditPortFactory, auditPort);
        stubAvailable(taxonPortFactory, mock(TaxonPort.class));

        Long id = service.save(dto, ACTOR_ID, ref -> null);

        assertThat(id).isEqualTo(adId);
        ArgumentCaptor<AuditableSnapshot> beforeCaptor = ArgumentCaptor.forClass(AuditableSnapshot.class);
        ArgumentCaptor<AuditableSnapshot> afterCaptor = ArgumentCaptor.forClass(AuditableSnapshot.class);
        verify(auditPort).captureUpdate(eq(adId), beforeCaptor.capture(), afterCaptor.capture(), eq(ACTOR_ID));
        verify(auditPort, never()).captureCreation(any(), any(), any());
        assertThat(((AdvertisementSnapshotDto) beforeCaptor.getValue()).title()).isEqualTo("Old Title");
        assertThat(((AdvertisementSnapshotDto) afterCaptor.getValue()).title()).isEqualTo("New Title");
    }

    @Test
    void save_galleryTouched_usesGallerySnapshotIdRegardlessOfPreviousOne() {
        Long adId = 42L;
        AdvertisementSaveDto dto = new AdvertisementSaveDto(adId, "T", "D", Set.of(), 5L);
        AdvertisementInfoDto info = AdvertisementInfoDto.builder().id(adId).title("T").description("D").build();
        when(advertisementPort.findById(adId)).thenReturn(Optional.of(info));
        when(advertisementPort.save(dto, ACTOR_ID)).thenReturn(adId);
        stubAvailable(auditPortFactory, auditPort);
        stubAvailable(attachmentPortFactory, attachmentPort);
        when(attachmentPort.getLatestSnapshotId(EntityType.ADVERTISEMENT, adId)).thenReturn(777L);

        service.save(dto, ACTOR_ID, ref -> 999L);

        ArgumentCaptor<AuditableSnapshot> afterCaptor = ArgumentCaptor.forClass(AuditableSnapshot.class);
        verify(auditPort).captureUpdate(eq(adId), any(), afterCaptor.capture(), eq(ACTOR_ID));
        assertThat(((AdvertisementSnapshotDto) afterCaptor.getValue()).attachmentSnapshotId()).isEqualTo(999L);
    }

    @Test
    void save_galleryNotTouched_fallsBackToPreviousAttachmentSnapshotId() {
        Long adId = 42L;
        AdvertisementSaveDto dto = new AdvertisementSaveDto(adId, "T", "D", Set.of(), 5L);
        AdvertisementInfoDto info = AdvertisementInfoDto.builder().id(adId).title("T").description("D").build();
        when(advertisementPort.findById(adId)).thenReturn(Optional.of(info));
        when(advertisementPort.save(dto, ACTOR_ID)).thenReturn(adId);
        stubAvailable(auditPortFactory, auditPort);
        stubAvailable(attachmentPortFactory, attachmentPort);
        when(attachmentPort.getLatestSnapshotId(EntityType.ADVERTISEMENT, adId)).thenReturn(777L);

        service.save(dto, ACTOR_ID, ref -> null);

        ArgumentCaptor<AuditableSnapshot> afterCaptor = ArgumentCaptor.forClass(AuditableSnapshot.class);
        verify(auditPort).captureUpdate(eq(adId), any(), afterCaptor.capture(), eq(ACTOR_ID));
        assertThat(((AdvertisementSnapshotDto) afterCaptor.getValue()).attachmentSnapshotId()).isEqualTo(777L);
    }

    @Test
    void save_optionalPortsAbsent_completesWithoutException() {
        AdvertisementSaveDto dto = new AdvertisementSaveDto(null, "T", "D", null, null);
        when(advertisementPort.save(dto, ACTOR_ID)).thenReturn(1L);
        when(advertisementPort.findById(1L)).thenReturn(Optional.of(
                AdvertisementInfoDto.builder().id(1L).title("T").description("D").build()));
        // taxonPortFactory / auditPortFactory left unstubbed -- ObjectProvider-absent shape.

        Long id = service.save(dto, ACTOR_ID, ref -> null);

        assertThat(id).isEqualTo(1L);
    }
}
