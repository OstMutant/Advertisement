package org.ost.attachment.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.attachment.entities.Attachment;
import org.ost.attachment.repository.AttachmentRepository;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.ost.platform.attachment.dto.AttachmentMediaSummaryDto;
import org.ost.platform.attachment.dto.TempAttachmentDto;
import org.ost.platform.attachment.spi.AttachmentMediaChangeHook;
import org.ost.platform.attachment.util.YoutubeUtil;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.CurrentActorHook;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    private static final String CT_YOUTUBE = "video/youtube";
    private static final String CT_EMBED   = "video/embed";

    private final StorageService              storageService;
    private final AttachmentRepository        attachmentRepository;
    private final AttachmentSnapshotService   attachmentSnapshotService;
    private final CurrentActorHook            currentActorHook;
    private final ObjectProvider<AttachmentMediaChangeHook> mediaChangeHook;

    public List<Attachment> getByEntityId(@NonNull EntityType entityType, @NonNull Long entityId) {
        return attachmentRepository.getByEntityId(entityType, entityId);
    }

    public List<AttachmentItemDto> getByEntityIdDtos(@NonNull EntityType entityType, @NonNull Long entityId) {
        return attachmentRepository.getByEntityId(entityType, entityId).stream().map(AttachmentService::toDto).toList();
    }

    public AttachmentMediaSummaryDto getMediaSummary(@NonNull EntityType entityType, @NonNull Long entityId) {
        AttachmentRepository.MediaStats stats = attachmentRepository.loadMediaStats(entityType, entityId);
        return new AttachmentMediaSummaryDto(
                resolveDisplayUrl(stats.mainUrl(), stats.mainContentType()),
                stats.mainContentType(),
                stats.count());
    }

    public Map<Long, AttachmentMediaSummaryDto> getMediaSummaries(@NonNull EntityType entityType, @NonNull Set<Long> entityIds) {
        return attachmentRepository.loadMediaStats(entityType, entityIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                    AttachmentRepository.MediaStats stats = e.getValue();
                    return new AttachmentMediaSummaryDto(
                            resolveDisplayUrl(stats.mainUrl(), stats.mainContentType()),
                            stats.mainContentType(),
                            stats.count());
                }));
    }

    private static String resolveDisplayUrl(String url, String contentType) {
        if (url == null) return null;
        if (CT_YOUTUBE.equals(contentType)) return YoutubeUtil.thumbnailUrl(YoutubeUtil.extractId(url));
        if (CT_EMBED.equals(contentType))   return null;
        return url;
    }

    @Transactional
    public Attachment upload(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull String filename,
                             @NonNull InputStream inputStream, long contentLength, @NonNull String contentType) {
        log.info("Attachment upload: entityType={}, entityId={}, filename={}, size={}",
                entityType, entityId, filename, contentLength);
        String url = storageService.upload(folder(entityType, entityId), filename, inputStream, contentLength, contentType);
        closeQuietly(inputStream);
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
        log.info("Attachment delete: id={}", attachmentId);
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

    public TempAttachmentDto addVideoTemp(@NonNull String url) {
        String ytId = YoutubeUtil.extractId(url);
        if (ytId != null) {
            return new TempAttachmentDto(YoutubeUtil.watchUrl(ytId), YoutubeUtil.filename(ytId), CT_YOUTUBE, 0L);
        }
        if (url.isBlank()) throw new IllegalArgumentException("Invalid video URL");
        return new TempAttachmentDto(url, embedFilename(url), CT_EMBED, 0L);
    }

    @Transactional
    public Attachment addVideo(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull String url) {
        log.info("Attachment add video: entityType={}, entityId={}", entityType, entityId);
        String ytId = YoutubeUtil.extractId(url);
        if (url.isBlank() && ytId == null) throw new IllegalArgumentException("Invalid video URL");
        Attachment saved;
        if (ytId != null) {
            saved = attachmentRepository.save(Attachment.builder()
                    .entityType(entityType).entityId(entityId)
                    .url(YoutubeUtil.watchUrl(ytId)).filename(YoutubeUtil.filename(ytId))
                    .contentType(CT_YOUTUBE).size(0L).build());
        } else {
            saved = attachmentRepository.save(Attachment.builder()
                    .entityType(entityType).entityId(entityId)
                    .url(url).filename(embedFilename(url))
                    .contentType(CT_EMBED).size(0L).build());
        }
        captureMediaChanges(entityType, entityId);
        notifyMediaChanged(entityType, entityId);
        return saved;
    }

    public TempAttachmentDto uploadTemp(@NonNull String tempSessionId, @NonNull String filename,
                                        @NonNull InputStream inputStream, long contentLength,
                                        @NonNull String contentType) {
        String tempUrl = storageService.upload("temp/%s".formatted(tempSessionId), filename, inputStream, contentLength, contentType);
        closeQuietly(inputStream);
        return new TempAttachmentDto(tempUrl, filename, contentType, contentLength);
    }

    public void commitTempUploads(@NonNull EntityType entityType, @NonNull Long entityId,
                                  @NonNull List<TempAttachmentDto> temps) {
        commitTempUploadsQuiet(entityType, entityId, temps);
        captureMediaChanges(entityType, entityId);
        notifyMediaChanged(entityType, entityId);
    }

    public void captureSnapshot(@NonNull EntityType entityType, @NonNull Long entityId) {
        captureMediaChanges(entityType, entityId);
    }

    public void commitTempUploadsQuiet(@NonNull EntityType entityType, @NonNull Long entityId,
                                       @NonNull List<TempAttachmentDto> temps) {
        String folder = folder(entityType, entityId);
        List<Attachment> toSave = new ArrayList<>();
        try {
            for (TempAttachmentDto t : temps) {
                String finalUrl = isVideo(t.contentType())
                        ? t.tempUrl()
                        : storageService.move(t.tempUrl(), folder, t.filename());
                toSave.add(Attachment.builder()
                        .entityType(entityType)
                        .entityId(entityId)
                        .url(finalUrl)
                        .filename(t.filename())
                        .contentType(t.contentType())
                        .size(t.size())
                        .build());
            }
            attachmentRepository.saveAll(toSave);
        } catch (Exception e) {
            toSave.stream()
                    .filter(a -> !isVideo(a.getContentType()))
                    .forEach(a -> storageService.delete(a.getUrl()));
            throw e;
        }
    }

    public List<Attachment> getByEntityAndUrls(@NonNull EntityType entityType, @NonNull Long entityId,
                                               @NonNull String[] urls) {
        return attachmentRepository.findByEntityAndUrls(entityType, entityId, urls);
    }

    public List<AttachmentItemDto> getByEntityAndUrlsDtos(@NonNull EntityType entityType, @NonNull Long entityId,
                                                          @NonNull String[] urls) {
        return attachmentRepository.findByEntityAndUrls(entityType, entityId, urls).stream().map(AttachmentService::toDto).toList();
    }

    @Transactional
    public void restoreToUrls(@NonNull EntityType entityType, @NonNull Long entityId,
                              @NonNull String[] targetUrls) {
        Long actorId = currentActorHook.getCurrentActorId().orElseThrow();
        restoreToUrls(entityType, entityId, targetUrls, actorId);
    }

    @Transactional
    public void restoreToUrlsAndCapture(@NonNull EntityType entityType, @NonNull Long entityId,
                                        @NonNull String[] targetUrls) {
        Long actorId = currentActorHook.getCurrentActorId().orElseThrow();
        restoreToUrls(entityType, entityId, targetUrls, actorId);
        attachmentSnapshotService.capture(entityType, entityId, actorId);
    }

    @Transactional
    public void restoreToUrls(@NonNull EntityType entityType, @NonNull Long entityId,
                              @NonNull String[] targetUrls, @NonNull Long actorId) {
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
        log.info("Attachment delete all: entityType={}, entityId={}", entityType, entityId);
        attachmentRepository.softDeleteAll(entityType, entityId, actorId);
        notifyMediaChanged(entityType, entityId);
    }

    public void discardTempUploads(@NonNull List<TempAttachmentDto> temps) {
        temps.stream()
             .filter(t -> !isVideo(t.contentType()))
             .forEach(t -> storageService.delete(t.tempUrl()));
    }

    public AttachmentItemDto uploadDto(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull String filename,
                                       @NonNull InputStream inputStream, long contentLength, @NonNull String contentType) {
        return toDto(upload(entityType, entityId, filename, inputStream, contentLength, contentType));
    }

    public AttachmentItemDto addVideoDto(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull String url) {
        return toDto(addVideo(entityType, entityId, url));
    }

    // ── internals ────────────────────────────────────────────────────────────

    public static AttachmentItemDto toDto(Attachment a) {
        return new AttachmentItemDto(a.getId(), a.getUrl(), a.getFilename(), a.getContentType());
    }

    // Logged, not thrown -- a close failure shouldn't turn a successful upload into a reported one.
    private static void closeQuietly(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException e) {
            log.warn("Failed to close attachment upload input stream", e);
        }
    }

    private static String embedFilename(String url) {
        return url.replaceAll("https?://", "").replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static boolean isVideo(String contentType) {
        return CT_YOUTUBE.equals(contentType) || CT_EMBED.equals(contentType);
    }

    private static String folder(EntityType entityType, Long entityId) {
        return "%s/%d".formatted(entityType.name().toLowerCase(), entityId);
    }

    private void notifyMediaChanged(EntityType entityType, Long entityId) {
        mediaChangeHook.ifAvailable(hook -> hook.onMediaChanged(new EntityRef(entityType, entityId)));
    }

    private void captureMediaChanges(EntityType entityType, Long entityId) {
        Long actorId = currentActorHook.getCurrentActorId().orElseThrow();
        attachmentSnapshotService.capture(entityType, entityId, actorId);
    }
}
