package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.RequiredArgsConstructor;
import org.ost.mcp.rest.RestApiClient;
import org.ost.mcp.rest.RestPath;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/** Wraps AuditApiController, the audit timeline; readable by moderators and administrators only. */
@Component
@RequiredArgsConstructor
public class AuditTools {

    private final RestApiClient restApi;

    @McpTool(name = "getAuditTimeline",
            description = "Lists the audit timeline: every recorded create, update, delete and restore across the marketplace, newest first unless sort is given. "
                    + "Only administrators and moderators may call it, with their API key; other callers are refused. "
                    + "Filter by actorIds, entityTypes, actionTypes and a fromDate and toDate window, and page with page; page size comes from the caller's saved timeline settings. "
                    + ToolDescriptions.TOTAL_COUNT_NOTE + "The result has items, totalCount, page, returned, hasMore and nextPage; when hasMore is true call again with page = nextPage.",
            annotations = @McpTool.McpAnnotations(title = "Read the audit timeline", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getAuditTimeline(
            @McpToolParam(required = false, description = "Zero-based page number, default 0") Integer page,
            @McpToolParam(required = false, description = "Only changes made by these user ids") List<Long> actorIds,
            @McpToolParam(required = false, description = "Only these entity types: ADVERTISEMENT, USER, USER_SETTINGS, TAXON, PROVIDER_PROFILE, FEEDBACK, FEEDBACK_COMMENT") List<String> entityTypes,
            @McpToolParam(required = false, description = "Only these action types: CREATED, UPDATED, DELETED, RESTORED") List<String> actionTypes,
            @McpToolParam(required = false, description = "Only changes made at or after this ISO-8601 date-time") String fromDate,
            @McpToolParam(required = false, description = "Only changes made at or before this ISO-8601 date-time") String toDate,
            @McpToolParam(required = false, description = "Sort as createdAt,asc or createdAt,desc; default is newest first") String sort) {
        int pageNumber = page == null ? 0 : page;
        String path = RestPath.of("/api/audit/timeline")
                .param("page", pageNumber)
                .param("actorIds", actorIds)
                .param("entityTypes", entityTypes)
                .param("actionTypes", actionTypes)
                .param("fromDate", fromDate)
                .param("toDate", toDate)
                .param("sort", sort)
                .build();
        return ToolResults.paged(restApi.get(path), pageNumber);
    }
}
