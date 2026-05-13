package org.ost.advertisement.audit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import liquibase.integration.spring.SpringLiquibase;
import org.ost.advertisement.audit.AuditPort;
import org.ost.advertisement.audit.AuditUserProvider;
import org.ost.advertisement.audit.ConditionalOnAuditEnabled;
import org.ost.advertisement.audit.model.AuditDiffEngine;
import org.ost.advertisement.audit.model.AuditSnapshotMapper;
import org.ost.advertisement.audit.repository.ActivityRepository;
import org.ost.advertisement.audit.repository.AuditLogRepository;
import org.ost.advertisement.audit.repository.AuditReadRepository;
import org.ost.advertisement.audit.services.ActivityService;
import org.ost.advertisement.audit.services.AuditHistoryService;
import org.ost.advertisement.audit.services.AuditQueryService;
import org.ost.advertisement.audit.services.DefaultAuditPort;
import org.ost.advertisement.audit.services.NoOpAuditPort;
import org.ost.advertisement.events.spi.AdvertisementHistoryExtension;
import org.ost.advertisement.events.spi.UserActivityExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;

@AutoConfiguration(afterName = "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ComponentScan("org.ost.advertisement.audit")
public class AuditAutoConfiguration {

    @Bean("auditLiquibase")
    @ConditionalOnMissingBean(name = "auditLiquibase")
    public SpringLiquibase auditLiquibase(DataSource dataSource) {
        SpringLiquibase liq = new SpringLiquibase();
        liq.setDataSource(dataSource);
        liq.setChangeLog("classpath:db/audit-changelog/audit-changelog-master.xml");
        return liq;
    }

    @Bean
    @ConditionalOnMissingBean(AuditLogRepository.class)
    AuditLogRepository auditLogRepository(JdbcClient jdbcClient) {
        return new AuditLogRepository(jdbcClient);
    }

    @Bean
    @ConditionalOnAuditEnabled
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

    @Bean
    @ConditionalOnMissingBean(AuditReadRepository.class)
    AuditReadRepository auditReadRepository(
            JdbcClient jdbcClient,
            @Qualifier("userSettingsObjectMapper") ObjectMapper objectMapper) {
        return new AuditReadRepository(jdbcClient, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(ActivityRepository.class)
    ActivityRepository activityRepository(
            JdbcClient jdbcClient,
            @Qualifier("userSettingsObjectMapper") ObjectMapper objectMapper) {
        return new ActivityRepository(jdbcClient, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(AuditHistoryService.class)
    AuditHistoryService auditHistoryService(
            AuditReadRepository auditReadRepository,
            AuditSnapshotMapper snapshotMapper,
            ObjectProvider<AdvertisementHistoryExtension> historyExtension) {
        return new AuditHistoryService(auditReadRepository, snapshotMapper, historyExtension);
    }

    @Bean
    @ConditionalOnMissingBean(AuditQueryService.class)
    AuditQueryService auditQueryService(
            AuditReadRepository auditReadRepository,
            AuditSnapshotMapper snapshotMapper) {
        return new AuditQueryService(auditReadRepository, snapshotMapper);
    }

    @Bean
    @ConditionalOnMissingBean(ActivityService.class)
    ActivityService activityService(
            ActivityRepository activityRepository,
            ObjectProvider<UserActivityExtension> activityExtension) {
        return new ActivityService(activityRepository, activityExtension);
    }
}
