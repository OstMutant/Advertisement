package org.ost.apikey.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.apikey.services.ApiKeyService;
import org.ost.platform.apikey.dto.ApiKeySummaryDto;
import org.ost.platform.apikey.spi.ApiKeyPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/** Pure delegation implementing {@link ApiKeyPort} -- no business logic of its own. */
@Service
@RequiredArgsConstructor
public class ApiKeyPortImpl implements ApiKeyPort {

    private final ApiKeyService apiKeyService;

    @Override
    public String create(@NonNull Long actorId, String label) {
        return apiKeyService.create(actorId, label);
    }

    @Override
    public Optional<Long> resolveActorId(@NonNull String rawKey) {
        return apiKeyService.resolveActorId(rawKey);
    }

    @Override
    public List<ApiKeySummaryDto> listForActor(@NonNull Long actorId) {
        return apiKeyService.listForActor(actorId);
    }

    @Override
    public void revoke(@NonNull Long actorId, @NonNull Long keyId) {
        apiKeyService.revoke(actorId, keyId);
    }

    @Override
    public void deleteAllForActor(@NonNull Long actorId) {
        apiKeyService.deleteAllForActor(actorId);
    }
}
