package org.ost.orchestrator.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** {@link ProviderProfileDisplayEnrichmentService#enrichWithCategoryAndCity} -- see `.claude/nav/adr-index.md` for why a profile with no city must not throw. */
@ExtendWith(MockitoExtension.class)
class ProviderProfileDisplayEnrichmentServiceTest {

    @Mock
    private TaxonLookupService taxonLookupService;
    @Mock
    private ActorLookupService actorLookupService;

    private ProviderProfileDisplayEnrichmentService newService() {
        return new ProviderProfileDisplayEnrichmentService(taxonLookupService, actorLookupService);
    }

    @Test
    void enrichWithCategoryAndCity_noAssignedTaxons_doesNotThrowAndLeavesCategoryAndCityEmpty() {
        ProviderProfileDisplayEnrichmentService service = newService();
        when(taxonLookupService.getForEntity(EntityType.PROVIDER_PROFILE, 1L, Locale.ENGLISH)).thenReturn(List.of());
        ProviderProfileDto profile = ProviderProfileDto.builder().id(1L).build();

        ProviderProfileDto enriched = service.enrichWithCategoryAndCity(profile, Locale.ENGLISH);

        assertThat(enriched.getCityTaxonId()).isNull();
        assertThat(enriched.getCityName()).isNull();
        assertThat(enriched.getCategoryIds()).isEmpty();
        assertThat(enriched.getCategoryNames()).isEmpty();
    }

    @Test
    void enrichWithCategoryAndCity_categoryAndCityAssigned_resolvesBothFromTheSameAssignmentScan() {
        ProviderProfileDisplayEnrichmentService service = newService();
        TaxonDto category = TaxonDto.builder().id(1L).type(TaxonType.CATEGORY).name("Plumbing").description("").build();
        TaxonDto city = TaxonDto.builder().id(7L).type(TaxonType.CITY).name("Lviv").description("").build();
        when(taxonLookupService.getForEntity(EntityType.PROVIDER_PROFILE, 1L, Locale.ENGLISH)).thenReturn(List.of(category, city));
        ProviderProfileDto profile = ProviderProfileDto.builder().id(1L).build();

        ProviderProfileDto enriched = service.enrichWithCategoryAndCity(profile, Locale.ENGLISH);

        assertThat(enriched.getCategoryIds()).containsExactly(1L);
        assertThat(enriched.getCategoryNames()).containsExactly("Plumbing");
        assertThat(enriched.getCityTaxonId()).isEqualTo(7L);
        assertThat(enriched.getCityName()).isEqualTo("Lviv");
    }
}
