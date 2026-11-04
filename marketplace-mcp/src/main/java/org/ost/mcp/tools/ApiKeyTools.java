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
import java.util.Optional;

/** Wraps ApiKeyController; issueApiKey authenticates with Basic, every other tool with a bearer key. */
@Component
@RequiredArgsConstructor
public class ApiKeyTools {

    private final RestApiClient restApi;

    @McpTool(name = "issueApiKey",
            description = "Issues a new API key for the signed-in account and returns the raw key once, in item.rawKey; it is never retrievable again, so store it at once. "
                    + "This operation takes HTTP Basic authentication (email and password), not a bearer key: it works only on a connection made with an Authorization: Basic header "
                    + "and answers HTTP 401 on a Bearer connection. The optional label names the key and may be up to 100 characters. "
                    + "The result has status and item (item.rawKey); no location is returned.",
            annotations = @McpTool.McpAnnotations(title = "Issue an API key", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public CallToolResult issueApiKey(
            @McpToolParam(required = false, description = "Name of the key, up to 100 characters; omit for none") String label) {
        Map<String, Object> body = new LinkedHashMap<>();
        Optional.ofNullable(label).ifPresent(value -> body.put("label", value));
        return ToolResults.written(restApi.send(HttpMethod.POST, RestPath.of("/api/api-keys").build(),
                JsonBody.of(body), JsonBody.JSON, null));
    }

    @McpTool(name = "listApiKeys",
            description = "Lists the caller's own API keys as summaries only: label, creation date and masked key, never the raw key. "
                    + "Needs the caller's API key. Revoked keys are hidden unless includeRevoked is true, and then each revoked key carries a non-null revokedAt. "
                    + "The result has items and totalCount; each item has the id that revokeApiKey takes.",
            annotations = @McpTool.McpAnnotations(title = "List API keys", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult listApiKeys(
            @McpToolParam(required = false, description = "true to include revoked keys; omit or false to hide them") Boolean includeRevoked) {
        String path = RestPath.of("/api/api-keys").param("includeRevoked", includeRevoked).build();
        return ToolResults.list(restApi.get(path));
    }

    @McpTool(name = "revokeApiKey",
            description = "Revokes one of the caller's own API keys, identified by its id from listApiKeys. "
                    + "Needs the caller's API key. Answers HTTP 404 when the key does not exist or is not the caller's, and HTTP 409 when it is already revoked. "
                    + "The result has status, which is 204 on success.",
            annotations = @McpTool.McpAnnotations(title = "Revoke an API key", readOnlyHint = false,
                    destructiveHint = true, idempotentHint = true, openWorldHint = false))
    public CallToolResult revokeApiKey(
            @McpToolParam(description = "API key id, as listed by listApiKeys") @NonNull Long id) {
        return ToolResults.written(restApi.send(HttpMethod.DELETE, RestPath.of("/api/api-keys/{id}", id).build(), null, null, null));
    }
}
