package org.ost.restapi.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.FeedbackAccessService;
import org.ost.orchestrator.services.FeedbackCommentWriteService;
import org.ost.orchestrator.services.FeedbackWriteService;
import org.ost.orchestrator.services.ProviderProfileReadService;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.feedback.dto.FeedbackAggregateDto;
import org.ost.platform.feedback.dto.FeedbackCommentDto;
import org.ost.platform.feedback.dto.FeedbackCommentSaveDto;
import org.ost.platform.feedback.dto.FeedbackDto;
import org.ost.platform.feedback.dto.FeedbackSaveDto;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.restapi.api.concurrency.ETagUtil;
import org.ost.restapi.api.paging.PagedResponseBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/** Provider-profile reviews and their single reply: public list and aggregate, authenticated writes, replies and flags. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FeedbackApiController {

    private final FeedbackAccessService feedbackAccessService;
    private final FeedbackWriteService feedbackWriteService;
    private final FeedbackCommentWriteService feedbackCommentWriteService;
    private final ProviderProfileReadService providerProfileReadService;
    private final UserProfileService userProfileService;

    @Operation(operationId = "listProviderProfileFeedback", summary = "List a provider profile's reviews",
            description = "Public. Newest first, paged with the size from the caller's saved settings (or the shared default for a visitor). A hidden review carries no rating and no text, for everyone including its author.")
    @ApiResponse(responseCode = "404", description = "The provider profile does not exist")
    @GetMapping("/provider-profiles/{id}/feedback")
    public ResponseEntity<List<FeedbackEntryResponse>> listProviderProfileFeedback(@AuthenticationPrincipal Long actorId,
            @PathVariable @NonNull Long id, @RequestParam(defaultValue = "0") int page, UriComponentsBuilder uriBuilder) {
        ProviderProfileDto profile = providerProfileReadService.findById(id).orElseThrow(NoSuchElementException::new);
        int size = userProfileService.resolveFeedbackPageSize(actorId);
        List<FeedbackDto> entries = feedbackAccessService.findForEntity(EntityType.PROVIDER_PROFILE, id, page, size);
        Map<Long, String> authorNames = entries.isEmpty() ? Map.of()
                : feedbackAccessService.resolveAuthorNames(entries.stream().map(FeedbackDto::authorId).collect(Collectors.toSet()));
        Long ownerId = profile.getActorId();
        List<FeedbackEntryResponse> items = entries.stream()
                .map(entry -> toEntry(entry, actorId, authorNames, replyFor(entry.id(), actorId, ownerId)))
                .toList();
        int total = feedbackAccessService.getAggregate(EntityType.PROVIDER_PROFILE, id).reviewCount();
        return PagedResponseBuilder.build(uriBuilder, page, size, total, items);
    }

    @Operation(operationId = "getProviderProfileFeedbackAggregate", summary = "Get a provider profile's rating summary",
            description = "Public. The average rating and the number of reviews.")
    @GetMapping("/provider-profiles/{id}/feedback/aggregate")
    public AggregateResponse providerProfileFeedbackAggregate(@PathVariable @NonNull Long id) {
        FeedbackAggregateDto aggregate = feedbackAccessService.getAggregate(EntityType.PROVIDER_PROFILE, id);
        return new AggregateResponse(aggregate.avgRating(), aggregate.reviewCount());
    }

    @Operation(operationId = "createProviderProfileFeedback", summary = "Review a provider profile",
            description = "One review per user and profile (a second one is 409), at most 10 per day (429). The caller becomes the author.")
    @ApiResponse(responseCode = "404", description = "The provider profile does not exist")
    @ApiResponse(responseCode = "409", description = "The caller has already reviewed this profile")
    @ApiResponse(responseCode = "429", description = "Daily review limit reached")
    @PostMapping("/provider-profiles/{id}/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerKey")
    public FeedbackEntryResponse createProviderProfileFeedback(@AuthenticationPrincipal @NonNull Long actorId,
            @PathVariable @NonNull Long id, @RequestBody @Valid FeedbackWriteRequest request) {
        FeedbackDto created = feedbackWriteService.create(actorId, id, request.rating(), request.text());
        return toEntry(created, actorId, feedbackAccessService.resolveAuthorNames(Set.of(created.authorId())), null);
    }

    @Operation(operationId = "updateFeedback", summary = "Edit your own review",
            description = "If-Match must carry the version from the review's current state. Only the author may edit, and only within the edit window.")
    @ApiResponse(responseCode = "403", description = "The caller is not the author")
    @ApiResponse(responseCode = "409", description = "The edit window has closed")
    @ApiResponse(responseCode = "412", description = "The version is stale")
    @PutMapping("/feedback/{feedbackId}")
    @SecurityRequirement(name = "bearerKey")
    public FeedbackEntryResponse updateFeedback(@AuthenticationPrincipal @NonNull Long actorId, @PathVariable @NonNull Long feedbackId,
            @RequestBody @Valid FeedbackWriteRequest request,
            @Parameter(description = "Version from the review's current state") @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        FeedbackDto updated = feedbackWriteService.edit(actorId, feedbackId, request.rating(), request.text(), ETagUtil.requireIfMatch(ifMatch));
        return toEntry(updated, actorId, feedbackAccessService.resolveAuthorNames(Set.of(updated.authorId())),
                replyFor(updated.id(), actorId, ownerOf(updated.entityId())));
    }

    @Operation(operationId = "createFeedbackReply", summary = "Reply to a review",
            description = "Only the reviewed provider profile's owner may reply, once per review (a second reply is 409), at most 30 per day (429).")
    @ApiResponse(responseCode = "403", description = "The caller does not own the reviewed profile")
    @ApiResponse(responseCode = "409", description = "The review already has a reply")
    @ApiResponse(responseCode = "429", description = "Daily reply limit reached")
    @PostMapping("/feedback/{feedbackId}/reply")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerKey")
    public ReplyResponse createFeedbackReply(@AuthenticationPrincipal @NonNull Long actorId, @PathVariable @NonNull Long feedbackId,
            @RequestBody @Valid ReplyWriteRequest request) {
        FeedbackCommentDto saved = feedbackCommentWriteService.saveComment(new FeedbackCommentSaveDto(null, feedbackId, actorId, request.text(), null));
        return toReply(saved, actorId, actorId);
    }

    @Operation(operationId = "updateFeedbackReply", summary = "Edit your reply to a review",
            description = "If-Match must carry the reply's current version. Only the reply's author may edit it, within the edit window.")
    @ApiResponse(responseCode = "404", description = "The review has no reply")
    @PutMapping("/feedback/{feedbackId}/reply")
    @SecurityRequirement(name = "bearerKey")
    public ReplyResponse updateFeedbackReply(@AuthenticationPrincipal @NonNull Long actorId, @PathVariable @NonNull Long feedbackId,
            @RequestBody @Valid ReplyWriteRequest request,
            @Parameter(description = "Version from the reply's current state") @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        FeedbackCommentDto existing = existingReply(feedbackId);
        FeedbackCommentDto saved = feedbackCommentWriteService.saveComment(
                new FeedbackCommentSaveDto(existing.id(), feedbackId, actorId, request.text(), ETagUtil.requireIfMatch(ifMatch)));
        return toReply(saved, actorId, actorId);
    }

    @Operation(operationId = "deleteFeedbackReply", summary = "Delete your reply to a review",
            description = "Only the reply's author may delete it, within the edit window.")
    @ApiResponse(responseCode = "404", description = "The review has no reply")
    @DeleteMapping("/feedback/{feedbackId}/reply")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerKey")
    public void deleteFeedbackReply(@AuthenticationPrincipal @NonNull Long actorId, @PathVariable @NonNull Long feedbackId) {
        feedbackCommentWriteService.deleteComment(existingReply(feedbackId).id(), actorId);
    }

    @Operation(operationId = "flagFeedback", summary = "Report a review as a violation",
            description = "Hides the review pending moderation. The author cannot report their own review.")
    @ApiResponse(responseCode = "403", description = "The caller is the review's author")
    @ApiResponse(responseCode = "404", description = "The review does not exist")
    @PostMapping("/feedback/{feedbackId}/flags")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerKey")
    public void flagFeedback(@AuthenticationPrincipal @NonNull Long actorId, @PathVariable @NonNull Long feedbackId,
            @RequestBody @Valid FlagRequest request) {
        feedbackAccessService.flagFeedback(feedbackId, actorId, request.reason());
    }

    @Operation(operationId = "flagFeedbackReply", summary = "Report a review's reply as a violation",
            description = "Hides the reply pending moderation. The reply's own author cannot report it.")
    @ApiResponse(responseCode = "403", description = "The caller is the reply's author")
    @ApiResponse(responseCode = "404", description = "The review has no reply")
    @PostMapping("/feedback/{feedbackId}/reply/flags")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerKey")
    public void flagFeedbackReply(@AuthenticationPrincipal @NonNull Long actorId, @PathVariable @NonNull Long feedbackId,
            @RequestBody @Valid FlagRequest request) {
        feedbackAccessService.flagComment(existingReply(feedbackId).id(), actorId, request.reason());
    }

    private FeedbackCommentDto existingReply(Long feedbackId) {
        return feedbackAccessService.findCommentByFeedback(feedbackId)
                .orElseThrow(() -> new NoSuchElementException("Reply to feedback " + feedbackId + " not found"));
    }

    private Long ownerOf(Long profileId) {
        return providerProfileReadService.findById(profileId).map(ProviderProfileDto::getActorId)
                .orElseThrow(NoSuchElementException::new);
    }

    private ReplyResponse replyFor(Long feedbackId, Long actorId, Long ownerId) {
        return feedbackAccessService.findCommentByFeedback(feedbackId)
                .map(comment -> toReply(comment, actorId, ownerId))
                .orElse(null);
    }

    private static FeedbackEntryResponse toEntry(FeedbackDto entry, Long actorId, Map<Long, String> authorNames, ReplyResponse reply) {
        boolean hidden = entry.moderationStatus().isHidden();
        boolean editable = actorId != null && entry.authorId().equals(actorId) && entry.isEditable();
        return new FeedbackEntryResponse(entry.id(), entry.authorId(), authorNames.getOrDefault(entry.authorId(), ""),
                hidden ? null : entry.rating(), hidden ? null : entry.feedbackText(), hidden,
                entry.createdAt(), entry.updatedAt(), entry.version(), editable, reply);
    }

    private static ReplyResponse toReply(FeedbackCommentDto comment, Long actorId, Long ownerId) {
        boolean hidden = comment.moderationStatus().isHidden();
        boolean editable = actorId != null && actorId.equals(ownerId) && comment.isEditable();
        return new ReplyResponse(comment.id(), comment.authorId(), hidden ? null : comment.commentText(), hidden,
                comment.createdAt(), comment.updatedAt(), comment.version(), editable);
    }

    /** A review as the public sees it; a hidden review has a null rating and text. */
    public record FeedbackEntryResponse(Long id, Long authorId, String authorName, Integer rating, String text, boolean hidden,
                                        Instant createdAt, Instant updatedAt, Long version, boolean editable,
                                        ReplyResponse reply) {
    }

    /** The single reply to a review; a hidden reply has a null text. */
    public record ReplyResponse(Long id, Long authorId, String text, boolean hidden, Instant createdAt,
                                Instant updatedAt, Long version, boolean editable) {
    }

    /** Average rating and review count of a provider profile. */
    public record AggregateResponse(double averageRating, int reviewCount) {
    }

    /** Rating and text of a review. */
    public record FeedbackWriteRequest(
            @NotNull @Min(1) @Max(5) Integer rating,
            @NotBlank @Size(max = FeedbackSaveDto.TEXT_RAW_MAX_LENGTH) String text
    ) {
    }

    /** Text of a reply. */
    public record ReplyWriteRequest(@NotBlank @Size(max = FeedbackCommentSaveDto.TEXT_RAW_MAX_LENGTH) String text) {
    }

    /** Reason given when reporting a review or reply. */
    public record FlagRequest(@NotBlank @Size(max = 2000) String reason) {
    }
}
