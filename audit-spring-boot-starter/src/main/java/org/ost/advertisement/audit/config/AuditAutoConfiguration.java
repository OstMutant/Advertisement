package org.ost.advertisement.audit.config;

import org.ost.advertisement.audit.AuditPort;
import org.ost.advertisement.audit.AuditUserProvider;
import org.ost.advertisement.audit.model.AuditDiffEngine;
import org.ost.advertisement.audit.model.AuditSnapshotMapper;
import org.ost.advertisement.audit.repository.AuditLogRepository;
import org.ost.advertisement.audit.services.DefaultAuditPort;
import org.ost.advertisement.audit.services.NoOpAuditPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration(afterName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
public class AuditAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnMissingBean(AuditPort.class)
    DefaultAuditPort defaultAuditPort(
            AuditDiffEngine diffEngine,
            AuditSnapshotMapper snapshotMapper,
            AuditLogRepository auditLogRepository,
            ObjectProvider<AuditUserProvider> auditUserProvider) {
        return new DefaultAuditPort(diffEngine, snapshotMapper,
                                    auditLogRepository, auditUserProvider);
    }

    @Bean
    @ConditionalOnMissingBean(AuditPort.class)
    AuditPort noOpAuditPort() {
        return new NoOpAuditPort();
    }
}
