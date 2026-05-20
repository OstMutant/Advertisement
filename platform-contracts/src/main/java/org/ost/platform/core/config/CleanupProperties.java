package org.ost.platform.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.cleanup")
public record CleanupProperties(
        @DefaultValue("90") int retentionDays
) {}
