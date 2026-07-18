package org.ost.integrationtests.attachment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.attachment.entities.Attachment;
import org.ost.attachment.repository.AttachmentMediaChange;
import org.ost.attachment.repository.AttachmentRepository;
import org.ost.attachment.repository.AttachmentSnapshotRepository;
import org.ost.attachment.services.AttachmentSnapshotService;
import org.ost.platform.core.model.EntityType;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentSnapshotServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;
    @Mock
    private AttachmentSnapshotRepository attachmentSnapshotRepository;

    private AttachmentSnapshotService service;

    @BeforeEach
    void setUp() {
        service = new AttachmentSnapshotService(attachmentRepository, attachmentSnapshotRepository);
    }

    private static Attachment tracked(String url, String filename) {
        return Attachment.builder().url(url).filename(filename).contentType("image/jpeg").size(100L).build();
    }

    @SuppressWarnings("unchecked")
    private List<AttachmentMediaChange> capturedChanges() {
        ArgumentCaptor<List<AttachmentMediaChange>> captor = ArgumentCaptor.forClass(List.class);
        verify(attachmentSnapshotRepository).insert(eq(EntityType.ADVERTISEMENT), eq(1L), any(), captor.capture(), eq(42L));
        return captor.getValue();
    }

    @Test
    void captureAndGetId_firstSnapshot_usesRealFilenameNotUuidKey() {
        String url = "https://s3.example/advertisement/1/550e8400-e29b.jpg";
        when(attachmentRepository.getActiveUrls(EntityType.ADVERTISEMENT, 1L)).thenReturn(List.of(url));
        when(attachmentSnapshotRepository.getPrevUrls(EntityType.ADVERTISEMENT, 1L)).thenReturn(Optional.empty());
        when(attachmentRepository.findByEntityAndUrls(any(), any(), any()))
                .thenReturn(List.of(tracked(url, "sofa-front.jpg")));
        when(attachmentSnapshotRepository.findLatestId(EntityType.ADVERTISEMENT, 1L)).thenReturn(Optional.of(1L));

        service.captureAndGetId(EntityType.ADVERTISEMENT, 1L, 42L);

        assertThat(capturedChanges().get(0).after()).containsExactly("sofa-front.jpg");
    }

    @Test
    void captureAndGetId_noMatchingAttachmentRow_fallsBackToUrlSegment() {
        String url = "https://s3.example/advertisement/1/550e8400-e29b.jpg";
        when(attachmentRepository.getActiveUrls(EntityType.ADVERTISEMENT, 1L)).thenReturn(List.of(url));
        when(attachmentSnapshotRepository.getPrevUrls(EntityType.ADVERTISEMENT, 1L)).thenReturn(Optional.empty());
        when(attachmentRepository.findByEntityAndUrls(any(), any(), any())).thenReturn(List.of());
        when(attachmentSnapshotRepository.findLatestId(EntityType.ADVERTISEMENT, 1L)).thenReturn(Optional.of(1L));

        service.captureAndGetId(EntityType.ADVERTISEMENT, 1L, 42L);

        assertThat(capturedChanges().get(0).after()).containsExactly("550e8400-e29b.jpg");
    }

    @Test
    void getMediaStateForSnapshot_resolvesRealFilename() {
        String url = "https://s3.example/advertisement/1/uuid.jpg";
        when(attachmentSnapshotRepository.getUrlsById(1L)).thenReturn(Optional.of(List.of(url)));
        when(attachmentRepository.findByEntityAndUrls(any(), any(), any()))
                .thenReturn(List.of(tracked(url, "table.jpg")));

        String state = service.getMediaStateForSnapshot(EntityType.ADVERTISEMENT, 1L, 1L);

        assertThat(state).isEqualTo("table.jpg");
    }

    @Test
    void captureAndGetId_duplicateOriginalFilenamesAcrossUrls_bothResolveIndependently() {
        String url1 = "https://s3.example/advertisement/1/uuid1.jpg";
        String url2 = "https://s3.example/advertisement/1/uuid2.jpg";
        when(attachmentRepository.getActiveUrls(EntityType.ADVERTISEMENT, 1L)).thenReturn(List.of(url1, url2));
        when(attachmentSnapshotRepository.getPrevUrls(EntityType.ADVERTISEMENT, 1L)).thenReturn(Optional.empty());
        when(attachmentRepository.findByEntityAndUrls(any(), any(), any()))
                .thenReturn(List.of(tracked(url1, "photo.jpg"), tracked(url2, "photo.jpg")));
        when(attachmentSnapshotRepository.findLatestId(EntityType.ADVERTISEMENT, 1L)).thenReturn(Optional.of(1L));

        service.captureAndGetId(EntityType.ADVERTISEMENT, 1L, 42L);

        assertThat(capturedChanges().get(0).after()).containsExactly("photo.jpg", "photo.jpg");
    }
}
