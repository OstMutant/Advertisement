package org.ost.audit.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.platform.core.config.CleanupProperties;
import org.springframework.stereotype.Service;

/**
 * Scheduled job, triggered by {@link org.ost.audit.config.AuditAutoConfiguration}'s cron trigger,
 * that deletes {@code audit_log} rows older than the configured retention window.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditCleanupService {

    private final AuditLogRepository auditLogRepository;
    private final CleanupProperties  cleanupProperties;

    public void cleanup() {
        log.info("Audit cleanup started: retentionDays={}", cleanupProperties.retentionDays());
        int deleted = auditLogRepository.deleteOlderThan(cleanupProperties.retentionDays());
        log.info("Audit cleanup finished: deletedRows={}", deleted);
    }
}
