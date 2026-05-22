package org.ost.audit.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import liquibase.integration.spring.SpringLiquibase;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.audit.api.ConditionalOnAuditEnabled;
import org.ost.platform.core.spi.CurrentActorProvider;
import org.ost.audit.model.AuditDiffEngine;
import org.ost.audit.model.AuditSnapshotMapper;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.audit.services.ActivityService;
import org.ost.audit.services.AuditHistoryService;
import org.ost.audit.services.AuditQueryService;
import org.ost.audit.services.DefaultAuditPort;
import org.ost.platform.audit.spi.MediaHistoryExtension;
import org.ost.platform.audit.spi.AuditActorNameResolver;
import org.ost.platform.audit.spi.AuditEntityExistenceChecker;
import org.ost.platform.audit.spi.ActivityFeedExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

import javax.sql.DataSource;

@AutoConfiguration(afterName = "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ConditionalOnAuditEnabled
@ComponentScan("org.ost.audit")
@EnableJdbcRepositories(basePackages = "org.ost.audit.repository")
public class AuditAutoConfiguration {

    @Bean("auditObjectMapper")
    @ConditionalOnMissingBean(name = "auditObjectMapper")
    ObjectMapper auditObjectMapper() {
        return new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Bean("auditLiquibase")
    @ConditionalOnMissingBean(name = "auditLiquibase")
    public SpringLiquibase auditLiquibase(DataSource dataSource) {
        SpringLiquibase liq = new SpringLiquibase();
        liq.setDataSource(dataSource);
        liq.setChangeLog("classpath:db/audit-changelog/audit-changelog-master.xml");
        return liq;
    }

    @Bean
    @ConditionalOnMissingBean(AuditPort.class)
    DefaultAuditPort defaultAuditPort(
            AuditDiffEngine diffEngine,
            AuditSnapshotMapper snapshotMapper,
            AuditLogRepository auditLogRepository,
            ObjectProvider<CurrentActorProvider> currentActorProvider,
            AuditQueryService auditQueryService,
            AuditHistoryService auditHistoryService) {
        return new DefaultAuditPort(diffEngine, snapshotMapper,
                                    auditLogRepository, currentActorProvider, auditQueryService, auditHistoryService);
    }

    @Bean
    @ConditionalOnMissingBean(AuditHistoryService.class)
    AuditHistoryService auditHistoryService(
            AuditLogRepository auditLogRepository,
            AuditSnapshotMapper snapshotMapper,
            ObjectProvider<MediaHistoryExtension> historyExtension,
            ObjectProvider<AuditActorNameResolver> actorNameResolver) {
        return new AuditHistoryService(auditLogRepository, snapshotMapper,
                                       historyExtension, actorNameResolver);
    }

    @Bean
    @ConditionalOnMissingBean(AuditQueryService.class)
    AuditQueryService auditQueryService(AuditLogRepository auditLogRepository) {
        return new AuditQueryService(auditLogRepository);
    }

    @Bean
    @ConditionalOnMissingBean(ActivityService.class)
    ActivityService activityService(
            AuditLogRepository auditLogRepository,
            ObjectProvider<ActivityFeedExtension> activityExtension,
            ObjectProvider<AuditActorNameResolver> actorNameResolver,
            ObjectProvider<AuditEntityExistenceChecker> existenceChecker) {
        return new ActivityService(auditLogRepository, activityExtension,
                                   actorNameResolver, existenceChecker);
    }
}
