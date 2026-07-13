package org.ost.marketplace.ui.views.main.tabs.advertisements.query;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.marketplace.ui.query.filter.ValidationService;
import org.ost.marketplace.ui.query.filter.FilterFieldMeta;
import org.ost.marketplace.ui.query.filter.ValidationPredicates;
import org.ost.platform.taxon.dto.TaxonDto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static org.ost.platform.advertisement.dto.AdvertisementFilterDto.Fields.*;
import static org.ost.marketplace.ui.views.utils.SupportUtil.nullIfBlank;
import static org.ost.marketplace.ui.query.utils.TimeZoneUtil.toInstant;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AdvertisementFilterMeta {

    public static final FilterFieldMeta<String, AdvertisementFilterDto, String> TITLE =
            FilterFieldMeta.of(title, AdvertisementFilterDto::getTitle,
                    (dto, v) -> dto.setTitle(nullIfBlank(v)));

    private static final BiPredicate<ValidationService<AdvertisementFilterDto>, AdvertisementFilterDto> createdValid =
            ValidationPredicates.range(createdAtStart, createdAtEnd);

    public static final FilterFieldMeta<LocalDateTime, AdvertisementFilterDto, Instant> CREATED_AT_START =
            FilterFieldMeta.of(createdAtStart, AdvertisementFilterDto::getCreatedAtStart,
                    (dto, v) -> dto.setCreatedAtStart(toInstant(v)), createdValid);

    public static final FilterFieldMeta<LocalDateTime, AdvertisementFilterDto, Instant> CREATED_AT_END =
            FilterFieldMeta.of(createdAtEnd, AdvertisementFilterDto::getCreatedAtEnd,
                    (dto, v) -> dto.setCreatedAtEnd(toInstant(v)), createdValid);

    private static final BiPredicate<ValidationService<AdvertisementFilterDto>, AdvertisementFilterDto> updatedValid =
            ValidationPredicates.range(updatedAtStart, updatedAtEnd);

    public static final FilterFieldMeta<LocalDateTime, AdvertisementFilterDto, Instant> UPDATED_AT_START =
            FilterFieldMeta.of(updatedAtStart, AdvertisementFilterDto::getUpdatedAtStart,
                    (dto, v) -> dto.setUpdatedAtStart(toInstant(v)), updatedValid);

    public static final FilterFieldMeta<LocalDateTime, AdvertisementFilterDto, Instant> UPDATED_AT_END =
            FilterFieldMeta.of(updatedAtEnd, AdvertisementFilterDto::getUpdatedAtEnd,
                    (dto, v) -> dto.setUpdatedAtEnd(toInstant(v)), updatedValid);

    public static final FilterFieldMeta<Set<TaxonDto>, AdvertisementFilterDto, Set<Long>> CATEGORY_IDS =
            FilterFieldMeta.of(categoryIds, AdvertisementFilterDto::getCategoryIds,
                    (dto, v) -> dto.setCategoryIds(v == null || v.isEmpty() ? null
                            : v.stream().map(TaxonDto::getId).collect(Collectors.toSet())));
}
