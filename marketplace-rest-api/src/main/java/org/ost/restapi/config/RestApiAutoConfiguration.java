package org.ost.restapi.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/** Entry point that wires this module's controllers/config/beans into the Spring context. */
@AutoConfiguration
@ComponentScan("org.ost.restapi")
@EnableConfigurationProperties(ApiWriteLimitProperties.class)
public class RestApiAutoConfiguration {
}
