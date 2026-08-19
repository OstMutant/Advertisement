package org.ost.orchestrator.services;

import lombok.RequiredArgsConstructor;
import org.ost.orchestrator.spi.CurrentUserHook;
import org.ost.platform.user.dto.UserDto;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Thin wrapper over {@link CurrentUserHook}, reused by any adapter routing through the orchestrator. */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final CurrentUserHook currentUserHook;

    public Optional<UserDto> getCurrentUser() {
        return currentUserHook.getCurrentUser();
    }

    public Optional<String> getCurrentUserLocale() {
        return currentUserHook.getCurrentUserLocale();
    }
}
