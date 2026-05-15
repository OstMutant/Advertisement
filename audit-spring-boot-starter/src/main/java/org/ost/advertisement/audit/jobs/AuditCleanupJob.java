package org.ost.advertisement.audit.jobs;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.audit.ConditionalOnAuditEnabled;
import org.ost.advertisement.audit.repository.AuditLogRepository;
import org.ost.advertisement.config.CleanupProperties;
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
        int pruned = auditLogRepository.deleteOlderThan(cleanupProperties.retentionDays());
        log.info("Pruned {} audit log entries", pruned);
    }
}
