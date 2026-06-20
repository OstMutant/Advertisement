package org.ost.marketplace.ui.views.main.tabs.timeline.query;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.platform.audit.dto.AuditTimelineFilterDto;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.user.dto.UserDto;
import org.ost.marketplace.ui.query.filter.FilterFieldMeta;
import org.ost.marketplace.ui.query.filter.ValidationPredicates;
import org.ost.marketplace.ui.query.filter.ValidationService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.function.BiPredicate;

import static org.ost.platform.audit.dto.AuditTimelineFilterDto.Fields.actorId;
import static org.ost.platform.audit.dto.AuditTimelineFilterDto.Fields.actionTypes;
import static org.ost.platform.audit.dto.AuditTimelineFilterDto.Fields.entityTypes;
import static org.ost.platform.audit.dto.AuditTimelineFilterDto.Fields.fromDate;
import static org.ost.platform.audit.dto.AuditTimelineFilterDto.Fields.toDate;
import static org.ost.marketplace.ui.query.utils.TimeZoneUtil.toInstant;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TimelineFilterMeta {

    private static final BiPredicate<ValidationService<AuditTimelineFilterDto>, AuditTimelineFilterDto> dateValid =
            ValidationPredicates.range(fromDate, toDate);

    public static final FilterFieldMeta<UserDto, AuditTimelineFilterDto, Long> ACTOR =
            FilterFieldMeta.of(actorId, AuditTimelineFilterDto::getActorId,
                    (dto, v) -> dto.setActorId(v != null ? v.id() : null));

    public static final FilterFieldMeta<Set<EntityType>, AuditTimelineFilterDto, Set<EntityType>> ENTITY_TYPES =
            FilterFieldMeta.of(entityTypes, AuditTimelineFilterDto::getEntityTypes,
                    (dto, v) -> dto.setEntityTypes(v == null || v.isEmpty() ? null : v));

    public static final FilterFieldMeta<Set<ActionType>, AuditTimelineFilterDto, Set<ActionType>> ACTION_TYPES =
            FilterFieldMeta.of(actionTypes, AuditTimelineFilterDto::getActionTypes,
                    (dto, v) -> dto.setActionTypes(v == null || v.isEmpty() ? null : v));

    public static final FilterFieldMeta<LocalDateTime, AuditTimelineFilterDto, Instant> FROM_DATE =
            FilterFieldMeta.of(fromDate, AuditTimelineFilterDto::getFromDate,
                    (dto, v) -> dto.setFromDate(toInstant(v)), dateValid);

    public static final FilterFieldMeta<LocalDateTime, AuditTimelineFilterDto, Instant> TO_DATE =
            FilterFieldMeta.of(toDate, AuditTimelineFilterDto::getToDate,
                    (dto, v) -> dto.setToDate(toInstant(v)), dateValid);
}
