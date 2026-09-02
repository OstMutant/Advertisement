package org.ost.marketplace.ui.views.main.tabs.providers.query;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.marketplace.ui.query.filter.FilterFieldMeta;
import org.ost.marketplace.ui.query.filter.ValidationService;
import org.ost.marketplace.ui.query.filter.ValidationPredicates;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.ost.platform.taxon.dto.TaxonDto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import static org.ost.platform.providerprofile.dto.ProviderProfileFilterDto.Fields.*;
import static org.ost.marketplace.ui.query.utils.TimeZoneUtil.toInstant;

/** Typed {@link org.ost.marketplace.ui.query.filter.FilterFieldMeta} constants for the Providers catalog's query bar, referencing {@link ProviderProfileFilterDto}'s {@code Fields.*} constants. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProviderProfileFilterMeta {

    public static final FilterFieldMeta<Set<ProviderKind>, ProviderProfileFilterDto, Set<ProviderKind>> KINDS =
            FilterFieldMeta.of(kinds, ProviderProfileFilterDto::getKinds,
                    (dto, v) -> dto.setKinds(v == null || v.isEmpty() ? null : v));

    private static final BiPredicate<ValidationService<ProviderProfileFilterDto>, ProviderProfileFilterDto> createdValid =
            ValidationPredicates.range(createdAtStart, createdAtEnd);

    public static final FilterFieldMeta<LocalDateTime, ProviderProfileFilterDto, Instant> CREATED_AT_START =
            FilterFieldMeta.of(createdAtStart, ProviderProfileFilterDto::getCreatedAtStart,
                    (dto, v) -> dto.setCreatedAtStart(toInstant(v)), createdValid);

    public static final FilterFieldMeta<LocalDateTime, ProviderProfileFilterDto, Instant> CREATED_AT_END =
            FilterFieldMeta.of(createdAtEnd, ProviderProfileFilterDto::getCreatedAtEnd,
                    (dto, v) -> dto.setCreatedAtEnd(toInstant(v)), createdValid);

    private static final BiPredicate<ValidationService<ProviderProfileFilterDto>, ProviderProfileFilterDto> updatedValid =
            ValidationPredicates.range(updatedAtStart, updatedAtEnd);

    public static final FilterFieldMeta<LocalDateTime, ProviderProfileFilterDto, Instant> UPDATED_AT_START =
            FilterFieldMeta.of(updatedAtStart, ProviderProfileFilterDto::getUpdatedAtStart,
                    (dto, v) -> dto.setUpdatedAtStart(toInstant(v)), updatedValid);

    public static final FilterFieldMeta<LocalDateTime, ProviderProfileFilterDto, Instant> UPDATED_AT_END =
            FilterFieldMeta.of(updatedAtEnd, ProviderProfileFilterDto::getUpdatedAtEnd,
                    (dto, v) -> dto.setUpdatedAtEnd(toInstant(v)), updatedValid);

    public static final FilterFieldMeta<Set<TaxonDto>, ProviderProfileFilterDto, Set<Long>> CATEGORY_IDS =
            FilterFieldMeta.of(categoryIds, ProviderProfileFilterDto::getCategoryIds,
                    (dto, v) -> dto.setCategoryIds(v == null || v.isEmpty() ? null
                            : v.stream().map(TaxonDto::getId).collect(Collectors.toSet())));

    public static final FilterFieldMeta<TaxonDto, ProviderProfileFilterDto, Long> CITY_TAXON_ID =
            FilterFieldMeta.of(cityTaxonId, ProviderProfileFilterDto::getCityTaxonId,
                    (dto, v) -> dto.setCityTaxonId(v == null ? null : v.getId()));
}
