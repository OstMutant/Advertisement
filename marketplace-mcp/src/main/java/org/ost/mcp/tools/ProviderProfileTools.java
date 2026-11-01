package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.mcp.rest.RestApiClient;
import org.ost.mcp.rest.RestPath;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/** Read-only MCP tools over the provider profile endpoints of the REST API. */
@Component
@RequiredArgsConstructor
public class ProviderProfileTools {

    private final RestApiClient restApi;

    @McpTool(name = "listProviderProfiles",
            description = "Lists provider profiles (kinds MASTER, SHOP, SUPPORT), one page at a time, optionally filtered and sorted. "
                    + "The list is public, no API key is needed. Page size is not a parameter: it comes from the caller's saved "
                    + "settings. The result has items, totalCount, page, returned, hasMore and nextPage; when hasMore is true "
                    + "call again with page = nextPage to continue.",
            annotations = @McpTool.McpAnnotations(title = "List provider profiles", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult listProviderProfiles(
            @McpToolParam(required = false, description = "Zero-based page number, default 0") Integer page,
            @McpToolParam(required = false, description = "Only these kinds: MASTER, SHOP, SUPPORT") List<String> kinds,
            @McpToolParam(required = false, description = "Only profiles in any of these category ids (from listTaxons with type CATEGORY)") List<Long> categoryIds,
            @McpToolParam(required = false, description = "Only profiles in this city taxon id (from listTaxons with type CITY)") Long cityTaxonId,
            @McpToolParam(required = false, description = "Sort as field,direction with field createdAt or updatedAt and direction asc or desc, for example updatedAt,desc") String sort,
            @McpToolParam(required = false, description = "Language tag for category and city names, for example en or uk") String locale) {
        int pageNumber = page == null ? 0 : page;
        String path = RestPath.of("/api/provider-profiles")
                .param("page", pageNumber)
                .param("kinds", kinds)
                .param("categoryIds", categoryIds)
                .param("cityTaxonId", cityTaxonId)
                .param("sort", sort)
                .param("locale", locale)
                .build();
        return ToolResults.paged(restApi.get(path), pageNumber);
    }

    @McpTool(name = "getProviderProfile",
            description = "Returns one provider profile by id with its category and city names. "
                    + "The result has item and etag; the etag is the version a later update or delete must send as If-Match. "
                    + "Public, no API key needed. Answers an error when the id is unknown.",
            annotations = @McpTool.McpAnnotations(title = "Get a provider profile", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getProviderProfile(
            @McpToolParam(description = "Provider profile id, as listed by listProviderProfiles") @NonNull Long id,
            @McpToolParam(required = false, description = "Language tag for category and city names, for example en or uk") String locale) {
        String path = RestPath.of("/api/provider-profiles/{id}", id).param("locale", locale).build();
        return ToolResults.single("item", restApi.get(path));
    }
}
