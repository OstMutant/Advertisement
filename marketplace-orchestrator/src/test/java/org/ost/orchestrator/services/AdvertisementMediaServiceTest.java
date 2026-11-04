package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.ost.platform.attachment.model.AttachmentLimits;
import org.ost.platform.core.model.EntityType;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for {@link AdvertisementMediaService}: edit rights, gallery cap and size, and history recording of every change. */
@ExtendWith(MockitoExtension.class)
class AdvertisementMediaServiceTest {

    private static final Long OWNER_ID = 10L;
    private static final Long OTHER_ID = 20L;
    private static final Long AD_ID = 7L;
    private static final String VIDEO_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @Mock private AttachmentMediaService attachmentMediaService;
    @Mock private AdvertisementReadService advertisementReadService;
    @Mock private AdvertisementSaveService advertisementSaveService;
    @Mock private AuthorizationService authorizationService;
    @Mock private TransactionTemplate tx;

    private AdvertisementMediaService service;

    @BeforeEach
    void setUp() {
        lenient().when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<?>) inv.getArgument(0)).doInTransaction(null));
        lenient().doAnswer(inv -> {
            ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(null);
            return null;
        }).when(tx).executeWithoutResult(any());
        service = new AdvertisementMediaService(attachmentMediaService, advertisementReadService,
                advertisementSaveService, authorizationService, tx);
    }

    // ── uploadAll ────────────────────────────────────────────────────────

    @Test
    void uploadAll_byOwner_savesEveryFileAndRecordsOneMediaChange() {
        stubAdvertisement();
        when(attachmentMediaService.getByEntityId(EntityType.ADVERTISEMENT, AD_ID)).thenReturn(List.of());
        when(attachmentMediaService.upload(eq(EntityType.ADVERTISEMENT), eq(AD_ID), eq("a.png"), any(InputStream.class),
                eq(3L), eq("image/png")))
                .thenReturn(new AttachmentItemDto(1L, "http://storage/a.png", "a.png", "image/png"));
        when(attachmentMediaService.upload(eq(EntityType.ADVERTISEMENT), eq(AD_ID), eq("b.png"), any(InputStream.class),
                eq(3L), eq("image/png")))
                .thenReturn(new AttachmentItemDto(2L, "http://storage/b.png", "b.png", "image/png"));

        List<AttachmentItemDto> result = service.uploadAll(OWNER_ID, AD_ID,
                List.of(item("a.png", 3L), item("b.png", 3L)));

        assertThat(result).hasSize(2);
        verify(authorizationService).requireCanOperate(OWNER_ID, OWNER_ID);
        verify(advertisementSaveService, times(1)).recordMediaChange(AD_ID, OWNER_ID);
    }

    @Test
    void uploadAll_batchExceedsGalleryCap_throwsIllegalArgumentAndUploadsNothing() {
        stubAdvertisement();
        when(attachmentMediaService.getByEntityId(EntityType.ADVERTISEMENT, AD_ID)).thenReturn(items(9));

        assertThatThrownBy(() -> service.uploadAll(OWNER_ID, AD_ID, List.of(item("a.png", 3L), item("b.png", 3L))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(attachmentMediaService, never()).upload(any(), any(), any(), any(), anyLong(), any());
        verifyNoInteractions(advertisementSaveService);
    }

    @Test
    void uploadAll_oversizeFileAnywhereInBatch_throwsIllegalArgumentBeforeAnyUpload() {
        stubAdvertisement();
        when(attachmentMediaService.getByEntityId(EntityType.ADVERTISEMENT, AD_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.uploadAll(OWNER_ID, AD_ID,
                List.of(item("small.png", 3L), item("big.png", AttachmentLimits.MAX_FILE_SIZE + 1))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(attachmentMediaService, never()).upload(any(), any(), any(), any(), anyLong(), any());
        verifyNoInteractions(advertisementSaveService);
    }

    @Test
    void uploadAll_withoutEditRights_uploadsNothing() {
        stubAdvertisement();
        doThrow(new AccessDeniedException("not the owner")).when(authorizationService).requireCanOperate(OTHER_ID, OWNER_ID);

        assertThatThrownBy(() -> service.uploadAll(OTHER_ID, AD_ID, List.of(item("a.png", 3L))))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(attachmentMediaService, advertisementSaveService);
    }

    @Test
    void uploadAll_unknownAdvertisement_throwsNotFound() {
        when(advertisementReadService.findById(AD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.uploadAll(OWNER_ID, AD_ID, List.of(item("a.png", 3L))))
                .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(attachmentMediaService, advertisementSaveService);
    }

    // ── addVideo ─────────────────────────────────────────────────────────

    @Test
    void addVideo_byOwner_recordsMediaChange() {
        stubAdvertisement();
        when(attachmentMediaService.getByEntityId(EntityType.ADVERTISEMENT, AD_ID)).thenReturn(List.of());
        AttachmentItemDto saved = new AttachmentItemDto(2L, VIDEO_URL, "video", "youtube");
        when(attachmentMediaService.addVideo(EntityType.ADVERTISEMENT, AD_ID, VIDEO_URL)).thenReturn(saved);

        AttachmentItemDto result = service.addVideo(OWNER_ID, AD_ID, VIDEO_URL);

        assertThat(result).isEqualTo(saved);
        verify(advertisementSaveService).recordMediaChange(AD_ID, OWNER_ID);
    }

    @Test
    void addVideo_galleryFull_throwsIllegalArgumentAndAddsNothing() {
        stubAdvertisement();
        when(attachmentMediaService.getByEntityId(EntityType.ADVERTISEMENT, AD_ID)).thenReturn(items(AttachmentLimits.MAX_FILES));

        assertThatThrownBy(() -> service.addVideo(OWNER_ID, AD_ID, VIDEO_URL))
                .isInstanceOf(IllegalArgumentException.class);
        verify(attachmentMediaService, never()).addVideo(any(), anyLong(), any());
        verifyNoInteractions(advertisementSaveService);
    }

    // ── delete ───────────────────────────────────────────────────────────

    @Test
    void delete_attachmentOfAnotherAdvertisement_throwsNotFoundAndDeletesNothing() {
        stubAdvertisement();
        when(attachmentMediaService.getByEntityId(EntityType.ADVERTISEMENT, AD_ID))
                .thenReturn(List.of(new AttachmentItemDto(1L, "u", "a.png", "image/png")));

        assertThatThrownBy(() -> service.delete(OWNER_ID, AD_ID, 99L))
                .isInstanceOf(NoSuchElementException.class);
        verify(attachmentMediaService, never()).delete(anyLong());
        verifyNoInteractions(advertisementSaveService);
    }

    @Test
    void delete_ownAttachment_deletesCapturesSnapshotAndRecordsChange() {
        stubAdvertisement();
        when(attachmentMediaService.getByEntityId(EntityType.ADVERTISEMENT, AD_ID))
                .thenReturn(List.of(new AttachmentItemDto(5L, "u", "a.png", "image/png")));

        service.delete(OWNER_ID, AD_ID, 5L);

        verify(attachmentMediaService).delete(5L);
        verify(attachmentMediaService).captureSnapshot(EntityType.ADVERTISEMENT, AD_ID);
        verify(advertisementSaveService).recordMediaChange(AD_ID, OWNER_ID);
    }

    // ── list ─────────────────────────────────────────────────────────────

    @Test
    void list_unknownAdvertisement_throwsNotFound() {
        when(advertisementReadService.findById(AD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.list(AD_ID))
                .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(attachmentMediaService);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void stubAdvertisement() {
        when(advertisementReadService.findById(AD_ID))
                .thenReturn(Optional.of(AdvertisementInfoDto.builder().id(AD_ID).createdBy(OWNER_ID).build()));
    }

    private static AdvertisementMediaService.UploadItem item(String filename, long size) {
        return new AdvertisementMediaService.UploadItem(filename, InputStream.nullInputStream(), size, "image/png");
    }

    private static List<AttachmentItemDto> items(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> new AttachmentItemDto((long) i, "u" + i, "f" + i + ".png", "image/png"))
                .toList();
    }
}
