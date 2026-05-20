package org.ost.marketplace.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.core.config.CleanupProperties;
import org.ost.marketplace.repository.advertisement.AdvertisementRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CleanupJob {

    private final AdvertisementRepository advertisementRepository;
    private final CleanupProperties       cleanupProperties;

    @Scheduled(cron = "0 0 2 * * *", zone = "Europe/Kyiv")
    public void run() {
        log.info("Cleanup job started, retention = {} days", cleanupProperties.retentionDays());
        advertisementRepository.deleteOlderThan(cleanupProperties.retentionDays());
        log.info("Cleanup job finished");
    }
}
