package org.ost.attachment.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.attachment.repository.AttachmentRepository;
import org.ost.attachment.repository.AttachmentSnapshotRepository;
import org.ost.platform.attachment.model.AttachmentMediaContentType;
import org.ost.platform.core.config.CleanupProperties;
import org.ost.platform.core.model.EntityType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentCleanupService {

    private final AttachmentRepository         attachmentRepository;
    private final AttachmentSnapshotRepository attachmentSnapshotRepository;
    private final StorageService               storageService;
    private final CleanupProperties             cleanupProperties;

    /**
     * Deliberately not {@code @Transactional}: {@link #deleteAttachments} depends on its single
     * {@code deleteByUrls()} DELETE statement (already atomic on its own — one SQL statement) auto
     * -committing immediately, before the S3 delete loop that follows it. Wrapping this method in
     * a transaction would defer that commit until the whole method returns, recreating the exact
     * crash-window bug this ordering exists to close — see improvement-049 item 4.
     */
    public void cleanup() {
        log.info("Attachment cleanup started, retention = {} days", cleanupProperties.retentionDays());
        deleteStaleTempUploads();
        deleteAttachments();
        deleteStaleSnapshots();
        sweepOrphanedEntityFiles();
        log.info("Attachment cleanup finished");
    }

    private void deleteStaleTempUploads() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.DAYS);
        List<String> stale = storageService.listByPrefix("temp/", cutoff);
        if (stale.isEmpty()) {
            log.info("Deleted 0 stale temp uploads");
            return;
        }
        int deleted = 0;
        for (String url : stale) {
            try {
                storageService.delete(url);
                deleted++;
            } catch (Exception e) { //NOSONAR java:S7467 — e.getMessage() is used
                log.warn("Failed to delete stale temp upload {}: {}", url, e.getMessage());
            }
        }
        log.info("Deleted {} stale temp uploads", deleted);
    }

    private void deleteAttachments() {
        List<AttachmentRepository.DeletableAttachment> candidates =
                attachmentRepository.findUrlsDeletedOlderThan(cleanupProperties.retentionDays());
        if (candidates.isEmpty()) {
            log.info("Deleted 0 attachments");
            return;
        }

        List<String> allUrls = candidates.stream().map(AttachmentRepository.DeletableAttachment::url).toList();
        Set<String> videoUrls = candidates.stream()
                .filter(c -> AttachmentMediaContentType.isEmbedded(c.contentType()))
                .map(AttachmentRepository.DeletableAttachment::url)
                .collect(Collectors.toSet());

        // DB deleted+committed first (see cleanup() javadoc); only actually-removed urls reach S3 delete (improvement-090 item 1)
        List<String> deletedUrls = attachmentRepository.deleteByUrls(allUrls);
        log.info("Deleted {} attachments", deletedUrls.size());

        Set<String> failedUrls = new HashSet<>();
        deletedUrls.stream()
                .filter(url -> !videoUrls.contains(url)) // external video urls have no S3 object
                .forEach(url -> {
                    try { storageService.delete(url); } catch (Exception e) { //NOSONAR java:S7467 — e.getMessage() is used
                        log.warn("Failed to delete S3 object {}: {}", url, e.getMessage());
                        failedUrls.add(url);
                    }
                });
        if (!failedUrls.isEmpty()) {
            log.warn("{} S3 objects failed to delete after their DB rows were already removed — orphaned storage, safe to sweep later", failedUrls.size());
        }
    }

    // pure historical bookkeeping -- age-based purge is safe (improvement-090 item 3)
    private void deleteStaleSnapshots() {
        int deleted = attachmentSnapshotRepository.deleteOlderThan(cleanupProperties.retentionDays());
        log.info("Deleted {} stale attachment snapshots", deleted);
    }

    // Deletes entity-folder S3 files with no matching attachment row at all -- left behind when a
    // save's DB transaction rolls back after storageService.move() already ran.
    private void sweepOrphanedEntityFiles() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.DAYS);
        int deleted = 0;
        for (EntityType type : EntityType.values()) {
            List<String> candidates = storageService.listByPrefix(type.name().toLowerCase() + "/", cutoff);
            if (candidates.isEmpty()) continue;
            Set<String> existing = attachmentRepository.findExistingUrls(candidates);
            for (String url : candidates) {
                if (existing.contains(url)) continue;
                try {
                    storageService.delete(url);
                    deleted++;
                } catch (Exception e) { //NOSONAR java:S7467 — e.getMessage() is used
                    log.warn("Failed to delete orphaned entity file {}: {}", url, e.getMessage());
                }
            }
        }
        log.info("Deleted {} orphaned entity files with no matching attachment row", deleted);
    }
}
