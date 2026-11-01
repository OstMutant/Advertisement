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

/** Read-only MCP tools over the advertisement endpoints of the REST API. */
@Component
@RequiredArgsConstructor
public class AdvertisementTools {

    private final RestApiClient restApi;

    @McpTool(name = "listAdvertisements",
            description = "Lists marketplace advertisements, one page at a time, optionally filtered and sorted. "
                    + "The list is public, no API key is needed. Page size is not a parameter: it comes from the caller's saved "
                    + "settings. The result has items, totalCount, page, returned, hasMore and nextPage; when hasMore is true "
                    + "call again with page = nextPage to continue.",
            annotations = @McpTool.McpAnnotations(title = "List advertisements", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult listAdvertisements(
            @McpToolParam(required = false, description = "Zero-based page number, default 0") Integer page,
            @McpToolParam(required = false, description = "Text the advertisement title must contain") String title,
            @McpToolParam(required = false, description = "Only advertisements in any of these category ids (from listTaxons with type CATEGORY)") List<Long> categoryIds,
            @McpToolParam(required = false, description = "Only advertisements in this city taxon id (from listTaxons with type CITY)") Long cityTaxonId,
            @McpToolParam(required = false, description = "Only these kinds: OFFER, REQUEST, PRODUCT") List<String> adKinds,
            @McpToolParam(required = false, description = "Sort as field,direction with field title, createdAt or updatedAt and direction asc or desc, for example updatedAt,desc") String sort,
            @McpToolParam(required = false, description = "Language tag for category and city names, for example en or uk") String locale) {
        int pageNumber = page == null ? 0 : page;
        String path = RestPath.of("/api/advertisements")
                .param("page", pageNumber)
                .param("title", title)
                .param("categoryIds", categoryIds)
                .param("cityTaxonId", cityTaxonId)
                .param("adKinds", adKinds)
                .param("sort", sort)
                .param("locale", locale)
                .build();
        return ToolResults.paged(restApi.get(path), pageNumber);
    }

    @McpTool(name = "getAdvertisement",
            description = "Returns one advertisement by id with its category and city names, author and media summary. "
                    + "The result has item and etag; the etag is the version a later update or delete must send as If-Match. "
                    + "Public, no API key needed. Answers an error when the id is unknown.",
            annotations = @McpTool.McpAnnotations(title = "Get an advertisement", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getAdvertisement(
            @McpToolParam(description = "Advertisement id, as listed by listAdvertisements") @NonNull Long id,
            @McpToolParam(required = false, description = "Language tag for category and city names, for example en or uk") String locale) {
        String path = RestPath.of("/api/advertisements/{id}", id).param("locale", locale).build();
        return ToolResults.single("item", restApi.get(path));
    }

    @McpTool(name = "listAdvertisementMedia",
            description = "Lists the photos and videos in one advertisement's gallery, in display order. "
                    + "The result has items and totalCount. Public, no API key needed. Answers an error when the id is unknown.",
            annotations = @McpTool.McpAnnotations(title = "List advertisement media", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult listAdvertisementMedia(
            @McpToolParam(description = "Advertisement id, as listed by listAdvertisements") @NonNull Long id) {
        return ToolResults.list(restApi.get(RestPath.of("/api/advertisements/{id}/media", id).build()));
    }
}
