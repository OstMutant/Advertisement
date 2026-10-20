package org.ost.restapi.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.FeedbackAccessService;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.feedback.dto.ModerationQueueFilterDto;
import org.ost.platform.feedback.dto.ModerationQueueItemDto;
import org.ost.platform.feedback.model.FeedbackModerationStatus;
import org.ost.platform.feedback.model.ModerationQueueItemType;
import org.ost.restapi.api.paging.PagedResponseBuilder;
import org.ost.restapi.api.paging.SortQueryParser;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Moderation queue of flagged reviews and replies, and the approve / confirm-violation decisions on them -- administrators and moderators only. */
@RestController
@RequestMapping("/api/moderation")
@RequiredArgsConstructor
public class ModerationApiController {

    // Mirrors the moderation queue's UI-sortable set (ModerationQueueSortMeta) -- same cross-module constraint as UserApiController's SORTABLE_FIELDS.
    private static final Set<String> SORTABLE_FIELDS = Set.of(ModerationQueueItemDto.Fields.createdAt, ModerationQueueItemDto.Fields.type);

    // The UI's default queue order (ModerationQueryConfig), used when no sort is requested.
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, ModerationQueueItemDto.Fields.createdAt);

    private final FeedbackAccessService feedbackAccessService;
    private final UserProfileService userProfileService;

    @Operation(operationId = "getModerationQueue", summary = "List the moderation queue",
            description = "Administrators and moderators only. Lists flagged reviews and replies, each with its full text. Newest first unless sorted (sort=createdAt,desc or sort=type,asc). An empty statuses filter means only HIDDEN entries, the ones awaiting a decision. Paged with the caller's saved moderation page size.")
    @GetMapping("/queue")
    @SecurityRequirement(name = "bearerKey")
    public ResponseEntity<List<ModerationQueueEntryResponse>> getModerationQueue(@AuthenticationPrincipal @NonNull Long actorId,
            @ModelAttribute @Valid ModerationQueueFilterDto filter, @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String sort, UriComponentsBuilder uriBuilder) {
        int size = userProfileService.resolveModerationPageSize(actorId);
        Sort parsed = SortQueryParser.parse(sort, SORTABLE_FIELDS);
        FeedbackAccessService.ModerationQueuePage result = feedbackAccessService.findModerationQueue(
                actorId, filter, parsed.isUnsorted() ? DEFAULT_SORT : parsed, page, size);
        List<ModerationQueueEntryResponse> items = result.items().stream().map(ModerationApiController::toEntry).toList();
        return PagedResponseBuilder.build(uriBuilder, page, size, result.totalCount(), items);
    }

    @Operation(operationId = "approveFeedback", summary = "Approve a flagged review",
            description = "Administrators and moderators only. Restores a flagged (HIDDEN) or confirmed (CONFIRMED_HIDDEN) review to visible. 403 for other callers, 404 when unknown, 409 when the review is not awaiting that decision.")
    @PostMapping("/feedback/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerKey")
    public void approveFeedback(@AuthenticationPrincipal @NonNull Long actorId, @PathVariable @NonNull Long id) {
        feedbackAccessService.approveFeedback(id, actorId);
    }

    @Operation(operationId = "confirmFeedbackViolation", summary = "Confirm a flagged review as a violation",
            description = "Administrators and moderators only. Keeps a flagged (HIDDEN) review hidden for good. Applies only to a flagged entry. 403 for other callers, 404 when unknown, 409 when the review is not flagged.")
    @PostMapping("/feedback/{id}/confirm-violation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerKey")
    public void confirmFeedbackViolation(@AuthenticationPrincipal @NonNull Long actorId, @PathVariable @NonNull Long id) {
        feedbackAccessService.confirmFeedbackViolation(id, actorId);
    }

    @Operation(operationId = "approveFeedbackReply", summary = "Approve a flagged reply",
            description = "Administrators and moderators only. Restores a flagged (HIDDEN) or confirmed (CONFIRMED_HIDDEN) reply to visible. 403 for other callers, 404 when unknown, 409 when the reply is not awaiting that decision.")
    @PostMapping("/replies/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerKey")
    public void approveFeedbackReply(@AuthenticationPrincipal @NonNull Long actorId, @PathVariable @NonNull Long id) {
        feedbackAccessService.approveComment(id, actorId);
    }

    @Operation(operationId = "confirmFeedbackReplyViolation", summary = "Confirm a flagged reply as a violation",
            description = "Administrators and moderators only. Keeps a flagged (HIDDEN) reply hidden for good. Applies only to a flagged entry. 403 for other callers, 404 when unknown, 409 when the reply is not flagged.")
    @PostMapping("/replies/{id}/confirm-violation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerKey")
    public void confirmFeedbackReplyViolation(@AuthenticationPrincipal @NonNull Long actorId, @PathVariable @NonNull Long id) {
        feedbackAccessService.confirmCommentViolation(id, actorId);
    }

    private static ModerationQueueEntryResponse toEntry(ModerationQueueItemDto item) {
        return new ModerationQueueEntryResponse(item.getType(), item.getId(), item.getEntityId(), item.getContentText(), item.getRating(),
                item.getModerationReason(), item.getAuthorId(), item.getAuthorName(), item.getCreatedAt(), item.getModerationStatus());
    }

    /** One flagged review (type FEEDBACK) or reply (type COMMENT) in the moderation queue; profileId is the reviewed provider profile. */
    public record ModerationQueueEntryResponse(ModerationQueueItemType type, Long id, Long profileId, String text, Integer rating,
                                               String reason, Long authorId, String authorName, Instant createdAt,
                                               FeedbackModerationStatus status) {
    }
}
