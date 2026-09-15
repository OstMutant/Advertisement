package org.ost.marketplace.spi;

import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.auth.AuthContextService;
import org.ost.orchestrator.spi.SessionActorHook;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SessionActorHookImpl implements SessionActorHook {

    private final AuthContextService authContextService;

    @Override
    public Optional<Long> getCurrentActorId() {
        return authContextService.getCurrentActorId();
    }
}
