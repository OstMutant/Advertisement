package org.ost.attachment.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.attachment.repository.AttachmentRepository;
import org.ost.platform.core.config.CleanupProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentCleanupService {

    private final AttachmentRepository  attachmentRepository;
    private final StorageService        storageService;
    private final CleanupProperties     cleanupProperties;

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
        List<String> urls = attachmentRepository.findUrlsDeletedOlderThan(cleanupProperties.retentionDays());
        if (urls.isEmpty()) {
            log.info("Deleted 0 attachments");
            return;
        }

        // DB rows deleted first, and committed immediately (see cleanup()'s javadoc for why this
        // method carries no @Transactional) -- a crash at any point after this line leaves only
        // orphaned S3 objects (safe, sweepable by prefix/age later), never a DB row pointing at a
        // file that no longer exists.
        int deleted = attachmentRepository.deleteByUrls(urls);
        log.info("Deleted {} attachments", deleted);

        Set<String> failedUrls = new HashSet<>();
        urls.forEach(url -> {
            try { storageService.delete(url); } catch (Exception e) { //NOSONAR java:S7467 — e.getMessage() is used
                log.warn("Failed to delete S3 object {}: {}", url, e.getMessage());
                failedUrls.add(url);
            }
        });
        if (!failedUrls.isEmpty()) {
            log.warn("{} S3 objects failed to delete after their DB rows were already removed — orphaned storage, safe to sweep later", failedUrls.size());
        }
    }
}
