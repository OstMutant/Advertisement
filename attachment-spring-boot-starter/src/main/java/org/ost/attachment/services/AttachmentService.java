package org.ost.attachment.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.attachment.entities.Attachment;
import org.ost.attachment.repository.AttachmentRepository;
import org.ost.attachment.util.MediaContentTypeUtil;
import org.ost.attachment.util.YoutubeUtil;
import org.ost.platform.core.spi.CurrentActorHook;
import org.ost.platform.attachment.spi.AttachmentMediaChangeHook;
import org.ost.platform.attachment.dto.AttachmentMediaSummaryDto;
import org.ost.attachment.services.StorageService;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    public record TempAttachment(String tempUrl, String filename, String contentType, long size) {}

    private static final String CT_YOUTUBE = "video/youtube";
    private static final String CT_EMBED   = "video/embed";

    private final StorageService              storageService;
    private final AttachmentRepository        attachmentRepository;
    private final AttachmentSnapshotService   attachmentSnapshotService;
    private final CurrentActorHook            currentActorHook;
    private final AttachmentMediaChangeHook   mediaChangeHook;

    public List<Attachment> getByEntityId(@NonNull EntityType entityType, @NonNull Long entityId) {
        return attachmentRepository.getByEntityId(entityType, entityId);
    }

    public AttachmentMediaSummaryDto getMediaSummary(@NonNull EntityType entityType, @NonNull Long entityId) {
        AttachmentRepository.MediaStats stats = attachmentRepository.loadMediaStats(entityType, entityId);
        return new AttachmentMediaSummaryDto(
                resolveDisplayUrl(stats.mainUrl(), stats.mainContentType()),
                stats.mainContentType(),
                stats.count());
    }

    private static String resolveDisplayUrl(String url, String contentType) {
        if (url == null) return null;
        if (CT_YOUTUBE.equals(contentType)) return YoutubeUtil.thumbnailUrl(YoutubeUtil.extractId(url));
        if (CT_EMBED.equals(contentType))   return null;
        if (MediaContentTypeUtil.isUploadedVideo(contentType)) return url;
        return url;
    }

    public Attachment upload(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull String filename,
                             @NonNull InputStream inputStream, long contentLength, @NonNull String contentType) {
        String url = storageService.upload(folder(entityType, entityId), filename, inputStream, contentLength, contentType);
        try {
            Attachment saved = attachmentRepository.save(Attachment.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .url(url)
                    .filename(filename)
                    .contentType(contentType)
                    .size(contentLength)
                    .build());
            captureMediaChanges(entityType, entityId);
            notifyMediaChanged(entityType, entityId);
            return saved;
        } catch (Exception e) {
            storageService.delete(url);
            throw e;
        }
    }

    @Transactional
    public void delete(@NonNull Long attachmentId) {
        attachmentRepository.findById(attachmentId).ifPresent(attachment -> {
            Long actorId = currentActorHook.getCurrentActorId().orElseThrow();
            attachmentRepository.softDelete(attachmentId, actorId);
            captureMediaChanges(attachment.getEntityType(), attachment.getEntityId());
            notifyMediaChanged(attachment.getEntityType(), attachment.getEntityId());
        });
    }

    @Transactional
    public void deleteSkipSnapshot(@NonNull Long attachmentId) {
        attachmentRepository.findById(attachmentId).ifPresent(attachment -> {
            Long actorId = currentActorHook.getCurrentActorId().orElseThrow();
            attachmentRepository.softDelete(attachmentId, actorId);
            notifyMediaChanged(attachment.getEntityType(), attachment.getEntityId());
        });
    }

    public TempAttachment addVideoTemp(@NonNull String url) {
        String ytId = YoutubeUtil.extractId(url);
        if (ytId != null) {
            return new TempAttachment("https://www.youtube.com/watch?v=" + ytId, "YouTube-" + ytId, CT_YOUTUBE, 0L);
        }
        if (url.isBlank()) throw new IllegalArgumentException("Invalid video URL");
        String filename = url.replaceAll("https?://", "").replaceAll("[^a-zA-Z0-9._-]", "_");
        return new TempAttachment(url, filename, CT_EMBED, 0L);
    }

    @Transactional
    public Attachment addVideo(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull String url) {
        String ytId = YoutubeUtil.extractId(url);
        if (ytId != null) {
            String watchUrl = "https://www.youtube.com/watch?v=" + ytId;
            Attachment saved = attachmentRepository.save(Attachment.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .url(watchUrl)
                    .filename("YouTube-" + ytId)
                    .contentType(CT_YOUTUBE)
                    .size(0L)
                    .build());
            captureMediaChanges(entityType, entityId);
            notifyMediaChanged(entityType, entityId);
            return saved;
        }
        if (url.isBlank()) throw new IllegalArgumentException("Invalid video URL");
        String filename = url.replaceAll("https?://", "").replaceAll("[^a-zA-Z0-9._-]", "_");
        Attachment saved = attachmentRepository.save(Attachment.builder()
                .entityType(entityType)
                .entityId(entityId)
                .url(url)
                .filename(filename)
                .contentType(CT_EMBED)
                .size(0L)
                .build());
        captureMediaChanges(entityType, entityId);
        notifyMediaChanged(entityType, entityId);
        return saved;
    }

    public TempAttachment uploadTemp(@NonNull String tempSessionId, @NonNull String filename,
                                     @NonNull InputStream inputStream, long contentLength, @NonNull String contentType) {
        String tempUrl = storageService.upload("temp/" + tempSessionId, filename, inputStream, contentLength, contentType);
        return new TempAttachment(tempUrl, filename, contentType, contentLength);
    }

    public void commitTempUploads(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull List<TempAttachment> temps) {
        commitTempUploadsQuiet(entityType, entityId, temps);
        captureMediaChanges(entityType, entityId);
        notifyMediaChanged(entityType, entityId);
    }

    public void captureSnapshot(@NonNull EntityType entityType, @NonNull Long entityId) {
        captureMediaChanges(entityType, entityId);
    }

    public void commitTempUploadsQuiet(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull List<TempAttachment> temps) {
        String folder = folder(entityType, entityId);
        List<Attachment> toSave = temps.stream()
                .map(t -> {
                    String finalUrl = isVideo(t.contentType())
                            ? t.tempUrl()
                            : storageService.move(t.tempUrl(), folder, t.filename());
                    return Attachment.builder()
                            .entityType(entityType)
                            .entityId(entityId)
                            .url(finalUrl)
                            .filename(t.filename())
                            .contentType(t.contentType())
                            .size(t.size())
                            .build();
                })
                .toList();
        try {
            attachmentRepository.saveAll(toSave);
        } catch (Exception e) {
            toSave.stream()
                    .filter(a -> !isVideo(a.getContentType()))
                    .forEach(a -> storageService.delete(a.getUrl()));
            throw e;
        }
    }

    @Transactional
    public void restoreToUrls(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull String[] targetUrls, @NonNull Long actorId) {
        if (targetUrls.length == 0) {
            attachmentRepository.softDeleteAll(entityType, entityId, actorId);
            notifyMediaChanged(entityType, entityId);
            return;
        }
        attachmentRepository.restoreUndelete(entityType, entityId, targetUrls);
        attachmentRepository.restoreMarkDeleted(entityType, entityId, actorId, targetUrls);
        notifyMediaChanged(entityType, entityId);
    }

    @Transactional
    public void softDeleteAll(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull Long actorId) {
        attachmentRepository.softDeleteAll(entityType, entityId, actorId);
        notifyMediaChanged(entityType, entityId);
    }

    public void discardTempUploads(@NonNull List<TempAttachment> temps) {
        temps.stream()
             .filter(t -> !isVideo(t.contentType()))
             .forEach(t -> storageService.delete(t.tempUrl()));
    }

    // ── internals ────────────────────────────────────────────────────────────

    private static boolean isVideo(String contentType) {
        return CT_YOUTUBE.equals(contentType) || CT_EMBED.equals(contentType);
    }

    private static String folder(EntityType entityType, Long entityId) {
        return entityType.name().toLowerCase() + "/" + entityId;
    }

    private void notifyMediaChanged(EntityType entityType, Long entityId) {
        mediaChangeHook.onMediaChanged(new EntityRef(entityType, entityId));
    }

    private void captureMediaChanges(EntityType entityType, Long entityId) {
        currentActorHook.getCurrentActorId().ifPresent(actorId ->
                attachmentSnapshotService.capture(entityType, entityId, actorId));
    }
}
