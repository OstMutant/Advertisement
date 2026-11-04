package org.ost.marketplace.services.auth;

import org.ost.orchestrator.spi.CurrentUserHook;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.spi.AuthenticatedPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.orchestrator.services.UserProfileService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/** Resolves the acting user from {@code SecurityContextHolder} for both a Vaadin session principal and a REST bearer-token principal. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthContextService implements CurrentUserHook {

    private final UserProfileService userProfileService;

    @Override
    public Optional<UserDto> getCurrentUser() {
        return currentPrincipal().map(AuthenticatedPrincipal::toUserDto);
    }

    /** A Vaadin session carries the language chosen at sign-in; a REST caller holds only a user id, so their saved language is read. */
    @Override
    public Optional<String> getCurrentUserLocale() {
        Optional<AuthenticatedPrincipal> principal = currentPrincipal();
        if (principal.isPresent()) {
            return principal.map(AuthenticatedPrincipal::locale);
        }
        return getCurrentActorId().flatMap(userProfileService::findLocale);
    }

    /**
     * Resolves the acting user's id from either a Vaadin session principal ({@link AuthenticatedPrincipal})
     * or a REST bearer-token principal (a plain {@link Long}, set by {@code ApiKeyAuthenticationFilter}).
     */
    public Optional<Long> getCurrentActorId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return Optional.empty();
            }
            if (auth.getPrincipal() instanceof AuthenticatedPrincipal p) {
                return Optional.of(p.toUserDto().id());
            }
            if (auth.getPrincipal() instanceof Long userId) {
                return Optional.of(userId);
            }
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Failed to read current actor id from security context", ex);
            return Optional.empty();
        }
    }

    private Optional<AuthenticatedPrincipal> currentPrincipal() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return Optional.empty();
            }
            if (auth.getPrincipal() instanceof AuthenticatedPrincipal p) {
                return Optional.of(p);
            }
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Failed to read current user from security context", ex);
            return Optional.empty();
        }
    }
}
