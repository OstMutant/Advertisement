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
import org.ost.platform.promotion.spi.PromotionPlacementPort;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.dto.ProviderProfileScope;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for {@link PromotionCatalogService}: the scope of a list request, the sort without the promotion order and the badge data of a page. */
@ExtendWith(MockitoExtension.class)
class PromotionCatalogServiceTest {

    @Mock private ComponentFactory<PromotionPlacementPort> portFactory;
    @Mock private PromotionPlacementPort port;

    private PromotionCatalogService service;
    private final ProviderProfileFilterDto noFilter = ProviderProfileFilterDto.empty();

    @BeforeEach
    void setUp() {
        service = new PromotionCatalogService(portFactory);
    }

    private static PromotionPlacementDto placement(long id) {
        return new PromotionPlacementDto(EntityType.PROVIDER_PROFILE, id, PromotionReason.TOP_RATED);
    }

    private void stubPlacements(PromotionPlacementDto... placements) {
        when(portFactory.findIfAvailable()).thenReturn(Optional.of(port));
        when(port.findPromoted(EntityType.PROVIDER_PROFILE)).thenReturn(List.of(placements));
    }

    @Test
    void scopeFor_noPromotedFilterAndNoPromotedSort_isAllWithoutAnyQuery() {
        ProviderProfileScope scope = service.scopeFor(noFilter, Sort.by(Sort.Order.desc("updatedAt")));

        assertThat(scope).isEqualTo(ProviderProfileScope.ALL);
        verifyNoInteractions(portFactory);
    }

    @Test
    void scopeFor_promotedSortDescending_pinsThePromotedIdsFirst() {
        stubPlacements(placement(1L), placement(2L));

        ProviderProfileScope scope = service.scopeFor(noFilter, Sort.by(Sort.Order.desc(ProviderProfileDto.Fields.promoted)));

        assertThat(scope.onlyIds()).isNull();
        assertThat(scope.pinnedGroups()).containsExactly(Set.of(1L, 2L));
        assertThat(scope.pinnedAfterRest()).isFalse();
    }

    @Test
    void scopeFor_promotedSortAscending_pinsThemAfterTheRest() {
        stubPlacements(placement(1L));

        ProviderProfileScope scope = service.scopeFor(noFilter, Sort.by(Sort.Order.asc(ProviderProfileDto.Fields.promoted)));

        assertThat(scope.pinnedAfterRest()).isTrue();
    }

    @Test
    void scopeFor_promotedFilter_limitsTheListToThePromotedIds() {
        stubPlacements(placement(1L), placement(2L));

        ProviderProfileScope scope = service.scopeFor(ProviderProfileFilterDto.builder().promoted(true).build(), Sort.unsorted());

        assertThat(scope.onlyIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(scope.pinnedGroups()).isEmpty();
    }

    @Test
    void scopeFor_promotedFilterWithoutTheStarter_limitsTheListToNothing() {
        when(portFactory.findIfAvailable()).thenReturn(Optional.empty());

        ProviderProfileScope scope = service.scopeFor(ProviderProfileFilterDto.builder().promoted(true).build(), Sort.unsorted());

        assertThat(scope.onlyIds()).isEmpty();
    }

    @Test
    void withoutPromoted_dropsOnlyThePromotionOrder() {
        Sort sort = Sort.by(Sort.Order.desc(ProviderProfileDto.Fields.promoted), Sort.Order.desc("updatedAt"));

        assertThat(service.withoutPromoted(sort)).isEqualTo(Sort.by(Sort.Order.desc("updatedAt")));
    }

    @Test
    void promotedPlacements_starterAbsent_isEmpty() {
        when(portFactory.findIfAvailable()).thenReturn(Optional.empty());

        assertThat(service.promotedPlacements()).isEmpty();
    }

    @Test
    void withPromotion_promotedProfile_getsTheReasonAndTheFlag() {
        PromotionPlacementDto top = new PromotionPlacementDto(EntityType.PROVIDER_PROFILE, 1L, PromotionReason.TOP_RATED);

        List<ProviderProfileDto> marked = service.withPromotion(
                List.of(ProviderProfileDto.builder().id(1L).build(), ProviderProfileDto.builder().id(2L).build()), List.of(top));

        assertThat(marked.get(0).isPromoted()).isTrue();
        assertThat(marked.get(0).getPromotionReason()).isEqualTo(PromotionReason.TOP_RATED);
        assertThat(marked.get(1).isPromoted()).isFalse();
        assertThat(marked.get(1).getPromotionReason()).isNull();
    }

    @Test
    void scopeFor_withGivenPlacements_readsNothingFromThePort() {
        ProviderProfileScope scope = service.scopeFor(noFilter, Sort.by(Sort.Order.desc(ProviderProfileDto.Fields.promoted)), List.of(placement(1L)));

        assertThat(scope.pinnedGroups()).containsExactly(Set.of(1L));
        verifyNoInteractions(portFactory);
    }
}
