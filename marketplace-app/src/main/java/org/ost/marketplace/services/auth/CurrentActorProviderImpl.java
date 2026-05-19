package org.ost.marketplace.services.auth;

import lombok.RequiredArgsConstructor;
import org.ost.marketplace.entities.User;
import org.ost.platform.core.spi.CurrentActorProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CurrentActorProviderImpl implements CurrentActorProvider {

    private final AuthContextService authContextService;

    @Override
    public Optional<Long> getCurrentActorId() {
        return authContextService.getCurrentUser().map(User::getId);
    }
}
