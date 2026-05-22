package org.ost.attachment.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import liquibase.integration.spring.SpringLiquibase;
import org.ost.attachment.service.AttachmentService;
import org.ost.attachment.service.AttachmentSnapshotService;
import org.ost.attachment.service.DefaultAttachmentPort;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.attachment.storage.ConditionalOnAttachmentEnabled;
import org.ost.platform.core.config.CleanupProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

import javax.sql.DataSource;

@AutoConfiguration(afterName = "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ConditionalOnAttachmentEnabled
@ComponentScan("org.ost.attachment")
@EnableJdbcRepositories(basePackages = "org.ost.attachment.repository")
@EnableConfigurationProperties(CleanupProperties.class)
public class AttachmentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CleanupProperties cleanupProperties() {
        return new CleanupProperties(90);
    }

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
    @ConditionalOnMissingBean(AttachmentPort.class)
    DefaultAttachmentPort defaultAttachmentPort(
            AttachmentService attachmentService,
            AttachmentSnapshotService attachmentSnapshotService) {
        return new DefaultAttachmentPort(attachmentService, attachmentSnapshotService);
    }

}
