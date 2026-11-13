package org.ost.integrationtests.support;

import org.ost.orchestrator.config.PromotionTopRatedProperties;
import org.ost.orchestrator.services.PromotionCatalogService;
import org.ost.orchestrator.services.PromotionRefreshService;
import org.ost.orchestrator.services.TopRatedPromotionRule;
import org.ost.promotion.config.PromotionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/** Wires the promotion starter and the orchestrator beans {@code FeedbackAccessService} depends on, for contexts that list orchestrator beans one by one instead of loading {@code OrchestratorAutoConfiguration}. */
@TestConfiguration
@EnableConfigurationProperties(PromotionTopRatedProperties.class)
@Import({PromotionAutoConfiguration.class, PromotionRefreshService.class, TopRatedPromotionRule.class, PromotionCatalogService.class})
public class PromotionTestSupport {
}
