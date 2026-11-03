package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.mcp.rest.RestApiClient;
import org.ost.mcp.rest.RestPath;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.List;

/** Wraps ModerationApiController: the queue and the approve and confirm decisions, which answer 204; administrators and moderators only. */
@Component
@RequiredArgsConstructor
public class ModerationTools {

    private final RestApiClient restApi;

    @McpTool(name = "getModerationQueue",
            description = "Lists flagged feedback and comments, one page at a time, optionally filtered and sorted. "
                    + "Only administrators and moderators may call it, with their API key; other callers are refused. "
                    + "An empty statuses list means only HIDDEN entries, the ones awaiting a decision; sort is field,direction "
                    + "with field createdAt or type and defaults to newest first. Page size comes from the caller's saved settings. "
                    + "The result has items, totalCount, page, returned, hasMore and nextPage; when hasMore is true call again with page = nextPage.",
            annotations = @McpTool.McpAnnotations(title = "List the moderation queue", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getModerationQueue(
            @McpToolParam(required = false, description = "Zero-based page number, default 0") Integer page,
            @McpToolParam(required = false, description = "Only these types: FEEDBACK, COMMENT") List<String> types,
            @McpToolParam(required = false, description = "Only these statuses: NEW, HIDDEN, CONFIRMED_HIDDEN; omit or leave empty for HIDDEN only") List<String> statuses,
            @McpToolParam(required = false, description = "Only entries created at or after this ISO-8601 date-time") String createdAtStart,
            @McpToolParam(required = false, description = "Only entries created at or before this ISO-8601 date-time") String createdAtEnd,
            @McpToolParam(required = false, description = "Text the author name must contain") String authorName,
            @McpToolParam(required = false, description = "Text the reason of the flag must contain") String reasonText,
            @McpToolParam(required = false, description = "Sort as field,direction with field createdAt or type and direction asc or desc, for example createdAt,desc") String sort) {
        int pageNumber = page == null ? 0 : page;
        String path = RestPath.of("/api/moderation/queue")
                .param("page", pageNumber)
                .param("types", types)
                .param("statuses", statuses)
                .param("createdAtStart", createdAtStart)
                .param("createdAtEnd", createdAtEnd)
                .param("authorName", authorName)
                .param("reasonText", reasonText)
                .param("sort", sort)
                .build();
        return ToolResults.paged(restApi.get(path), pageNumber);
    }

    @McpTool(name = "approveFeedback",
            description = "Restores a flagged (HIDDEN) or confirmed (CONFIRMED_HIDDEN) review to visible. "
                    + "Only administrators and moderators may call it, with their API key. "
                    + "id is the review id as listed by getModerationQueue; the call answers HTTP 409 when the review is not awaiting that decision. "
                    + "The result has status (204); no item, location or etag is returned.",
            annotations = @McpTool.McpAnnotations(title = "Approve a flagged review", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult approveFeedback(
            @McpToolParam(description = "Review id, as listed by getModerationQueue") @NonNull Long id) {
        return ToolResults.written(restApi.send(HttpMethod.POST, RestPath.of("/api/moderation/feedback/{id}/approve", id).build(), null, null, null));
    }

    @McpTool(name = "confirmFeedbackViolation",
            description = "Keeps a flagged (HIDDEN) review hidden for good. "
                    + "Only administrators and moderators may call it, with their API key. "
                    + "id is the review id as listed by getModerationQueue; the call answers HTTP 409 when the review is not flagged. "
                    + "The result has status (204); no item, location or etag is returned.",
            annotations = @McpTool.McpAnnotations(title = "Confirm a review violation", readOnlyHint = false,
                    destructiveHint = true, idempotentHint = true, openWorldHint = false))
    public CallToolResult confirmFeedbackViolation(
            @McpToolParam(description = "Review id, as listed by getModerationQueue") @NonNull Long id) {
        return ToolResults.written(restApi.send(HttpMethod.POST, RestPath.of("/api/moderation/feedback/{id}/confirm-violation", id).build(), null, null, null));
    }

    @McpTool(name = "approveFeedbackReply",
            description = "Restores a flagged (HIDDEN) or confirmed (CONFIRMED_HIDDEN) reply to visible. "
                    + "Only administrators and moderators may call it, with their API key. "
                    + "id is the reply's review id as listed by getModerationQueue; the call answers HTTP 409 when the reply is not awaiting that decision. "
                    + "The result has status (204); no item, location or etag is returned.",
            annotations = @McpTool.McpAnnotations(title = "Approve a flagged reply", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult approveFeedbackReply(
            @McpToolParam(description = "Review id of the reply, as listed by getModerationQueue") @NonNull Long id) {
        return ToolResults.written(restApi.send(HttpMethod.POST, RestPath.of("/api/moderation/replies/{id}/approve", id).build(), null, null, null));
    }

    @McpTool(name = "confirmFeedbackReplyViolation",
            description = "Keeps a flagged (HIDDEN) reply hidden for good. "
                    + "Only administrators and moderators may call it, with their API key. "
                    + "id is the reply's review id as listed by getModerationQueue; the call answers HTTP 409 when the reply is not flagged. "
                    + "The result has status (204); no item, location or etag is returned.",
            annotations = @McpTool.McpAnnotations(title = "Confirm a reply violation", readOnlyHint = false,
                    destructiveHint = true, idempotentHint = true, openWorldHint = false))
    public CallToolResult confirmFeedbackReplyViolation(
            @McpToolParam(description = "Review id of the reply, as listed by getModerationQueue") @NonNull Long id) {
        return ToolResults.written(restApi.send(HttpMethod.POST, RestPath.of("/api/moderation/replies/{id}/confirm-violation", id).build(), null, null, null));
    }
}
