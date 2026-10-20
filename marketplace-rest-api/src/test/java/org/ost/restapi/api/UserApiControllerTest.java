package org.ost.restapi.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.AccessDeniedException;
import org.ost.orchestrator.services.AuthorizationService;
import org.ost.orchestrator.services.SnapshotRestoreService;
import org.ost.orchestrator.services.UserDeleteService;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.core.StaleWriteException;
import org.ost.platform.core.TooManyAttemptsException;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.user.dto.SettingsSnapshotDto;
import org.ost.platform.user.dto.UserProfileDto;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.platform.user.dto.UserSnapshotDto;
import org.ost.platform.user.model.Role;
import org.ost.restapi.api.audit.ActivityHistorySupport;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fast MockMvc contract tests for {@link UserApiController} -- mocked orchestrator services, no DB. */
@ExtendWith(MockitoExtension.class)
class UserApiControllerTest {

    private static final Long ACTOR_ID = 10L;

    @Mock private UserProfileService userProfileService;
    @Mock private AuthorizationService authorizationService;
    @Mock private UserDeleteService userDeleteService;
    @Mock private ActivityHistorySupport activityHistorySupport;
    @Mock private SnapshotRestoreService snapshotRestoreService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvc(new UserApiController(userProfileService, authorizationService, userDeleteService,
                activityHistorySupport, snapshotRestoreService));
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
                .thenReturn(Optional.of(new UserDto(1L, "New User", "new@example.com", Role.ADMIN, null, null, 0L)));
        String body = """
                {"name":"New User","email":"new@example.com","password":"password123"}""";

        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
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
        doThrow(new TooManyAttemptsException("Too many failed registration attempts, try again later"))
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

        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
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

    // ── history ──────────────────────────────────────────────────────────

