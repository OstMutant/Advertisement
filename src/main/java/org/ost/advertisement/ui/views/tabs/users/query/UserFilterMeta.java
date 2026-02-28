package org.ost.advertisement.ui.views.tabs.users.query;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.services.ValidationService;
import org.ost.advertisement.ui.views.components.query.processor.FilterFieldMeta;
import org.ost.advertisement.ui.views.components.query.processor.ValidationPredicates;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.function.BiPredicate;

import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.*;
import static org.ost.advertisement.ui.views.utils.SupportUtil.nullIfBlank;
import static org.ost.advertisement.ui.views.utils.SupportUtil.toLong;
import static org.ost.advertisement.ui.views.utils.TimeZoneUtil.toInstant;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserFilterMeta {

    private static final BiPredicate<ValidationService<UserFilterDto>, UserFilterDto> idValid =
            ValidationPredicates.range(startId, endId);

    private static final BiPredicate<ValidationService<UserFilterDto>, UserFilterDto> createdValid =
            ValidationPredicates.range(createdAtStart, createdAtEnd);

    private static final BiPredicate<ValidationService<UserFilterDto>, UserFilterDto> updatedValid =
            ValidationPredicates.range(updatedAtStart, updatedAtEnd);

    public static final FilterFieldMeta<Double, UserFilterDto, Long> ID_MIN =
            FilterFieldMeta.of(startId, UserFilterDto::getStartId, (dto, v) -> dto.setStartId(toLong(v)), idValid);

    public static final FilterFieldMeta<Double, UserFilterDto, Long> ID_MAX =
            FilterFieldMeta.of(endId, UserFilterDto::getEndId, (dto, v) -> dto.setEndId(toLong(v)), idValid);

    public static final FilterFieldMeta<String, UserFilterDto, String> NAME =
            FilterFieldMeta.of(name, UserFilterDto::getName, (dto, v) -> dto.setName(nullIfBlank(v)));

    public static final FilterFieldMeta<String, UserFilterDto, String> EMAIL =
            FilterFieldMeta.of(email, UserFilterDto::getEmail, (dto, v) -> dto.setEmail(nullIfBlank(v)));

    public static final FilterFieldMeta<Set<Role>, UserFilterDto, Set<Role>> ROLES =
            FilterFieldMeta.of(roles, UserFilterDto::getRoles,
                    (dto, v) -> dto.setRoles(v == null || v.isEmpty() ? null : v));

    public static final FilterFieldMeta<LocalDateTime, UserFilterDto, Instant> CREATED_AT_START =
            FilterFieldMeta.of(createdAtStart, UserFilterDto::getCreatedAtStart,
                    (dto, v) -> dto.setCreatedAtStart(toInstant(v)), createdValid);

    public static final FilterFieldMeta<LocalDateTime, UserFilterDto, Instant> CREATED_AT_END =
            FilterFieldMeta.of(createdAtEnd, UserFilterDto::getCreatedAtEnd,
                    (dto, v) -> dto.setCreatedAtEnd(toInstant(v)), createdValid);

    public static final FilterFieldMeta<LocalDateTime, UserFilterDto, Instant> UPDATED_AT_START =
            FilterFieldMeta.of(updatedAtStart, UserFilterDto::getUpdatedAtStart,
                    (dto, v) -> dto.setUpdatedAtStart(toInstant(v)), updatedValid);

    public static final FilterFieldMeta<LocalDateTime, UserFilterDto, Instant> UPDATED_AT_END =
            FilterFieldMeta.of(updatedAtEnd, UserFilterDto::getUpdatedAtEnd,
                    (dto, v) -> dto.setUpdatedAtEnd(toInstant(v)), updatedValid);
}