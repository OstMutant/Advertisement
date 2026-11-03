package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.mcp.rest.JsonBody;
import org.ost.mcp.rest.RestApiClient;
import org.ost.mcp.rest.RestPath;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Wraps FeedbackApiController: reviews, replies and flags. */
@Component
@RequiredArgsConstructor
public class FeedbackTools {

    private static final String REPLY = "/api/feedback/{feedbackId}/reply";

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

    @McpTool(name = "createProviderProfileFeedback",
            description = "Leaves a review on one provider profile. Needs the caller's API key. "
                    + "rating is an integer from 1 to 5 and text is required and must not be blank. "
                    + "The result has status and item of the new review; the version is item.version.",
            annotations = @McpTool.McpAnnotations(title = "Review a provider profile", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public CallToolResult createProviderProfileFeedback(
            @McpToolParam(description = "Provider profile id, as listed by listProviderProfiles") @NonNull Long id,
            @McpToolParam(description = "Rating from 1 to 5") @NonNull Integer rating,
            @McpToolParam(description = "Review text, must not be blank") @NonNull String text) {
        return ToolResults.written(restApi.send(HttpMethod.POST, RestPath.of("/api/provider-profiles/{id}/feedback", id).build(),
                reviewBody(rating, text), JsonBody.JSON, null));
    }

    @McpTool(name = "updateFeedback",
            description = "Changes the rating and text of one review: an integer rating from 1 to 5 and a non-blank text. "
                    + "Only the review's author may edit it, only within the edit window, with their API key; the REST API refuses later edits. "
                    + "etag is the version of the review from listProviderProfileFeedback, sent as If-Match; a stale version is refused with HTTP 412. "
                    + "The result has status and item of the updated review; the new version is item.version.",
            annotations = @McpTool.McpAnnotations(title = "Update a review", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult updateFeedback(
            @McpToolParam(description = "Review id, as listed by listProviderProfileFeedback") @NonNull Long feedbackId,
            @McpToolParam(description = "The version field of the review in listProviderProfileFeedback, sent as If-Match") @NonNull String etag,
            @McpToolParam(description = "Rating from 1 to 5") @NonNull Integer rating,
            @McpToolParam(description = "Review text, must not be blank") @NonNull String text) {
        return ToolResults.written(restApi.send(HttpMethod.PUT, RestPath.of("/api/feedback/{feedbackId}", feedbackId).build(),
                reviewBody(rating, text), JsonBody.JSON, etag));
    }

    @McpTool(name = "createFeedbackReply",
            description = "Posts the provider's reply to one review. Only the owner of the reviewed provider profile may call it, "
                    + "with their API key. text is required and must not be blank. "
                    + "The result has status and item of the new reply; the version is item.version.",
            annotations = @McpTool.McpAnnotations(title = "Reply to a review", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public CallToolResult createFeedbackReply(
            @McpToolParam(description = "Review id, as listed by listProviderProfileFeedback") @NonNull Long feedbackId,
            @McpToolParam(description = "Reply text, must not be blank") @NonNull String text) {
        return ToolResults.written(restApi.send(HttpMethod.POST, RestPath.of(REPLY, feedbackId).build(),
                JsonBody.of("text", text), JsonBody.JSON, null));
    }

    @McpTool(name = "updateFeedbackReply",
            description = "Changes the text of the provider's reply to one review; text must not be blank. "
                    + "Only the reply's author may edit it, only within the edit window, with their API key; the REST API refuses later edits. "
                    + "etag is the version of the reply from listProviderProfileFeedback, sent as If-Match; a stale version is refused with HTTP 412. "
                    + "The result has status and item of the updated reply; the new version is item.version.",
            annotations = @McpTool.McpAnnotations(title = "Update a review reply", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult updateFeedbackReply(
            @McpToolParam(description = "Review id, as listed by listProviderProfileFeedback") @NonNull Long feedbackId,
            @McpToolParam(description = "The version field of the reply inside the review in listProviderProfileFeedback, sent as If-Match") @NonNull String etag,
            @McpToolParam(description = "Reply text, must not be blank") @NonNull String text) {
        return ToolResults.written(restApi.send(HttpMethod.PUT, RestPath.of(REPLY, feedbackId).build(),
                JsonBody.of("text", text), JsonBody.JSON, etag));
    }

    @McpTool(name = "deleteFeedbackReply",
            description = "Deletes the provider's reply to one review. Only the reply's author may call it, with their API key. "
                    + "No version check is made, so no etag is needed. "
                    + "The result has status.",
            annotations = @McpTool.McpAnnotations(title = "Delete a review reply", readOnlyHint = false,
                    destructiveHint = true, idempotentHint = true, openWorldHint = false))
    public CallToolResult deleteFeedbackReply(
            @McpToolParam(description = "Review id, as listed by listProviderProfileFeedback") @NonNull Long feedbackId) {
        return ToolResults.written(restApi.send(HttpMethod.DELETE, RestPath.of(REPLY, feedbackId).build(),
                null, null, null));
    }

    @McpTool(name = "flagFeedback",
            description = "Flags one review for moderation. Any signed-in caller may flag, with their API key. "
                    + "reason says why and is required, up to 2000 characters. A flagged review is hidden until a moderator decides. "
                    + "The result has status.",
            annotations = @McpTool.McpAnnotations(title = "Flag a review", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public CallToolResult flagFeedback(
            @McpToolParam(description = "Review id, as listed by listProviderProfileFeedback") @NonNull Long feedbackId,
            @McpToolParam(description = "Why the review is flagged, required, up to 2000 characters") @NonNull String reason) {
        return ToolResults.written(restApi.send(HttpMethod.POST, RestPath.of("/api/feedback/{feedbackId}/flags", feedbackId).build(),
                JsonBody.of("reason", reason), JsonBody.JSON, null));
    }

    @McpTool(name = "flagFeedbackReply",
            description = "Flags the provider's reply to one review for moderation. Any signed-in caller may flag, with their API key. "
                    + "reason says why and is required, up to 2000 characters. A flagged reply is hidden until a moderator decides. "
                    + "The result has status.",
            annotations = @McpTool.McpAnnotations(title = "Flag a review reply", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public CallToolResult flagFeedbackReply(
            @McpToolParam(description = "Review id, as listed by listProviderProfileFeedback") @NonNull Long feedbackId,
            @McpToolParam(description = "Why the reply is flagged, required, up to 2000 characters") @NonNull String reason) {
        return ToolResults.written(restApi.send(HttpMethod.POST, RestPath.of("/api/feedback/{feedbackId}/reply/flags", feedbackId).build(),
                JsonBody.of("reason", reason), JsonBody.JSON, null));
    }

    private static String reviewBody(Integer rating, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rating", rating);
        body.put("text", text);
        return JsonBody.of(body);
    }
}
