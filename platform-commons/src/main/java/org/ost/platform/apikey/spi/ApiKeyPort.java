package org.ost.platform.apikey.spi;

import lombok.NonNull;
import org.ost.platform.apikey.dto.ApiKeySummaryDto;

import java.util.List;
import java.util.Optional;

/**
 * Issues, resolves, lists, and revokes per-actor API keys for bearer-token authentication on the
 * external REST API. Implementation lives in apikey-spring-boot-starter.
 */
public interface ApiKeyPort {

    String create(@NonNull Long actorId, String label);

    Optional<Long> resolveActorId(@NonNull String rawKey);

    List<ApiKeySummaryDto> listForActor(@NonNull Long actorId);

    void revoke(@NonNull Long actorId, @NonNull Long keyId);

    void deleteAllForActor(@NonNull Long actorId);
}
