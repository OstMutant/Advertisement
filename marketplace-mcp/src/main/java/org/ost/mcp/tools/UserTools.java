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
import java.util.List;
import java.util.Map;

/** Wraps UserApiController: public registration, the user list for administrators and moderators, account changes by the account's owner or an administrator. */
@Component
@RequiredArgsConstructor
public class UserTools {

    private static final String USER = "/api/users/{id}";

    private final RestApiClient restApi;

    @McpTool(name = "registerUser",
            description = "Registers a new user account with a name, email and password. No API key is needed. "
                    + "Registration is rate limited per client address to 5 failed attempts per 15 minutes; the first account ever "
                    + "registered becomes the administrator. Afterwards issue a bearer API key with issueApiKey, using Basic authentication "
                    + "with the email and password. The result has status and item of the new user; no location is returned.",
            annotations = @McpTool.McpAnnotations(title = "Register a user", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public CallToolResult registerUser(
            @McpToolParam(description = "Display name of the new user") @NonNull String name,
            @McpToolParam(description = "Email address, unique across accounts") @NonNull String email,
            @McpToolParam(description = "Password of the new account") @NonNull String password) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("email", email);
        body.put("password", password);
        return ToolResults.written(restApi.send(HttpMethod.POST, RestPath.of("/api/users").build(),
                JsonBody.of(body), JsonBody.JSON, null));
    }

    @McpTool(name = "listUsers",
            description = "Lists user accounts, one page at a time, optionally filtered and sorted. "
                    + "Only administrators and moderators may call it, with their API key; other callers are refused. "
                    + "Page size is not a parameter: it comes from the caller's saved settings. "
                    + "The result has items, totalCount, page, returned, hasMore and nextPage; when hasMore is true call again with page = nextPage.",
            annotations = @McpTool.McpAnnotations(title = "List users", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult listUsers(
            @McpToolParam(required = false, description = "Zero-based page number, default 0") Integer page,
            @McpToolParam(required = false, description = "Text the user name must contain") String name,
            @McpToolParam(required = false, description = "Text the email address must contain") String email,
            @McpToolParam(required = false, description = "Only these roles: USER, ADMIN, MODERATOR") List<String> roles,
            @McpToolParam(required = false, description = "Only accounts created at or after this ISO-8601 date-time") String createdAtStart,
            @McpToolParam(required = false, description = "Only accounts created at or before this ISO-8601 date-time") String createdAtEnd,
            @McpToolParam(required = false, description = "Only accounts updated at or after this ISO-8601 date-time") String updatedAtStart,
            @McpToolParam(required = false, description = "Only accounts updated at or before this ISO-8601 date-time") String updatedAtEnd,
            @McpToolParam(required = false, description = "Only accounts with an id at or after this value") Long startId,
            @McpToolParam(required = false, description = "Only accounts with an id at or before this value") Long endId,
            @McpToolParam(required = false, description = "Sort as field,direction with field id, name, email, role, createdAt or updatedAt and direction asc or desc, for example name,asc") String sort) {
        int pageNumber = page == null ? 0 : page;
        String path = RestPath.of("/api/users")
                .param("page", pageNumber)
                .param("name", name)
                .param("email", email)
                .param("roles", roles)
                .param("createdAtStart", createdAtStart)
                .param("createdAtEnd", createdAtEnd)
                .param("updatedAtStart", updatedAtStart)
                .param("updatedAtEnd", updatedAtEnd)
                .param("startId", startId)
                .param("endId", endId)
                .param("sort", sort)
                .build();
        return ToolResults.paged(restApi.get(path), pageNumber);
    }

    @McpTool(name = "getUser",
            description = "Returns one user account by id. Allowed to administrators and moderators, and to the user themselves, with the caller's API key. "
                    + "The result has item and etag; the etag is the version updateUser and restoreUserFromSnapshot must send as If-Match. "
                    + "Answers an error when the id is unknown.",
            annotations = @McpTool.McpAnnotations(title = "Get a user", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getUser(
            @McpToolParam(description = "User id, as listed by listUsers") @NonNull Long id) {
        return ToolResults.single("item", restApi.get(RestPath.of(USER, id).build()));
    }

    @McpTool(name = "updateUser",
            description = "Replaces the name and role of one user account. A user may change their own name; only an administrator may change a role, "
                    + "and never their own. Needs the caller's API key. "
                    + "etag is the version returned by getUser, sent as If-Match; a stale etag is refused with HTTP 412. "
                    + "The result has status, item and etag of the updated user.",
            annotations = @McpTool.McpAnnotations(title = "Update a user", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult updateUser(
            @McpToolParam(description = "User id, as listed by listUsers") @NonNull Long id,
            @McpToolParam(description = "Version from getUser, sent as If-Match, quotes included") @NonNull String etag,
            @McpToolParam(description = "New display name") @NonNull String name,
            @McpToolParam(description = "New role: USER, ADMIN or MODERATOR") @NonNull String role) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("role", role);
        return ToolResults.written(restApi.send(HttpMethod.PUT, RestPath.of(USER, id).build(),
                JsonBody.of(body), JsonBody.JSON, etag));
    }

    @McpTool(name = "deleteUser",
            description = "Deletes one user account together with its advertisements, provider profile and API keys. "
                    + "Allowed to the account's owner or an administrator, with the caller's API key; an administrator cannot delete their own account. "
                    + "The result has status (204); no etag is returned.",
            annotations = @McpTool.McpAnnotations(title = "Delete a user", readOnlyHint = false,
                    destructiveHint = true, idempotentHint = true, openWorldHint = false))
    public CallToolResult deleteUser(
            @McpToolParam(description = "User id, as listed by listUsers") @NonNull Long id) {
        return ToolResults.written(restApi.send(HttpMethod.DELETE, RestPath.of(USER, id).build(), null, null, null));
    }

    @McpTool(name = "getUserHistory",
            description = "Lists the change history of one user account, newest first. Needs the caller's API key; "
                    + "administrators and moderators see every entry, other callers only the entries they authored. "
                    + "Answers an error when the id is unknown. The result has items and totalCount; each item has a snapshotId "
                    + "that getUserHistorySnapshot takes.",
            annotations = @McpTool.McpAnnotations(title = "List user history", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getUserHistory(
            @McpToolParam(description = "User id, as listed by listUsers") @NonNull Long id) {
        return ToolResults.list(restApi.get(RestPath.of("/api/users/{id}/history", id).build()));
    }

    @McpTool(name = "getUserHistorySnapshot",
            description = "Returns one recorded state of a user account from its history, with its version. Needs the caller's API key. "
                    + "Answers HTTP 403 when the snapshot is not visible to the caller and HTTP 404 when it is unknown. "
                    + "The result has snapshot, the recorded state, and version.",
            annotations = @McpTool.McpAnnotations(title = "Get a user history snapshot", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getUserHistorySnapshot(
            @McpToolParam(description = "User id, as listed by listUsers") @NonNull Long id,
            @McpToolParam(description = "Snapshot id, as listed by getUserHistory") @NonNull Long snapshotId) {
        return ToolResults.object(restApi.get(RestPath.of("/api/users/{id}/history/{snapshotId}", id, snapshotId).build()));
    }

    @McpTool(name = "restoreUserFromSnapshot",
            description = "Restores the name and role of a user account to a recorded state from its history, identified by id and snapshotId. "
                    + "Same permissions and validation as updateUser; nothing else is restored. Needs the caller's API key. "
                    + "etag is the version returned by getUser, sent as If-Match; a stale etag is refused with HTTP 412. "
                    + "The result has status, item and etag of the restored user.",
            annotations = @McpTool.McpAnnotations(title = "Restore a user from a snapshot", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult restoreUserFromSnapshot(
            @McpToolParam(description = "User id, as listed by listUsers") @NonNull Long id,
            @McpToolParam(description = "Snapshot id, as listed by getUserHistory") @NonNull Long snapshotId,
            @McpToolParam(description = "Version from getUser, sent as If-Match, quotes included") @NonNull String etag) {
        return ToolResults.written(restApi.send(HttpMethod.POST,
                RestPath.of("/api/users/{id}/history/{snapshotId}/restore", id, snapshotId).build(), null, null, etag));
    }
}
