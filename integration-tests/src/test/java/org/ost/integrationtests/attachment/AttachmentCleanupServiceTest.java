package org.ost.integrationtests.attachment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.attachment.repository.AttachmentRepository;
import org.ost.attachment.services.AttachmentCleanupService;
import org.ost.attachment.services.StorageService;
import org.ost.platform.core.config.CleanupProperties;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers improvement-049 item 4: {@link AttachmentCleanupService#cleanup} used to delete S3
 * objects before their DB rows — if the process crashed between the two, the DB rows survived
 * pointing at files that no longer existed. Verifies the DB delete now happens first and commits
 * independently (see {@code AttachmentCleanupService.cleanup()}'s own javadoc for why it carries
 * no {@code @Transactional}), so a storage-delete failure can never prevent or reorder the DB
 * delete that already happened.
 *
 * <p>No Spring context, no Testcontainers — {@link AttachmentRepository}/{@link StorageService}
 * are mocked directly, same shape as {@code AttachmentServiceTest} in this module.</p>
 */
@ExtendWith(MockitoExtension.class)
class AttachmentCleanupServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private StorageService storageService;

    private AttachmentCleanupService service;

    @BeforeEach
    void setUp() {
        CleanupProperties cleanupProperties = new CleanupProperties(90, "0 0 2 * * *", "Europe/Kyiv");
        service = new AttachmentCleanupService(attachmentRepository, storageService, cleanupProperties);
        when(storageService.listByPrefix(anyString(), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(List.of());
    }

    @Test
    void cleanup_deletesDbRowsBeforeS3Objects() {
        List<String> urls = List.of("attachment/1.jpg", "attachment/2.jpg");
        when(attachmentRepository.findUrlsDeletedOlderThan(anyInt())).thenReturn(urls);
        when(attachmentRepository.deleteByUrls(anyList())).thenReturn(2);

        service.cleanup();

        InOrder order = inOrder(attachmentRepository, storageService);
        order.verify(attachmentRepository).deleteByUrls(urls);
        order.verify(storageService).delete("attachment/1.jpg");
        order.verify(storageService).delete("attachment/2.jpg");
    }

    @Test
    void cleanup_s3DeleteFails_dbRowsAlreadyDeletedRegardless() {
        List<String> urls = List.of("attachment/1.jpg", "attachment/2.jpg");
        when(attachmentRepository.findUrlsDeletedOlderThan(anyInt())).thenReturn(urls);
        when(attachmentRepository.deleteByUrls(anyList())).thenReturn(2);
        doThrow(new RuntimeException("S3 unavailable")).when(storageService).delete("attachment/1.jpg");

        service.cleanup();

        // the DB delete already happened and committed before the S3 loop even started -- a
        // storage failure afterward cannot un-delete those rows or prevent the call from having
        // happened
        verify(attachmentRepository).deleteByUrls(urls);
    }

    @Test
    void cleanup_orphanedEntityFileWithNoDbRow_getsDeleted() {
        when(attachmentRepository.findUrlsDeletedOlderThan(anyInt())).thenReturn(List.of());
        String orphanUrl = "https://s3.example/advertisement/1/orphan.jpg";
        when(storageService.listByPrefix(eq("advertisement/"), any(Instant.class)))
                .thenReturn(List.of(orphanUrl));
        when(attachmentRepository.findExistingUrls(anyCollection())).thenReturn(Set.of());

        service.cleanup();

        verify(storageService).delete(orphanUrl);
    }

    @Test
    void cleanup_entityFileWithMatchingDbRow_isNotDeleted() {
        when(attachmentRepository.findUrlsDeletedOlderThan(anyInt())).thenReturn(List.of());
        String trackedUrl = "https://s3.example/advertisement/1/tracked.jpg";
        when(storageService.listByPrefix(eq("advertisement/"), any(Instant.class)))
                .thenReturn(List.of(trackedUrl));
        when(attachmentRepository.findExistingUrls(anyCollection())).thenReturn(Set.of(trackedUrl));

        service.cleanup();

        verify(storageService, never()).delete(trackedUrl);
    }
}
