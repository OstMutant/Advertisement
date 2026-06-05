package org.ost.marketplace.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.marketplace.services.AdvertisementService;
import org.ost.platform.core.config.CleanupProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.support.CronTrigger;

import java.util.TimeZone;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class SchedulingConfig {

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
}