    @Test
    void history_delegatesToSupport() throws Exception {
        when(activityHistorySupport.history(ACTOR_ID, EntityType.USER, 7L)).thenReturn(List.of());

        mockMvc.perform(get("/api/users/7/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void historySnapshot_unknown_returnsNotFound() throws Exception {
        when(activityHistorySupport.snapshot(eq(ACTOR_ID), eq(EntityType.USER), eq(7L), eq(5L), eq(UserSnapshotDto.class)))
                .thenThrow(new NoSuchElementException("Snapshot 5 not found"));

        mockMvc.perform(get("/api/users/7/history/5")).andExpect(status().isNotFound());
    }

    @Test
    void mySettingsHistory_delegatesToSupport() throws Exception {
        when(activityHistorySupport.history(ACTOR_ID, EntityType.USER_SETTINGS, ACTOR_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/users/me/settings/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void mySettingsHistorySnapshot_unknown_returnsNotFound() throws Exception {
        when(activityHistorySupport.snapshot(eq(ACTOR_ID), eq(EntityType.USER_SETTINGS), eq(ACTOR_ID), eq(5L), eq(SettingsSnapshotDto.class)))
                .thenThrow(new NoSuchElementException("Snapshot 5 not found"));

        mockMvc.perform(get("/api/users/me/settings/history/5")).andExpect(status().isNotFound());
    }

    // ── update ───────────────────────────────────────────────────────────

    @Test
    void update_validBody_savesWithParsedVersionThenReturnsFreshCopyWithETag() throws Exception {
        UserDto updated = new UserDto(2L, "Max Moderator", "max@example.com", Role.MODERATOR, null, null, 1L);
        when(userProfileService.findById(2L)).thenReturn(Optional.of(updated));
        String body = """
                {"name":"Max Moderator","role":"MODERATOR"}""";

        mockMvc.perform(put("/api/users/2").header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.role").value("MODERATOR"));

        verify(userProfileService).save(eq(new UserProfileDto(2L, "Max Moderator", Role.MODERATOR, 0L)), eq(ACTOR_ID));
    }

    @Test
    void update_serviceDeniesTheChange_returns403() throws Exception {
        doThrow(new AccessDeniedException("may not change the role")).when(userProfileService).save(any(), eq(ACTOR_ID));
        String body = """
                {"name":"Max Moderator","role":"ADMIN"}""";

        mockMvc.perform(put("/api/users/2").header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_staleVersion_returns412() throws Exception {
        doThrow(new StaleWriteException("stale")).when(userProfileService).save(any(), eq(ACTOR_ID));
        String body = """
                {"name":"Max Moderator","role":"MODERATOR"}""";

        mockMvc.perform(put("/api/users/2").header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void update_unknownUser_returns404() throws Exception {
        doThrow(new NoSuchElementException()).when(userProfileService).save(any(), eq(ACTOR_ID));
        String body = """
                {"name":"Max Moderator","role":"MODERATOR"}""";

        mockMvc.perform(put("/api/users/2").header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_blankName_returns400() throws Exception {
        String body = """
                {"name":"","role":"MODERATOR"}""";

        mockMvc.perform(put("/api/users/2").header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void update_missingRole_returns400() throws Exception {
        String body = """
                {"name":"Max Moderator"}""";

        mockMvc.perform(put("/api/users/2").header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.role").exists());
    }

    // ── delete ───────────────────────────────────────────────────────────

    @Test
    void delete_delegatesWithTheActorId_returns204() throws Exception {
        mockMvc.perform(delete("/api/users/2")).andExpect(status().isNoContent());

        verify(userDeleteService).delete(2L, ACTOR_ID);
    }

    @Test
    void delete_serviceDeniesIt_returns403() throws Exception {
        doThrow(new AccessDeniedException("may not edit account 2")).when(userDeleteService).delete(2L, ACTOR_ID);

        mockMvc.perform(delete("/api/users/2")).andExpect(status().isForbidden());
    }

    // ── changeMyLocale ───────────────────────────────────────────────────

    @Test
    void changeMyLocale_supportedLocale_delegatesAndReturns204() throws Exception {
        mockMvc.perform(put("/api/users/me/locale").contentType(MediaType.APPLICATION_JSON).content("""
                        {"locale":"uk"}"""))
                .andExpect(status().isNoContent());

        verify(userProfileService).updateLocale(ACTOR_ID, "uk");
    }

    @Test
    void changeMyLocale_unsupportedLocale_returns400() throws Exception {
        mockMvc.perform(put("/api/users/me/locale").contentType(MediaType.APPLICATION_JSON).content("""
                        {"locale":"de"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.locale").exists());
    }

    @Test
    void changeMyLocale_missingLocale_returns400() throws Exception {
        mockMvc.perform(put("/api/users/me/locale").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.locale").exists());
    }

    // ── updateSettings — positive ──────────────────────────────────────────────────

    @Test
    void updateSettings_validBody_savesThenReturnsFreshCopy() throws Exception {
        UserSettingsDto saved = UserSettingsDto.builder().adsPageSize(30).usersPageSize(30).timelinePageSize(20).providerProfilesPageSize(20).version(1).build();
        when(userProfileService.loadSettings(ACTOR_ID)).thenReturn(saved);
        String body = """
                {"adsPageSize":30,"usersPageSize":30,"timelinePageSize":20,"providerProfilesPageSize":20}""";

        mockMvc.perform(patch("/api/users/me/settings").header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adsPageSize").value(30));

        verify(userProfileService).saveSettings(eq(ACTOR_ID), any());
    }

    // ── updateSettings — negative — bean validation ──────────────────────────────────────────

    @Test
    void updateSettings_pageSizeBelowMinimum_returns400() throws Exception {
        String body = """
                {"adsPageSize":1,"usersPageSize":20,"timelinePageSize":20,"providerProfilesPageSize":20}""";

        mockMvc.perform(patch("/api/users/me/settings").header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.adsPageSize").exists());
    }

    @Test
    void updateSettings_pageSizeAboveMaximum_returns400() throws Exception {
        String body = """
                {"adsPageSize":20,"usersPageSize":500,"timelinePageSize":20,"providerProfilesPageSize":20}""";

        mockMvc.perform(patch("/api/users/me/settings").header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.usersPageSize").exists());
    }

    @Test
    void updateSettings_providerProfilesPageSizeBelowMinimum_returns400() throws Exception {
        String body = """
                {"adsPageSize":20,"usersPageSize":20,"timelinePageSize":20,"providerProfilesPageSize":1}""";

        mockMvc.perform(patch("/api/users/me/settings").header("If-Match", "\"0\"")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.providerProfilesPageSize").exists());
    }

    // ── restoreFromSnapshot ──────────────────────────────────────────────

    @Test
    void restoreFromSnapshot_delegatesAndReturnsResource() throws Exception {
        UserDto restored = new UserDto(7L, "Alice", "alice@example.com", Role.USER, null, null, 4L);
        when(userProfileService.findById(7L)).thenReturn(Optional.of(restored));

        mockMvc.perform(post("/api/users/7/history/5/restore").header("If-Match", "\"3\""))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"4\""))
                .andExpect(jsonPath("$.name").value("Alice"));

        verify(snapshotRestoreService).restoreUser(ACTOR_ID, 7L, 5L, 3L);
    }

    @Test
    void restoreFromSnapshot_missingIfMatch_isBadRequest() throws Exception {
        mockMvc.perform(post("/api/users/7/history/5/restore")).andExpect(status().isBadRequest());
    }

    @Test
    void restoreFromSnapshot_unknownSnapshot_returnsNotFound() throws Exception {
        doThrow(new NoSuchElementException("Snapshot 5 not found")).when(snapshotRestoreService).restoreUser(ACTOR_ID, 7L, 5L, 3L);

        mockMvc.perform(post("/api/users/7/history/5/restore").header("If-Match", "\"3\""))
                .andExpect(status().isNotFound());
    }

    @Test
    void restoreFromSnapshot_staleVersion_returnsPreconditionFailed() throws Exception {
        doThrow(new StaleWriteException("stale")).when(snapshotRestoreService).restoreUser(ACTOR_ID, 7L, 5L, 3L);

        mockMvc.perform(post("/api/users/7/history/5/restore").header("If-Match", "\"3\""))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void restoreMySettingsFromSnapshot_delegatesAndReturnsSettings() throws Exception {
        UserSettingsDto restored = UserSettingsDto.builder().adsPageSize(30).usersPageSize(20).timelinePageSize(20)
                .providerProfilesPageSize(20).version(4).build();
        when(userProfileService.loadSettings(ACTOR_ID)).thenReturn(restored);

        mockMvc.perform(post("/api/users/me/settings/history/5/restore").header("If-Match", "\"3\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adsPageSize").value(30));

        verify(snapshotRestoreService).restoreSettings(ACTOR_ID, ACTOR_ID, 5L, 3L);
    }
}
