package org.ost.restapi.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.AccessDeniedException;
import org.ost.orchestrator.services.ProviderProfileDisplayEnrichmentService;
import org.ost.orchestrator.services.ProviderProfileReadService;
import org.ost.orchestrator.services.ProviderProfileSaveService;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
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
class ProviderProfileApiControllerTest {

    private static final Long ACTOR_ID = 10L;

    @Mock private ProviderProfileSaveService saveService;
    @Mock private ProviderProfileReadService readService;
    @Mock private ProviderProfileDisplayEnrichmentService enrichmentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvc(new ProviderProfileApiController(saveService, readService, enrichmentService));
        authenticateAs(ACTOR_ID);
        lenient().when(enrichmentService.enrichWithCategoryAndCity(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(enrichmentService.enrichWithActor(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(enrichmentService.enrichWithCategoriesAndCity(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(enrichmentService.enrichWithActorInfo(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        clearAuthentication();
    }

    // ── create ──────────────────────────────────────────────────────────

    @Test
    void create_targetUserIdAlwaysEqualsActorId() throws Exception {
        ProviderProfileDto saved = ProviderProfileDto.builder().id(1L).build();
        when(saveService.save(any(), eq(ACTOR_ID), eq(ACTOR_ID))).thenReturn(1L);
        when(readService.findById(1L)).thenReturn(Optional.of(saved));
        String body = """
                {"kind":"MASTER","about":"About","categoryIds":[]}""";

        mockMvc.perform(post("/api/provider-profiles").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void create_missingKind_returns400() throws Exception {
        String body = """
                {"about":"About"}""";

        mockMvc.perform(post("/api/provider-profiles").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.kind").exists());
    }

    @Test
    void create_aboutOverRawMaxLength_returns400() throws Exception {
        String tooLong = "x".repeat(ProviderProfileSaveDto.ABOUT_RAW_MAX_LENGTH + 1);
        String body = """
                {"kind":"MASTER","about":"%s"}""".formatted(tooLong);

        mockMvc.perform(post("/api/provider-profiles").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.about").exists());
    }

    @Test
    void create_tooManyCategoryIds_returns400() throws Exception {
        String body = """
                {"kind":"MASTER","categoryIds":[1,2,3,4,5,6,7,8,9,10,11]}""";

        mockMvc.perform(post("/api/provider-profiles").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.categoryIds").exists());
    }

    // ── list ──────────────────────────────────────────────────────────

    @Test
    void list_delegatesToReadServiceAndSetsTotalCountHeader() throws Exception {
        ProviderProfileDto info = ProviderProfileDto.builder().id(1L).build();
        ProviderProfileFilterDto filter = ProviderProfileFilterDto.empty();
        when(readService.getFiltered(eq(filter), eq(0), eq(20), eq(Sort.unsorted()))).thenReturn(List.of(info));
        when(readService.count(eq(filter))).thenReturn(1);

        mockMvc.perform(get("/api/provider-profiles"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"));
    }

    @Test
    void list_withSortParam_parsesIntoSort() throws Exception {
        ProviderProfileFilterDto filter = ProviderProfileFilterDto.empty();
        when(readService.getFiltered(eq(filter), eq(0), eq(20), eq(Sort.by(Sort.Direction.DESC, "createdAt")))).thenReturn(List.of());
        when(readService.count(eq(filter))).thenReturn(0);

        mockMvc.perform(get("/api/provider-profiles").param("sort", "createdAt,desc")).andExpect(status().isOk());

        verify(readService).getFiltered(eq(filter), eq(0), eq(20), eq(Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Test
    void list_unknownSortField_returns400() throws Exception {
        mockMvc.perform(get("/api/provider-profiles").param("sort", "secretField")).andExpect(status().isBadRequest());
    }

    // ── getById ──────────────────────────────────────────────────────────

    @Test
    void getById_notFound_returns404() throws Exception {
        when(readService.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/provider-profiles/1")).andExpect(status().isNotFound());
    }

    // ── update ──────────────────────────────────────────────────────────

    @Test
    void update_staleVersion_returns409() throws Exception {
        when(saveService.save(any(), eq(ACTOR_ID), eq(ACTOR_ID))).thenThrow(new OptimisticLockingFailureException("stale"));
        String body = """
                {"kind":"MASTER","version":0}""";

        mockMvc.perform(put("/api/provider-profiles/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void update_notOwner_returns403() throws Exception {
        when(saveService.save(any(), eq(ACTOR_ID), eq(ACTOR_ID))).thenThrow(new AccessDeniedException("not the owner"));
        String body = """
                {"kind":"MASTER","version":0}""";

        mockMvc.perform(put("/api/provider-profiles/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    // ── delete ──────────────────────────────────────────────────────────

    @Test
    void delete_delegatesToSaveService() throws Exception {
        mockMvc.perform(delete("/api/provider-profiles/1").param("version", "0")).andExpect(status().isOk());

        verify(saveService).delete(1L, ACTOR_ID, 0L);
    }

    @Test
    void delete_notOwner_returns403() throws Exception {
        doThrow(new AccessDeniedException("not the owner")).when(saveService).delete(1L, ACTOR_ID, 0L);

        mockMvc.perform(delete("/api/provider-profiles/1").param("version", "0")).andExpect(status().isForbidden());
    }
}
