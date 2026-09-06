package org.ost.restapi.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.AccessDeniedException;
import org.ost.orchestrator.services.AdvertisementDisplayEnrichmentService;
import org.ost.orchestrator.services.AdvertisementReadService;
import org.ost.orchestrator.services.AdvertisementSaveService;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.dto.AdvertisementSaveDto;
import org.ost.platform.advertisement.model.AdKind;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
class AdvertisementApiControllerTest {

    private static final Long ACTOR_ID = 10L;
    private static final String VALID_CREATE_BODY = """
            {"title":"Title","description":"Desc","adKind":"OFFER","categoryIds":[1]}""";

    @Mock private AdvertisementSaveService saveService;
    @Mock private AdvertisementReadService readService;
    @Mock private AdvertisementDisplayEnrichmentService enrichmentService;
    @Mock private UserProfileService userProfileService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvc(new AdvertisementApiController(saveService, readService, enrichmentService, userProfileService));
        authenticateAs(ACTOR_ID);
        lenient().when(userProfileService.resolveAdsPageSize(any())).thenReturn(20);
        lenient().when(enrichmentService.enrichWithCategoryAndCity(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(enrichmentService.enrichWithActor(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(enrichmentService.enrichWithMedia(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(enrichmentService.enrichWithCategoriesAndCity(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(enrichmentService.enrichWithActorInfo(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(enrichmentService.enrichWithMediaSummary(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        clearAuthentication();
    }

    // ── create — positive ──────────────────────────────────────────────────

    @Test
    void create_validBody_savesThenReturnsFreshCopy() throws Exception {
        AdvertisementInfoDto saved = AdvertisementInfoDto.builder().id(100L).title("Title").build();
        when(saveService.save(any(), eq(ACTOR_ID), any())).thenReturn(100L);
        when(readService.findById(100L)).thenReturn(Optional.of(saved));

        mockMvc.perform(post("/api/advertisements").contentType(MediaType.APPLICATION_JSON).content(VALID_CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100));
    }

    // ── create — negative (400 bean validation) ─────────────────────────────

    @Test
    void create_blankTitle_returns400WithFieldError() throws Exception {
        String body = """
                {"title":"","description":"Desc","adKind":"OFFER"}""";

        mockMvc.perform(post("/api/advertisements").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    void create_missingAdKind_returns400WithFieldError() throws Exception {
        String body = """
                {"title":"Title","description":"Desc"}""";

        mockMvc.perform(post("/api/advertisements").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.adKind").exists());
    }

    @Test
    void create_descriptionOverRawMaxLength_returns400() throws Exception {
        String tooLong = "x".repeat(AdvertisementSaveDto.DESCRIPTION_RAW_MAX_LENGTH + 1);
        String body = """
                {"title":"Title","description":"%s","adKind":"OFFER"}""".formatted(tooLong);

        mockMvc.perform(post("/api/advertisements").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.description").exists());
    }

    @Test
    void create_tooManyCategoryIds_returns400() throws Exception {
        String ids = "[1,2,3,4,5,6,7,8,9,10,11]";
        String body = """
                {"title":"Title","description":"Desc","adKind":"OFFER","categoryIds":%s}""".formatted(ids);

        mockMvc.perform(post("/api/advertisements").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.categoryIds").exists());
    }

    // ── list — positive ──────────────────────────────────────────────────

    @Test
    void list_delegatesToReadServiceAndSetsTotalCountHeader() throws Exception {
        AdvertisementInfoDto info = AdvertisementInfoDto.builder().id(1L).build();
        AdvertisementFilterDto filter = AdvertisementFilterDto.empty();
        when(readService.getFiltered(eq(filter), eq(0), eq(20), eq(Sort.unsorted()))).thenReturn(List.of(info));
        when(readService.count(eq(filter))).thenReturn(1);

        mockMvc.perform(get("/api/advertisements"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void list_withSortParam_parsesIntoSort() throws Exception {
        AdvertisementFilterDto filter = AdvertisementFilterDto.empty();
        when(readService.getFiltered(eq(filter), eq(0), eq(20), eq(Sort.by(Sort.Direction.DESC, "createdAt")))).thenReturn(List.of());
        when(readService.count(eq(filter))).thenReturn(0);

        mockMvc.perform(get("/api/advertisements").param("sort", "createdAt,desc")).andExpect(status().isOk());

        verify(readService).getFiltered(eq(filter), eq(0), eq(20), eq(Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @Test
    void list_pageSizeComesFromCallersSavedSettings_urlSizeParamIsIgnored() throws Exception {
        AdvertisementFilterDto filter = AdvertisementFilterDto.empty();
        when(userProfileService.resolveAdsPageSize(ACTOR_ID)).thenReturn(50);
        when(readService.getFiltered(eq(filter), eq(0), eq(50), any())).thenReturn(List.of());
        when(readService.count(eq(filter))).thenReturn(0);

        // "size=5" in the URL must have no effect -- there is no such request parameter anymore.
        mockMvc.perform(get("/api/advertisements").param("size", "5")).andExpect(status().isOk());

        verify(readService).getFiltered(eq(filter), eq(0), eq(50), any());
    }

    @Test
    void list_anonymousCaller_usesSharedDefaultPageSize() throws Exception {
        clearAuthentication();
        AdvertisementFilterDto filter = AdvertisementFilterDto.empty();
        when(userProfileService.resolveAdsPageSize(null)).thenReturn(20);
        when(readService.getFiltered(eq(filter), eq(0), eq(20), any())).thenReturn(List.of());
        when(readService.count(eq(filter))).thenReturn(0);

        mockMvc.perform(get("/api/advertisements")).andExpect(status().isOk());

        verify(readService).getFiltered(eq(filter), eq(0), eq(20), any());
    }

    @Test
    void list_notLastPage_setsLinkHeaderWithNext() throws Exception {
        AdvertisementFilterDto filter = AdvertisementFilterDto.empty();
        when(readService.getFiltered(eq(filter), eq(0), eq(20), any())).thenReturn(List.of());
        when(readService.count(eq(filter))).thenReturn(45);

        mockMvc.perform(get("/api/advertisements"))
                .andExpect(status().isOk())
                .andExpect(header().string("Link", org.hamcrest.Matchers.containsString("rel=\"next\"")));
    }

    // ── list — negative ──────────────────────────────────────────────────

    @Test
    void list_unknownSortField_returns400() throws Exception {
        mockMvc.perform(get("/api/advertisements").param("sort", "secretField"))
                .andExpect(status().isBadRequest());
    }

    // ── getById ──────────────────────────────────────────────────────────

    @Test
    void getById_found_returnsIt() throws Exception {
        AdvertisementInfoDto info = AdvertisementInfoDto.builder().id(1L).build();
        when(readService.findById(1L)).thenReturn(Optional.of(info));

        mockMvc.perform(get("/api/advertisements/1")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        when(readService.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/advertisements/1")).andExpect(status().isNotFound());
    }

    // ── update ──────────────────────────────────────────────────────────

    @Test
    void update_pathIdWinsOverBodyId() throws Exception {
        AdvertisementInfoDto saved = AdvertisementInfoDto.builder().id(1L).build();
        when(saveService.save(any(), eq(ACTOR_ID), any())).thenReturn(1L);
        when(readService.findById(1L)).thenReturn(Optional.of(saved));
        String body = """
                {"id":999,"title":"Title","description":"Desc","adKind":"OFFER","version":0}""";

        mockMvc.perform(put("/api/advertisements/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        verify(saveService).save(eq(new AdvertisementSaveDto(1L, "Title", "Desc", AdKind.OFFER, null, null, 0L)), eq(ACTOR_ID), any());
    }

    @Test
    void update_notFound_returns404() throws Exception {
        when(saveService.save(any(), eq(ACTOR_ID), any())).thenThrow(new java.util.NoSuchElementException());
        String body = """
                {"title":"Title","description":"Desc","adKind":"OFFER","version":0}""";

        mockMvc.perform(put("/api/advertisements/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_staleVersion_returns409() throws Exception {
        when(saveService.save(any(), eq(ACTOR_ID), any()))
                .thenThrow(new OptimisticLockingFailureException("stale"));
        String body = """
                {"title":"Title","description":"Desc","adKind":"OFFER","version":0}""";

        mockMvc.perform(put("/api/advertisements/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void update_notOwner_returns403() throws Exception {
        when(saveService.save(any(), eq(ACTOR_ID), any()))
                .thenThrow(new AccessDeniedException("not the owner"));
        String body = """
                {"title":"Title","description":"Desc","adKind":"OFFER","version":0}""";

        mockMvc.perform(put("/api/advertisements/1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    // ── delete ──────────────────────────────────────────────────────────

    @Test
    void delete_delegatesToSaveService() throws Exception {
        mockMvc.perform(delete("/api/advertisements/1").param("version", "0")).andExpect(status().isOk());

        verify(saveService).delete(1L, ACTOR_ID, 0L);
    }

    @Test
    void delete_notOwner_returns403() throws Exception {
        org.mockito.Mockito.doThrow(new AccessDeniedException("not the owner")).when(saveService).delete(1L, ACTOR_ID, 0L);

        mockMvc.perform(delete("/api/advertisements/1").param("version", "0")).andExpect(status().isForbidden());
    }

    @Test
    void delete_staleVersion_returns409() throws Exception {
        org.mockito.Mockito.doThrow(new OptimisticLockingFailureException("stale")).when(saveService).delete(1L, ACTOR_ID, 0L);

        mockMvc.perform(delete("/api/advertisements/1").param("version", "0")).andExpect(status().isConflict());
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        org.mockito.Mockito.doThrow(new java.util.NoSuchElementException()).when(saveService).delete(1L, ACTOR_ID, 0L);

        mockMvc.perform(delete("/api/advertisements/1").param("version", "0")).andExpect(status().isNotFound());
    }
}
