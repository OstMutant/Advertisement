package org.ost.attachment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.attachment.entities.Attachment;
import org.ost.attachment.entities.MediaContentType;
import org.ost.attachment.repository.AttachmentRepository;
import org.ost.attachment.util.YoutubeUtil;
import org.ost.platform.core.spi.CurrentActorProvider;
import org.ost.platform.attachment.spi.MediaChangeConsumer;
import org.ost.platform.attachment.spi.MediaSummary;
import org.ost.platform.attachment.storage.ConditionalOnStorageEnabled;
import org.ost.platform.attachment.storage.StorageService;
import org.ost.platform.core.model.EntityType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnStorageEnabled
public class AttachmentService {

    public record TempAttachment(String tempUrl, String filename, String contentType, long size) {}

    private static final String CT_YOUTUBE = "video/youtube";
    private static final String CT_EMBED   = "video/embed";

    private final StorageService                                  storageService;
    private final AttachmentRepository                            attachmentRepository;
    private final AttachmentSnapshotService                       attachmentSnapshotService;
    private final ObjectProvider<CurrentActorProvider>   currentActorProvider;
    private final ObjectProvider<MediaChangeConsumer>             mediaChangeConsumer;

    public List<Attachment> getByEntityId(EntityType entityType, Long entityId) {
        return attachmentRepository.getByEntityId(entityType, entityId);
    }

    public MediaSummary getMediaSummary(EntityType entityType, Long entityId) {
        AttachmentRepository.MediaStats stats = attachmentRepository.loadMediaStats(entityType, entityId);
        return new MediaSummary(
                resolveDisplayUrl(stats.mainUrl(), stats.mainContentType()),
                stats.mainContentType(),
                stats.count());
    }

    private static String resolveDisplayUrl(String url, String contentType) {
        if (url == null) return null;
        if (CT_YOUTUBE.equals(contentType)) return YoutubeUtil.thumbnailUrl(YoutubeUtil.extractId(url));
        if (CT_EMBED.equals(contentType))   return null;
        if (MediaContentType.isUploadedVideo(contentType)) return url;
        return url;
    }

    public Attachment upload(EntityType entityType, Long entityId, String filename,
                             InputStream inputStream, long contentLength, String contentType) {
        String url = storageService.upload(folder(entityType, entityId), filename, inputStream, contentLength, contentType);
        try {
            Attachment saved = attachmentRepository.insert(entityType, entityId, url, filename, contentType, contentLength);
            captureMediaChanges(entityType, entityId);
            notifyMediaChanged(entityType, entityId);
            return saved;
        } catch (Exception e) {
            storageService.delete(url);
            throw e;
        }
    }

    @Transactional
    public void delete(Long attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId);
        if (attachment == null) return;
        Long actorId = resolveCurrentActorId();
        attachmentRepository.softDelete(attachmentId, actorId);
        captureMediaChanges(attachment.getEntityType(), attachment.getEntityId());
        notifyMediaChanged(attachment.getEntityType(), attachment.getEntityId());
    }

    @Transactional
    public void deleteSkipSnapshot(Long attachmentId) {
        Long actorId = resolveCurrentActorId();
        attachmentRepository.softDelete(attachmentId, actorId);
    }

    public TempAttachment addVideoTemp(String url) {
        String ytId = YoutubeUtil.extractId(url);
        if (ytId != null) {
            return new TempAttachment("https://www.youtube.com/watch?v=" + ytId, "YouTube-" + ytId, CT_YOUTUBE, 0L);
        }
        if (url == null || url.isBlank()) throw new IllegalArgumentException("Invalid video URL");
        String filename = url.replaceAll("https?://", "").replaceAll("[^a-zA-Z0-9._-]", "_");
        return new TempAttachment(url, filename, CT_EMBED, 0L);
    }

    @Transactional
    public Attachment addVideo(EntityType entityType, Long entityId, String url) {
        String ytId = YoutubeUtil.extractId(url);
        if (ytId != null) {
            String watchUrl = "https://www.youtube.com/watch?v=" + ytId;
            Attachment saved = attachmentRepository.insert(entityType, entityId, watchUrl, "YouTube-" + ytId, CT_YOUTUBE, 0L);
            captureMediaChanges(entityType, entityId);
            notifyMediaChanged(entityType, entityId);
            return saved;
        }
        if (url == null || url.isBlank()) throw new IllegalArgumentException("Invalid video URL");
        String filename = url.replaceAll("https?://", "").replaceAll("[^a-zA-Z0-9._-]", "_");
        Attachment saved = attachmentRepository.insert(entityType, entityId, url, filename, CT_EMBED, 0L);
        captureMediaChanges(entityType, entityId);
        notifyMediaChanged(entityType, entityId);
        return saved;
    }

    public TempAttachment uploadTemp(String tempSessionId, String filename,
                                     InputStream inputStream, long contentLength, String contentType) {
        String tempUrl = storageService.upload("temp/" + tempSessionId, filename, inputStream, contentLength, contentType);
        return new TempAttachment(tempUrl, filename, contentType, contentLength);
    }

    public void commitTempUploads(EntityType entityType, Long entityId, List<TempAttachment> temps) {
        commitTempUploadsQuiet(entityType, entityId, temps);
        captureMediaChanges(entityType, entityId);
        notifyMediaChanged(entityType, entityId);
    }

    public void captureSnapshot(EntityType entityType, Long entityId) {
        captureMediaChanges(entityType, entityId);
    }

    public void commitTempUploadsQuiet(EntityType entityType, Long entityId, List<TempAttachment> temps) {
        if (temps.isEmpty()) return;
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
            toSave.forEach(a -> attachmentRepository.insert(a.getEntityType(), a.getEntityId(),
                    a.getUrl(), a.getFilename(), a.getContentType(), a.getSize()));
        } catch (Exception e) {
            toSave.stream()
                    .filter(a -> !isVideo(a.getContentType()))
                    .forEach(a -> storageService.delete(a.getUrl()));
            throw e;
        }
    }

    @Transactional
    public void restoreToUrls(EntityType entityType, Long entityId, String[] targetUrls, Long actorId) {
        if (targetUrls == null || targetUrls.length == 0) {
            attachmentRepository.restoreDeleteAll(entityType, entityId, actorId);
            notifyMediaChanged(entityType, entityId);
            return;
        }
        attachmentRepository.restoreUndelete(entityType, entityId, targetUrls);
        attachmentRepository.restoreMarkDeleted(entityType, entityId, actorId, targetUrls);
        notifyMediaChanged(entityType, entityId);
    }

    @Transactional
    public void softDeleteAll(EntityType entityType, Long entityId, Long actorId) {
        attachmentRepository.softDeleteAll(entityType, entityId, actorId);
        notifyMediaChanged(entityType, entityId);
    }

    public void discardTempUploads(List<TempAttachment> temps) {
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
        mediaChangeConsumer.ifAvailable(c -> c.onMediaChanged(entityType, entityId));
    }

    private void captureMediaChanges(EntityType entityType, Long entityId) {
        Long actorId = resolveCurrentActorId();
        if (actorId != null) {
            attachmentSnapshotService.capture(entityType, entityId, actorId);
        }
    }

    private Long resolveCurrentActorId() {
        CurrentActorProvider p = currentActorProvider.getIfAvailable();
        return p == null ? null : p.getCurrentActorId().orElse(null);
    }
}
