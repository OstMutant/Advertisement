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

import java.util.Map;

/** Wraps the own-account endpoints (/api/users/me, settings, locale) of UserApiController. */
@Component
@RequiredArgsConstructor
public class AccountTools {

    private final RestApiClient restApi;

    @McpTool(name = "getMe",
            description = "Returns the account the connected API key belongs to, including its id, name, role and email. "
                    + "Use the id to tell which advertisements and profiles are the caller's own (ownerUserId in advertisement list items, actorId in provider profile list items). "
                    + "Needs the API key the agent is connected with; answers an error without it. "
                    + "The result has item and etag.",
            annotations = @McpTool.McpAnnotations(title = "Get the connected account", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getMe() {
        return ToolResults.single("item", restApi.get("/api/users/me"));
    }

    @McpTool(name = "getMySettings",
            description = "Returns the caller's saved page-size settings, which decide the page size of every list tool. "
                    + "Needs the API key; answers an error without it. The result has settings and etag.",
            annotations = @McpTool.McpAnnotations(title = "Get my settings", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getMySettings() {
        return ToolResults.single("settings", restApi.get("/api/users/me/settings"));
    }

    @McpTool(name = "updateMySettings",
            description = "Changes the caller's page-size settings, sent as a JSON merge patch that names only the fields to change: "
                    + "adsPageSize, usersPageSize, timelinePageSize, providerProfilesPageSize, feedbackPageSize and moderationPageSize, "
                    + "each an integer within the allowed range. A field left out keeps its value; null or an unknown field is refused with HTTP 400. "
                    + "Needs the API key. etag is the version returned by getMySettings, sent as If-Match; a stale etag is refused with HTTP 412. "
                    + "The result has status and item; no etag is returned, so read the version with getMySettings before the next change.",
            annotations = @McpTool.McpAnnotations(title = "Update my settings", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult updateMySettings(
            @McpToolParam(description = "Version from getMySettings, sent as If-Match, quotes included") @NonNull String etag,
            @McpToolParam(description = "JSON merge patch: only the page sizes to change, for example {\"adsPageSize\": 20}") @NonNull Map<String, Object> patch) {
        return ToolResults.written(restApi.send(HttpMethod.PATCH, RestPath.of("/api/users/me/settings").build(),
                JsonBody.of(patch), JsonBody.MERGE_PATCH, etag));
    }

    @McpTool(name = "changeMyLocale",
            description = "Changes the language of the caller's own account to en or uk; any other value is refused with HTTP 400. "
                    + "Needs the API key; answers an error without it. The result has status; the answer carries no item.",
            annotations = @McpTool.McpAnnotations(title = "Change my language", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult changeMyLocale(
            @McpToolParam(description = "Language of the account: en or uk") @NonNull String locale) {
        return ToolResults.written(restApi.send(HttpMethod.PUT, RestPath.of("/api/users/me/locale").build(),
                JsonBody.of("locale", locale), JsonBody.JSON, null));
    }

    @McpTool(name = "getMySettingsHistory",
            description = "Lists the earlier recorded states of the caller's page-size settings, newest first. "
                    + "Needs the API key; answers an error without it. The result has items and totalCount; each item has a snapshotId "
                    + "that getMySettingsHistorySnapshot takes.",
            annotations = @McpTool.McpAnnotations(title = "List my settings history", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getMySettingsHistory() {
        return ToolResults.list(restApi.get("/api/users/me/settings/history"));
    }

    @McpTool(name = "getMySettingsHistorySnapshot",
            description = "Returns one recorded state of the caller's page-size settings from their history, with its version. "
                    + "Needs the API key. Answers HTTP 403 when the snapshot is not visible to the caller and HTTP 404 when it is unknown. "
                    + "The result has snapshot, the recorded state, and version.",
            annotations = @McpTool.McpAnnotations(title = "Get a settings history snapshot", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getMySettingsHistorySnapshot(
            @McpToolParam(description = "Snapshot id, as listed by getMySettingsHistory") @NonNull Long snapshotId) {
        return ToolResults.object(restApi.get(RestPath.of("/api/users/me/settings/history/{snapshotId}", snapshotId).build()));
    }

    @McpTool(name = "restoreMySettingsFromSnapshot",
            description = "Returns the caller's page-size settings to a recorded state from their history, with the same validation as updateMySettings. "
                    + "Needs the API key. etag is the version returned by getMySettings, sent as If-Match; a stale etag is refused with HTTP 412. "
                    + "The result has status and item of the restored settings; no etag is returned, so read the version with getMySettings before the next change.",
            annotations = @McpTool.McpAnnotations(title = "Restore my settings from a snapshot", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult restoreMySettingsFromSnapshot(
            @McpToolParam(description = "Snapshot id, as listed by getMySettingsHistory") @NonNull Long snapshotId,
            @McpToolParam(description = "Version from getMySettings, sent as If-Match, quotes included") @NonNull String etag) {
        return ToolResults.written(restApi.send(HttpMethod.POST,
                RestPath.of("/api/users/me/settings/history/{snapshotId}/restore", snapshotId).build(), null, null, etag));
    }
}
