package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.promotion.dto.PromotionPlacementDto;
import org.ost.platform.promotion.model.PromotionReason;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.dto.ProviderProfileScope;
import org.ost.platform.providerprofile.spi.ProviderProfilePort;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for the list reads of {@link ProviderProfileReadService}: the promotion scope reaches the port and the promotion order never does. */
@ExtendWith(MockitoExtension.class)
class ProviderProfileReadServiceTest {

    @Mock private ComponentFactory<ProviderProfilePort> portFactory;
    @Mock private ProviderProfilePort port;
    @Mock private PromotionCatalogService promotionCatalogService;

    private ProviderProfileReadService service;
    private final ProviderProfileFilterDto filter = ProviderProfileFilterDto.empty();

    @BeforeEach
    void setUp() {
        service = new ProviderProfileReadService(portFactory, promotionCatalogService);
        when(portFactory.findIfAvailable()).thenReturn(Optional.of(port));
    }

    @Test
    void getFiltered_passesTheScopeAndTheSortWithoutThePromotionOrder() {
        Sort sort = Sort.by(Sort.Order.desc("promoted"), Sort.Order.desc("updatedAt"));
        Sort rest = Sort.by(Sort.Order.desc("updatedAt"));
        ProviderProfileScope scope = new ProviderProfileScope(null, List.of(Set.of(1L)), false);
        List<PromotionPlacementDto> placements = List.of(
                new PromotionPlacementDto(EntityType.PROVIDER_PROFILE, 1L, PromotionReason.TOP_RATED));
        when(promotionCatalogService.promotedPlacements()).thenReturn(placements);
        when(promotionCatalogService.scopeFor(filter, sort, placements)).thenReturn(scope);
        when(promotionCatalogService.withoutPromoted(sort)).thenReturn(rest);
        List<ProviderProfileDto> page = List.of(ProviderProfileDto.builder().id(1L).build());
        List<ProviderProfileDto> marked = List.of(ProviderProfileDto.builder().id(1L).promoted(true).build());
        when(port.getFiltered(filter, 0, 10, rest, scope)).thenReturn(page);
        when(promotionCatalogService.withPromotion(page, placements)).thenReturn(marked);

        assertThat(service.getFiltered(filter, 0, 10, sort)).isSameAs(marked);
        verify(promotionCatalogService).promotedPlacements();
    }

    @Test
    void getFilteredWithoutPromotion_readsNoPlacementsWhenTheRequestNeedsNone() {
        Sort sort = Sort.by(Sort.Order.desc("updatedAt"));
        when(promotionCatalogService.scopeFor(filter, sort)).thenReturn(ProviderProfileScope.ALL);
        when(promotionCatalogService.withoutPromoted(sort)).thenReturn(sort);
        List<ProviderProfileDto> page = List.of(ProviderProfileDto.builder().id(1L).build());
        when(port.getFiltered(filter, 0, 10, sort, ProviderProfileScope.ALL)).thenReturn(page);

        assertThat(service.getFilteredWithoutPromotion(filter, 0, 10, sort)).isSameAs(page);
        verify(promotionCatalogService, never()).promotedPlacements();
    }

    @Test
    void count_usesTheScopeOfTheFilterAlone() {
        ProviderProfileScope scope = new ProviderProfileScope(Set.of(1L, 2L), List.of(), false);
        when(promotionCatalogService.scopeFor(filter, Sort.unsorted())).thenReturn(scope);
        when(port.count(filter, scope)).thenReturn(2);

        assertThat(service.count(filter)).isEqualTo(2);
        verify(port).count(filter, scope);
    }
}
