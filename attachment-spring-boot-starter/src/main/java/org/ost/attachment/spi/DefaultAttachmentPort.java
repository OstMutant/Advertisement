package org.ost.attachment.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.attachment.entities.Attachment;
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
    public void restoreToSnapshot(@NonNull EntityRef entity, int snapshotVersion, @NonNull Long actorId) {
        String[] targetUrls = attachmentSnapshotService.getUrlsAtVersion(entity.entityType(), entity.entityId(), snapshotVersion);
        attachmentService.restoreToUrls(entity.entityType(), entity.entityId(), targetUrls, actorId);
        attachmentSnapshotService.capture(entity.entityType(), entity.entityId(), actorId);
    }

    @Override
    public AttachmentMediaSummaryDto getMediaSummary(@NonNull EntityRef entity) {
        return attachmentService.getMediaSummary(entity.entityType(), entity.entityId());
    }

    // ── gallery queries ───────────────────────────────────────────────────────

    @Override
    public List<AttachmentItemDto> getByEntityId(@NonNull EntityType entityType, @NonNull Long entityId) {
        return attachmentService.getByEntityId(entityType, entityId).stream()
                .map(DefaultAttachmentPort::toDto)
                .toList();
    }

    @Override
    public List<AttachmentItemDto> getByEntityAndUrls(@NonNull EntityType entityType, @NonNull Long entityId,
                                                      @NonNull String[] urls) {
        return attachmentService.getByEntityAndUrls(entityType, entityId, urls).stream()
                .map(DefaultAttachmentPort::toDto)
                .toList();
    }

    @Override
    public String[] getSnapshotUrlsAtVersion(@NonNull EntityType entityType, @NonNull Long entityId, int version) {
        return attachmentSnapshotService.getUrlsAtVersion(entityType, entityId, version);
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
        return toDto(attachmentService.upload(entityType, entityId, filename, inputStream, contentLength, contentType));
    }

    @Override
    public TempAttachmentDto addVideoTemp(@NonNull String url) {
        return attachmentService.addVideoTemp(url);
    }

    @Override
    public AttachmentItemDto addVideo(@NonNull EntityType entityType, @NonNull Long entityId,
                                      @NonNull String url) {
        return toDto(attachmentService.addVideo(entityType, entityId, url));
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
    public void restoreToUrlsAndCapture(@NonNull EntityType entityType, @NonNull Long entityId,
                                        @NonNull String[] targetUrls) {
        attachmentService.restoreToUrlsAndCapture(entityType, entityId, targetUrls);
    }

    // ── internals ────────────────────────────────────────────────────────────

    private static AttachmentItemDto toDto(Attachment a) {
        return new AttachmentItemDto(a.getId(), a.getUrl(), a.getFilename(), a.getContentType());
    }
}
