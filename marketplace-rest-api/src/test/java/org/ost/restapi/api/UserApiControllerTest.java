package org.ost.restapi.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.AccessDeniedException;
import org.ost.orchestrator.services.AuthorizationService;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.platform.user.model.Role;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.authenticateAs;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.clearAuthentication;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.mockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserApiControllerTest {

    private static final Long ACTOR_ID = 10L;

    @Mock private UserProfileService userProfileService;
    @Mock private AuthorizationService authorizationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvc(new UserApiController(userProfileService, authorizationService));
        authenticateAs(ACTOR_ID);
        lenient().when(userProfileService.resolveUsersPageSize(any())).thenReturn(20);
    }

    @AfterEach
    void tearDown() {
        clearAuthentication();
    }

    // ── register — positive ──────────────────────────────────────────────────

    @Test
    void register_validBody_registersThenResolvesIdByEmail() throws Exception {
        when(userProfileService.findByEmail("new@example.com"))
                .thenReturn(Optional.of(new UserDto(1L, "New User", "new@example.com", null, null, null, 0L)));
        String body = """
                {"name":"New User","email":"new@example.com","password":"password123"}""";

        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("new@example.com"));
    }

    // ── register — negative — bean validation ──────────────────────────────────────────

    @Test
    void register_blankName_returns400() throws Exception {
        String body = """
                {"name":"","email":"new@example.com","password":"password123"}""";

        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void register_invalidEmailFormat_returns400() throws Exception {
        String body = """
                {"name":"New User","email":"not-an-email","password":"password123"}""";

        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
    }

    @Test
    void register_passwordUnderMinLength_returns400() throws Exception {
        String body = """
                {"name":"New User","email":"new@example.com","password":"short"}""";

        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    // ── register — negative — ApiExceptionHandler mappings ──────────────────────────────

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        doThrow(new DuplicateKeyException("duplicate email")).when(userProfileService).register(any(), anyString());
        String body = """
                {"name":"New User","email":"taken@example.com","password":"password123"}""";

        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void register_tooManyAttempts_returns429() throws Exception {
        doThrow(new IllegalStateException("Too many failed registration attempts, try again later"))
                .when(userProfileService).register(any(), anyString());
        String body = """
                {"name":"New User","email":"new@example.com","password":"password123"}""";

        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
    }

    // ── list — positive ──────────────────────────────────────────────────

    @Test
    void list_privilegedActor_delegatesToServiceAndSetsTotalCountHeader() throws Exception {
        UserDto user = new UserDto(1L, "Alice", "alice@example.com", Role.USER, null, null, 0L);
        UserFilterDto filter = UserFilterDto.empty();
        when(userProfileService.getFiltered(eq(filter), eq(0), eq(20), eq(Sort.unsorted()))).thenReturn(List.of(user));
        when(userProfileService.count(eq(filter))).thenReturn(1);

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].email").value("alice@example.com"));
    }

    @Test
    void list_unknownSortField_returns400() throws Exception {
        mockMvc.perform(get("/api/users").param("sort", "secretField")).andExpect(status().isBadRequest());
    }

    @Test
    void list_pageSizeComesFromCallersSavedSettings_urlSizeParamIsIgnored() throws Exception {
        UserFilterDto filter = UserFilterDto.empty();
        when(userProfileService.resolveUsersPageSize(ACTOR_ID)).thenReturn(50);
        when(userProfileService.getFiltered(eq(filter), eq(0), eq(50), any())).thenReturn(List.of());
        when(userProfileService.count(eq(filter))).thenReturn(0);

        // "size=5" in the URL must have no effect -- there is no such request parameter anymore.
        mockMvc.perform(get("/api/users").param("size", "5")).andExpect(status().isOk());

        verify(userProfileService).getFiltered(eq(filter), eq(0), eq(50), any());
    }

    // ── list — negative — not privileged ──────────────────────────────────────────

    @Test
    void list_nonPrivilegedActor_returns403() throws Exception {
        doThrow(new AccessDeniedException("not privileged")).when(authorizationService).requireIsPrivileged(ACTOR_ID);

        mockMvc.perform(get("/api/users")).andExpect(status().isForbidden());
    }

    // ── getById ──────────────────────────────────────────────────────────

    @Test
    void getById_privilegedActor_returnsIt() throws Exception {
        UserDto user = new UserDto(1L, "Alice", "alice@example.com", Role.USER, null, null, 0L);
        when(userProfileService.findById(1L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/1")).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        when(userProfileService.findById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/users/1")).andExpect(status().isNotFound());
    }

    @Test
    void getById_nonPrivilegedActor_returns403() throws Exception {
        doThrow(new AccessDeniedException("not privileged")).when(authorizationService).requireIsPrivileged(ACTOR_ID);

        mockMvc.perform(get("/api/users/1")).andExpect(status().isForbidden());
    }

    // ── updateSettings — positive ──────────────────────────────────────────────────

    @Test
    void updateSettings_validBody_savesThenReturnsFreshCopy() throws Exception {
        UserSettingsDto saved = UserSettingsDto.builder().adsPageSize(30).usersPageSize(30).timelinePageSize(20).version(1).build();
        when(userProfileService.loadSettings(ACTOR_ID)).thenReturn(saved);
        String body = """
                {"adsPageSize":30,"usersPageSize":30,"timelinePageSize":20,"version":0}""";

        mockMvc.perform(patch("/api/users/me/settings").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adsPageSize").value(30));

        verify(userProfileService).saveSettings(eq(ACTOR_ID), any());
    }

    // ── updateSettings — negative — bean validation ──────────────────────────────────────────

    @Test
    void updateSettings_pageSizeBelowMinimum_returns400() throws Exception {
        String body = """
                {"adsPageSize":1,"usersPageSize":20,"timelinePageSize":20,"version":0}""";

        mockMvc.perform(patch("/api/users/me/settings").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.adsPageSize").exists());
    }

    @Test
    void updateSettings_pageSizeAboveMaximum_returns400() throws Exception {
        String body = """
                {"adsPageSize":20,"usersPageSize":500,"timelinePageSize":20,"version":0}""";

        mockMvc.perform(patch("/api/users/me/settings").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.usersPageSize").exists());
    }
}
