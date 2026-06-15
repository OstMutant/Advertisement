package org.ost.platform.attachment.spi;

import lombok.NonNull;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.ost.platform.attachment.dto.AttachmentMediaSummaryDto;
import org.ost.platform.attachment.dto.TempAttachmentDto;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;

import java.io.InputStream;
import java.util.List;

public interface AttachmentPort {

    // ── domain lifecycle ──────────────────────────────────────────────────────

    void softDeleteAll(@NonNull EntityRef entity, @NonNull Long actorId);

    void restoreToSnapshot(@NonNull EntityRef entity, int snapshotVersion, @NonNull Long actorId);

    AttachmentMediaSummaryDto getMediaSummary(@NonNull EntityRef entity);

    // ── gallery queries ───────────────────────────────────────────────────────

    List<AttachmentItemDto> getByEntityId(@NonNull EntityType entityType, @NonNull Long entityId);

    List<AttachmentItemDto> getByEntityAndUrls(@NonNull EntityType entityType, @NonNull Long entityId,
                                               @NonNull String[] urls);

    String[] getSnapshotUrlsAtVersion(@NonNull EntityType entityType, @NonNull Long entityId, int version);

    // ── gallery upload commands ───────────────────────────────────────────────

    TempAttachmentDto uploadTemp(@NonNull String tempSessionId, @NonNull String filename,
                                 @NonNull InputStream inputStream, long contentLength,
                                 @NonNull String contentType);

    AttachmentItemDto upload(@NonNull EntityType entityType, @NonNull Long entityId,
                             @NonNull String filename, @NonNull InputStream inputStream,
                             long contentLength, @NonNull String contentType);

    TempAttachmentDto addVideoTemp(@NonNull String url);

    AttachmentItemDto addVideo(@NonNull EntityType entityType, @NonNull Long entityId,
                               @NonNull String url);

    // ── gallery commit/discard ────────────────────────────────────────────────

    void deleteSkipSnapshot(@NonNull Long attachmentId);

    void commitTempUploads(@NonNull EntityType entityType, @NonNull Long entityId,
                           @NonNull List<TempAttachmentDto> temps);

    void captureSnapshot(@NonNull EntityType entityType, @NonNull Long entityId);

    void discardTempUploads(@NonNull List<TempAttachmentDto> temps);

    void restoreToUrlsAndCapture(@NonNull EntityType entityType, @NonNull Long entityId,
                                 @NonNull String[] targetUrls);
}
