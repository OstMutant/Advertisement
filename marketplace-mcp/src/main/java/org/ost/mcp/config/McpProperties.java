package org.ost.mcp.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Binds the {@code marketplace.mcp.*} prefix: where the REST API this module wraps can be reached and which browser origins may call {@code /mcp}; {@code allowedOrigins} entries may use {@code http://host:*} for any port, and requests without an {@code Origin} header are not restricted. */
@ConfigurationProperties(prefix = "marketplace.mcp")
public record McpProperties(
        @DefaultValue("http://localhost:8080") String restBaseUrl,
        @DefaultValue({"http://localhost:*", "http://127.0.0.1:*"}) List<String> allowedOrigins
) {}
