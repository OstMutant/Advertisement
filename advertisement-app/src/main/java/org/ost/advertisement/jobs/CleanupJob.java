package org.ost.advertisement.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.storage.api.StorageService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupJob {

    private final NamedParameterJdbcTemplate     jdbc;
    private final ObjectProvider<StorageService> storageService;

    @Value("${app.cleanup.retention-days:90}")
    private int retentionDays;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void run() {
        log.info("Cleanup job started, retention = {} days", retentionDays);

        deleteAttachments();
        deleteAdvertisements();
        pruneSnapshots();

        log.info("Cleanup job finished");
    }

    private void deleteAttachments() {
        List<String> urls = jdbc.queryForList(
                "SELECT url FROM attachment WHERE deleted_at < NOW() - MAKE_INTERVAL(days => :days)",
                new MapSqlParameterSource("days", retentionDays),
                String.class
        );

        storageService.ifAvailable(s -> urls.forEach(url -> {
            try { s.delete(url); } catch (Exception e) {
                log.warn("Failed to delete S3 object {}: {}", url, e.getMessage());
            }
        }));

        int deleted = jdbc.update(
                "DELETE FROM attachment WHERE deleted_at < NOW() - MAKE_INTERVAL(days => :days)",
                new MapSqlParameterSource("days", retentionDays)
        );
        log.info("Deleted {} attachments", deleted);
    }

    private void deleteAdvertisements() {
        int deleted = jdbc.update(
                "DELETE FROM advertisement WHERE deleted_at < NOW() - MAKE_INTERVAL(days => :days)",
                new MapSqlParameterSource("days", retentionDays)
        );
        log.info("Deleted {} advertisements", deleted);
    }

    private void pruneSnapshots() {
        int advSnaps = jdbc.update(
                "DELETE FROM advertisement_snapshot WHERE created_at < NOW() - MAKE_INTERVAL(days => :days)",
                new MapSqlParameterSource("days", retentionDays)
        );
        int userSnaps = jdbc.update(
                "DELETE FROM user_snapshot WHERE created_at < NOW() - MAKE_INTERVAL(days => :days)",
                new MapSqlParameterSource("days", retentionDays)
        );
        log.info("Pruned {} advertisement snapshots, {} user snapshots", advSnaps, userSnaps);
    }
}
