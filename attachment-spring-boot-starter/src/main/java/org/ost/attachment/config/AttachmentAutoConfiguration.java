package org.ost.attachment.config;

import liquibase.integration.spring.SpringLiquibase;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import javax.sql.DataSource;

@AutoConfiguration(afterName = "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ComponentScan("org.ost.attachment")
public class AttachmentAutoConfiguration {

    @Bean("attachmentLiquibase")
    @ConditionalOnMissingBean(name = "attachmentLiquibase")
    public SpringLiquibase attachmentLiquibase(DataSource dataSource) {
        SpringLiquibase liq = new SpringLiquibase();
        liq.setDataSource(dataSource);
        liq.setChangeLog("classpath:db/attachment-changelog/db.attachment-changelog-master.xml");
        return liq;
    }
}
