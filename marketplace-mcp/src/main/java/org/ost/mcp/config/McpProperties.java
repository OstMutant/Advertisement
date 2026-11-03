package org.ost.mcp.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Binds the marketplace.mcp.* prefix: the REST API base URL and the browser origins allowed to call /mcp. */
@ConfigurationProperties(prefix = "marketplace.mcp")
public record McpProperties(
        @DefaultValue("http://localhost:8080") String restBaseUrl,
        @DefaultValue({"http://localhost:*", "http://127.0.0.1:*"}) List<String> allowedOrigins
) {}
