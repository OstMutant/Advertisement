package org.ost.integrationtests.level1.advertisement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.advertisement.repository.AdvertisementRepository;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.integrationtests.support.AdvertisementServiceTestSupport;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.model.SqlPredicate;
import org.ost.platform.taxon.spi.TaxonPort;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers the delegation of {@link AdvertisementService}'s query and count to {@link TaxonPort#categoryAndCityPredicate}, including the no-restriction cases. */
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
    void getFiltered_taxonPredicateResolved_passesPredicateToRepository() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.builder().categoryIds(Set.of(1L)).build();
        SqlPredicate predicate = new SqlPredicate("a.id = :_one", Map.of("_one", 100L));
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.of(taxonPort));
        when(taxonPort.categoryAndCityPredicate(EntityType.ADVERTISEMENT, AdvertisementRepository.ID, Set.of(1L), null)).thenReturn(Optional.of(predicate));

        newService().getFiltered(filter, 0, 10, Sort.unsorted());

        verify(repository).findByFilter(eq(filter), any(Pageable.class), eq(predicate));
    }

    @Test
    void getFiltered_taxonStarterAbsent_passesNullPredicateToRepository() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.builder().categoryIds(Set.of(1L)).build();
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.empty());

        newService().getFiltered(filter, 0, 10, Sort.unsorted());

        verify(repository).findByFilter(eq(filter), any(Pageable.class), isNull());
    }

    @Test
    void getFiltered_noTaxonFilterRequested_passesNullPredicateToRepository() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.builder().categoryIds(null).build();
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.of(taxonPort));
        when(taxonPort.categoryAndCityPredicate(EntityType.ADVERTISEMENT, AdvertisementRepository.ID, null, null)).thenReturn(Optional.empty());

        newService().getFiltered(filter, 0, 10, Sort.unsorted());

        verify(repository).findByFilter(eq(filter), any(Pageable.class), isNull());
    }

    @Test
    void count_taxonPredicateResolved_passesPredicateToRepository() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.builder().categoryIds(Set.of(1L)).build();
        SqlPredicate predicate = new SqlPredicate("a.id = :_one", Map.of("_one", 100L));
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.of(taxonPort));
        when(taxonPort.categoryAndCityPredicate(EntityType.ADVERTISEMENT, AdvertisementRepository.ID, Set.of(1L), null)).thenReturn(Optional.of(predicate));

        newService().count(filter);

        verify(repository).countByFilter(eq(filter), eq(predicate));
    }

    @Test
    void count_taxonStarterAbsent_passesNullPredicateToRepository() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.builder().categoryIds(Set.of(1L)).build();
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.empty());

        newService().count(filter);

        verify(repository).countByFilter(eq(filter), isNull());
    }

    @Test
    void count_noTaxonFilterRequested_passesNullPredicateToRepository() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.builder().categoryIds(null).build();
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.of(taxonPort));
        when(taxonPort.categoryAndCityPredicate(EntityType.ADVERTISEMENT, AdvertisementRepository.ID, null, null)).thenReturn(Optional.empty());

        newService().count(filter);

        verify(repository).countByFilter(eq(filter), isNull());
    }
}
