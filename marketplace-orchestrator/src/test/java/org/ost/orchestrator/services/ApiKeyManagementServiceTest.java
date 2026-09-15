package org.ost.orchestrator.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.apikey.dto.ApiKeySummaryDto;
import org.ost.platform.apikey.spi.ApiKeyPort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyManagementServiceTest {

    private static final Long USER_ID = 10L;

    @Mock private ComponentFactory<ApiKeyPort> apiKeyPortFactory;
    @Mock private ApiKeyPort apiKeyPort;

    private ApiKeyManagementService service;

    @BeforeEach
    void setUp() {
        service = new ApiKeyManagementService(apiKeyPortFactory);
        lenient().when(apiKeyPortFactory.get()).thenReturn(apiKeyPort);
        lenient().when(apiKeyPortFactory.findIfAvailable()).thenReturn(Optional.of(apiKeyPort));
    }

    @Test
    void create_delegatesToPort() {
        when(apiKeyPort.create(USER_ID, "label")).thenReturn("raw-key");

        String rawKey = service.create(USER_ID, "label");

        assertThat(rawKey).isEqualTo("raw-key");
    }

    @Test
    void resolveActorId_portAvailable_delegatesToPort() {
        when(apiKeyPort.resolveActorId("raw-key")).thenReturn(Optional.of(USER_ID));

        Optional<Long> resolved = service.resolveActorId("raw-key");

        assertThat(resolved).contains(USER_ID);
    }

    @Test
    void resolveActorId_starterAbsent_returnsEmpty() {
        when(apiKeyPortFactory.findIfAvailable()).thenReturn(Optional.empty());

        assertThat(service.resolveActorId("raw-key")).isEmpty();
    }

    @Test
    void listForActor_delegatesToPort() {
        ApiKeySummaryDto summary = new ApiKeySummaryDto(1L, "abc1234567", "label", Instant.now(), null, null);
        when(apiKeyPort.listForActor(USER_ID)).thenReturn(List.of(summary));

        List<ApiKeySummaryDto> keys = service.listForActor(USER_ID);

        assertThat(keys).containsExactly(summary);
    }

    @Test
    void revoke_delegatesToPort() {
        service.revoke(USER_ID, 5L);

        verify(apiKeyPort).revoke(USER_ID, 5L);
    }
}
