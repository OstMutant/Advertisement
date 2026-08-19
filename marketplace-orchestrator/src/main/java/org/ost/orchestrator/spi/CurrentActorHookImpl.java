package org.ost.orchestrator.spi;

import lombok.RequiredArgsConstructor;
import org.ost.platform.core.spi.CurrentActorHook;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CurrentActorHookImpl implements CurrentActorHook {

    private final SessionActorHook sessionActorHook;

    @Override
    public Optional<Long> getCurrentActorId() {
        return sessionActorHook.getCurrentActorId();
    }
}
