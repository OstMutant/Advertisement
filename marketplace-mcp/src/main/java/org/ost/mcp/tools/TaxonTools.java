package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.mcp.rest.RestApiClient;
import org.ost.mcp.rest.RestPath;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** Read-only MCP tools over the taxon (category and city) endpoints of the REST API. */
@Component
@RequiredArgsConstructor
public class TaxonTools {

    private final RestApiClient restApi;

    @McpTool(name = "listTaxons",
            description = "Lists all categories and cities, or only one type. The list is public, no API key is needed. "
                    + "The ids are what the category and city filters of listAdvertisements and listProviderProfiles take. "
                    + "The result has items and totalCount.",
            annotations = @McpTool.McpAnnotations(title = "List categories and cities", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult listTaxons(
            @McpToolParam(required = false, description = "CATEGORY or CITY; omit for both") String type,
            @McpToolParam(required = false, description = "Language tag for the names, for example en or uk") String locale) {
        String path = RestPath.of("/api/taxons").param("type", type).param("locale", locale).build();
        return ToolResults.list(restApi.get(path));
    }

    @McpTool(name = "getTaxon",
            description = "Returns one category or city by id. Falls back to English names when the locale has none. "
                    + "The result has item and etag. Public, no API key needed. Answers an error when the id is unknown.",
            annotations = @McpTool.McpAnnotations(title = "Get a category or city", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getTaxon(
            @McpToolParam(description = "Taxon id, as listed by listTaxons") @NonNull Long id,
            @McpToolParam(required = false, description = "Language tag for the names, for example en or uk") String locale) {
        String path = RestPath.of("/api/taxons/{id}", id).param("locale", locale).build();
        return ToolResults.single("item", restApi.get(path));
    }
}
