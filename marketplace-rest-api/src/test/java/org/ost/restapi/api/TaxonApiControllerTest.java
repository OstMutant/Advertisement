package org.ost.restapi.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.AccessDeniedException;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.dto.TaxonFilterDto;
import org.ost.platform.taxon.dto.TaxonTranslationDto;
import org.ost.platform.taxon.model.TaxonType;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.authenticateAs;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.clearAuthentication;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.mockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TaxonApiControllerTest {

    private static final Long ACTOR_ID = 10L;

    @Mock private TaxonCatalogService taxonCatalogService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvc(new TaxonApiController(taxonCatalogService));
        authenticateAs(ACTOR_ID);
    }

    @AfterEach
    void tearDown() {
        clearAuthentication();
    }

    // ── create — positive ──────────────────────────────────────────────────

    @Test
    void create_convertsTranslationListToLocaleMap() throws Exception {
        TaxonDto created = TaxonDto.builder().id(1L).type(TaxonType.CATEGORY).name("Plumbing").description("Plumbing services").build();
        Map<Locale, TaxonTranslationDto> expected = Map.of(Locale.ENGLISH,
                TaxonTranslationDto.builder().locale("en").name("Plumbing").description("Plumbing services").build());
        when(taxonCatalogService.create(TaxonType.CATEGORY, expected, ACTOR_ID)).thenReturn(1L);
        when(taxonCatalogService.findById(1L, Locale.ENGLISH)).thenReturn(Optional.of(created));
        String body = """
                {"type":"CATEGORY","translations":[{"locale":"en","name":"Plumbing","description":"Plumbing services"}]}""";

        mockMvc.perform(post("/api/taxons").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    // ── create — negative ──────────────────────────────────────────────────

    @Test
    void create_incompleteTranslations_returns400() throws Exception {
        when(taxonCatalogService.create(any(), any(), eq(ACTOR_ID)))
                .thenThrow(new IllegalArgumentException("Translation for locale 'uk' is missing or incomplete"));
        String body = """
                {"type":"CATEGORY","translations":[{"locale":"en","name":"Plumbing","description":"Plumbing services"}]}""";

        mockMvc.perform(post("/api/taxons").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Translation for locale 'uk' is missing or incomplete"));
    }

    @Test
    void create_nonPrivilegedActor_returns403() throws Exception {
        when(taxonCatalogService.create(any(), any(), eq(ACTOR_ID))).thenThrow(new AccessDeniedException("not privileged"));
        String body = """
                {"type":"CATEGORY","translations":[{"locale":"en","name":"Plumbing","description":"Plumbing services"}]}""";

        mockMvc.perform(post("/api/taxons").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    // ── list ──────────────────────────────────────────────────────────

    @Test
    void list_delegatesToServiceAndSetsTotalCountHeader() throws Exception {
        TaxonDto taxon = TaxonDto.builder().id(1L).type(TaxonType.CITY).name("Kyiv").description("").build();
        TaxonFilterDto filter = TaxonFilterDto.empty();
        when(taxonCatalogService.getAll(eq(TaxonType.CITY), eq(Locale.forLanguageTag("uk")), eq(filter), eq(Sort.unsorted())))
                .thenReturn(List.of(taxon));

        mockMvc.perform(get("/api/taxons").param("type", "CITY").param("locale", "uk"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"));
    }

    @Test
    void list_withSortParam_parsesIntoSort() throws Exception {
        TaxonFilterDto filter = TaxonFilterDto.empty();
        when(taxonCatalogService.getAll(eq(TaxonType.CATEGORY), eq(Locale.ENGLISH), eq(filter), eq(Sort.by(Sort.Direction.DESC, "id"))))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/taxons").param("type", "CATEGORY").param("sort", "id,desc")).andExpect(status().isOk());

        verify(taxonCatalogService).getAll(eq(TaxonType.CATEGORY), eq(Locale.ENGLISH), eq(filter), eq(Sort.by(Sort.Direction.DESC, "id")));
    }

    @Test
    void list_unknownSortField_returns400() throws Exception {
        mockMvc.perform(get("/api/taxons").param("type", "CATEGORY").param("sort", "secretField"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_omittedType_mergesCategoriesAndCities() throws Exception {
        TaxonFilterDto filter = TaxonFilterDto.empty();
        TaxonDto category = TaxonDto.builder().id(1L).type(TaxonType.CATEGORY).name("Plumbing").description("").build();
        TaxonDto city = TaxonDto.builder().id(2L).type(TaxonType.CITY).name("Kyiv").description("").build();
        when(taxonCatalogService.getAll(eq(TaxonType.CATEGORY), eq(Locale.ENGLISH), eq(filter), eq(Sort.unsorted())))
                .thenReturn(List.of(category));
        when(taxonCatalogService.getAll(eq(TaxonType.CITY), eq(Locale.ENGLISH), eq(filter), eq(Sort.unsorted())))
                .thenReturn(List.of(city));

        mockMvc.perform(get("/api/taxons"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "2"))
                .andExpect(jsonPath("$[0].name").value("Plumbing"))
                .andExpect(jsonPath("$[1].name").value("Kyiv"));
    }

    // ── getById ──────────────────────────────────────────────────────────

    @Test
    void getById_notFound_returns404() throws Exception {
        when(taxonCatalogService.findById(eq(1L), eq(Locale.ENGLISH))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/taxons/1")).andExpect(status().isNotFound());
    }

    @Test
    void getById_missingTranslation_fallsBackToEnglish() throws Exception {
        TaxonDto taxon = TaxonDto.builder().id(1L).type(TaxonType.CITY).name("Kyiv").description("").build();
        when(taxonCatalogService.findById(eq(1L), eq(Locale.forLanguageTag("de")))).thenReturn(Optional.of(taxon));

        mockMvc.perform(get("/api/taxons/1").param("locale", "de"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kyiv"));
    }

    // ── update ──────────────────────────────────────────────────────────

    @Test
    void update_staleVersion_returns409() throws Exception {
        doThrow(new OptimisticLockingFailureException("stale")).when(taxonCatalogService)
                .update(eq(1L), any(), eq(ACTOR_ID), eq(0L));
        String body = """
                {"translations":[{"locale":"en","name":"Plumbing","description":"Plumbing services"}],"version":0}""";

        mockMvc.perform(put("/api/taxons/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    // ── delete ──────────────────────────────────────────────────────────

    @Test
    void softDelete_delegatesToService() throws Exception {
        mockMvc.perform(delete("/api/taxons/1").param("version", "0")).andExpect(status().isOk());

        verify(taxonCatalogService).softDelete(1L, ACTOR_ID, 0L);
    }

    @Test
    void softDelete_nonPrivilegedActor_returns403() throws Exception {
        doThrow(new AccessDeniedException("not privileged")).when(taxonCatalogService).softDelete(1L, ACTOR_ID, 0L);

        mockMvc.perform(delete("/api/taxons/1").param("version", "0")).andExpect(status().isForbidden());
    }
}
