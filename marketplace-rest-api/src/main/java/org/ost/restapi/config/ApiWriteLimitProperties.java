package org.ost.restapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Binds the {@code marketplace.rest-api.write-limit.*} prefix: how many write requests one account may send per window. */
@ConfigurationProperties(prefix = "marketplace.rest-api.write-limit")
public record ApiWriteLimitProperties(
        @DefaultValue("60") int maxRequests,
        @DefaultValue("1m") Duration window
) {}
