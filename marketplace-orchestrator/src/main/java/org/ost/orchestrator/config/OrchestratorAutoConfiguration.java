package org.ost.orchestrator.config;

import lombok.extern.slf4j.Slf4j;
import org.ost.orchestrator.services.UserCleanupService;
import org.ost.platform.core.config.CleanupProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.support.CronTrigger;

import java.util.TimeZone;

@Slf4j
@AutoConfiguration
@ComponentScan("org.ost.orchestrator")
@EnableConfigurationProperties(CleanupProperties.class)
@EnableScheduling
public class OrchestratorAutoConfiguration {

    @Bean
    SchedulingConfigurer userCleanupScheduler(UserCleanupService userCleanupService, CleanupProperties cleanupProperties) {
        return registrar -> registrar.addTriggerTask(
                () -> {
                    log.info("User cleanup started, retention = {} days", cleanupProperties.retentionDays());
                    userCleanupService.cleanup(cleanupProperties.retentionDays());
                    log.info("User cleanup finished");
                },
                new CronTrigger(cleanupProperties.cronExpression(),
                                TimeZone.getTimeZone(cleanupProperties.timezone())));
    }
}
