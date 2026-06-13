package org.ost.advertisement.config;

import liquibase.integration.spring.SpringLiquibase;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.core.ComponentFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.DependsOn;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

import javax.sql.DataSource;

@AutoConfiguration(afterName = "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ComponentScan({"org.ost.advertisement.spi", "org.ost.advertisement.services", "org.ost.advertisement.repository"})
@EnableJdbcRepositories(basePackages = "org.ost.advertisement.repository")
public class AdvertisementAutoConfiguration {

    @Bean("advertisementLiquibase")
    @ConditionalOnMissingBean(name = "advertisementLiquibase")
    @DependsOn("userLiquibase")
    public SpringLiquibase advertisementLiquibase(DataSource dataSource) {
        SpringLiquibase liq = new SpringLiquibase();
        liq.setDataSource(dataSource);
        liq.setChangeLog("classpath:db/advertisement-changelog/advertisement-changelog-master.xml");
        return liq;
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<AdvertisementPort> advertisementPortFactory(ObjectProvider<AdvertisementPort> p) {
        return new ComponentFactory<>(p);
    }
}
