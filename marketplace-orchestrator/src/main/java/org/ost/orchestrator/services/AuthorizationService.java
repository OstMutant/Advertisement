package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.spi.UserAuthorizationPort;
import org.springframework.stereotype.Service;

/**
 * Application-level role and ownership checks, reused by marketplace-app's AccessEvaluator.
 * Mandatory {@link UserAuthorizationPort} dependency, same shape as {@link UserDeleteService}'s
 * {@code UserAccountPort} field -- user-spring-boot-starter is a compile-scope, non-optional
 * dependency of the final app, so this isn't wrapped in {@code ComponentFactory}.
 */
@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final UserAuthorizationPort authorizationPort;

    public boolean isAdmin(@NonNull UserDto user) {
        return authorizationPort.isAdmin(user);
    }

    public boolean isModerator(@NonNull UserDto user) {
        return authorizationPort.isModerator(user);
    }

    public boolean isOwner(@NonNull UserDto user, @NonNull Long ownerId) {
        return authorizationPort.isOwner(user, ownerId);
    }
}
