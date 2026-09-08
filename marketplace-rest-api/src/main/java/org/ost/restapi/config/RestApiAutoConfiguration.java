package org.ost.restapi.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/** Entry point that wires this module's controllers/config/beans into the Spring context. */
@AutoConfiguration
@ComponentScan("org.ost.restapi")
public class RestApiAutoConfiguration {
}
