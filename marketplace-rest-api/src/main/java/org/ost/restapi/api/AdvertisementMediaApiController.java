package org.ost.restapi.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.AdvertisementMediaService;
import org.ost.platform.attachment.dto.AttachmentItemDto;
import org.ost.platform.attachment.model.AttachmentLimits;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Photo and video gallery of an advertisement: reads are public, writes need bearer authentication, every change lands in the advertisement's history. */
@RestController
@RequestMapping("/api/advertisements/{id}/media")
@RequiredArgsConstructor
public class AdvertisementMediaApiController {

    private final AdvertisementMediaService mediaService;

    @Operation(operationId = "listAdvertisementMedia", summary = "List an advertisement's gallery",
            description = "Photos and video links in gallery order. 404 when the advertisement is unknown.")
    @GetMapping
    public List<MediaItemResponse> list(@PathVariable Long id) {
        return mediaService.list(id).stream().map(AdvertisementMediaApiController::toResponse).toList();
    }

    @Operation(operationId = "uploadAdvertisementMedia", summary = "Upload photos or videos to an advertisement's gallery",
            description = "Multipart field 'files', one or more files. At most 10 items per advertisement, 50 MB per file; types JPEG, PNG, WebP, GIF, MP4, WebM. "
                    + "400 for too many items, a bad type or an oversize file; 403 when the caller may not edit the advertisement; 404 when unknown.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerKey")
    public List<MediaItemResponse> upload(@AuthenticationPrincipal Long actorId, @PathVariable Long id,
            @RequestParam("files") List<MultipartFile> files) throws IOException {
        if (files.isEmpty()) {
            throw new IllegalArgumentException("At least one file is required");
        }
        if (files.size() > AttachmentLimits.MAX_FILES) {
            throw new IllegalArgumentException("A gallery holds at most %d items".formatted(AttachmentLimits.MAX_FILES));
        }
        List<AdvertisementMediaService.UploadItem> items = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.getOriginalFilename() == null || file.getContentType() == null) {
                throw new IllegalArgumentException("Every file needs a name and a content type");
            }
            items.add(new AdvertisementMediaService.UploadItem(file.getOriginalFilename(), file.getInputStream(),
                    file.getSize(), file.getContentType()));
        }
        return mediaService.uploadAll(actorId, id, items).stream().map(AdvertisementMediaApiController::toResponse).toList();
    }

    @Operation(operationId = "addAdvertisementVideo", summary = "Add a video link to an advertisement's gallery",
            description = "Accepts a YouTube or Vimeo link; 400 for any other host. Counts against the 10-item limit. 403 when the caller may not edit the advertisement; 404 when unknown.")
    @PostMapping("/videos")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerKey")
    public MediaItemResponse addVideo(@AuthenticationPrincipal Long actorId, @PathVariable Long id,
            @RequestBody @Valid VideoRequest request) {
        return toResponse(mediaService.addVideo(actorId, id, request.url()));
    }

    @Operation(operationId = "deleteAdvertisementMedia", summary = "Remove one item from an advertisement's gallery",
            description = "404 when the item does not belong to this advertisement; 403 when the caller may not edit it.")
    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerKey")
    public void delete(@AuthenticationPrincipal Long actorId, @PathVariable Long id, @PathVariable Long attachmentId) {
        mediaService.delete(actorId, id, attachmentId);
    }

    private static MediaItemResponse toResponse(AttachmentItemDto item) {
        return new MediaItemResponse(item.id(), item.url(), item.filename(), item.contentType());
    }

    public record MediaItemResponse(Long id, String url, String filename, String contentType) {
    }

    public record VideoRequest(@NotBlank @Size(max = 2048) String url) {
    }
}
