package org.ost.attachment.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.attachment.entities.Attachment;
import org.ost.attachment.repository.AttachmentRepository;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.ost.platform.attachment.dto.AttachmentMediaSummaryDto;
import org.ost.platform.attachment.dto.TempAttachmentDto;
import org.ost.platform.attachment.util.YoutubeUtil;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.CurrentActorHook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
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
    private static final Set<String> ALLOWED_EMBED_HOSTS = Set.of("vimeo.com", "player.vimeo.com");

    private final StorageService              storageService;
    private final AttachmentRepository        attachmentRepository;
    private final AttachmentSnapshotService   attachmentSnapshotService;
    private final CurrentActorHook            currentActorHook;

    public List<AttachmentItemDto> getByEntityId(@NonNull EntityType entityType, @NonNull Long entityId) {
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
    public AttachmentItemDto upload(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull String filename,
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
            return toDto(saved);
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
        });
    }

    public TempAttachmentDto addVideoTemp(@NonNull String url) {
        VideoDescriptor d = resolveVideoDescriptor(url);
        return new TempAttachmentDto(d.url(), d.filename(), d.contentType(), 0L);
    }

    @Transactional
    public AttachmentItemDto addVideo(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull String url) {
        log.info("Attachment add video: entityType={}, entityId={}", entityType, entityId);
        VideoDescriptor d = resolveVideoDescriptor(url);
        Attachment saved = attachmentRepository.save(Attachment.builder()
                .entityType(entityType).entityId(entityId)
                .url(d.url()).filename(d.filename())
                .contentType(d.contentType()).size(0L).build());
        captureMediaChanges(entityType, entityId);
        return toDto(saved);
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

    public List<AttachmentItemDto> getByEntityAndUrls(@NonNull EntityType entityType, @NonNull Long entityId,
                                                       @NonNull String[] urls) {
        return attachmentRepository.findByEntityAndUrls(entityType, entityId, urls).stream().map(AttachmentService::toDto).toList();
    }

    @Transactional
    public void restoreToUrls(@NonNull EntityType entityType, @NonNull Long entityId,
                              @NonNull String[] targetUrls) {
        Long actorId = currentActorHook.getCurrentActorId().orElseThrow();
        restoreToUrls(entityType, entityId, targetUrls, actorId);
    }

    private void restoreToUrls(EntityType entityType, Long entityId, String[] targetUrls, Long actorId) {
        if (targetUrls.length == 0) {
            attachmentRepository.softDeleteAll(entityType, entityId, actorId);
            return;
        }
        attachmentRepository.restoreUndelete(entityType, entityId, targetUrls);
        attachmentRepository.restoreMarkDeleted(entityType, entityId, actorId, targetUrls);
    }

    @Transactional
    public void softDeleteAll(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull Long actorId) {
        log.info("Attachment delete all: entityType={}, entityId={}", entityType, entityId);
        attachmentRepository.softDeleteAll(entityType, entityId, actorId);
    }

    public void discardTempUploads(@NonNull List<TempAttachmentDto> temps) {
        temps.stream()
             .filter(t -> !isVideo(t.contentType()))
             .forEach(t -> storageService.delete(t.tempUrl()));
    }

    // ── internals ────────────────────────────────────────────────────────────

    public static AttachmentItemDto toDto(Attachment a) {
        return new AttachmentItemDto(a.getId(), a.getUrl(), a.getFilename(), a.getContentType());
    }

    private record VideoDescriptor(String url, String filename, String contentType) {}

    private VideoDescriptor resolveVideoDescriptor(String url) {
        String ytId = YoutubeUtil.extractId(url);
        if (ytId != null) {
            return new VideoDescriptor(YoutubeUtil.watchUrl(ytId), YoutubeUtil.filename(ytId), CT_YOUTUBE);
        }
        if (url.isBlank()) throw new IllegalArgumentException("Invalid video URL");
        validateEmbedUrl(url);
        return new VideoDescriptor(url, embedFilename(url), CT_EMBED);
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

    private static void validateEmbedUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid video URL", e);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        boolean validScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        boolean validHost = host != null && ALLOWED_EMBED_HOSTS.stream()
                .anyMatch(allowed -> host.equalsIgnoreCase(allowed) || host.toLowerCase().endsWith("." + allowed));
        if (!validScheme || !validHost) throw new IllegalArgumentException("Invalid video URL");
    }

    private static boolean isVideo(String contentType) {
        return CT_YOUTUBE.equals(contentType) || CT_EMBED.equals(contentType);
    }

    private static String folder(EntityType entityType, Long entityId) {
        return "%s/%d".formatted(entityType.name().toLowerCase(), entityId);
    }

    private void captureMediaChanges(EntityType entityType, Long entityId) {
        Long actorId = currentActorHook.getCurrentActorId().orElseThrow();
        attachmentSnapshotService.capture(entityType, entityId, actorId);
    }
}
