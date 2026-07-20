package org.ost.integrationtests.advertisement;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.advertisement.repository.AdvertisementRepository;
import org.ost.advertisement.services.AdvertisementService;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.attachment.spi.AttachmentPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.taxon.spi.TaxonPort;
import org.ost.platform.user.spi.UserPort;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Locale;
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
 * {@link AdvertisementService#getFiltered}/{@code count} resolve an optional category filter via
 * the private {@code resolveCategoryFilter()}, which used a nullable {@code Set<Long>} to encode
 * three states (no filter / match-nothing / match-these-ids). Covers the tri-state behavior
 * through the public entry points.
 */
@ExtendWith(MockitoExtension.class)
class AdvertisementServiceCategoryFilterTest {

    @Mock
    private AdvertisementRepository repository;
    @Mock
    private ComponentFactory<AttachmentPort> attachmentPortFactory;
    @Mock
    private ComponentFactory<TaxonPort> taxonPortFactory;
    @Mock
    private ComponentFactory<UserPort> userPortFactory;
    @Mock
    private TaxonPort taxonPort;

    private AdvertisementService newService() {
        return new AdvertisementService(repository, attachmentPortFactory, taxonPortFactory, userPortFactory);
    }

    @Test
    void getFiltered_noCategoryFilterRequested_appliesNoRestriction() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.builder().categoryIds(null).build();
        when(repository.findByFilter(eq(filter), any(Pageable.class), isNull())).thenReturn(List.of());

        newService().getFiltered(filter, 0, 10, Sort.unsorted(), Locale.ENGLISH);

        verify(repository).findByFilter(eq(filter), any(Pageable.class), isNull());
        verify(taxonPortFactory, never()).findIfAvailable();
    }

    @Test
    void getFiltered_categoryFilterMatchesNothing_returnsEmptyWithoutQueryingRepository() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.builder().categoryIds(Set.of(1L)).build();
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.of(taxonPort));
        when(taxonPort.findEntityIdsWithAnyTaxon(EntityType.ADVERTISEMENT, Set.of(1L))).thenReturn(Set.of());

        List<AdvertisementInfoDto> result = newService().getFiltered(filter, 0, 10, Sort.unsorted(), Locale.ENGLISH);

        assertThat(result).isEmpty();
        verify(repository, never()).findByFilter(any(), any(), any());
    }

    @Test
    void getFiltered_categoryFilterMatchesSome_appliesResolvedIds() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.builder().categoryIds(Set.of(1L)).build();
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.of(taxonPort));
        when(taxonPort.findEntityIdsWithAnyTaxon(EntityType.ADVERTISEMENT, Set.of(1L))).thenReturn(Set.of(100L, 200L));
        when(repository.findByFilter(eq(filter), any(Pageable.class), eq(Set.of(100L, 200L)))).thenReturn(List.of());

        newService().getFiltered(filter, 0, 10, Sort.unsorted(), Locale.ENGLISH);

        verify(repository).findByFilter(eq(filter), any(Pageable.class), eq(Set.of(100L, 200L)));
    }

    @Test
    void getFiltered_categoryFilterRequestedButTaxonStarterAbsent_appliesNoRestriction() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.builder().categoryIds(Set.of(1L)).build();
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.empty());
        when(repository.findByFilter(eq(filter), any(Pageable.class), isNull())).thenReturn(List.of());

        newService().getFiltered(filter, 0, 10, Sort.unsorted(), Locale.ENGLISH);

        verify(repository).findByFilter(eq(filter), any(Pageable.class), isNull());
    }

    @Test
    void count_categoryFilterMatchesNothing_returnsZeroWithoutQueryingRepository() {
        AdvertisementFilterDto filter = AdvertisementFilterDto.builder().categoryIds(Set.of(1L)).build();
        when(taxonPortFactory.findIfAvailable()).thenReturn(Optional.of(taxonPort));
        when(taxonPort.findEntityIdsWithAnyTaxon(EntityType.ADVERTISEMENT, Set.of(1L))).thenReturn(Set.of());

        int result = newService().count(filter);

        assertThat(result).isZero();
        verify(repository, never()).countByFilter(any(), any());
    }
}
