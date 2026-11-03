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

/** Wraps AdvertisementApiController; also carries listAdvertisementMedia of AdvertisementMediaApiController. */
@Component
@RequiredArgsConstructor
public class AdvertisementTools {

    private static final String ADVERTISEMENT = "/api/advertisements/{id}";

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
        String path = RestPath.of(ADVERTISEMENT, id).param("locale", locale).build();
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

    @McpTool(name = "createAdvertisement",
            description = "Creates a marketplace advertisement for the caller, who must send their API key. "
                    + "adKind is OFFER, REQUEST or PRODUCT; categoryIds takes category ids and cityTaxonId a city id, both from listTaxons. "
                    + "The result has status and item of the new advertisement; location and etag are not returned, so read the version with getAdvertisement.",
            annotations = @McpTool.McpAnnotations(title = "Create an advertisement", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public CallToolResult createAdvertisement(
            @McpToolParam(description = "Advertisement title") @NonNull String title,
            @McpToolParam(description = "Advertisement description") @NonNull String description,
            @McpToolParam(description = "OFFER, REQUEST or PRODUCT") @NonNull String adKind,
            @McpToolParam(required = false, description = "Category ids (from listTaxons with type CATEGORY)") List<Long> categoryIds,
            @McpToolParam(required = false, description = "City taxon id (from listTaxons with type CITY)") Long cityTaxonId) {
        return ToolResults.written(restApi.send(HttpMethod.POST, RestPath.of("/api/advertisements").build(),
                JsonBody.of(advertisementBody(title, description, adKind, categoryIds, cityTaxonId)), JsonBody.JSON, null));
    }

    @McpTool(name = "updateAdvertisement",
            description = "Replaces the fields of one advertisement with the values sent. Only its author, an administrator or a moderator "
                    + "may call it, with their API key. etag is the version returned by getAdvertisement, sent as If-Match; "
                    + "a stale etag is refused with HTTP 412. The result has status and item of the updated advertisement; no etag is returned.",
            annotations = @McpTool.McpAnnotations(title = "Update an advertisement", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult updateAdvertisement(
            @McpToolParam(description = "Advertisement id, as listed by listAdvertisements") @NonNull Long id,
            @McpToolParam(description = "Version from getAdvertisement, sent as If-Match, quotes included") @NonNull String etag,
            @McpToolParam(description = "Advertisement title") @NonNull String title,
            @McpToolParam(description = "Advertisement description") @NonNull String description,
            @McpToolParam(description = "OFFER, REQUEST or PRODUCT") @NonNull String adKind,
            @McpToolParam(required = false, description = "Category ids (from listTaxons with type CATEGORY)") List<Long> categoryIds,
            @McpToolParam(required = false, description = "City taxon id (from listTaxons with type CITY)") Long cityTaxonId) {
        return ToolResults.written(restApi.send(HttpMethod.PUT, RestPath.of(ADVERTISEMENT, id).build(),
                JsonBody.of(advertisementBody(title, description, adKind, categoryIds, cityTaxonId)), JsonBody.JSON, etag));
    }

    @McpTool(name = "patchAdvertisement",
            description = "Changes only the fields named in patch, sent as a JSON merge patch; null clears categoryIds or cityTaxonId, and null on title, description or adKind is refused with HTTP 400."
                    + "Only its author, an administrator or a moderator may call it, with their API key. "
                    + "etag is the version returned by getAdvertisement, sent as If-Match; a stale etag is refused with HTTP 412. "
                    + "The result has status and item of the updated advertisement; no etag is returned.",
            annotations = @McpTool.McpAnnotations(title = "Patch an advertisement", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult patchAdvertisement(
            @McpToolParam(description = "Advertisement id, as listed by listAdvertisements") @NonNull Long id,
            @McpToolParam(description = "Version from getAdvertisement, sent as If-Match, quotes included") @NonNull String etag,
            @McpToolParam(description = "JSON merge patch: only the fields to change, for example {\"title\": \"New title\"}; null clears a field") @NonNull Map<String, Object> patch) {
        return ToolResults.written(restApi.send(HttpMethod.PATCH, RestPath.of(ADVERTISEMENT, id).build(),
                JsonBody.of(patch), JsonBody.MERGE_PATCH, etag));
    }

    @McpTool(name = "deleteAdvertisement",
            description = "Deletes one advertisement. Only its author, an administrator or a moderator may call it, with their API key. "
                    + "etag is the version returned by getAdvertisement, sent as If-Match; a stale etag is refused with HTTP 412. "
                    + "The result has status (204); no etag is returned.",
            annotations = @McpTool.McpAnnotations(title = "Delete an advertisement", readOnlyHint = false,
                    destructiveHint = true, idempotentHint = true, openWorldHint = false))
    public CallToolResult deleteAdvertisement(
            @McpToolParam(description = "Advertisement id, as listed by listAdvertisements") @NonNull Long id,
            @McpToolParam(description = "Version from getAdvertisement, sent as If-Match, quotes included") @NonNull String etag) {
        return ToolResults.written(restApi.send(HttpMethod.DELETE, RestPath.of(ADVERTISEMENT, id).build(), null, null, etag));
    }

    @McpTool(name = "getAdvertisementHistory",
            description = "Lists the change history of one advertisement, newest first. Needs the caller's API key; "
                    + "administrators and moderators see every entry, other callers only the entries they authored. "
                    + "Answers an error when the id is unknown. The result has items and totalCount; each item has a snapshotId "
                    + "that getAdvertisementHistorySnapshot takes.",
            annotations = @McpTool.McpAnnotations(title = "List advertisement history", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getAdvertisementHistory(
            @McpToolParam(description = "Advertisement id, as listed by listAdvertisements") @NonNull Long id) {
        return ToolResults.list(restApi.get(RestPath.of("/api/advertisements/{id}/history", id).build()));
    }

    @McpTool(name = "getAdvertisementHistorySnapshot",
            description = "Returns one recorded state of an advertisement from its history, with its version. Needs the caller's API key. "
                    + "Answers HTTP 403 when the snapshot is not visible to the caller and HTTP 404 when it is unknown. "
                    + "The result has snapshot, the recorded state, and version.",
            annotations = @McpTool.McpAnnotations(title = "Get an advertisement history snapshot", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getAdvertisementHistorySnapshot(
            @McpToolParam(description = "Advertisement id, as listed by listAdvertisements") @NonNull Long id,
            @McpToolParam(description = "Snapshot id, as listed by getAdvertisementHistory") @NonNull Long snapshotId) {
        return ToolResults.object(restApi.get(RestPath.of("/api/advertisements/{id}/history/{snapshotId}", id, snapshotId).build()));
    }

    @McpTool(name = "restoreAdvertisementFromSnapshot",
            description = "Restores an advertisement to a recorded state from its history, identified by id and snapshotId. "
                    + "Only its author, an administrator or a moderator may call it, with their API key. "
                    + "etag is the version returned by getAdvertisement, sent as If-Match; a stale etag is refused with HTTP 412. "
                    + "The result has status and item of the restored advertisement; no etag is returned.",
            annotations = @McpTool.McpAnnotations(title = "Restore an advertisement from a snapshot", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult restoreAdvertisementFromSnapshot(
            @McpToolParam(description = "Advertisement id, as listed by listAdvertisements") @NonNull Long id,
            @McpToolParam(description = "Snapshot id, as listed by getAdvertisementHistory") @NonNull Long snapshotId,
            @McpToolParam(description = "Version from getAdvertisement, sent as If-Match, quotes included") @NonNull String etag) {
        return ToolResults.written(restApi.send(HttpMethod.POST,
                RestPath.of("/api/advertisements/{id}/history/{snapshotId}/restore", id, snapshotId).build(), null, null, etag));
    }

    private static Map<String, Object> advertisementBody(String title, String description, String adKind,
                                                         List<Long> categoryIds, Long cityTaxonId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("description", description);
        body.put("adKind", adKind);
        Optional.ofNullable(categoryIds).ifPresent(ids -> body.put("categoryIds", ids));
        Optional.ofNullable(cityTaxonId).ifPresent(id -> body.put("cityTaxonId", id));
        return body;
    }
}
