package org.ost.mcp.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/** Auto-configuration that component-scans org.ost.mcp and binds McpProperties; registered through the AutoConfiguration.imports file. */
@AutoConfiguration
@ComponentScan("org.ost.mcp")
@EnableConfigurationProperties(McpProperties.class)
public class McpAutoConfiguration {
}
