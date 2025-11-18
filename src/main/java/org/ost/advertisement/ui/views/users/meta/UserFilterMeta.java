package org.ost.advertisement.ui.views.users.meta;

import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.createdAtEnd;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.createdAtStart;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.email;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.endId;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.name;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.role;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.startId;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.updatedAtEnd;
import static org.ost.advertisement.dto.filter.UserFilterDto.Fields.updatedAtStart;
import static org.ost.advertisement.ui.utils.SupportUtil.toLong;
import static org.ost.advertisement.ui.utils.TimeZoneUtil.toInstant;

import java.time.Instant;
import java.time.LocalDate;
import java.util.function.BiPredicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.services.ValidationService;
import org.ost.advertisement.ui.views.components.filters.meta.FilterField;
import org.ost.advertisement.ui.views.components.filters.meta.ValidationPredicates;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserFilterMeta {

	private static final BiPredicate<ValidationService<UserFilterDto>, UserFilterDto> idValid =
		ValidationPredicates.range(startId, endId);

	private static final BiPredicate<ValidationService<UserFilterDto>, UserFilterDto> createdValid =
		ValidationPredicates.range(createdAtStart, createdAtEnd);

	private static final BiPredicate<ValidationService<UserFilterDto>, UserFilterDto> updatedValid =
		ValidationPredicates.range(updatedAtStart, updatedAtEnd);

	public static final FilterField<Double, UserFilterDto, Long> ID_MIN =
		FilterField.of(startId, UserFilterDto::getStartId, (dto, v) -> dto.setStartId(toLong(v)), idValid);

	public static final FilterField<Double, UserFilterDto, Long> ID_MAX =
		FilterField.of(endId, UserFilterDto::getEndId, (dto, v) -> dto.setEndId(toLong(v)), idValid);

	public static final FilterField<String, UserFilterDto, String> NAME =
		FilterField.of(name, UserFilterDto::getName, (dto, v) -> dto.setName(v == null || v.isBlank() ? null : v));

	public static final FilterField<String, UserFilterDto, String> EMAIL =
		FilterField.of(email, UserFilterDto::getEmail, (dto, v) -> dto.setEmail(v == null || v.isBlank() ? null : v));

	public static final FilterField<Role, UserFilterDto, Role> ROLE =
		FilterField.of(role, UserFilterDto::getRole, UserFilterDto::setRole);

	public static final FilterField<LocalDate, UserFilterDto, Instant> CREATED_AT_START =
		FilterField.of(createdAtStart, UserFilterDto::getCreatedAtStart,
			(dto, v) -> dto.setCreatedAtStart(toInstant(v)), createdValid);

	public static final FilterField<LocalDate, UserFilterDto, Instant> CREATED_AT_END =
		FilterField.of(createdAtEnd, UserFilterDto::getCreatedAtEnd,
			(dto, v) -> dto.setCreatedAtEnd(toInstant(v)), createdValid);

	public static final FilterField<LocalDate, UserFilterDto, Instant> UPDATED_AT_START =
		FilterField.of(updatedAtStart, UserFilterDto::getUpdatedAtStart,
			(dto, v) -> dto.setUpdatedAtStart(toInstant(v)), updatedValid);

	public static final FilterField<LocalDate, UserFilterDto, Instant> UPDATED_AT_END =
		FilterField.of(updatedAtEnd, UserFilterDto::getUpdatedAtEnd,
			(dto, v) -> dto.setUpdatedAtEnd(toInstant(v)), updatedValid);
}
