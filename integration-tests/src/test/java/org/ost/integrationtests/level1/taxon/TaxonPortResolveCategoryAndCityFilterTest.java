package org.ost.integrationtests.level1.taxon;

import org.junit.jupiter.api.Test;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.spi.TaxonPort;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plain unit test for {@link TaxonPort#resolveCategoryAndCityFilter}, a default method composed
 * entirely from the interface's own {@link TaxonPort#findEntityIdsWithAnyTaxon} — no Spring
 * context, no DB. A Mockito mock built with {@code CALLS_REAL_METHODS} still executes the real
 * default method body (and the private helper it calls); only the one abstract method needs
 * stubbing, so no throw-stub overrides for the other 14 interface methods are needed.
 */
class TaxonPortResolveCategoryAndCityFilterTest {

    private static TaxonPort stubPort(Map<Set<Long>, Set<Long>> responsesByTaxonIds) {
        TaxonPort port = mock(TaxonPort.class, CALLS_REAL_METHODS);
        responsesByTaxonIds.forEach((taxonIds, entityIds) ->
                when(port.findEntityIdsWithAnyTaxon(EntityType.ADVERTISEMENT, taxonIds)).thenReturn(entityIds));
        return port;
    }

    @Test
    void resolve_neitherCategoryNorCityRequested_returnsEmpty() {
        TaxonPort port = stubPort(Map.of());

        Optional<Set<Long>> result = port.resolveCategoryAndCityFilter(EntityType.ADVERTISEMENT, null, null);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_onlyCategoryRequested_returnsCategoryMatches() {
        TaxonPort port = stubPort(Map.of(Set.of(1L, 2L), Set.of(100L, 200L)));

        Optional<Set<Long>> result = port.resolveCategoryAndCityFilter(EntityType.ADVERTISEMENT, Set.of(1L, 2L), null);

        assertThat(result).contains(Set.of(100L, 200L));
    }

    @Test
    void resolve_onlyCityRequested_returnsCityMatches() {
        TaxonPort port = stubPort(Map.of(Set.of(5L), Set.of(300L)));

        Optional<Set<Long>> result = port.resolveCategoryAndCityFilter(EntityType.ADVERTISEMENT, null, 5L);

        assertThat(result).contains(Set.of(300L));
    }

    @Test
    void resolve_bothRequestedWithOverlap_returnsIntersection() {
        TaxonPort port = stubPort(Map.of(
                Set.of(1L), Set.of(100L, 200L, 300L),
                Set.of(5L), Set.of(200L, 300L, 400L)));

        Optional<Set<Long>> result = port.resolveCategoryAndCityFilter(EntityType.ADVERTISEMENT, Set.of(1L), 5L);

        assertThat(result).contains(Set.of(200L, 300L));
    }

    @Test
    void resolve_bothRequestedNoOverlap_returnsPresentButEmptySet() {
        TaxonPort port = stubPort(Map.of(
                Set.of(1L), Set.of(100L),
                Set.of(5L), Set.of(200L)));

        Optional<Set<Long>> result = port.resolveCategoryAndCityFilter(EntityType.ADVERTISEMENT, Set.of(1L), 5L);

        assertThat(result).contains(Set.of());
    }

    @Test
    void resolve_categoryMatchesNothing_returnsPresentButEmptySet() {
        TaxonPort port = stubPort(Map.of(Set.of(1L), Set.of()));

        Optional<Set<Long>> result = port.resolveCategoryAndCityFilter(EntityType.ADVERTISEMENT, Set.of(1L), null);

        assertThat(result).contains(Set.of());
    }
}
