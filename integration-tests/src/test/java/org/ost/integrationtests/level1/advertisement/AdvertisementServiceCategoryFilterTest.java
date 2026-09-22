package org.ost.integrationtests.level1.advertisement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.advertisement.repository.AdvertisementRepository;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.integrationtests.support.AdvertisementServiceTestSupport;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.spi.TaxonPort;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link AdvertisementService#getFiltered}/{@code count}'s delegation to
 * {@link TaxonPort#resolveCategoryAndCityFilter} — the tri-state AND-combine logic itself (no
 * filter / match-nothing / match-these-ids) is covered directly against the real default method in
 * {@code TaxonPortResolveCategoryAndCityFilterTest}, not re-verified here.
 */
@ExtendWith(MockitoExtension.class)
class AdvertisementServiceCategoryFilterTest {

    @Mock
    private AdvertisementRepository repository;
    @Mock
    private ComponentFactory<TaxonPort> taxonPortFactory;
    @Mock
    private TaxonPort taxonPort;

    private AdvertisementService newService() {
        return AdvertisementServiceTestSupport.newService(repository, taxonPortFactory);
    }

    @Test
    void getFiltered_taxonFilterResolvesToEmpty_appliesNoRestriction() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.builder().categoryIds(null).build();
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.of(taxonPort));
        when(taxonPort.resolveCategoryAndCityFilter(EntityType.ADVERTISEMENT, null, null)).thenReturn(Optional.empty());
        when(repository.findByFilter(eq(filter), any(Pageable.class), isNull())).thenReturn(List.of());

        newService().getFiltered(filter, 0, 10, Sort.unsorted());

        verify(repository).findByFilter(eq(filter), any(Pageable.class), isNull());
    }

    @Test
    void getFiltered_taxonFilterResolvesToEmptySet_returnsEmptyWithoutQueryingRepository() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.builder().categoryIds(Set.of(1L)).build();
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.of(taxonPort));
        when(taxonPort.resolveCategoryAndCityFilter(EntityType.ADVERTISEMENT, Set.of(1L), null)).thenReturn(Optional.of(Set.of()));

        List<AdvertisementInfoDto> result = newService().getFiltered(filter, 0, 10, Sort.unsorted());

        assertThat(result).isEmpty();
        verify(repository, never()).findByFilter(any(), any(), any());
    }

    @Test
    void getFiltered_taxonFilterResolvesToIds_appliesResolvedIds() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.builder().categoryIds(Set.of(1L)).build();
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.of(taxonPort));
        when(taxonPort.resolveCategoryAndCityFilter(EntityType.ADVERTISEMENT, Set.of(1L), null)).thenReturn(Optional.of(Set.of(100L, 200L)));
        when(repository.findByFilter(eq(filter), any(Pageable.class), eq(Set.of(100L, 200L)))).thenReturn(List.of());

        newService().getFiltered(filter, 0, 10, Sort.unsorted());

        verify(repository).findByFilter(eq(filter), any(Pageable.class), eq(Set.of(100L, 200L)));
    }

    @Test
    void getFiltered_taxonStarterAbsent_appliesNoRestriction() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.builder().categoryIds(Set.of(1L)).build();
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.empty());
        when(repository.findByFilter(eq(filter), any(Pageable.class), isNull())).thenReturn(List.of());

        newService().getFiltered(filter, 0, 10, Sort.unsorted());

        verify(repository).findByFilter(eq(filter), any(Pageable.class), isNull());
    }

    @Test
    void count_taxonFilterResolvesToEmptySet_returnsZeroWithoutQueryingRepository() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.builder().categoryIds(Set.of(1L)).build();
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.of(taxonPort));
        when(taxonPort.resolveCategoryAndCityFilter(EntityType.ADVERTISEMENT, Set.of(1L), null)).thenReturn(Optional.of(Set.of()));

        int result = newService().count(filter);

        assertThat(result).isZero();
        verify(repository, never()).countByFilter(any(), any());
    }
}
