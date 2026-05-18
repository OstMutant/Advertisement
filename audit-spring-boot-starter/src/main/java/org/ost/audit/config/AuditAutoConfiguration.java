package org.ost.audit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import liquibase.integration.spring.SpringLiquibase;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.audit.api.ConditionalOnAuditEnabled;
import org.ost.platform.core.spi.CurrentUserProvider;
import org.ost.audit.model.AuditDiffEngine;
import org.ost.audit.model.AuditSnapshotMapper;
import org.ost.audit.repository.ActivityRepository;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.audit.repository.AuditReadRepository;
import org.ost.platform.core.spi.EntityDisplayNameResolver;
import org.ost.audit.services.ActivityService;
import org.ost.audit.services.AuditHistoryService;
import org.ost.audit.services.AuditQueryService;
import org.ost.audit.services.DefaultAuditPort;
import org.ost.audit.services.NoOpAuditPort;
import org.ost.platform.audit.spi.AdvertisementHistoryExtension;
import org.ost.platform.audit.spi.AuditActorNameResolver;
import org.ost.platform.audit.spi.AuditEntityExistenceChecker;
import org.ost.platform.audit.spi.UserActivityExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.List;

@AutoConfiguration(afterName = "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ComponentScan("org.ost.audit")
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
            ObjectProvider<CurrentUserProvider> auditUserProvider,
            AuditQueryService auditQueryService,
            AuditHistoryService auditHistoryService) {
        return new DefaultAuditPort(diffEngine, snapshotMapper,
                                    auditLogRepository, auditUserProvider, auditQueryService, auditHistoryService);
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
            @Qualifier("userSettingsObjectMapper") ObjectMapper objectMapper,
            List<EntityDisplayNameResolver> resolvers) {
        return new ActivityRepository(jdbcClient, objectMapper, resolvers);
    }

    @Bean
    @ConditionalOnMissingBean(AuditHistoryService.class)
    AuditHistoryService auditHistoryService(
            AuditReadRepository auditReadRepository,
            AuditSnapshotMapper snapshotMapper,
            ObjectProvider<AdvertisementHistoryExtension> historyExtension,
            ObjectProvider<AuditActorNameResolver> actorNameResolver) {
        return new AuditHistoryService(auditReadRepository, snapshotMapper,
                                       historyExtension, actorNameResolver);
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
            ObjectProvider<UserActivityExtension> activityExtension,
            ObjectProvider<AuditActorNameResolver> actorNameResolver,
            ObjectProvider<AuditEntityExistenceChecker> existenceChecker) {
        return new ActivityService(activityRepository, activityExtension,
                                   actorNameResolver, existenceChecker);
    }
}
