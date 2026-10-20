package org.ost.apikey.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.apikey.entity.ApiKey;
import org.ost.apikey.repository.ApiKeyRepository;
import org.ost.apikey.security.ApiKeyHasher;
import org.ost.platform.apikey.dto.ApiKeySummaryDto;
import org.ost.platform.apikey.model.ApiKeyAlreadyRevokedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Issues, resolves, lists, revokes, and bulk-deletes API keys -- the only business-logic class
 * this starter carries.
 */
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private static final int KEY_PREFIX_LENGTH = 10;

    private final ApiKeyRepository repository;
    private final ApiKeyHasher hasher;

    @Transactional
    public String create(@NonNull Long actorId, String label) {
        String rawKey = hasher.generate();
        repository.save(ApiKey.builder()
                .actorId(actorId)
                .keyHash(hasher.hash(rawKey))
                .keyPrefix(rawKey.substring(0, KEY_PREFIX_LENGTH))
                .label(label)
                .build());
        return rawKey;
    }

    public Optional<Long> resolveActorId(@NonNull String rawKey) {
        Optional<ApiKey> found = repository.findActiveByKeyHash(hasher.hash(rawKey));
        found.ifPresent(key -> repository.touchLastUsed(key.getId()));
        return found.map(ApiKey::getActorId);
    }

    public List<ApiKeySummaryDto> listForActor(@NonNull Long actorId, boolean includeRevoked) {
        return repository.findByActorId(actorId).stream().filter(key -> includeRevoked || key.getRevokedAt() == null).map(ApiKey::toSummaryDto).toList();
    }

    @Transactional
    public void revoke(@NonNull Long actorId, @NonNull Long keyId) {
        ApiKey key = repository.findByIdAndActorId(keyId, actorId).orElseThrow(() -> new NoSuchElementException("API key not found"));
        if (key.getRevokedAt() != null) {
            throw new ApiKeyAlreadyRevokedException("API key is already revoked");
        }
        repository.revoke(actorId, keyId);
    }

    @Transactional
    public void deleteAllForActor(@NonNull Long actorId) {
        repository.deleteAllForActor(actorId);
    }
}
