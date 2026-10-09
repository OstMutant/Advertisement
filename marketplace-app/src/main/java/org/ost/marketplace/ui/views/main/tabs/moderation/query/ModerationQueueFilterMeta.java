package org.ost.marketplace.ui.views.main.tabs.moderation.query;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.marketplace.ui.query.filter.FilterFieldMeta;
import org.ost.marketplace.ui.query.filter.ValidationPredicates;
import org.ost.marketplace.ui.query.filter.ValidationService;
import org.ost.orchestrator.dto.ModerationQueueFilterDto;
import org.ost.orchestrator.dto.ModerationQueueItemType;
import org.ost.platform.feedback.model.FeedbackModerationStatus;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.function.BiPredicate;

import static org.ost.orchestrator.dto.ModerationQueueFilterDto.Fields.*;
import static org.ost.marketplace.ui.views.utils.SupportUtil.nullIfBlank;
import static org.ost.marketplace.ui.query.utils.TimeZoneUtil.toInstant;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ModerationQueueFilterMeta {

    public static final FilterFieldMeta<Set<ModerationQueueItemType>, ModerationQueueFilterDto, Set<ModerationQueueItemType>> TYPES =
            FilterFieldMeta.of(types, ModerationQueueFilterDto::getTypes,
                    (dto, v) -> dto.setTypes(v == null || v.isEmpty() ? null : v));

    private static final BiPredicate<ValidationService<ModerationQueueFilterDto>, ModerationQueueFilterDto> createdValid =
            ValidationPredicates.range(createdAtStart, createdAtEnd);

    public static final FilterFieldMeta<LocalDateTime, ModerationQueueFilterDto, Instant> CREATED_AT_START =
            FilterFieldMeta.of(createdAtStart, ModerationQueueFilterDto::getCreatedAtStart,
                    (dto, v) -> dto.setCreatedAtStart(toInstant(v)), createdValid);

    public static final FilterFieldMeta<LocalDateTime, ModerationQueueFilterDto, Instant> CREATED_AT_END =
            FilterFieldMeta.of(createdAtEnd, ModerationQueueFilterDto::getCreatedAtEnd,
                    (dto, v) -> dto.setCreatedAtEnd(toInstant(v)), createdValid);

    public static final FilterFieldMeta<String, ModerationQueueFilterDto, String> AUTHOR_NAME =
            FilterFieldMeta.of(authorName, ModerationQueueFilterDto::getAuthorName,
                    (dto, v) -> dto.setAuthorName(nullIfBlank(v)));

    public static final FilterFieldMeta<String, ModerationQueueFilterDto, String> REASON_TEXT =
            FilterFieldMeta.of(reasonText, ModerationQueueFilterDto::getReasonText,
                    (dto, v) -> dto.setReasonText(nullIfBlank(v)));

    public static final FilterFieldMeta<Set<FeedbackModerationStatus>, ModerationQueueFilterDto, Set<FeedbackModerationStatus>> STATUSES =
            FilterFieldMeta.of(statuses, ModerationQueueFilterDto::getStatuses,
                    (dto, v) -> dto.setStatuses(v == null || v.isEmpty() ? null : v));
}
