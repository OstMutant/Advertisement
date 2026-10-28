package org.ost.restapi.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.ApiKeyManagementService;
import org.ost.platform.apikey.dto.ApiKeySummaryDto;
import org.ost.platform.apikey.model.ApiKeyAlreadyRevokedException;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.spi.AuthenticatedPrincipal;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.authenticateAs;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.clearAuthentication;
import static org.ost.restapi.api.support.RestApiMockMvcTestSupport.mockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fast MockMvc contract tests for {@link ApiKeyController} -- mocked orchestrator services, no DB. */
@ExtendWith(MockitoExtension.class)
class ApiKeyControllerTest {

    private static final Long ACTOR_ID = 10L;

    @Mock private ApiKeyManagementService apiKeyManagementService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = mockMvc(new ApiKeyController(apiKeyManagementService));
    }

    @AfterEach
    void tearDown() {
        clearAuthentication();
    }

    // ── create (Basic auth) ──────────────────────────────────────────────────

    @Test
    void create_withLabel_delegatesToServiceWithPrincipalsUserId() throws Exception {
        authenticateAs(testPrincipal());
        when(apiKeyManagementService.create(ACTOR_ID, "my key")).thenReturn("raw-key");

        mockMvc.perform(post("/api/api-keys").contentType(MediaType.APPLICATION_JSON).content("""
                        {"label":"my key"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rawKey").value("raw-key"));
    }

    @Test
    void create_noRequestBody_passesNullLabel() throws Exception {
        authenticateAs(testPrincipal());
        when(apiKeyManagementService.create(ACTOR_ID, null)).thenReturn("raw-key");

        mockMvc.perform(post("/api/api-keys")).andExpect(status().isCreated());

        verify(apiKeyManagementService).create(ACTOR_ID, null);
    }

    // ── list (bearer) ──────────────────────────────────────────────────

    @Test
    void list_delegatesToServiceWithActorId() throws Exception {
        authenticateAs(ACTOR_ID);
        ApiKeySummaryDto summary = new ApiKeySummaryDto(1L, "abc1234567", "label", Instant.now(), null, null);
        when(apiKeyManagementService.listForActor(ACTOR_ID, false)).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void list_includeRevoked_passesFlagToService() throws Exception {
        authenticateAs(ACTOR_ID);
        ApiKeySummaryDto summary = new ApiKeySummaryDto(1L, "abc1234567", "label", Instant.now(), null, Instant.now());
        when(apiKeyManagementService.listForActor(ACTOR_ID, true)).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/api-keys?includeRevoked=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].revokedAt").exists());
    }

    // ── revoke (bearer) ──────────────────────────────────────────────────

    @Test
    void revoke_delegatesToServiceWithActorIdAndKeyId() throws Exception {
        authenticateAs(ACTOR_ID);

        mockMvc.perform(delete("/api/api-keys/5")).andExpect(status().isNoContent());

        verify(apiKeyManagementService).revoke(ACTOR_ID, 5L);
    }

    @Test
    void revoke_unknownKey_returnsNotFound() throws Exception {
        authenticateAs(ACTOR_ID);
        doThrow(new NoSuchElementException("API key not found")).when(apiKeyManagementService).revoke(ACTOR_ID, 6L);

        mockMvc.perform(delete("/api/api-keys/6"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resource not found."));
    }

    @Test
    void revoke_alreadyRevoked_returnsConflict() throws Exception {
        authenticateAs(ACTOR_ID);
        doThrow(new ApiKeyAlreadyRevokedException("API key is already revoked")).when(apiKeyManagementService).revoke(ACTOR_ID, 7L);

        mockMvc.perform(delete("/api/api-keys/7"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("API key is already revoked"));
    }

    private static AuthenticatedPrincipal testPrincipal() {
        UserDto userDto = new UserDto(ACTOR_ID, "Name", "user@example.com", null, null, null, 0L);
        return new AuthenticatedPrincipal() {
            @Override
            public UserDto toUserDto() {
                return userDto;
            }

            @Override
            public String locale() {
                return "en";
            }
        };
    }
}
