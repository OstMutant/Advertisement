package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.promotion.dto.PromotionPlacementDto;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.dto.ProviderProfileScope;
import org.ost.platform.providerprofile.spi.ProviderProfilePort;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Shared read-only {@link ProviderProfilePort} lookups, reused by every UI and REST adapter. */
@Service
@RequiredArgsConstructor
public class ProviderProfileReadService {

    private final ComponentFactory<ProviderProfilePort> providerProfilePortFactory;
    private final PromotionCatalogService promotionCatalogService;

    /** One page of the catalog without promotion marks, for callers that never show them. */
    public List<ProviderProfileDto> getFilteredWithoutPromotion(@NonNull ProviderProfileFilterDto filter, int page, int size, @NonNull Sort sort) {
        ProviderProfileScope scope = promotionCatalogService.scopeFor(filter, sort);
        return providerProfilePortFactory.findIfAvailable()
                .map(p -> p.getFiltered(filter, page, size, promotionCatalogService.withoutPromoted(sort), scope))
                .orElse(List.of());
    }

    /** One page of the catalog, already marked with its promotion; the placements are read once for both the scope and the marks. */
    public List<ProviderProfileDto> getFiltered(@NonNull ProviderProfileFilterDto filter, int page, int size, @NonNull Sort sort) {
        List<PromotionPlacementDto> placements = promotionCatalogService.promotedPlacements();
        ProviderProfileScope scope = promotionCatalogService.scopeFor(filter, sort, placements);
        return providerProfilePortFactory.findIfAvailable()
                .map(p -> p.getFiltered(filter, page, size, promotionCatalogService.withoutPromoted(sort), scope))
                .map(profiles -> promotionCatalogService.withPromotion(profiles, placements))
                .orElse(List.of());
    }

    public int count(@NonNull ProviderProfileFilterDto filter) {
        ProviderProfileScope scope = promotionCatalogService.scopeFor(filter, Sort.unsorted());
        return providerProfilePortFactory.findIfAvailable().map(p -> p.count(filter, scope)).orElse(0);
    }

    public Optional<ProviderProfileDto> findById(@NonNull Long id) {
        return providerProfilePortFactory.findIfAvailable().flatMap(p -> p.findById(id));
    }

    public Optional<ProviderProfileDto> findByActorId(@NonNull Long actorId) {
        return providerProfilePortFactory.findIfAvailable().flatMap(p -> p.findByActorId(actorId));
    }

    public boolean isAvailable() {
        return providerProfilePortFactory.findIfAvailable().isPresent();
    }
}
