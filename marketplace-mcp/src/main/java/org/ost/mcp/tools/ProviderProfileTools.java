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
import java.util.Optional;

/** Wraps ProviderProfileApiController; writes are allowed to the profile's owner, an administrator or a moderator. */
@Component
@RequiredArgsConstructor
public class ProviderProfileTools {

    private static final String PROFILE = "/api/provider-profiles/{id}";

    private final RestApiClient restApi;

    @McpTool(name = "listProviderProfiles",
            description = "Lists provider profiles (kinds MASTER, SHOP, SUPPORT), one page at a time, optionally filtered and sorted. "
                    + "The list is public, no API key is needed. Page size is not a parameter: it comes from the caller's saved "
                    + "settings. " + ToolDescriptions.TOTAL_COUNT_NOTE
                    + "The result has items, totalCount, page, returned, hasMore and nextPage; when hasMore is true "
                    + "call again with page = nextPage to continue.",
            annotations = @McpTool.McpAnnotations(title = "List provider profiles", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult listProviderProfiles(
            @McpToolParam(required = false, description = "Zero-based page number, default 0") Integer page,
            @McpToolParam(required = false, description = "Only these kinds: MASTER, SHOP, SUPPORT") List<String> kinds,
            @McpToolParam(required = false, description = "Only profiles in any of these category ids (from listTaxons with type CATEGORY)") List<Long> categoryIds,
            @McpToolParam(required = false, description = "Only profiles in this city taxon id (from listTaxons with type CITY)") Long cityTaxonId,
            @McpToolParam(required = false, description = "Sort as field,direction with field createdAt or updatedAt and direction asc or desc, for example updatedAt,desc; without it the newest changes come first, then the newest created, then id") String sort,
            @McpToolParam(required = false, description = "Language tag for category and city names, for example en or uk") String locale) {
        int pageNumber = page == null ? 0 : page;
        String path = RestPath.of("/api/provider-profiles")
                .param("page", pageNumber)
                .param("kinds", kinds)
                .param("categoryIds", categoryIds)
                .param("cityTaxonId", cityTaxonId)
                .param("sort", sort)
                .param("locale", locale)
                .build();
        return ToolResults.paged(restApi.get(path), pageNumber);
    }

    @McpTool(name = "getProviderProfile",
            description = "Returns one provider profile by id with its category and city names. "
                    + "The result has item and etag; the etag is the version a later update or delete must send as If-Match. "
                    + "Public, no API key needed. Answers an error when the id is unknown.",
            annotations = @McpTool.McpAnnotations(title = "Get a provider profile", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getProviderProfile(
            @McpToolParam(description = "Provider profile id, as listed by listProviderProfiles") @NonNull Long id,
            @McpToolParam(required = false, description = "Language tag for category and city names, for example en or uk") String locale) {
        String path = RestPath.of(PROFILE, id).param("locale", locale).build();
        return ToolResults.single("item", restApi.get(path));
    }

    @McpTool(name = "createProviderProfile",
            description = "Creates a provider profile of the given kind for the caller's own account, the account cannot be chosen. "
                    + "Needs the caller's API key; SUPPORT needs an administrator or moderator caller. "
                    + "categoryIds holds category ids and cityTaxonId a city id, both from listTaxons; about, phone, telegram and viber are optional. "
                    + "The result has status and item of the new profile; no location or etag is returned.",
            annotations = @McpTool.McpAnnotations(title = "Create a provider profile", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public CallToolResult createProviderProfile(
            @McpToolParam(description = "MASTER, SHOP or SUPPORT") @NonNull String kind,
            @McpToolParam(required = false, description = "Free text about the provider") String about,
            @McpToolParam(required = false, description = "Category ids, from listTaxons with type CATEGORY") List<Long> categoryIds,
            @McpToolParam(required = false, description = "City taxon id, from listTaxons with type CITY") Long cityTaxonId,
            @McpToolParam(required = false, description = "Phone number") String phone,
            @McpToolParam(required = false, description = "Telegram contact") String telegram,
            @McpToolParam(required = false, description = "Viber contact") String viber) {
        return ToolResults.written(restApi.send(HttpMethod.POST, RestPath.of("/api/provider-profiles").build(),
                JsonBody.of(profileBody(kind, about, categoryIds, cityTaxonId, phone, telegram, viber)), JsonBody.JSON, null));
    }

    @McpTool(name = "updateProviderProfile",
            description = "Replaces all fields of one provider profile; the owner of the profile, an administrator or a moderator may call it, with their API key."
                    + "It is a full replace: omitting phone, telegram or viber clears them, and kind is required. "
                    + "etag is the version returned by getProviderProfile for this item; a stale etag is refused with HTTP 412. "
                    + "The result has status and item; no etag is returned.",
            annotations = @McpTool.McpAnnotations(title = "Update a provider profile", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult updateProviderProfile(
            @McpToolParam(description = "Provider profile id, as listed by listProviderProfiles") @NonNull Long id,
            @McpToolParam(description = "Version from getProviderProfile, sent as If-Match, quotes included") @NonNull String etag,
            @McpToolParam(description = "MASTER, SHOP or SUPPORT") @NonNull String kind,
            @McpToolParam(required = false, description = "Free text about the provider") String about,
            @McpToolParam(required = false, description = "Category ids, from listTaxons with type CATEGORY") List<Long> categoryIds,
            @McpToolParam(required = false, description = "City taxon id, from listTaxons with type CITY") Long cityTaxonId,
            @McpToolParam(required = false, description = "Phone number") String phone,
            @McpToolParam(required = false, description = "Telegram contact") String telegram,
            @McpToolParam(required = false, description = "Viber contact") String viber) {
        return ToolResults.written(restApi.send(HttpMethod.PUT, RestPath.of(PROFILE, id).build(),
                JsonBody.of(profileBody(kind, about, categoryIds, cityTaxonId, phone, telegram, viber)), JsonBody.JSON, etag));
    }

    @McpTool(name = "patchProviderProfile",
            description = "Changes only the fields given in patch of one provider profile, as a JSON merge patch; the owner of the profile, an administrator or a moderator may call it, with their API key."
                    + "A null clears about, categoryIds, cityTaxonId, phone, telegram or viber; a null kind is refused, an unknown field is refused, and kind SUPPORT needs an administrator or moderator caller. "
                    + "etag is the version returned by getProviderProfile, sent as If-Match; a stale etag is refused with HTTP 412. "
                    + "The result has status and item; no etag is returned.",
            annotations = @McpTool.McpAnnotations(title = "Patch a provider profile", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult patchProviderProfile(
            @McpToolParam(description = "Provider profile id, as listed by listProviderProfiles") @NonNull Long id,
            @McpToolParam(description = "Version from getProviderProfile, sent as If-Match, quotes included") @NonNull String etag,
            @McpToolParam(description = "JSON merge patch: only the fields to change, a field set to null clears it") @NonNull Map<String, Object> patch) {
        return ToolResults.written(restApi.send(HttpMethod.PATCH, RestPath.of(PROFILE, id).build(),
                JsonBody.of(patch), JsonBody.MERGE_PATCH, etag));
    }

    @McpTool(name = "deleteProviderProfile",
            description = "Deletes one provider profile. The owner of the profile, an administrator or a moderator may call it, with their API key."
                    + "etag is the version returned by getProviderProfile, sent as If-Match; a stale etag is refused with HTTP 412. "
                    + "The result has status (204); no etag is returned.",
            annotations = @McpTool.McpAnnotations(title = "Delete a provider profile", readOnlyHint = false,
                    destructiveHint = true, idempotentHint = true, openWorldHint = false))
    public CallToolResult deleteProviderProfile(
            @McpToolParam(description = "Provider profile id, as listed by listProviderProfiles") @NonNull Long id,
            @McpToolParam(description = "Version from getProviderProfile, sent as If-Match, quotes included") @NonNull String etag) {
        return ToolResults.written(restApi.send(HttpMethod.DELETE, RestPath.of(PROFILE, id).build(), null, null, etag));
    }

    @McpTool(name = "getProviderProfileHistory",
            description = "Lists the change history of one provider profile, newest first. Needs the caller's API key; "
                    + "administrators and moderators see every entry, other callers only the entries they authored. "
                    + "Answers an error when the id is unknown. The result has items and totalCount; each item has a snapshotId "
                    + "that getProviderProfileHistorySnapshot takes.",
            annotations = @McpTool.McpAnnotations(title = "List provider profile history", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getProviderProfileHistory(
            @McpToolParam(description = "Provider profile id, as listed by listProviderProfiles") @NonNull Long id) {
        return ToolResults.list(restApi.get(RestPath.of("/api/provider-profiles/{id}/history", id).build()));
    }

    @McpTool(name = "getProviderProfileHistorySnapshot",
            description = "Returns one recorded state of a provider profile from its history. Needs the caller's API key; "
                    + "administrators and moderators can read any snapshot, other callers only those in their own visible history. "
                    + "The result has snapshot, the recorded state, and version. Answers HTTP 403 when the snapshot is not visible to the caller and HTTP 404 when it is unknown.",
            annotations = @McpTool.McpAnnotations(title = "Get a provider profile history snapshot", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getProviderProfileHistorySnapshot(
            @McpToolParam(description = "Provider profile id, as listed by listProviderProfiles") @NonNull Long id,
            @McpToolParam(description = "Snapshot id, as listed by getProviderProfileHistory") @NonNull Long snapshotId) {
        return ToolResults.object(restApi.get(RestPath.of("/api/provider-profiles/{id}/history/{snapshotId}", id, snapshotId).build()));
    }

    @McpTool(name = "restoreProviderProfileFromSnapshot",
            description = "Restores a provider profile to a recorded state from its history, identified by id and snapshotId. "
                    + "It has the same permissions and validation as an update: the owner of the profile, an administrator or a moderator may call it, with their API key."
                    + "etag is the version returned by getProviderProfile, sent as If-Match; a stale etag is refused with HTTP 412. "
                    + "The result has status and item of the restored profile; no etag is returned.",
            annotations = @McpTool.McpAnnotations(title = "Restore a provider profile from a snapshot", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult restoreProviderProfileFromSnapshot(
            @McpToolParam(description = "Provider profile id, as listed by listProviderProfiles") @NonNull Long id,
            @McpToolParam(description = "Snapshot id, as listed by getProviderProfileHistory") @NonNull Long snapshotId,
            @McpToolParam(description = "Version from getProviderProfile, sent as If-Match, quotes included") @NonNull String etag) {
        return ToolResults.written(restApi.send(HttpMethod.POST,
                RestPath.of("/api/provider-profiles/{id}/history/{snapshotId}/restore", id, snapshotId).build(), null, null, etag));
    }

    private static Map<String, Object> profileBody(String kind, String about, List<Long> categoryIds, Long cityTaxonId,
                                                   String phone, String telegram, String viber) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", kind);
        putIfPresent(body, "about", about);
        putIfPresent(body, "categoryIds", categoryIds);
        putIfPresent(body, "cityTaxonId", cityTaxonId);
        putIfPresent(body, "phone", phone);
        putIfPresent(body, "telegram", telegram);
        putIfPresent(body, "viber", viber);
        return body;
    }

    private static void putIfPresent(Map<String, Object> body, String key, Object value) {
        Optional.ofNullable(value).ifPresent(present -> body.put(key, present));
    }
}
