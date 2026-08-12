package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.ost.platform.attachment.dto.TempAttachmentDto;
import org.ost.platform.attachment.spi.AttachmentAuditPort;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

/**
 * Full attachment gallery lifecycle ({@link AttachmentPort}) plus audit-diff media state
 * ({@link AttachmentAuditPort}), reused by every marketplace-app adapter. Reuses
 * {@link AttachmentSnapshotReaderService} internally for {@code getLatestSnapshotId} instead of
 * re-wrapping that port call again.
 */
@Service
@RequiredArgsConstructor
public class AttachmentMediaService {

    private final ComponentFactory<AttachmentPort>      attachmentPortFactory;
    private final ComponentFactory<AttachmentAuditPort>  attachmentAuditPortFactory;
    private final AttachmentSnapshotReaderService         attachmentSnapshotReaderService;

    // ── gallery queries ───────────────────────────────────────────────────────

    public List<AttachmentItemDto> getByEntityId(@NonNull EntityType entityType, @NonNull Long entityId) {
        return attachmentPortFactory.findIfAvailable()
                .map(p -> p.getByEntityId(entityType, entityId))
                .orElse(List.of());
    }

    public List<AttachmentItemDto> getByEntityAndUrls(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull String[] urls) {
        return attachmentPortFactory.findIfAvailable()
                .map(p -> p.getByEntityAndUrls(entityType, entityId, urls))
                .orElse(List.of());
    }

    public String[] getUrlsBySnapshotId(@NonNull Long snapshotId) {
        return attachmentPortFactory.findIfAvailable()
                .map(p -> p.getUrlsBySnapshotId(snapshotId))
                .orElse(new String[0]);
    }

    public Long getLatestSnapshotId(@NonNull EntityType entityType, @NonNull Long entityId) {
        return attachmentSnapshotReaderService.getLatestSnapshotId(entityType, entityId);
    }

    // ── gallery upload commands ───────────────────────────────────────────────

    public TempAttachmentDto uploadTemp(@NonNull String tempSessionId, @NonNull String filename,
                                        @NonNull InputStream inputStream, long contentLength,
                                        @NonNull String contentType) {
        return attachmentPortFactory.get().uploadTemp(tempSessionId, filename, inputStream, contentLength, contentType);
    }

    public AttachmentItemDto upload(@NonNull EntityType entityType, @NonNull Long entityId,
                                    @NonNull String filename, @NonNull InputStream inputStream,
                                    long contentLength, @NonNull String contentType) {
        return attachmentPortFactory.get().upload(entityType, entityId, filename, inputStream, contentLength, contentType);
    }

    public TempAttachmentDto addVideoTemp(@NonNull String url) {
        return attachmentPortFactory.get().addVideoTemp(url);
    }

    public AttachmentItemDto addVideo(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull String url) {
        return attachmentPortFactory.get().addVideo(entityType, entityId, url);
    }

    // ── gallery commit/discard ────────────────────────────────────────────────

    public void delete(@NonNull Long attachmentId) {
        attachmentPortFactory.get().delete(attachmentId);
    }

    public void commitTempUploads(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull List<TempAttachmentDto> temps) {
        attachmentPortFactory.get().commitTempUploads(entityType, entityId, temps);
    }

    public void captureSnapshot(@NonNull EntityType entityType, @NonNull Long entityId) {
        attachmentPortFactory.get().captureSnapshot(entityType, entityId);
    }

    public void discardTempUploads(@NonNull List<TempAttachmentDto> temps) {
        attachmentPortFactory.get().discardTempUploads(temps);
    }

    public void restoreToUrls(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull String[] targetUrls) {
        attachmentPortFactory.get().restoreToUrls(entityType, entityId, targetUrls);
    }

    // ── audit diff ────────────────────────────────────────────────────────────

    public String getMediaStateForSnapshot(@NonNull EntityRef entity, @NonNull Long snapshotId) {
        return attachmentAuditPortFactory.findIfAvailable()
                .map(p -> p.getMediaStateForSnapshot(entity, snapshotId))
                .orElse(null);
    }

    public List<ChangeEntry> getChangesBySnapshotId(@NonNull Long attachmentSnapshotId) {
        return attachmentAuditPortFactory.findIfAvailable()
                .map(p -> p.getChangesBySnapshotId(attachmentSnapshotId))
                .orElse(List.of());
    }

    public boolean isAvailable() {
        return attachmentPortFactory.findIfAvailable().isPresent();
    }
}
