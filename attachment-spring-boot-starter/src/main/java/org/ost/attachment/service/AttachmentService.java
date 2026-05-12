package org.ost.attachment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.attachment.entity.Attachment;
import org.ost.attachment.repository.AttachmentRepository;
import org.ost.attachment.util.YoutubeUtil;
import org.ost.advertisement.events.spi.AttachmentCurrentUserProvider;
import org.ost.advertisement.events.AdvertisementMediaUpdatedEvent;
import org.ost.advertisement.spi.storage.ConditionalOnStorageEnabled;
import org.ost.advertisement.spi.storage.StorageService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
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
    private final PhotoSnapshotService                            photoSnapshotService;
    private final ObjectProvider<AttachmentCurrentUserProvider>   currentUserProvider;
    private final ApplicationEventPublisher                       eventPublisher;

    public List<Attachment> getByEntityId(Long entityId) {
        return attachmentRepository.getByEntityId(entityId);
    }

    public Attachment upload(Long entityId, String filename,
                             InputStream inputStream, long contentLength, String contentType) {
        String url = storageService.upload("advertisements/" + entityId, filename, inputStream, contentLength, contentType);
        try {
            Attachment saved = attachmentRepository.insert(entityId, url, filename, contentType, contentLength);
            captureMediaChanges(entityId);
            publishMediaUpdate(entityId);
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
        Long userId = resolveCurrentUserId();
        attachmentRepository.softDelete(attachmentId, userId);
        captureMediaChanges(attachment.getEntityId());
        publishMediaUpdate(attachment.getEntityId());
    }

    @Transactional
    public void deleteSkipSnapshot(Long attachmentId) {
        Long userId = resolveCurrentUserId();
        attachmentRepository.softDelete(attachmentId, userId);
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
    public Attachment addVideo(Long entityId, String url) {
        String ytId = YoutubeUtil.extractId(url);
        if (ytId != null) {
            String watchUrl = "https://www.youtube.com/watch?v=" + ytId;
            Attachment saved = attachmentRepository.insert(entityId, watchUrl, "YouTube-" + ytId, CT_YOUTUBE, 0L);
            captureMediaChanges(entityId);
            publishMediaUpdate(entityId);
            return saved;
        }
        if (url == null || url.isBlank()) throw new IllegalArgumentException("Invalid video URL");
        String filename = url.replaceAll("https?://", "").replaceAll("[^a-zA-Z0-9._-]", "_");
        Attachment saved = attachmentRepository.insert(entityId, url, filename, CT_EMBED, 0L);
        captureMediaChanges(entityId);
        publishMediaUpdate(entityId);
        return saved;
    }

    public TempAttachment uploadTemp(String tempSessionId, String filename,
                                     InputStream inputStream, long contentLength, String contentType) {
        String tempUrl = storageService.upload("temp/" + tempSessionId, filename, inputStream, contentLength, contentType);
        return new TempAttachment(tempUrl, filename, contentType, contentLength);
    }

    public void commitTempUploads(Long entityId, List<TempAttachment> temps) {
        commitTempUploadsQuiet(entityId, temps);
        captureMediaChanges(entityId);
        publishMediaUpdate(entityId);
    }

    public void captureSnapshot(Long entityId) {
        captureMediaChanges(entityId);
    }

    public void commitTempUploadsQuiet(Long entityId, List<TempAttachment> temps) {
        if (temps.isEmpty()) return;
        String folder = "advertisements/" + entityId;
        List<Attachment> toSave = temps.stream()
                .map(t -> {
                    String finalUrl = isVideo(t.contentType())
                            ? t.tempUrl()
                            : storageService.move(t.tempUrl(), folder, t.filename());
                    return Attachment.builder()
                            .entityId(entityId)
                            .url(finalUrl)
                            .filename(t.filename())
                            .contentType(t.contentType())
                            .size(t.size())
                            .build();
                })
                .toList();
        try {
            toSave.forEach(a -> attachmentRepository.insert(a.getEntityId(), a.getUrl(), a.getFilename(), a.getContentType(), a.getSize()));
        } catch (Exception e) {
            toSave.stream()
                    .filter(a -> !isVideo(a.getContentType()))
                    .forEach(a -> storageService.delete(a.getUrl()));
            throw e;
        }
    }

    @Transactional
    public void restoreToUrls(Long adId, String[] targetUrls, Long userId) {
        if (targetUrls == null || targetUrls.length == 0) {
            attachmentRepository.restoreDeleteAll(adId, userId);
            return;
        }
        attachmentRepository.restoreUndelete(adId, targetUrls);
        attachmentRepository.restoreMarkDeleted(adId, userId, targetUrls);
        publishMediaUpdate(adId);
    }

    @Transactional
    public void softDeleteAll(Long entityId, Long deletedByUserId) {
        attachmentRepository.softDeleteAll(entityId, deletedByUserId);
        publishMediaUpdate(entityId);
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

    private void publishMediaUpdate(Long entityId) {
        AttachmentRepository.MediaStats stats = attachmentRepository.loadMediaStats(entityId);
        String displayUrl = resolveDisplayUrl(stats.mainUrl(), stats.mainContentType());
        eventPublisher.publishEvent(new AdvertisementMediaUpdatedEvent(entityId, displayUrl, stats.count()));
    }

    private static String resolveDisplayUrl(String url, String contentType) {
        if (url == null) return null;
        if (CT_YOUTUBE.equals(contentType)) return YoutubeUtil.thumbnailUrl(YoutubeUtil.extractId(url));
        if (CT_EMBED.equals(contentType)) return null;
        return url;
    }

    private void captureMediaChanges(Long entityId) {
        Long userId = resolveCurrentUserId();
        if (userId != null) {
            photoSnapshotService.capture(entityId, userId);
        }
    }

    private Long resolveCurrentUserId() {
        AttachmentCurrentUserProvider p = currentUserProvider.getIfAvailable();
        return p == null ? null : p.getCurrentUserId().orElse(null);
    }
}
