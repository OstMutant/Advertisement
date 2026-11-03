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

/** Wraps ContactApiController: contact channels, revealing a channel, and monthly view counts. */
@Component
@RequiredArgsConstructor
public class ContactTools {

    private final RestApiClient restApi;

    @McpTool(name = "getAdvertisementContact",
            description = "Tells which contact channels (PHONE, TELEGRAM, VIBER) an advertisement has set, without revealing their values. "
                    + "The list is public, no API key is needed. The id is the advertisement id. "
                    + "Use revealAdvertisementContact to get the value of one channel.",
            annotations = @McpTool.McpAnnotations(title = "Get advertisement contact channels", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getAdvertisementContact(
            @McpToolParam(description = "Advertisement id, as listed by listAdvertisements") @NonNull Long id) {
        return ToolResults.object(restApi.get(RestPath.of("/api/advertisements/{id}/contact", id).build()));
    }

    @McpTool(name = "getProviderProfileContact",
            description = "Tells which contact channels (PHONE, TELEGRAM, VIBER) a provider profile has set, without revealing their values. "
                    + "The list is public, no API key is needed. The id is the provider profile id. "
                    + "Use revealProviderProfileContact to get the value of one channel.",
            annotations = @McpTool.McpAnnotations(title = "Get provider profile contact channels", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getProviderProfileContact(
            @McpToolParam(description = "Provider profile id, as listed by listProviderProfiles") @NonNull Long id) {
        return ToolResults.object(restApi.get(RestPath.of("/api/provider-profiles/{id}/contact", id).build()));
    }

    @McpTool(name = "revealAdvertisementContact",
            description = "Reveals the value of one contact channel (PHONE, TELEGRAM or VIBER) of an advertisement. "
                    + "Public, no API key is needed, but reveals are rate limited and every reveal is recorded and counted. "
                    + "Answers HTTP 429 with a Retry-After when the limit is reached. The result has status and item, where item holds the revealed value.",
            annotations = @McpTool.McpAnnotations(title = "Reveal advertisement contact", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public CallToolResult revealAdvertisementContact(
            @McpToolParam(description = "Advertisement id, as listed by listAdvertisements") @NonNull Long id,
            @McpToolParam(description = "PHONE, TELEGRAM or VIBER") @NonNull String channel) {
        return ToolResults.written(restApi.send(HttpMethod.POST, RestPath.of("/api/advertisements/{id}/contact/reveals", id).build(),
                reveal(channel), JsonBody.JSON, null));
    }

    @McpTool(name = "revealProviderProfileContact",
            description = "Reveals the value of one contact channel (PHONE, TELEGRAM or VIBER) of a provider profile. "
                    + "Public, no API key is needed, but reveals are rate limited and every reveal is recorded and counted. "
                    + "Answers HTTP 429 with a Retry-After when the limit is reached. The result has status and item, where item holds the revealed value.",
            annotations = @McpTool.McpAnnotations(title = "Reveal provider profile contact", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public CallToolResult revealProviderProfileContact(
            @McpToolParam(description = "Provider profile id, as listed by listProviderProfiles") @NonNull Long id,
            @McpToolParam(description = "PHONE, TELEGRAM or VIBER") @NonNull String channel) {
        return ToolResults.written(restApi.send(HttpMethod.POST, RestPath.of("/api/provider-profiles/{id}/contact/reveals", id).build(),
                reveal(channel), JsonBody.JSON, null));
    }

    @McpTool(name = "getProviderProfileContactViews",
            description = "Returns how many times each contact channel of one provider profile was revealed this month, "
                    + "with one count per channel that was revealed. Needs the caller's API key; allowed to the profile's owner, "
                    + "administrators and moderators, and refused with HTTP 403 for other callers. The result is a list with one object per channel revealed this month, each with channel and count.",
            annotations = @McpTool.McpAnnotations(title = "Get provider profile contact views", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getProviderProfileContactViews(
            @McpToolParam(description = "Provider profile id, as listed by listProviderProfiles") @NonNull Long id) {
        return ToolResults.object(restApi.get(RestPath.of("/api/provider-profiles/{id}/contact/views", id).build()));
    }

    private static String reveal(String channel) {
        return JsonBody.of("channel", channel);
    }
}
