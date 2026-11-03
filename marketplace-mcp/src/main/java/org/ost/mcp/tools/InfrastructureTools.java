package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.RequiredArgsConstructor;
import org.ost.mcp.rest.RestApiClient;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/** Wraps HealthController and SitemapController of the org.ost.restapi root package, public and without a bearer key. */
@Component
@RequiredArgsConstructor
public class InfrastructureTools {

    private final RestApiClient restApi;

    @McpTool(name = "getHealth",
            description = "Liveness check: answers ok when the application is up. Public, no API key needed. "
                    + "The result has status and body.",
            annotations = @McpTool.McpAnnotations(title = "Check that the application is up", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getHealth() {
        return ToolResults.text(restApi.getText("/health"));
    }

    @McpTool(name = "getSitemap",
            description = "Returns the sitemap XML that lists every active advertisement and provider profile URL, cached for 15 minutes. "
                    + "Public, no API key needed. The result has status and the XML text in body.",
            annotations = @McpTool.McpAnnotations(title = "Read the sitemap", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getSitemap() {
        return ToolResults.text(restApi.getText("/sitemap.xml"));
    }
}
