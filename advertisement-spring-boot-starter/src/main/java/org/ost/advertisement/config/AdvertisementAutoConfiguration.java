package org.ost.advertisement.config;

import liquibase.integration.spring.SpringLiquibase;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.config.CleanupProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.support.CronTrigger;

import javax.sql.DataSource;
import java.util.TimeZone;

@Slf4j
@AutoConfiguration(afterName = "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration")
@ConditionalOnClass(DataSource.class)
@ComponentScan({"org.ost.advertisement.spi", "org.ost.advertisement.services", "org.ost.advertisement.repository"})
@EnableJdbcRepositories(basePackages = "org.ost.advertisement.repository")
@EnableConfigurationProperties(CleanupProperties.class)
@EnableScheduling
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
    SchedulingConfigurer advertisementCleanupScheduler(AdvertisementService advertisementService,
                                                       CleanupProperties cleanupProperties) {
        return registrar -> registrar.addTriggerTask(
                () -> {
                    log.info("Advertisement cleanup started, retention = {} days", cleanupProperties.retentionDays());
                    advertisementService.cleanup(cleanupProperties.retentionDays());
                    log.info("Advertisement cleanup finished");
                },
                new CronTrigger(cleanupProperties.cronExpression(),
                                TimeZone.getTimeZone(cleanupProperties.timezone())));
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<AdvertisementPort> advertisementPortFactory(ObjectProvider<AdvertisementPort> p) {
        return new ComponentFactory<>(p);
    }
}
