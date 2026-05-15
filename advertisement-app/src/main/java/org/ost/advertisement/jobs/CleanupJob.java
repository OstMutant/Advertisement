package org.ost.advertisement.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.config.CleanupProperties;
import org.ost.advertisement.repository.advertisement.AdvertisementRepositoryCustom;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupJob {

    private final AdvertisementRepositoryCustom advertisementRepository;
    private final CleanupProperties             cleanupProperties;

    @Scheduled(cron = "0 0 2 * * *", zone = "Europe/Kyiv")
    public void run() {
        log.info("Cleanup job started, retention = {} days", cleanupProperties.retentionDays());
        int deleted = advertisementRepository.deleteOlderThan(cleanupProperties.retentionDays());
        log.info("Deleted {} advertisements, cleanup job finished", deleted);
    }
}
