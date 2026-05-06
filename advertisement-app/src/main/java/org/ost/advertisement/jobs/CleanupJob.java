package org.ost.advertisement.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.config.CleanupProperties;
import org.ost.advertisement.repository.advertisement.AdvertisementRepositoryCustom;
import org.ost.advertisement.repository.audit.AuditLogRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupJob {

    private final AdvertisementRepositoryCustom advertisementRepository;
    private final AuditLogRepository            auditLogRepository;
    private final CleanupProperties             cleanupProperties;

    @Scheduled(cron = "0 0 2 * * *", zone = "Europe/Kyiv")
    @Transactional
    public void run() {
        log.info("Cleanup job started, retention = {} days", cleanupProperties.retentionDays());
        deleteAdvertisements();
        pruneSnapshots();
        log.info("Cleanup job finished");
    }

    private void deleteAdvertisements() {
        int deleted = advertisementRepository.deleteOlderThan(cleanupProperties.retentionDays());
        log.info("Deleted {} advertisements", deleted);
    }

    private void pruneSnapshots() {
        int pruned = auditLogRepository.deleteOlderThan(cleanupProperties.retentionDays());
        log.info("Pruned {} audit log entries", pruned);
    }
}
