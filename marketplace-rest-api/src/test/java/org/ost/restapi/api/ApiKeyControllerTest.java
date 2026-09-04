package org.ost.restapi.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.ApiKeyManagementService;
import org.ost.platform.apikey.dto.ApiKeySummaryDto;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.spi.AuthenticatedPrincipal;
import org.ost.restapi.api.ApiKeyController.ApiKeyCreateRequest;
import org.ost.restapi.api.ApiKeyController.ApiKeyCreatedResponse;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyControllerTest {

    private static final Long ACTOR_ID = 10L;

    @Mock private ApiKeyManagementService apiKeyManagementService;

    private ApiKeyController controller;

    @BeforeEach
    void setUp() {
        controller = new ApiKeyController(apiKeyManagementService);
    }

    @Test
    void create_delegatesToServiceWithPrincipalsUserId() {
        AuthenticatedPrincipal principal = testPrincipal();
        when(apiKeyManagementService.create(ACTOR_ID, "my key")).thenReturn("raw-key");

        ApiKeyCreatedResponse response = controller.create(principal, new ApiKeyCreateRequest("my key"));

        assertThat(response.rawKey()).isEqualTo("raw-key");
    }

    @Test
    void create_noRequestBody_passesNullLabel() {
        AuthenticatedPrincipal principal = testPrincipal();
        when(apiKeyManagementService.create(ACTOR_ID, null)).thenReturn("raw-key");

        controller.create(principal, null);

        verify(apiKeyManagementService).create(ACTOR_ID, null);
    }

    @Test
    void list_delegatesToServiceWithActorId() {
        ApiKeySummaryDto summary = new ApiKeySummaryDto(1L, "abc1234567", "label", Instant.now(), null, null);
        when(apiKeyManagementService.listForActor(ACTOR_ID)).thenReturn(List.of(summary));

        List<ApiKeySummaryDto> keys = controller.list(ACTOR_ID);

        assertThat(keys).containsExactly(summary);
    }

    @Test
    void revoke_delegatesToServiceWithActorIdAndKeyId() {
        controller.revoke(ACTOR_ID, 5L);

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
