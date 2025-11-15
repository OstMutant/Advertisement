package org.ost.advertisement.ui.views.advertisements.meta;

import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.createdAtEnd;
import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.createdAtStart;
import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.title;
import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.updatedAtEnd;
import static org.ost.advertisement.dto.filter.AdvertisementFilterDto.Fields.updatedAtStart;
import static org.ost.advertisement.ui.utils.TimeZoneUtil.toInstant;

import java.time.Instant;
import java.time.LocalDate;
import java.util.function.BiPredicate;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.services.ValidationService;
import org.ost.advertisement.ui.views.components.filters.meta.FilterField;
import org.ost.advertisement.ui.views.components.filters.meta.ValidationPredicates;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AdvertisementFilterMeta {

	public static final FilterField<String, AdvertisementFilterDto, String> TITLE =
		FilterField.of(title, AdvertisementFilterDto::getTitle, AdvertisementFilterDto::setTitle);

	private static final BiPredicate<ValidationService<AdvertisementFilterDto>, AdvertisementFilterDto> createdValid =
		ValidationPredicates.range(createdAtStart, createdAtEnd);

	public static final FilterField<LocalDate, AdvertisementFilterDto, Instant> CREATED_AT_START =
		FilterField.of(createdAtStart, AdvertisementFilterDto::getCreatedAtStart,
			(dto, v) -> dto.setCreatedAtStart(toInstant(v)), createdValid);

	public static final FilterField<LocalDate, AdvertisementFilterDto, Instant> CREATED_AT_END =
		FilterField.of(createdAtEnd, AdvertisementFilterDto::getCreatedAtEnd,
			(dto, v) -> dto.setCreatedAtEnd(toInstant(v)), createdValid);

	private static final BiPredicate<ValidationService<AdvertisementFilterDto>, AdvertisementFilterDto> updatedValid =
		ValidationPredicates.range(updatedAtStart, updatedAtEnd);

	public static final FilterField<LocalDate, AdvertisementFilterDto, Instant> UPDATED_AT_START =
		FilterField.of(updatedAtStart, AdvertisementFilterDto::getUpdatedAtStart,
			(dto, v) -> dto.setUpdatedAtStart(toInstant(v)), updatedValid);

	public static final FilterField<LocalDate, AdvertisementFilterDto, Instant> UPDATED_AT_END =
		FilterField.of(updatedAtEnd, AdvertisementFilterDto::getUpdatedAtEnd,
			(dto, v) -> dto.setUpdatedAtEnd(toInstant(v)), updatedValid);
}
