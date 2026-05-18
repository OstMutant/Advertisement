package org.ost.marketplace.services.auth;

import lombok.RequiredArgsConstructor;
import org.ost.marketplace.entities.User;
import org.ost.platform.core.spi.CurrentUserProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CurrentUserProviderImpl implements CurrentUserProvider {

    private final AuthContextService authContextService;

    @Override
    public Optional<Long> getCurrentUserId() {
        return authContextService.getCurrentUser().map(User::getId);
    }
}
