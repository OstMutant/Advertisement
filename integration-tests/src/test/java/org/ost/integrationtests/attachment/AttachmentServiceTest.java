package org.ost.integrationtests.attachment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.attachment.entities.Attachment;
import org.ost.attachment.repository.AttachmentRepository;
import org.ost.attachment.services.AttachmentService;
import org.ost.attachment.services.AttachmentSnapshotService;
import org.ost.attachment.services.StorageService;
import org.ost.platform.attachment.dto.TempAttachmentDto;
import org.ost.platform.attachment.spi.AttachmentMediaChangeHook;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.CurrentActorHook;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers improvement-049 item 2: {@link AttachmentService#commitTempUploadsQuiet} used to run
 * {@code storageService.move()} inside the {@code .stream().map()} building {@code toSave}, i.e.
 * outside the {@code try} block whose {@code catch} exists specifically to delete whatever moved
 * successfully before a failure. A mid-batch {@code move()} failure meant files that had already
 * physically moved were orphaned: not in the DB, not cleaned up by this method's own error path.
 *
 * <p>No Spring context, no Testcontainers — {@link StorageService}/{@link AttachmentRepository}/
 * {@link AttachmentSnapshotService}/{@link CurrentActorHook} are mocked directly, same shape as
 * {@code UserServiceTest} in this module. Lives in {@code integration-tests} because {@link
 * AttachmentService} belongs to {@code attachment-spring-boot-starter}, a domain starter that
 * never carries its own test code (see {@code integration-tests/CLAUDE.md}).</p>
 */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private StorageService storageService;
    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private AttachmentSnapshotService attachmentSnapshotService;
    @Mock
    private CurrentActorHook currentActorHook;
    @Mock
    private ObjectProvider<AttachmentMediaChangeHook> mediaChangeHook;
    @Mock
    private InputStream inputStream;

    private AttachmentService service;

    @BeforeEach
    void setUp() {
        service = new AttachmentService(storageService, attachmentRepository,
                attachmentSnapshotService, currentActorHook, mediaChangeHook);
    }

    @Test
    void commitTempUploadsQuiet_moveFailsMidBatch_cleansUpAlreadyMovedFiles() {
        TempAttachmentDto temp1 = new TempAttachmentDto("temp/1.jpg", "1.jpg", "image/jpeg", 100);
        TempAttachmentDto temp2 = new TempAttachmentDto("temp/2.jpg", "2.jpg", "image/jpeg", 100);
        TempAttachmentDto temp3 = new TempAttachmentDto("temp/3.jpg", "3.jpg", "image/jpeg", 100);

        when(storageService.move(eq("temp/1.jpg"), anyString(), eq("1.jpg"))).thenReturn("final/1.jpg");
        when(storageService.move(eq("temp/2.jpg"), anyString(), eq("2.jpg"))).thenReturn("final/2.jpg");
        when(storageService.move(eq("temp/3.jpg"), anyString(), eq("3.jpg")))
                .thenThrow(new RuntimeException("storage failure on file 3"));

        assertThatThrownBy(() ->
                service.commitTempUploadsQuiet(EntityType.ADVERTISEMENT, 1L, List.of(temp1, temp2, temp3)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("storage failure on file 3");

        // files 1 and 2 physically moved before file 3 failed -- must be cleaned up
        verify(storageService).delete("final/1.jpg");
        verify(storageService).delete("final/2.jpg");
        // file 3 never moved, nothing to delete for it
        verify(storageService, never()).delete("temp/3.jpg");
        // the batch never reached the DB save step
        verify(attachmentRepository, never()).saveAll(any());
    }

    @Test
    void commitTempUploadsQuiet_allMovesSucceed_savesAllAndNoCleanupCalled() {
        TempAttachmentDto temp1 = new TempAttachmentDto("temp/1.jpg", "1.jpg", "image/jpeg", 100);
        TempAttachmentDto temp2 = new TempAttachmentDto("temp/2.jpg", "2.jpg", "image/jpeg", 100);

        when(storageService.move(eq("temp/1.jpg"), anyString(), eq("1.jpg"))).thenReturn("final/1.jpg");
        when(storageService.move(eq("temp/2.jpg"), anyString(), eq("2.jpg"))).thenReturn("final/2.jpg");

        service.commitTempUploadsQuiet(EntityType.ADVERTISEMENT, 1L, List.of(temp1, temp2));

        verify(attachmentRepository, times(1)).saveAll(any());
        verify(storageService, never()).delete(anyString());
    }

    @Test
    void upload_closesInputStreamAfterS3UploadSucceeds() throws IOException {
        when(storageService.upload(anyString(), eq("photo.jpg"), eq(inputStream), eq(100L), eq("image/jpeg")))
                .thenReturn("final/photo.jpg");
        when(attachmentRepository.save(any())).thenReturn(Attachment.builder()
                .id(1L).entityType(EntityType.ADVERTISEMENT).entityId(1L)
                .url("final/photo.jpg").filename("photo.jpg").contentType("image/jpeg").size(100L)
                .build());
        when(currentActorHook.getCurrentActorId()).thenReturn(Optional.of(1L));

        service.upload(EntityType.ADVERTISEMENT, 1L, "photo.jpg", inputStream, 100L, "image/jpeg");

        verify(inputStream).close();
    }

    // improvement-093: captureMediaChanges() used to silently skip without an actor
    @Test
    void upload_noCurrentActor_throwsInsteadOfSilentlySkippingSnapshot() throws IOException {
        when(storageService.upload(anyString(), eq("photo.jpg"), eq(inputStream), eq(100L), eq("image/jpeg")))
                .thenReturn("final/photo.jpg");
        when(attachmentRepository.save(any())).thenReturn(Attachment.builder()
                .id(1L).entityType(EntityType.ADVERTISEMENT).entityId(1L)
                .url("final/photo.jpg").filename("photo.jpg").contentType("image/jpeg").size(100L)
                .build());
        when(currentActorHook.getCurrentActorId()).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.upload(EntityType.ADVERTISEMENT, 1L, "photo.jpg", inputStream, 100L, "image/jpeg"))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void uploadTemp_closesInputStreamAfterS3UploadSucceeds() throws IOException {
        when(storageService.upload(anyString(), eq("clip.mp4"), eq(inputStream), eq(200L), eq("video/mp4")))
                .thenReturn("temp/session-1/clip.mp4");

        service.uploadTemp("session-1", "clip.mp4", inputStream, 200L, "video/mp4");

        verify(inputStream).close();
    }
}
