package org.ost.orchestrator.config;

import lombok.extern.slf4j.Slf4j;
import org.ost.orchestrator.services.UserCleanupService;
import org.ost.platform.advertisement.spi.AdvertisementPort;
import org.ost.platform.apikey.spi.ApiKeyPort;
import org.ost.platform.attachment.spi.AttachmentAuditPort;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.contact.spi.ContactPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.config.CleanupProperties;
import org.ost.platform.feedback.spi.FeedbackPort;
import org.ost.platform.promotion.spi.PromotionPlacementPort;
import org.ost.platform.providerprofile.spi.ProviderProfilePort;
import org.ost.platform.taxon.spi.TaxonPort;
import org.ost.platform.user.spi.UserPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.support.CronTrigger;

import java.util.TimeZone;

/** Registers every orchestrator service by scanning its own package, declares the factory of every port it consumes so it starts when a starter is absent, binds the cleanup and promotion settings and schedules the retention purge of deleted accounts. */
@Slf4j
@AutoConfiguration
@ComponentScan("org.ost.orchestrator")
@EnableConfigurationProperties({CleanupProperties.class, PromotionTopRatedProperties.class})
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

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<AdvertisementPort> advertisementPortFactory(ObjectProvider<AdvertisementPort> p) {
        return new ComponentFactory<>(p);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<ApiKeyPort> apiKeyPortFactory(ObjectProvider<ApiKeyPort> p) {
        return new ComponentFactory<>(p);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<AttachmentAuditPort> attachmentAuditPortFactory(ObjectProvider<AttachmentAuditPort> p) {
        return new ComponentFactory<>(p);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<AttachmentPort> attachmentPortFactory(ObjectProvider<AttachmentPort> p) {
        return new ComponentFactory<>(p);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<AuditPort> auditPortFactory(ObjectProvider<AuditPort> p) {
        return new ComponentFactory<>(p);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<ContactPort> contactPortFactory(ObjectProvider<ContactPort> p) {
        return new ComponentFactory<>(p);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<FeedbackPort> feedbackPortFactory(ObjectProvider<FeedbackPort> p) {
        return new ComponentFactory<>(p);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<PromotionPlacementPort> promotionPlacementPortFactory(ObjectProvider<PromotionPlacementPort> p) {
        return new ComponentFactory<>(p);
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

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory<UserPort> userPortFactory(ObjectProvider<UserPort> p) {
        return new ComponentFactory<>(p);
    }
}
