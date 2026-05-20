package org.ost.audit.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.audit.api.ConditionalOnAuditEnabled;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.platform.core.config.CleanupProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnAuditEnabled
@RequiredArgsConstructor
public class AuditCleanupJob {

    private final AuditLogRepository auditLogRepository;
    private final CleanupProperties  cleanupProperties;

    @Scheduled(cron = "0 0 2 * * *", zone = "Europe/Kyiv")
    public void run() {
        auditLogRepository.deleteOlderThan(cleanupProperties.retentionDays());
        log.info("Audit cleanup job finished");
    }
}
