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

/** Wraps TaxonApiController: categories and cities, with writes for administrators and moderators only. */
@Component
@RequiredArgsConstructor
public class TaxonTools {

    private static final String TAXON = "/api/taxons/{id}";

    private final RestApiClient restApi;

    @McpTool(name = "listTaxons",
            description = "Lists all categories and cities, or only one type. The list is public, no API key is needed. "
                    + "The ids are what the category and city filters of listAdvertisements and listProviderProfiles take. "
                    + "The result has items and totalCount.",
            annotations = @McpTool.McpAnnotations(title = "List categories and cities", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult listTaxons(
            @McpToolParam(required = false, description = "CATEGORY or CITY; omit for both") String type,
            @McpToolParam(required = false, description = "Language tag for the names, for example en or uk") String locale) {
        String path = RestPath.of("/api/taxons").param("type", type).param("locale", locale).build();
        return ToolResults.list(restApi.get(path));
    }

    @McpTool(name = "getTaxon",
            description = "Returns one category or city by id. Falls back to English names when the locale has none. "
                    + "The result has item and etag. Public, no API key needed. Answers an error when the id is unknown.",
            annotations = @McpTool.McpAnnotations(title = "Get a category or city", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getTaxon(
            @McpToolParam(description = "Taxon id, as listed by listTaxons") @NonNull Long id,
            @McpToolParam(required = false, description = "Language tag for the names, for example en or uk") String locale) {
        String path = RestPath.of(TAXON, id).param("locale", locale).build();
        return ToolResults.single("item", restApi.get(path));
    }

    @McpTool(name = "getTaxonHistory",
            description = "Lists the change history of one category or city, newest first. Needs the caller's API key; "
                    + "administrators and moderators see every entry, other callers only the entries they authored. "
                    + "Answers an error when the id is unknown. The result has items and totalCount; each item has a snapshotId "
                    + "that getTaxonHistorySnapshot takes.",
            annotations = @McpTool.McpAnnotations(title = "List category or city history", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getTaxonHistory(
            @McpToolParam(description = "Taxon id, as listed by listTaxons") @NonNull Long id) {
        return ToolResults.list(restApi.get(RestPath.of("/api/taxons/{id}/history", id).build()));
    }

    @McpTool(name = "getTaxonHistorySnapshot",
            description = "Returns one recorded state of a category or city from its history. Needs the caller's API key; "
                    + "administrators and moderators can read any snapshot, other callers only those in their own visible history. "
                    + "The result has snapshot, the recorded state, and version. Answers an error when the snapshot is unknown or not visible to the caller.",
            annotations = @McpTool.McpAnnotations(title = "Get a category or city history snapshot", readOnlyHint = true,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult getTaxonHistorySnapshot(
            @McpToolParam(description = "Taxon id, as listed by listTaxons") @NonNull Long id,
            @McpToolParam(description = "Snapshot id, as listed by getTaxonHistory") @NonNull Long snapshotId) {
        return ToolResults.object(restApi.get(RestPath.of("/api/taxons/{id}/history/{snapshotId}", id, snapshotId).build()));
    }

    @McpTool(name = "createTaxon",
            description = "Creates a category or city. Only administrators and moderators may call it, with their API key. "
                    + "type is CATEGORY or CITY; translations holds one entry per supported language (en and uk today), "
                    + "each with a non-blank name and description. The result has status and item of the new taxon; no location or etag is returned.",
            annotations = @McpTool.McpAnnotations(title = "Create a category or city", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public CallToolResult createTaxon(
            @McpToolParam(description = "CATEGORY or CITY") @NonNull String type,
            @McpToolParam(description = "One entry per supported language, each with a locale tag, a non-blank name and a non-blank description") @NonNull List<Translation> translations) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type);
        body.put("translations", translations);
        return ToolResults.written(restApi.send(HttpMethod.POST, RestPath.of("/api/taxons").build(),
                JsonBody.of(body), JsonBody.JSON, null));
    }

    @McpTool(name = "updateTaxon",
            description = "Changes the translations of one category or city. Only administrators and moderators may call it, with their API key. "
                    + "etag is the version returned by getTaxon for this item; a stale etag is refused with HTTP 412. "
                    + "translations holds one entry per supported language, each with a non-blank name and description. "
                    + "The result has status and item of the updated taxon; no etag is returned.",
            annotations = @McpTool.McpAnnotations(title = "Update a category or city", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult updateTaxon(
            @McpToolParam(description = "Taxon id, as listed by listTaxons") @NonNull Long id,
            @McpToolParam(description = "Version from getTaxon, sent as If-Match, quotes included") @NonNull String etag,
            @McpToolParam(description = "One entry per supported language, each with a locale tag, a non-blank name and a non-blank description") @NonNull List<Translation> translations) {
        return ToolResults.written(restApi.send(HttpMethod.PUT, RestPath.of(TAXON, id).build(),
                JsonBody.of("translations", translations), JsonBody.JSON, etag));
    }

    @McpTool(name = "deleteTaxon",
            description = "Soft-deletes one category or city; restoreTaxon clears the deletion mark and undoes it. "
                    + "Only administrators and moderators may call it, with their API key. "
                    + "etag is the version returned by getTaxon, sent as If-Match; a stale etag is refused with HTTP 412. "
                    + "The result has status (204); no etag is returned.",
            annotations = @McpTool.McpAnnotations(title = "Delete a category or city", readOnlyHint = false,
                    destructiveHint = true, idempotentHint = true, openWorldHint = false))
    public CallToolResult deleteTaxon(
            @McpToolParam(description = "Taxon id, as listed by listTaxons") @NonNull Long id,
            @McpToolParam(description = "Version from getTaxon, sent as If-Match, quotes included") @NonNull String etag) {
        return ToolResults.written(restApi.send(HttpMethod.DELETE, RestPath.of(TAXON, id).build(), null, null, etag));
    }

    @McpTool(name = "restoreTaxon",
            description = "Clears the deletion mark of a soft-deleted category or city, undoing deleteTaxon. "
                    + "Only administrators and moderators may call it, with their API key. "
                    + "The result has status and item of the restored taxon; no etag is returned.",
            annotations = @McpTool.McpAnnotations(title = "Restore a deleted category or city", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult restoreTaxon(
            @McpToolParam(description = "Taxon id, as listed by listTaxons") @NonNull Long id) {
        return ToolResults.written(restApi.send(HttpMethod.POST, RestPath.of("/api/taxons/{id}/restore", id).build(), null, null, null));
    }

    @McpTool(name = "restoreTaxonFromSnapshot",
            description = "Restores a category or city to a recorded state from its history, identified by id and snapshotId. "
                    + "Only administrators and moderators may call it, with their API key. "
                    + "etag is the version returned by getTaxon, sent as If-Match; a stale etag is refused with HTTP 412. "
                    + "The result has status and item of the restored taxon; no etag is returned.",
            annotations = @McpTool.McpAnnotations(title = "Restore a category or city from a snapshot", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public CallToolResult restoreTaxonFromSnapshot(
            @McpToolParam(description = "Taxon id, as listed by listTaxons") @NonNull Long id,
            @McpToolParam(description = "Snapshot id, as listed by getTaxonHistory") @NonNull Long snapshotId,
            @McpToolParam(description = "Version from getTaxon, sent as If-Match, quotes included") @NonNull String etag) {
        return ToolResults.written(restApi.send(HttpMethod.POST,
                RestPath.of("/api/taxons/{id}/history/{snapshotId}/restore", id, snapshotId).build(), null, null, etag));
    }

    /** One language version of a taxon's names and description, as sent to the REST API. */
    public record Translation(String locale, String name, String description) {}
}
