package org.ost.advertisement.config;

import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.core.ComponentFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

import javax.sql.DataSource;

@AutoConfiguration
@ConditionalOnClass(DataSource.class)
@ComponentScan({"org.ost.advertisement.spi", "org.ost.advertisement.services", "org.ost.advertisement.repository"})
@EnableJdbcRepositories(basePackages = "org.ost.advertisement.repository")
public class AdvertisementAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<AdvertisementPort> advertisementPortFactory(ObjectProvider<AdvertisementPort> p) {
        return new ComponentFactory<>(p);
    }
}
