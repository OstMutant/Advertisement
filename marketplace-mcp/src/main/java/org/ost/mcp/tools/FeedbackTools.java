package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.mcp.rest.RestApiClient;
import org.ost.mcp.rest.RestPath;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** Read-only MCP tools over the provider profile feedback endpoints of the REST API. */
@Component
@RequiredArgsConstructor
public class FeedbackTools {

    private final RestApiClient restApi;

    @McpTool(name = "listProviderProfileFeedback",
            description = "Lists the reviews of one provider profile, newest first, one page at a time. "
                    + "The list is public, no API key is needed. Page size is not a parameter: it comes from the caller's saved "
                    + "settings. A hidden review carries no rating and no text. The result has items, totalCount, page, returned, "
                    + "hasMore and nextPage; when hasMore is true call again with page = nextPage to continue.",
            annotations = @McpTool.McpAnnotations(title = "List provider profile reviews", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult listProviderProfileFeedback(
            @McpToolParam(description = "Provider profile id, as listed by listProviderProfiles") @NonNull Long id,
            @McpToolParam(required = false, description = "Zero-based page number, default 0") Integer page) {
        int pageNumber = page == null ? 0 : page;
        String path = RestPath.of("/api/provider-profiles/{id}/feedback", id).param("page", pageNumber).build();
        return ToolResults.paged(restApi.get(path), pageNumber);
    }

    @McpTool(name = "getProviderProfileFeedbackAggregate",
            description = "Returns the average rating and the number of visible reviews of one provider profile. "
                    + "Public, no API key needed.",
            annotations = @McpTool.McpAnnotations(title = "Get provider profile review summary", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getProviderProfileFeedbackAggregate(
            @McpToolParam(description = "Provider profile id, as listed by listProviderProfiles") @NonNull Long id) {
        return ToolResults.object(restApi.get(RestPath.of("/api/provider-profiles/{id}/feedback/aggregate", id).build()));
    }
}
