package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.promotion.dto.PromotionPlacementDto;
import org.ost.platform.promotion.spi.PromotionPlacementPort;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.dto.ProviderProfileScope;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Turns the promotion filter and sort of a provider profile list into the scope the list query takes, and marks the profiles of a page with their promotion. */
@Service
@RequiredArgsConstructor
public class PromotionCatalogService {

    private final ComponentFactory<PromotionPlacementPort> promotionPlacementPortFactory;

    public List<PromotionPlacementDto> activePlacements() {
        return promotionPlacementPortFactory.findIfAvailable()
                .map(port -> port.findActive(EntityType.PROVIDER_PROFILE))
                .orElse(List.of());
    }

    /** Like the variant taking placements, but reads them only when the filter or sort actually needs them. */
    public ProviderProfileScope scopeFor(@NonNull ProviderProfileFilterDto filter, @NonNull Sort sort) {
        boolean needed = Boolean.TRUE.equals(filter.getPromoted()) || sort.getOrderFor(ProviderProfileDto.Fields.promoted) != null;
        return needed ? scopeFor(filter, sort, activePlacements()) : ProviderProfileScope.ALL;
    }

    public ProviderProfileScope scopeFor(@NonNull ProviderProfileFilterDto filter, @NonNull Sort sort,
                                         @NonNull List<PromotionPlacementDto> placements) {
        boolean onlyPromoted = Boolean.TRUE.equals(filter.getPromoted());
        Sort.Order promotedOrder = sort.getOrderFor(ProviderProfileDto.Fields.promoted);
        if (!onlyPromoted && promotedOrder == null) {
            return ProviderProfileScope.ALL;
        }
        Set<Long> onlyIds = onlyPromoted
                ? placements.stream().map(PromotionPlacementDto::entityId).collect(Collectors.toSet())
                : null;
        List<Set<Long>> groups = promotedOrder == null ? List.of() : groupsByRank(placements);
        return new ProviderProfileScope(onlyIds, groups, promotedOrder != null && promotedOrder.isAscending());
    }

    public Sort withoutPromoted(@NonNull Sort sort) {
        return Sort.by(sort.stream().filter(order -> !order.getProperty().equals(ProviderProfileDto.Fields.promoted)).toList());
    }

    /** Marks each profile that has a placement with the highest-ranked one; the end date is filled in only when {@code withDetails}. */
    public List<ProviderProfileDto> withPromotion(@NonNull List<ProviderProfileDto> profiles,
                                                  @NonNull List<PromotionPlacementDto> placements, boolean withDetails) {
        Map<Long, PromotionPlacementDto> byEntity = placements.stream()
                .collect(Collectors.toMap(PromotionPlacementDto::entityId, Function.identity(),
                        BinaryOperator.minBy(Comparator.comparingInt((PromotionPlacementDto placement) -> placement.reason().rank()))));
        return profiles.stream()
                .map(profile -> Optional.ofNullable(byEntity.get(profile.getId()))
                        .map(promotion -> profile.toBuilder()
                                .promoted(true)
                                .promotionReason(promotion.reason())
                                .promotionValidUntil(withDetails ? promotion.validUntil() : null)
                                .build())
                        .orElse(profile))
                .toList();
    }

    private static List<Set<Long>> groupsByRank(List<PromotionPlacementDto> placements) {
        return placements.stream()
                .collect(Collectors.groupingBy(placement -> placement.reason().rank(), TreeMap::new,
                        Collectors.mapping(PromotionPlacementDto::entityId, Collectors.toSet())))
                .values().stream().toList();
    }
}
