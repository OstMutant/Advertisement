package org.ost.attachment.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import liquibase.integration.spring.SpringLiquibase;
import org.ost.attachment.AttachmentPackageMarker;
import org.ost.attachment.services.AttachmentCleanupService;
import org.ost.platform.core.config.CleanupProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.support.CronTrigger;

import javax.sql.DataSource;
import java.util.TimeZone;

@AutoConfiguration(afterName = "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ComponentScan(basePackageClasses = AttachmentPackageMarker.class)
@EnableJdbcRepositories(basePackages = "org.ost.attachment.repository")
@EnableConfigurationProperties(CleanupProperties.class)
public class AttachmentAutoConfiguration {

    @Bean("attachmentObjectMapper")
    @ConditionalOnMissingBean(name = "attachmentObjectMapper")
    ObjectMapper attachmentObjectMapper() {
        return new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Bean("attachmentLiquibase")
    @ConditionalOnMissingBean(name = "attachmentLiquibase")
    public SpringLiquibase attachmentLiquibase(DataSource dataSource) {
        SpringLiquibase liq = new SpringLiquibase();
        liq.setDataSource(dataSource);
        liq.setChangeLog("classpath:db/attachment-changelog/db.attachment-changelog-master.xml");
        return liq;
    }

    @Bean
    SchedulingConfigurer attachmentCleanupScheduler(AttachmentCleanupService cleanupService,
                                                    CleanupProperties cleanupProperties) {
        return registrar -> registrar.addTriggerTask(
                cleanupService::cleanup,
                new CronTrigger(cleanupProperties.cronExpression(),
                                TimeZone.getTimeZone(cleanupProperties.timezone())));
    }

}
