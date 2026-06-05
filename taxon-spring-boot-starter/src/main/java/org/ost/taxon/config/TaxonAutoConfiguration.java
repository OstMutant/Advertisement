package org.ost.taxon.config;

import liquibase.integration.spring.SpringLiquibase;
import org.ost.taxon.TaxonPackageMarker;
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
@ComponentScan(basePackageClasses = TaxonPackageMarker.class)
@EnableJdbcRepositories(basePackages = "org.ost.taxon.repository")
@EnableConfigurationProperties(TaxonProperties.class)
public class TaxonAutoConfiguration {

    @Bean("taxonLiquibase")
    @ConditionalOnMissingBean(name = "taxonLiquibase")
    public SpringLiquibase taxonLiquibase(DataSource dataSource) {
        SpringLiquibase liq = new SpringLiquibase();
        liq.setDataSource(dataSource);
        liq.setChangeLog("classpath:db/taxon-changelog/master.xml");
        return liq;
    }
}
