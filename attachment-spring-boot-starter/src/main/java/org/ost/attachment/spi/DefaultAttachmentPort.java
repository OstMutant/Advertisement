package org.ost.attachment.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.attachment.services.AttachmentService;
import org.ost.attachment.services.AttachmentSnapshotService;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.ost.platform.attachment.dto.AttachmentMediaSummaryDto;
import org.ost.platform.attachment.dto.TempAttachmentDto;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultAttachmentPort implements AttachmentPort {

    private final AttachmentService         attachmentService;
    private final AttachmentSnapshotService attachmentSnapshotService;

    // ── domain lifecycle ──────────────────────────────────────────────────────

    @Override
    public void softDeleteAll(@NonNull EntityRef entity, @NonNull Long actorId) {
        attachmentService.softDeleteAll(entity.entityType(), entity.entityId(), actorId);
    }

    @Override
    public AttachmentMediaSummaryDto getMediaSummary(@NonNull EntityRef entity) {
        return attachmentService.getMediaSummary(entity.entityType(), entity.entityId());
    }

    // ── gallery queries ───────────────────────────────────────────────────────

    @Override
    public List<AttachmentItemDto> getByEntityId(@NonNull EntityType entityType, @NonNull Long entityId) {
        return attachmentService.getByEntityIdDtos(entityType, entityId);
    }

    @Override
    public List<AttachmentItemDto> getByEntityAndUrls(@NonNull EntityType entityType, @NonNull Long entityId,
                                                      @NonNull String[] urls) {
        return attachmentService.getByEntityAndUrlsDtos(entityType, entityId, urls);
    }

    @Override
    public String[] getUrlsBySnapshotId(@NonNull Long snapshotId) {
        return attachmentSnapshotService.getUrlsBySnapshotId(snapshotId);
    }

    @Override
    public Long getLatestSnapshotId(@NonNull EntityType entityType, @NonNull Long entityId) {
        return attachmentSnapshotService.getLatestSnapshotId(entityType, entityId);
    }

    // ── gallery upload commands ───────────────────────────────────────────────

    @Override
    public TempAttachmentDto uploadTemp(@NonNull String tempSessionId, @NonNull String filename,
                                        @NonNull InputStream inputStream, long contentLength,
                                        @NonNull String contentType) {
        return attachmentService.uploadTemp(tempSessionId, filename, inputStream, contentLength, contentType);
    }

    @Override
    public AttachmentItemDto upload(@NonNull EntityType entityType, @NonNull Long entityId,
                                    @NonNull String filename, @NonNull InputStream inputStream,
                                    long contentLength, @NonNull String contentType) {
        return attachmentService.uploadDto(entityType, entityId, filename, inputStream, contentLength, contentType);
    }

    @Override
    public TempAttachmentDto addVideoTemp(@NonNull String url) {
        return attachmentService.addVideoTemp(url);
    }

    @Override
    public AttachmentItemDto addVideo(@NonNull EntityType entityType, @NonNull Long entityId,
                                      @NonNull String url) {
        return attachmentService.addVideoDto(entityType, entityId, url);
    }

    // ── gallery commit/discard ────────────────────────────────────────────────

    @Override
    public void deleteSkipSnapshot(@NonNull Long attachmentId) {
        attachmentService.deleteSkipSnapshot(attachmentId);
    }

    @Override
    public void commitTempUploads(@NonNull EntityType entityType, @NonNull Long entityId,
                                  @NonNull List<TempAttachmentDto> temps) {
        attachmentService.commitTempUploads(entityType, entityId, temps);
    }

    @Override
    public void captureSnapshot(@NonNull EntityType entityType, @NonNull Long entityId) {
        attachmentService.captureSnapshot(entityType, entityId);
    }

    @Override
    public void discardTempUploads(@NonNull List<TempAttachmentDto> temps) {
        attachmentService.discardTempUploads(temps);
    }

    @Override
    public void restoreToUrls(@NonNull EntityType entityType, @NonNull Long entityId,
                              @NonNull String[] targetUrls) {
        attachmentService.restoreToUrls(entityType, entityId, targetUrls);
    }

    @Override
    public void restoreToUrlsAndCapture(@NonNull EntityType entityType, @NonNull Long entityId,
                                        @NonNull String[] targetUrls) {
        attachmentService.restoreToUrlsAndCapture(entityType, entityId, targetUrls);
    }
}
