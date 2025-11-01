package org.ost.advertisement.meta.filter;

import static org.ost.advertisement.ui.utils.TimeZoneUtil.toInstant;

import java.time.Instant;
import java.time.LocalDate;
import java.util.function.BiPredicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.services.ValidationService;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AdvertisementFilterMeta {

	private static final String TITLE_NAME = "title";
	private static final String CREATED_AT_START_NAME = "createdAtStart";
	private static final String CREATED_AT_END_NAME = "createdAtEnd";
	private static final String UPDATED_AT_START_NAME = "updatedAtStart";
	private static final String UPDATED_AT_END_NAME = "updatedAtEnd";

	public static final FilterField<String, AdvertisementFilterDto, String> TITLE =
		FilterField.of(TITLE_NAME, AdvertisementFilterDto::getTitle, AdvertisementFilterDto::setTitle);

	private static final BiPredicate<ValidationService<AdvertisementFilterDto>, AdvertisementFilterDto> createdValid =
		ValidationPredicates.range(CREATED_AT_START_NAME, CREATED_AT_END_NAME);

	public static final FilterField<LocalDate, AdvertisementFilterDto, Instant> CREATED_AT_START =
		FilterField.of(CREATED_AT_START_NAME, AdvertisementFilterDto::getCreatedAtStart,
			(dto, v) -> dto.setCreatedAtStart(toInstant(v)), createdValid);

	public static final FilterField<LocalDate, AdvertisementFilterDto, Instant> CREATED_AT_END =
		FilterField.of(CREATED_AT_END_NAME, AdvertisementFilterDto::getCreatedAtEnd,
			(dto, v) -> dto.setCreatedAtEnd(toInstant(v)), createdValid);

	private static final BiPredicate<ValidationService<AdvertisementFilterDto>, AdvertisementFilterDto> updatedValid =
		ValidationPredicates.range(UPDATED_AT_START_NAME, UPDATED_AT_END_NAME);

	public static final FilterField<LocalDate, AdvertisementFilterDto, Instant> UPDATED_AT_START =
		FilterField.of(UPDATED_AT_START_NAME, AdvertisementFilterDto::getUpdatedAtStart,
			(dto, v) -> dto.setUpdatedAtStart(toInstant(v)), updatedValid);

	public static final FilterField<LocalDate, AdvertisementFilterDto, Instant> UPDATED_AT_END =
		FilterField.of(UPDATED_AT_END_NAME, AdvertisementFilterDto::getUpdatedAtEnd,
			(dto, v) -> dto.setUpdatedAtEnd(toInstant(v)), updatedValid);

}
