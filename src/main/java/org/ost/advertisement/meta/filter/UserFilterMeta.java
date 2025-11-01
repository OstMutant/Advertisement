package org.ost.advertisement.meta.filter;

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

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserFilterMeta {

	private static final String START_ID_NAME = "startId";
	private static final String END_ID_NAME = "endId";
	private static final String NAME_NAME = "name";
	private static final String EMAIL_NAME = "email";
	private static final String ROLE_NAME = "role";
	private static final String CREATED_AT_START_NAME = "createdAtStart";
	private static final String CREATED_AT_END_NAME = "createdAtEnd";
	private static final String UPDATED_AT_START_NAME = "updatedAtStart";
	private static final String UPDATED_AT_END_NAME = "updatedAtEnd";

	private static final BiPredicate<ValidationService<UserFilterDto>, UserFilterDto> idValid =
		ValidationPredicates.range(START_ID_NAME, END_ID_NAME);

	private static final BiPredicate<ValidationService<UserFilterDto>, UserFilterDto> createdValid =
		ValidationPredicates.range(CREATED_AT_START_NAME, CREATED_AT_END_NAME);

	private static final BiPredicate<ValidationService<UserFilterDto>, UserFilterDto> updatedValid =
		ValidationPredicates.range(UPDATED_AT_START_NAME, UPDATED_AT_END_NAME);

	public static final FilterField<Double, UserFilterDto, Long> ID_MIN =
		FilterField.of(START_ID_NAME, UserFilterDto::getStartId, (dto, v) -> dto.setStartId(toLong(v)), idValid);

	public static final FilterField<Double, UserFilterDto, Long> ID_MAX =
		FilterField.of(END_ID_NAME, UserFilterDto::getEndId, (dto, v) -> dto.setEndId(toLong(v)), idValid);

	public static final FilterField<String, UserFilterDto, String> NAME =
		FilterField.of(NAME_NAME, UserFilterDto::getName,
			(dto, v) -> dto.setName(v == null || v.isBlank() ? null : v));

	public static final FilterField<String, UserFilterDto, String> EMAIL =
		FilterField.of(EMAIL_NAME, UserFilterDto::getEmail,
			(dto, v) -> dto.setEmail(v == null || v.isBlank() ? null : v));

	public static final FilterField<Role, UserFilterDto, Role> ROLE =
		FilterField.of(ROLE_NAME, UserFilterDto::getRole, UserFilterDto::setRole);

	public static final FilterField<LocalDate, UserFilterDto, Instant> CREATED_AT_START =
		FilterField.of(CREATED_AT_START_NAME, UserFilterDto::getCreatedAtStart,
			(dto, v) -> dto.setCreatedAtStart(toInstant(v)), createdValid);

	public static final FilterField<LocalDate, UserFilterDto, Instant> CREATED_AT_END =
		FilterField.of(CREATED_AT_END_NAME, UserFilterDto::getCreatedAtEnd,
			(dto, v) -> dto.setCreatedAtEnd(toInstant(v)), createdValid);

	public static final FilterField<LocalDate, UserFilterDto, Instant> UPDATED_AT_START =
		FilterField.of(UPDATED_AT_START_NAME, UserFilterDto::getUpdatedAtStart,
			(dto, v) -> dto.setUpdatedAtStart(toInstant(v)), updatedValid);

	public static final FilterField<LocalDate, UserFilterDto, Instant> UPDATED_AT_END =
		FilterField.of(UPDATED_AT_END_NAME, UserFilterDto::getUpdatedAtEnd,
			(dto, v) -> dto.setUpdatedAtEnd(toInstant(v)), updatedValid);
}


