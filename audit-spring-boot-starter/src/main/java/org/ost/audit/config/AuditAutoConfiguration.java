package org.ost.audit.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import liquibase.integration.spring.SpringLiquibase;
import org.ost.platform.audit.spi.AuditDomainHook;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.config.CleanupProperties;
import org.ost.platform.core.spi.CurrentActorHook;
import org.ost.audit.services.AuditCleanupService;
import org.ost.audit.services.DefaultAuditPort;
import org.ost.audit.repository.AuditLogRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.ost.audit.AuditPackageMarker;
import org.ost.audit.ui.AuditActivityListRenderer;
import org.ost.audit.ui.AuditActivityPanel;
import org.ost.audit.ui.AuditActivityRowRenderer;
import org.ost.audit.ui.AuditHistoryListRenderer;
import org.ost.audit.ui.AuditHistoryRowRenderer;
import org.ost.audit.ui.AuditSnapshotBinder;
import org.ost.audit.ui.AuditTimelinePanel;
import org.ost.platform.audit.spi.AuditUiPort;
import org.ost.platform.core.ComponentFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.support.CronTrigger;

import javax.sql.DataSource;
import java.util.TimeZone;

@AutoConfiguration(afterName = "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ComponentScan(basePackageClasses = AuditPackageMarker.class)
@EnableJdbcRepositories(basePackages = "org.ost.audit.repository")
public class AuditAutoConfiguration {

    @Bean("auditObjectMapper")
    @ConditionalOnMissingBean(name = "auditObjectMapper")
    ObjectMapper auditObjectMapper() {
        return new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Bean
    SchedulingConfigurer auditCleanupScheduler(AuditCleanupService cleanupService,
                                               CleanupProperties cleanupProperties) {
        return registrar -> registrar.addTriggerTask(
                cleanupService::cleanup,
                new CronTrigger(cleanupProperties.cronExpression(),
                                TimeZone.getTimeZone(cleanupProperties.timezone())));
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
    DefaultAuditPort defaultAuditPort(AuditLogRepository auditLogRepository,
                                      CurrentActorHook currentActorHook,
                                      AuditDomainHook auditDomainHook) {
        return new DefaultAuditPort(auditLogRepository, currentActorHook, auditDomainHook);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AuditPort> auditPortFactory(ObjectProvider<AuditPort> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AuditUiPort> auditUiPortFactory(ObjectProvider<AuditUiPort> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AuditActivityPanel> auditActivityPanelFactory(ObjectProvider<AuditActivityPanel> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AuditTimelinePanel> auditTimelinePanelFactory(ObjectProvider<AuditTimelinePanel> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    @SuppressWarnings("rawtypes")
    public ComponentFactory<AuditSnapshotBinder> auditSnapshotBinderFactory(ObjectProvider<AuditSnapshotBinder> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AuditActivityListRenderer> auditActivityListRendererFactory(ObjectProvider<AuditActivityListRenderer> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AuditHistoryListRenderer> auditHistoryListRendererFactory(ObjectProvider<AuditHistoryListRenderer> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AuditHistoryRowRenderer> auditHistoryRowRendererFactory(ObjectProvider<AuditHistoryRowRenderer> p) {
        return new ComponentFactory<>(p);
    }

    @Bean @ConditionalOnMissingBean
    public ComponentFactory<AuditActivityRowRenderer> auditActivityRowRendererFactory(ObjectProvider<AuditActivityRowRenderer> p) {
        return new ComponentFactory<>(p);
    }

}
