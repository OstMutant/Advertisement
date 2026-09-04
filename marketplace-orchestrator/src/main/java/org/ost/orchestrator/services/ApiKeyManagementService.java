package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.apikey.dto.ApiKeySummaryDto;
import org.ost.platform.apikey.spi.ApiKeyPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Wraps {@link ApiKeyPort} so marketplace-app never holds a direct {@code ApiKeyPort} reference. */
@Service
@RequiredArgsConstructor
public class ApiKeyManagementService {

    private final ComponentFactory<ApiKeyPort> apiKeyPortFactory;

    public String create(@NonNull Long userId, String label) {
        return apiKeyPortFactory.get().create(userId, label);
    }

    public Optional<Long> resolveActorId(@NonNull String rawKey) {
        return apiKeyPortFactory.findIfAvailable().flatMap(p -> p.resolveActorId(rawKey));
    }

    public List<ApiKeySummaryDto> listForActor(@NonNull Long userId) {
        return apiKeyPortFactory.get().listForActor(userId);
    }

    public void revoke(@NonNull Long userId, @NonNull Long keyId) {
        apiKeyPortFactory.get().revoke(userId, keyId);
    }
}
