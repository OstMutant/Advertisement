package org.ost.apikey.config;

import liquibase.integration.spring.SpringLiquibase;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.apikey.spi.ApiKeyPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

import javax.sql.DataSource;

/** Auto-configures the API-key credential domain -- its own Liquibase migration plus the {@code ApiKeyPort} component factory bean. */
@AutoConfiguration(afterName = "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ComponentScan({"org.ost.apikey.spi", "org.ost.apikey.services", "org.ost.apikey.repository", "org.ost.apikey.security"})
@EnableJdbcRepositories(basePackages = "org.ost.apikey.repository")
public class ApiKeyAutoConfiguration {

    @Bean("apikeyLiquibase")
    @ConditionalOnMissingBean(name = "apikeyLiquibase")
    public SpringLiquibase apikeyLiquibase(DataSource dataSource) {
        SpringLiquibase liq = new SpringLiquibase();
        liq.setDataSource(dataSource);
        liq.setChangeLog("classpath:db/apikey-changelog/apikey-changelog-master.xml");
        return liq;
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<ApiKeyPort> apiKeyPortFactory(ObjectProvider<ApiKeyPort> p) {
        return new ComponentFactory<>(p);
    }
}
