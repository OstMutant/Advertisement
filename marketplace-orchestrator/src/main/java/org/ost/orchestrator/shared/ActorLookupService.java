package org.ost.orchestrator.shared;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.spi.UserPort;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Shared read-only {@link UserPort} lookups reused by every domain's display-enrichment step. */
@Service
@RequiredArgsConstructor
public class ActorLookupService {

    private final ComponentFactory<UserPort> userPortFactory;

    public Map<Long, UserDto> findByIds(@NonNull Set<Long> ids) {
        return userPortFactory.findIfAvailable()
                .map(p -> p.findByIds(ids))
                .orElse(Map.of());
    }

    public Optional<UserDto> findById(@NonNull Long id) {
        return userPortFactory.findIfAvailable().flatMap(p -> p.findById(id));
    }
}
