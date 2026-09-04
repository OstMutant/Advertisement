package org.ost.marketplace.services.auth;

import org.ost.orchestrator.spi.CurrentUserHook;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.spi.AuthenticatedPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthContextService implements CurrentUserHook {

    private static final Logger log = LoggerFactory.getLogger(AuthContextService.class);

    @Override
    public Optional<UserDto> getCurrentUser() {
        return currentPrincipal().map(AuthenticatedPrincipal::toUserDto);
    }

    @Override
    public Optional<String> getCurrentUserLocale() {
        return currentPrincipal().map(AuthenticatedPrincipal::locale);
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
