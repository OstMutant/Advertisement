package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.RequiredArgsConstructor;
import org.ost.mcp.rest.RestApiClient;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/** Read-only MCP tools over the account endpoints of the REST API, answered for the connected API key. */
@Component
@RequiredArgsConstructor
public class AccountTools {

    private final RestApiClient restApi;

    @McpTool(name = "getMe",
            description = "Returns the account the connected API key belongs to, including its id, name, role and email. "
                    + "Use the id to tell which advertisements and profiles are the caller's own (ownerUserId in list items). "
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
}
