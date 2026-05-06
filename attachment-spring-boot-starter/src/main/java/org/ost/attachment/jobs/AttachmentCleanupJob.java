package org.ost.attachment.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.spi.storage.StorageService;
import org.ost.attachment.repository.AttachmentRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentCleanupJob {

    private final AttachmentRepository           attachmentRepository;
    private final ObjectProvider<StorageService> storageService;

    @Value("${app.cleanup.retention-days:90}")
    private int retentionDays;

    @Scheduled(cron = "0 0 2 * * *", zone = "Europe/Kyiv")
    @Transactional
    public void run() {
        log.info("Attachment cleanup started, retention = {} days", retentionDays);
        deleteStaleTempUploads();
        deleteAttachments();
        log.info("Attachment cleanup finished");
    }

    private void deleteStaleTempUploads() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.DAYS);
        storageService.ifAvailable(s -> {
            List<String> stale = s.listByPrefix("temp/", cutoff);
            if (stale.isEmpty()) {
                log.info("Deleted 0 stale temp uploads");
                return;
            }
            int deleted = 0;
            for (String url : stale) {
                try {
                    s.delete(url);
                    deleted++;
                } catch (Exception e) {
                    log.warn("Failed to delete stale temp upload {}: {}", url, e.getMessage());
                }
            }
            log.info("Deleted {} stale temp uploads", deleted);
        });
    }

    private void deleteAttachments() {
        List<String> urls = attachmentRepository.findUrlsDeletedOlderThan(retentionDays);
        if (urls.isEmpty()) {
            log.info("Deleted 0 attachments");
            return;
        }

        Set<String> failedUrls = new HashSet<>();
        storageService.ifAvailable(s -> urls.forEach(url -> {
            try { s.delete(url); } catch (Exception e) {
                log.warn("Failed to delete S3 object {}: {}", url, e.getMessage());
                failedUrls.add(url);
            }
        }));

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
