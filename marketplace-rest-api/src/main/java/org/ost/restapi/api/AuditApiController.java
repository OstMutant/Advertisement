package org.ost.restapi.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.services.AuditQueryService;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.audit.dto.AuditTimelineFilterDto;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.ChangeEntry;
import org.ost.platform.core.model.EntityType;
import org.ost.restapi.api.paging.PagedResponseBuilder;
import org.ost.restapi.api.paging.SortQueryParser;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** The global audit timeline, readable by administrators and moderators only; per-resource history lives on each resource's own controller. */
@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditApiController {

    private static final Set<String> SORTABLE_FIELDS = Set.of(AuditTimelineItemDto.Fields.createdAt);

    private final AuditQueryService auditQueryService;
    private final UserProfileService userProfileService;

    @Operation(operationId = "getAuditTimeline", summary = "List the global audit timeline",
            description = "Admin/moderator only. Newest first unless sort=createdAt,asc is given. size is not caller-supplied -- it comes from the caller's saved timeline page size (PATCH /api/users/me/settings).")
    @GetMapping("/timeline")
    @SecurityRequirement(name = "bearerKey")
    public ResponseEntity<List<TimelineEntryResponse>> timeline(@AuthenticationPrincipal @NonNull Long actorId,
            @ModelAttribute @Valid AuditTimelineFilterDto filter, @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(required = false) String sort, UriComponentsBuilder uriBuilder) {
        int size = userProfileService.resolveTimelinePageSize(actorId);
        Sort parsed = SortQueryParser.parse(sort, SORTABLE_FIELDS);
        Sort sortObj = parsed.isSorted() ? parsed : Sort.by(Sort.Direction.DESC, AuditTimelineItemDto.Fields.createdAt);
        List<TimelineEntryResponse> items = auditQueryService.getTimelinePage(actorId, filter, sortObj, page, size).stream()
                .map(item -> new TimelineEntryResponse(item.snapshotId(), item.entityRef().entityType(),
                        item.entityRef().entityId(), item.actionType(), item.createdAt(), item.changedByActorId(), item.changes()))
                .toList();
        return PagedResponseBuilder.build(uriBuilder, page, size, auditQueryService.countTimeline(actorId, filter), items);
    }

    /** One recorded change of any entity in the global timeline. */
    public record TimelineEntryResponse(Long snapshotId, EntityType entityType, Long entityId, ActionType actionType,
                                        Instant createdAt, Long actorId, List<ChangeEntry> changes) {
    }
}
