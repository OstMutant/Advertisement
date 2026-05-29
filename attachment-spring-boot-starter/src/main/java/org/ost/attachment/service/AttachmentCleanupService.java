package org.ost.attachment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.attachment.repository.AttachmentRepository;
import org.ost.platform.core.config.CleanupProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
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

        Set<String> failedUrls = new HashSet<>();
        urls.forEach(url -> {
            try { storageService.delete(url); } catch (Exception e) { //NOSONAR java:S7467 — e.getMessage() is used
                log.warn("Failed to delete S3 object {}: {}", url, e.getMessage());
                failedUrls.add(url);
            }
        });

        List<String> toDelete = failedUrls.isEmpty()
                ? urls
                : urls.stream().filter(u -> !failedUrls.contains(u)).toList();

        int deleted = attachmentRepository.deleteByUrls(toDelete);
        log.info("Deleted {} attachments", deleted);
        if (!failedUrls.isEmpty()) {
            log.warn("{} attachments skipped — S3 deletion failed, will retry on next run", failedUrls.size());
        }
    }
}
