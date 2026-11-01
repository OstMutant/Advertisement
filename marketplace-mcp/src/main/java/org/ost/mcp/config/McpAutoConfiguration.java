package org.ost.mcp.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

/** Entry point that wires this module's MCP beans into the Spring context. */
@AutoConfiguration
@ComponentScan("org.ost.mcp")
@EnableConfigurationProperties(McpProperties.class)
public class McpAutoConfiguration {
}
