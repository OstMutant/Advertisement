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
import org.ost.advertisement.ui.views.components.query.filter.meta.FilterFieldMeta;
import org.ost.advertisement.ui.views.components.query.filter.meta.ValidationPredicates;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AdvertisementFilterMeta {

	public static final FilterFieldMeta<String, AdvertisementFilterDto, String> TITLE =
		FilterFieldMeta.of(title, AdvertisementFilterDto::getTitle,
			(dto, v) -> dto.setTitle(v == null || v.isBlank() ? null : v));

	private static final BiPredicate<ValidationService<AdvertisementFilterDto>, AdvertisementFilterDto> createdValid =
		ValidationPredicates.range(createdAtStart, createdAtEnd);

	public static final FilterFieldMeta<LocalDate, AdvertisementFilterDto, Instant> CREATED_AT_START =
		FilterFieldMeta.of(createdAtStart, AdvertisementFilterDto::getCreatedAtStart,
			(dto, v) -> dto.setCreatedAtStart(toInstant(v)), createdValid);

	public static final FilterFieldMeta<LocalDate, AdvertisementFilterDto, Instant> CREATED_AT_END =
		FilterFieldMeta.of(createdAtEnd, AdvertisementFilterDto::getCreatedAtEnd,
			(dto, v) -> dto.setCreatedAtEnd(toInstant(v)), createdValid);

	private static final BiPredicate<ValidationService<AdvertisementFilterDto>, AdvertisementFilterDto> updatedValid =
		ValidationPredicates.range(updatedAtStart, updatedAtEnd);

	public static final FilterFieldMeta<LocalDate, AdvertisementFilterDto, Instant> UPDATED_AT_START =
		FilterFieldMeta.of(updatedAtStart, AdvertisementFilterDto::getUpdatedAtStart,
			(dto, v) -> dto.setUpdatedAtStart(toInstant(v)), updatedValid);

	public static final FilterFieldMeta<LocalDate, AdvertisementFilterDto, Instant> UPDATED_AT_END =
		FilterFieldMeta.of(updatedAtEnd, AdvertisementFilterDto::getUpdatedAtEnd,
			(dto, v) -> dto.setUpdatedAtEnd(toInstant(v)), updatedValid);
}
