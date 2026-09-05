package org.ost.restapi.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.ApiKeyManagementService;
import org.ost.platform.apikey.dto.ApiKeySummaryDto;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.spi.AuthenticatedPrincipal;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rawKey").value("raw-key"));
    }

    @Test
    void create_noRequestBody_passesNullLabel() throws Exception {
        authenticateAs(testPrincipal());
        when(apiKeyManagementService.create(ACTOR_ID, null)).thenReturn("raw-key");

        mockMvc.perform(post("/api/api-keys")).andExpect(status().isOk());

        verify(apiKeyManagementService).create(ACTOR_ID, null);
    }

    // ── list (bearer) ──────────────────────────────────────────────────

    @Test
    void list_delegatesToServiceWithActorId() throws Exception {
        authenticateAs(ACTOR_ID);
        ApiKeySummaryDto summary = new ApiKeySummaryDto(1L, "abc1234567", "label", Instant.now(), null, null);
        when(apiKeyManagementService.listForActor(ACTOR_ID)).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    // ── revoke (bearer) ──────────────────────────────────────────────────

    @Test
    void revoke_delegatesToServiceWithActorIdAndKeyId() throws Exception {
        authenticateAs(ACTOR_ID);

        mockMvc.perform(delete("/api/api-keys/5")).andExpect(status().isOk());

        verify(apiKeyManagementService).revoke(ACTOR_ID, 5L);
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
