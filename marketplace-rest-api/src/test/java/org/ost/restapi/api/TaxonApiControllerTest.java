package org.ost.restapi.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.dto.TaxonFilterDto;
import org.ost.platform.taxon.dto.TaxonTranslationDto;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.restapi.api.TaxonApiController.TaxonCreateRequest;
import org.ost.restapi.api.TaxonApiController.TaxonTranslationRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaxonApiControllerTest {

    private static final Long ACTOR_ID = 10L;

    @Mock private TaxonCatalogService taxonCatalogService;

    private TaxonApiController controller;

    @BeforeEach
    void setUp() {
        controller = new TaxonApiController(taxonCatalogService);
    }

    @Test
    void create_convertsTranslationListToLocaleMap() {
        TaxonCreateRequest request = new TaxonCreateRequest(TaxonType.CATEGORY,
                List.of(new TaxonTranslationRequest("en", "Plumbing", "Plumbing services")));
        TaxonDto created = TaxonDto.builder().id(1L).type(TaxonType.CATEGORY).name("Plumbing").description("Plumbing services").build();
        Map<Locale, TaxonTranslationDto> expected = Map.of(Locale.ENGLISH,
                TaxonTranslationDto.builder().locale("en").name("Plumbing").description("Plumbing services").build());
        when(taxonCatalogService.create(TaxonType.CATEGORY, expected, ACTOR_ID)).thenReturn(1L);
        when(taxonCatalogService.findById(1L, Locale.ENGLISH)).thenReturn(Optional.of(created));

        TaxonDto result = controller.create(ACTOR_ID, request);

        assertThat(result).isEqualTo(created);
    }

    private static UriComponentsBuilder baseUri() {
        return UriComponentsBuilder.fromUriString("http://localhost/api/taxons");
    }

    @Test
    void list_delegatesToServiceAndSetsTotalCountHeader() {
        TaxonDto taxon = TaxonDto.builder().id(1L).type(TaxonType.CITY).name("Kyiv").description("").build();
        TaxonFilterDto filter = TaxonFilterDto.empty();
        when(taxonCatalogService.getPage(eq(TaxonType.CITY), eq(Locale.forLanguageTag("uk")), eq(filter), eq(0), eq(20), eq(Sort.unsorted())))
                .thenReturn(List.of(taxon));
        when(taxonCatalogService.count(eq(TaxonType.CITY), eq(filter))).thenReturn(1);

        ResponseEntity<List<TaxonDto>> result = controller.list(TaxonType.CITY, "uk", filter, 0, 20, null, baseUri());

        assertThat(result.getBody()).containsExactly(taxon);
        assertThat(result.getHeaders().getFirst("X-Total-Count")).isEqualTo("1");
    }

    @Test
    void list_withSortParam_parsesIntoSort() {
        TaxonFilterDto filter = TaxonFilterDto.empty();
        when(taxonCatalogService.getPage(eq(TaxonType.CATEGORY), eq(Locale.ENGLISH), eq(filter), eq(0), eq(20), eq(Sort.by(Sort.Direction.DESC, "id"))))
                .thenReturn(List.of());
        when(taxonCatalogService.count(eq(TaxonType.CATEGORY), eq(filter))).thenReturn(0);

        controller.list(TaxonType.CATEGORY, "en", filter, 0, 20, "id,desc", baseUri());

        verify(taxonCatalogService).getPage(eq(TaxonType.CATEGORY), eq(Locale.ENGLISH), eq(filter), eq(0), eq(20), eq(Sort.by(Sort.Direction.DESC, "id")));
    }

    @Test
    void list_unknownSortField_throws() {
        TaxonFilterDto filter = TaxonFilterDto.empty();

        assertThatThrownBy(() -> controller.list(TaxonType.CATEGORY, "en", filter, 0, 20, "secretField", baseUri()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getById_notFound_throws() {
        when(taxonCatalogService.findById(eq(1L), eq(Locale.ENGLISH))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getById(1L, "en")).isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void softDelete_delegatesToService() {
        controller.softDelete(ACTOR_ID, 1L, 0L);

        verify(taxonCatalogService).softDelete(1L, ACTOR_ID, 0L);
    }
}
