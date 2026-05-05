package org.ost.advertisement.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.repository.advertisement.AdvertisementProjection;
import org.ost.advertisement.repository.audit.AuditLogProjection;
import org.ost.attachment.repository.AttachmentProjection;
import org.ost.advertisement.spi.storage.StorageService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
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
public class CleanupJob {

    private final JdbcClient                     jdbcClient;
    private final ObjectProvider<StorageService> storageService;

    @Value("${app.cleanup.retention-days:90}")
    private int retentionDays;

    @Scheduled(cron = "0 0 2 * * *", zone = "Europe/Kyiv")
    @Transactional
    public void run() {
        log.info("Cleanup job started, retention = {} days", retentionDays);

        deleteStaleTempUploads();
        deleteAttachments();
        deleteAdvertisements();
        pruneSnapshots();

        log.info("Cleanup job finished");
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
        List<String> urls = jdbcClient.sql(
                "SELECT " + AttachmentProjection.Write.URL +
                " FROM " + AttachmentProjection.Write.TABLE +
                " WHERE deleted_at < NOW() - MAKE_INTERVAL(days => :days)")
                .paramSource(new MapSqlParameterSource("days", retentionDays))
                .query(String.class).list();

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

        int deleted = jdbcClient.sql(
                "DELETE FROM " + AttachmentProjection.Write.TABLE + " WHERE url IN (:urls)")
                .paramSource(new MapSqlParameterSource("urls", toDelete))
                .update();
        log.info("Deleted {} attachments", deleted);
        if (!failedUrls.isEmpty()) {
            log.warn("{} attachments skipped — S3 deletion failed, will retry on next run", failedUrls.size());
        }
    }

    private void deleteAdvertisements() {
        int deleted = jdbcClient.sql(
                "DELETE FROM " + AdvertisementProjection.Write.TABLE +
                " WHERE deleted_at < NOW() - MAKE_INTERVAL(days => :days)")
                .paramSource(new MapSqlParameterSource("days", retentionDays))
                .update();
        log.info("Deleted {} advertisements", deleted);
    }

    private void pruneSnapshots() {
        int pruned = jdbcClient.sql(
                "DELETE FROM " + AuditLogProjection.Write.TABLE +
                " WHERE created_at < NOW() - MAKE_INTERVAL(days => :days)")
                .paramSource(new MapSqlParameterSource("days", retentionDays))
                .update();
        log.info("Pruned {} audit log entries", pruned);
    }
}
