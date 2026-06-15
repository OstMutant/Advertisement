package org.ost.marketplace.spi;

import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.auth.AuthContextService;
import org.ost.platform.core.spi.CurrentActorHook;
import org.ost.platform.user.dto.UserDto;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CurrentActorHookImpl implements CurrentActorHook {

    private final AuthContextService authContextService;

    @Override
    public Optional<Long> getCurrentActorId() {
        return authContextService.getCurrentUser().map(UserDto::id);
    }
}
