package org.ost.provider.config;

import liquibase.integration.spring.SpringLiquibase;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.providerprofile.spi.ProviderProfilePort;
import org.ost.platform.taxon.spi.TaxonPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

import javax.sql.DataSource;

@AutoConfiguration(afterName = "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ComponentScan({"org.ost.provider.spi", "org.ost.provider.services", "org.ost.provider.repository"})
@EnableJdbcRepositories(basePackages = "org.ost.provider.repository")
public class ProviderProfileAutoConfiguration {

    @Bean("providerProfileLiquibase")
    @ConditionalOnMissingBean(name = "providerProfileLiquibase")
    @DependsOn("userLiquibase")
    public SpringLiquibase providerProfileLiquibase(DataSource dataSource) {
        SpringLiquibase liq = new SpringLiquibase();
        liq.setDataSource(dataSource);
        liq.setChangeLog("classpath:db/provider-profile-changelog/provider-profile-changelog-master.xml");
        return liq;
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<ProviderProfilePort> providerProfilePortFactory(ObjectProvider<ProviderProfilePort> p) {
        return new ComponentFactory<>(p);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<TaxonPort> taxonPortFactory(ObjectProvider<TaxonPort> p) {
        return new ComponentFactory<>(p);
    }
}
