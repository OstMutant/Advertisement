package org.ost.audit.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import liquibase.integration.spring.SpringLiquibase;
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
import org.ost.platform.ui.ComponentFactory;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Scope;
import org.springframework.core.ResolvableType;
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
                                      CurrentActorHook currentActorHook) {
        return new DefaultAuditPort(auditLogRepository, currentActorHook);
    }

    @Bean
    @Scope("prototype")
    @ConditionalOnMissingBean
    public ComponentFactory<?> componentFactory(InjectionPoint injectionPoint, ConfigurableListableBeanFactory beanFactory) {
        ResolvableType type = injectionPoint.getField() != null
                ? ResolvableType.forField(injectionPoint.getField())
                : ResolvableType.forMethodParameter(injectionPoint.getMethodParameter());
        Class<?> beanClass = type.getGeneric(0).toClass();
        return new ComponentFactory<>(beanFactory.getBeanProvider(ResolvableType.forClass(beanClass)));
    }

}
