package org.ost.audit.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import liquibase.integration.spring.SpringLiquibase;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.audit.api.ConditionalOnAuditEnabled;
import org.ost.platform.core.spi.CurrentActorHook;
import org.ost.audit.model.AuditDiffEngine;
import org.ost.audit.model.AuditSnapshotMapper;
import org.ost.audit.repository.AuditLogRepository;
import org.ost.audit.services.ActivityService;
import org.ost.audit.services.AuditHistoryService;
import org.ost.audit.services.AuditQueryService;
import org.ost.audit.services.DefaultAuditPort;
import org.ost.platform.attachment.spi.AttachmentAuditHook;
import org.ost.platform.audit.spi.AuditDomainHook;
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
            ObjectProvider<CurrentActorHook> currentActorHook,
            AuditQueryService auditQueryService,
            AuditHistoryService auditHistoryService) {
        return new DefaultAuditPort(diffEngine, snapshotMapper,
                                    auditLogRepository, currentActorHook, auditQueryService, auditHistoryService);
    }

    @Bean
    @ConditionalOnMissingBean(AuditHistoryService.class)
    AuditHistoryService auditHistoryService(
            AuditLogRepository auditLogRepository,
            AuditSnapshotMapper snapshotMapper,
            ObjectProvider<AttachmentAuditHook> attachmentAuditHook,
            ObjectProvider<AuditDomainHook> auditDomainHook) {
        return new AuditHistoryService(auditLogRepository, snapshotMapper,
                                       attachmentAuditHook, auditDomainHook);
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
            ObjectProvider<AttachmentAuditHook> attachmentAuditHook,
            ObjectProvider<AuditDomainHook> auditDomainHook) {
        return new ActivityService(auditLogRepository, attachmentAuditHook, auditDomainHook);
    }
}
