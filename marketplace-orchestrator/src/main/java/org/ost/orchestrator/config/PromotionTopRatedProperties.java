package org.ost.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Binds the {@code app.promotion.top-rated} prefix: the stored average and the number of visible reviews a provider profile needs to be promoted as top rated. */
@ConfigurationProperties(prefix = "app.promotion.top-rated")
public record PromotionTopRatedProperties(
        @DefaultValue("4.8") double minAverage,
        @DefaultValue("5")   int    minReviews
) {}
