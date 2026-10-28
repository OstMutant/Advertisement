package org.ost.contact.config;

import liquibase.integration.spring.SpringLiquibase;
import org.ost.platform.contact.spi.ContactPort;
import org.ost.platform.core.ComponentFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

import javax.sql.DataSource;

/** Auto-configures the Contact domain -- its own Liquibase migration plus the {@code ContactPort} component factory bean. */
@AutoConfiguration(afterName = "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ComponentScan({"org.ost.contact.spi", "org.ost.contact.services", "org.ost.contact.repository"})
@EnableJdbcRepositories(basePackages = "org.ost.contact.repository")
public class ContactAutoConfiguration {

    @Bean("contactLiquibase")
    @ConditionalOnMissingBean(name = "contactLiquibase")
    public SpringLiquibase contactLiquibase(DataSource dataSource) {
        SpringLiquibase liq = new SpringLiquibase();
        liq.setDataSource(dataSource);
        liq.setChangeLog("classpath:db/contact-changelog/contact-changelog-master.xml");
        return liq;
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<ContactPort> contactPortFactory(ObjectProvider<ContactPort> p) {
        return new ComponentFactory<>(p);
    }
}
