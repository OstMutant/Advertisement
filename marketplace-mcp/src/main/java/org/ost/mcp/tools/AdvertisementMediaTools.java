package org.ost.mcp.tools;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.mcp.rest.FilePart;
import org.ost.mcp.rest.JsonBody;
import org.ost.mcp.rest.RestApiClient;
import org.ost.mcp.rest.RestPath;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Wraps AdvertisementMediaApiController: gallery photo upload as multipart, video links and deletes. */
@Component
@RequiredArgsConstructor
public class AdvertisementMediaTools {

    private final RestApiClient restApi;

    @McpTool(name = "uploadAdvertisementMedia",
            description = "Uploads one or more photos or videos to the gallery of an advertisement. "
                    + "Only the advertisement's author, administrators and moderators may call it, with their API key; others get HTTP 403, and an unknown advertisement HTTP 404. "
                    + "Each file is sent as base64 text with its fileName and contentType (JPEG, PNG, WebP, GIF, MP4 or WebM); at most 10 items per advertisement and 50 MB per file. "
                    + "The result has status and item, where item lists the created gallery items with id, url, filename and contentType.",
            annotations = @McpTool.McpAnnotations(title = "Upload advertisement media", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public CallToolResult uploadAdvertisementMedia(
            @McpToolParam(description = "Advertisement id, as listed by listAdvertisements") @NonNull Long id,
            @McpToolParam(description = "One or more files, each with fileName, contentType (for example image/png or video/mp4) and contentBase64 (the file bytes as base64 text)") @NonNull List<MediaFile> files) {
        List<FilePart> parts = new ArrayList<>();
        for (MediaFile file : files) {
            try {
                byte[] bytes = Base64.getDecoder().decode(file.contentBase64());
                parts.add(new FilePart(file.fileName(), file.contentType(), new ByteArrayResource(bytes)));
            } catch (IllegalArgumentException _) {
                return CallToolResult.builder()
                        .addTextContent("contentBase64 of file " + file.fileName() + " is not valid base64")
                        .isError(true)
                        .build();
            }
        }
        return ToolResults.written(restApi.postMultipart(RestPath.of("/api/advertisements/{id}/media", id).build(), "files", parts));
    }

    @McpTool(name = "addAdvertisementVideo",
            description = "Adds a video link to the gallery of an advertisement. "
                    + "Only the advertisement's author, administrators and moderators may call it, with their API key; others get HTTP 403, and an unknown advertisement HTTP 404. "
                    + "The url must be a YouTube or Vimeo link; any other host is refused with HTTP 400. The video counts against the limit of 10 gallery items. "
                    + "The result has status and item, the created gallery item.",
            annotations = @McpTool.McpAnnotations(title = "Add advertisement video", readOnlyHint = false,
                    destructiveHint = false, idempotentHint = false, openWorldHint = false))
    public CallToolResult addAdvertisementVideo(
            @McpToolParam(description = "Advertisement id, as listed by listAdvertisements") @NonNull Long id,
            @McpToolParam(description = "YouTube or Vimeo link of the video") @NonNull String url) {
        return ToolResults.written(restApi.send(HttpMethod.POST, RestPath.of("/api/advertisements/{id}/media/videos", id).build(),
                JsonBody.of("url", url), JsonBody.JSON, null));
    }

    @McpTool(name = "deleteAdvertisementMedia",
            description = "Removes one item from the gallery of an advertisement. "
                    + "Only the advertisement's author, administrators and moderators may call it, with their API key; others get HTTP 403, and an unknown advertisement HTTP 404. "
                    + "attachmentId is the item id from listAdvertisementMedia; an item that belongs to another advertisement is refused with HTTP 404. "
                    + "The result has status.",
            annotations = @McpTool.McpAnnotations(title = "Delete advertisement media", readOnlyHint = false,
                    destructiveHint = true, idempotentHint = true, openWorldHint = false))
    public CallToolResult deleteAdvertisementMedia(
            @McpToolParam(description = "Advertisement id, as listed by listAdvertisements") @NonNull Long id,
            @McpToolParam(description = "Gallery item id, as listed by listAdvertisementMedia") @NonNull Long attachmentId) {
        return ToolResults.written(restApi.send(HttpMethod.DELETE,
                RestPath.of("/api/advertisements/{id}/media/{attachmentId}", id, attachmentId).build(), null, null, null));
    }

    /** One file of an upload: its name, media type and base64 content. */
    public record MediaFile(String fileName, String contentType, String contentBase64) {}
}
