package org.ost.restapi.api;

import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.AccessDeniedException;
import org.ost.orchestrator.services.ProviderProfileDisplayEnrichmentService;
import org.ost.orchestrator.services.ProviderProfileReadService;
import org.ost.orchestrator.services.ProviderProfileSaveService;
import org.ost.orchestrator.services.SnapshotRestoreService;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSaveDto;
import org.ost.platform.providerprofile.dto.ProviderProfileSnapshotDto;
import org.ost.platform.core.StaleWriteException;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.ost.restapi.api.audit.ActivityHistorySupport;
import org.ost.restapi.api.patch.MergePatchSupport;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.authenticateAs;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.clearAuthentication;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.mockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fast MockMvc contract tests for {@link ProviderProfileApiController} -- mocked orchestrator services, no DB. */
@ExtendWith(MockitoExtension.class)
class ProviderProfileApiControllerTest {

    private static final Long ACTOR_ID = 10L;

    @Mock private ProviderProfileSaveService saveService;
    @Mock private ProviderProfileReadService readService;
    @Mock private ProviderProfileDisplayEnrichmentService enrichmentService;
    @Mock private UserProfileService userProfileService;
    @Mock private ActivityHistorySupport activityHistorySupport;
    @Mock private SnapshotRestoreService snapshotRestoreService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvc(new ProviderProfileApiController(saveService, readService, enrichmentService, userProfileService,
                activityHistorySupport, snapshotRestoreService,
                new MergePatchSupport(Validation.buildDefaultValidatorFactory().getValidator())));
        authenticateAs(ACTOR_ID);
        lenient().when(enrichmentService.enrichWithCategoryAndCity(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(enrichmentService.enrichWithActor(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(enrichmentService.enrichWithCategoriesAndCity(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(enrichmentService.enrichWithActorInfo(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userProfileService.resolveProviderProfilesPageSize(any())).thenReturn(20);
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

    @Test
    void list_pageSizeComesFromCallersSavedSettings_urlSizeParamIsIgnored() throws Exception {
        ProviderProfileFilterDto filter = ProviderProfileFilterDto.empty();
        when(userProfileService.resolveProviderProfilesPageSize(ACTOR_ID)).thenReturn(50);
        when(readService.getFiltered(eq(filter), eq(0), eq(50), any())).thenReturn(List.of());
        when(readService.count(eq(filter))).thenReturn(0);

        // "size=5" in the URL must have no effect -- there is no such request parameter anymore.
        mockMvc.perform(get("/api/provider-profiles").param("size", "5")).andExpect(status().isOk());

        verify(readService).getFiltered(eq(filter), eq(0), eq(50), any());
    }

    @Test
    void list_anonymousCaller_usesSharedDefaultPageSize() throws Exception {
        clearAuthentication();
        ProviderProfileFilterDto filter = ProviderProfileFilterDto.empty();
        when(userProfileService.resolveProviderProfilesPageSize(null)).thenReturn(20);
        when(readService.getFiltered(eq(filter), eq(0), eq(20), any())).thenReturn(List.of());
        when(readService.count(eq(filter))).thenReturn(0);

        mockMvc.perform(get("/api/provider-profiles")).andExpect(status().isOk());

        verify(readService).getFiltered(eq(filter), eq(0), eq(20), any());
    }

    // ── getById ──────────────────────────────────────────────────────────

    @Test
    void getById_notFound_returns404() throws Exception {
        when(readService.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/provider-profiles/1")).andExpect(status().isNotFound());
    }

    // ── history ──────────────────────────────────────────────────────────

    @Test
    void history_delegatesToSupport() throws Exception {
        when(activityHistorySupport.history(ACTOR_ID, EntityType.PROVIDER_PROFILE, 7L)).thenReturn(List.of());

        mockMvc.perform(get("/api/provider-profiles/7/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void historySnapshot_unknown_returnsNotFound() throws Exception {
        when(activityHistorySupport.snapshot(eq(ACTOR_ID), eq(EntityType.PROVIDER_PROFILE), eq(7L), eq(5L), eq(ProviderProfileSnapshotDto.class)))
                .thenThrow(new java.util.NoSuchElementException("Snapshot 5 not found"));

        mockMvc.perform(get("/api/provider-profiles/7/history/5")).andExpect(status().isNotFound());
    }

    // ── update ──────────────────────────────────────────────────────────

    @Test
    void update_staleVersion_returns412() throws Exception {
        when(saveService.save(any(), eq(ACTOR_ID), eq(ACTOR_ID))).thenThrow(new StaleWriteException("stale"));
        String body = """
                {"kind":"MASTER"}""";

        mockMvc.perform(put("/api/provider-profiles/1").header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void update_notOwner_returns403() throws Exception {
        when(saveService.save(any(), eq(ACTOR_ID), eq(ACTOR_ID))).thenThrow(new AccessDeniedException("not the owner"));
        String body = """
                {"kind":"MASTER"}""";

        mockMvc.perform(put("/api/provider-profiles/1").header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    // ── patch ───────────────────────────────────────────────────────────

    private static final ProviderProfileSnapshotDto CURRENT = new ProviderProfileSnapshotDto(ProviderKind.MASTER, "About",
            List.of(1L, 2L), 5L, "+380501234567", "plumber_master", "+380507654321");

    private ProviderProfileSaveDto patchAndCaptureSave(String body) throws Exception {
        when(saveService.findCurrentSnapshot(1L)).thenReturn(Optional.of(CURRENT));
        when(saveService.save(any(), eq(ACTOR_ID), eq(ACTOR_ID))).thenReturn(1L);
        when(readService.findById(1L)).thenReturn(Optional.of(ProviderProfileDto.builder().id(1L).build()));

        mockMvc.perform(patch("/api/provider-profiles/1").header("If-Match", "\"4\"")
                        .contentType("application/merge-patch+json").content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<ProviderProfileSaveDto> captor = ArgumentCaptor.forClass(ProviderProfileSaveDto.class);
        verify(saveService).save(captor.capture(), eq(ACTOR_ID), eq(ACTOR_ID));
        return captor.getValue();
    }

    @Test
    void patch_emptyPatch_keepsEverythingAndSendsIfMatchVersion() throws Exception {
        ProviderProfileSaveDto dto = patchAndCaptureSave("{}");

        assertEquals(new ProviderProfileSaveDto(1L, ProviderKind.MASTER, "About", Set.of(1L, 2L), 5L,
                "+380501234567", "plumber_master", "+380507654321", 4L), dto);
    }

    @Test
    void patch_aboutValue_changesOnlyIt() throws Exception {
        ProviderProfileSaveDto dto = patchAndCaptureSave("{\"about\":\"New\"}");

        assertEquals(new ProviderProfileSaveDto(1L, ProviderKind.MASTER, "New", Set.of(1L, 2L), 5L,
                "+380501234567", "plumber_master", "+380507654321", 4L), dto);
    }

    @Test
    void patch_aboutNull_clearsIt() throws Exception {
        ProviderProfileSaveDto dto = patchAndCaptureSave("{\"about\":null}");

        assertNull(dto.about());
        assertEquals("+380501234567", dto.phone());
    }

    @Test
    void patch_categoryIdsValue_replacesWholeList() throws Exception {
        ProviderProfileSaveDto dto = patchAndCaptureSave("{\"categoryIds\":[7]}");

        assertEquals(Set.of(7L), Set.copyOf(dto.categoryIds()));
        assertEquals(5L, dto.cityTaxonId());
    }

    @Test
    void patch_categoryIdsNull_clearsList() throws Exception {
        ProviderProfileSaveDto dto = patchAndCaptureSave("{\"categoryIds\":null}");

        assertTrue(dto.categoryIds() == null || dto.categoryIds().isEmpty());
    }

    @Test
    void patch_cityTaxonIdValue_changesOnlyIt() throws Exception {
        ProviderProfileSaveDto dto = patchAndCaptureSave("{\"cityTaxonId\":9}");

        assertEquals(9L, dto.cityTaxonId());
        assertEquals("About", dto.about());
    }

    @Test
    void patch_cityTaxonIdNull_clearsIt() throws Exception {
        ProviderProfileSaveDto dto = patchAndCaptureSave("{\"cityTaxonId\":null}");

        assertNull(dto.cityTaxonId());
        assertEquals("About", dto.about());
    }

    @Test
    void patch_phoneValue_changesOnlyIt() throws Exception {
        ProviderProfileSaveDto dto = patchAndCaptureSave("{\"phone\":\"+380991112233\"}");

        assertEquals("+380991112233", dto.phone());
        assertEquals("plumber_master", dto.telegram());
        assertEquals("+380507654321", dto.viber());
    }

    @Test
    void patch_phoneNull_clearsOnlyIt() throws Exception {
        ProviderProfileSaveDto dto = patchAndCaptureSave("{\"phone\":null}");

        assertNull(dto.phone());
        assertEquals("plumber_master", dto.telegram());
        assertEquals("+380507654321", dto.viber());
    }

    @Test
    void patch_telegramValue_changesOnlyIt() throws Exception {
        ProviderProfileSaveDto dto = patchAndCaptureSave("{\"telegram\":\"new_handle\"}");

        assertEquals("new_handle", dto.telegram());
        assertEquals("+380501234567", dto.phone());
        assertEquals("+380507654321", dto.viber());
    }

    @Test
    void patch_telegramNull_clearsOnlyIt() throws Exception {
        ProviderProfileSaveDto dto = patchAndCaptureSave("{\"telegram\":null}");

        assertNull(dto.telegram());
        assertEquals("+380501234567", dto.phone());
    }

    @Test
    void patch_viberValue_changesOnlyIt() throws Exception {
        ProviderProfileSaveDto dto = patchAndCaptureSave("{\"viber\":\"+380991112233\"}");

        assertEquals("+380991112233", dto.viber());
        assertEquals("+380501234567", dto.phone());
        assertEquals("plumber_master", dto.telegram());
    }

    @Test
    void patch_viberNull_clearsOnlyIt() throws Exception {
        ProviderProfileSaveDto dto = patchAndCaptureSave("{\"viber\":null}");

        assertNull(dto.viber());
        assertEquals("+380501234567", dto.phone());
    }

    @Test
    void patch_plainJsonContentType_isAccepted() throws Exception {
        when(saveService.findCurrentSnapshot(1L)).thenReturn(Optional.of(CURRENT));
        when(saveService.save(any(), eq(ACTOR_ID), eq(ACTOR_ID))).thenReturn(1L);
        when(readService.findById(1L)).thenReturn(Optional.of(ProviderProfileDto.builder().id(1L).build()));

        mockMvc.perform(patch("/api/provider-profiles/1").header("If-Match", "\"4\"")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"about\":\"New\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void patch_kindNull_returns400WithFieldError() throws Exception {
        when(saveService.findCurrentSnapshot(1L)).thenReturn(Optional.of(CURRENT));

        mockMvc.perform(patch("/api/provider-profiles/1").header("If-Match", "\"4\"")
                        .contentType("application/merge-patch+json").content("{\"kind\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.kind").exists());
        verify(saveService, never()).save(any(), any(), any());
    }

    @Test
    void patch_invalidPhonePattern_returns400WithFieldError() throws Exception {
        when(saveService.findCurrentSnapshot(1L)).thenReturn(Optional.of(CURRENT));

        mockMvc.perform(patch("/api/provider-profiles/1").header("If-Match", "\"4\"")
                        .contentType("application/merge-patch+json").content("{\"phone\":\"not a phone\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phone").exists());
        verify(saveService, never()).save(any(), any(), any());
    }

    @Test
    void patch_unknownMember_returns400() throws Exception {
        when(saveService.findCurrentSnapshot(1L)).thenReturn(Optional.of(CURRENT));

        mockMvc.perform(patch("/api/provider-profiles/1").header("If-Match", "\"4\"")
                        .contentType("application/merge-patch+json").content("{\"cityTaxonID\":9}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_missingIfMatch_returns400() throws Exception {
        mockMvc.perform(patch("/api/provider-profiles/1")
                        .contentType("application/merge-patch+json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void patch_unknownId_returns404() throws Exception {
        when(saveService.findCurrentSnapshot(1L)).thenReturn(Optional.empty());

        mockMvc.perform(patch("/api/provider-profiles/1").header("If-Match", "\"4\"")
                        .contentType("application/merge-patch+json").content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_staleVersion_returns412() throws Exception {
        when(saveService.findCurrentSnapshot(1L)).thenReturn(Optional.of(CURRENT));
        when(saveService.save(any(), eq(ACTOR_ID), eq(ACTOR_ID))).thenThrow(new StaleWriteException("stale"));

        mockMvc.perform(patch("/api/provider-profiles/1").header("If-Match", "\"4\"")
                        .contentType("application/merge-patch+json").content("{}"))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void patch_notOwner_returns403AndBodyHoldsNoContacts() throws Exception {
        when(saveService.findCurrentSnapshot(1L)).thenReturn(Optional.of(CURRENT));
        when(saveService.save(any(), eq(ACTOR_ID), eq(ACTOR_ID))).thenThrow(new AccessDeniedException("not the owner"));

        String response = mockMvc.perform(patch("/api/provider-profiles/1").header("If-Match", "\"4\"")
                        .contentType("application/merge-patch+json").content("{}"))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        assertFalse(response.contains("+380501234567") || response.contains("plumber_master") || response.contains("+380507654321"));
    }

    @Test
    void patch_textPlain_returns415() throws Exception {
        mockMvc.perform(patch("/api/provider-profiles/1").header("If-Match", "\"4\"")
                        .contentType(MediaType.TEXT_PLAIN).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ── delete ──────────────────────────────────────────────────────────

    @Test
    void delete_delegatesToSaveService() throws Exception {
        mockMvc.perform(delete("/api/provider-profiles/1").header("If-Match", "\"0\"")).andExpect(status().isNoContent());

        verify(saveService).delete(1L, ACTOR_ID, 0L);
    }

    @Test
    void delete_notOwner_returns403() throws Exception {
        doThrow(new AccessDeniedException("not the owner")).when(saveService).delete(1L, ACTOR_ID, 0L);

        mockMvc.perform(delete("/api/provider-profiles/1").header("If-Match", "\"0\"")).andExpect(status().isForbidden());
    }

    // ── restoreFromSnapshot ──────────────────────────────────────────────

    @Test
    void restoreFromSnapshot_delegatesAndReturnsResource() throws Exception {
        when(snapshotRestoreService.restoreProviderProfile(ACTOR_ID, 7L, 5L, 3L)).thenReturn(7L);
        when(readService.findById(7L)).thenReturn(Optional.of(ProviderProfileDto.builder().id(7L).build()));

        mockMvc.perform(post("/api/provider-profiles/7/history/5/restore").header("If-Match", "\"3\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));

        verify(snapshotRestoreService).restoreProviderProfile(ACTOR_ID, 7L, 5L, 3L);
    }

    @Test
    void restoreFromSnapshot_missingIfMatch_isBadRequest() throws Exception {
        mockMvc.perform(post("/api/provider-profiles/7/history/5/restore")).andExpect(status().isBadRequest());
    }

    @Test
    void restoreFromSnapshot_unknownSnapshot_returnsNotFound() throws Exception {
        when(snapshotRestoreService.restoreProviderProfile(ACTOR_ID, 7L, 5L, 3L))
                .thenThrow(new NoSuchElementException("Snapshot 5 not found"));

        mockMvc.perform(post("/api/provider-profiles/7/history/5/restore").header("If-Match", "\"3\""))
                .andExpect(status().isNotFound());
    }

    @Test
    void restoreFromSnapshot_staleVersion_returnsPreconditionFailed() throws Exception {
        when(snapshotRestoreService.restoreProviderProfile(ACTOR_ID, 7L, 5L, 3L)).thenThrow(new StaleWriteException("stale"));

        mockMvc.perform(post("/api/provider-profiles/7/history/5/restore").header("If-Match", "\"3\""))
                .andExpect(status().isPreconditionFailed());
    }
}
