package org.ost.marketplace.services.auth;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.user.entity.User;
import org.ost.marketplace.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthContextService {

    private static final Logger log = LoggerFactory.getLogger(AuthContextService.class);

    public Optional<User> getCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return Optional.empty();
            }
            Object principal = auth.getPrincipal();
            if (principal instanceof UserPrincipal(User user)) {
                return Optional.ofNullable(user);
            }
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Failed to read current user from security context", ex);
            return Optional.empty();
        }
    }

    public void updateCurrentUser(@NonNull User user) {
        try {
            Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
            UserPrincipal principal = new UserPrincipal(user);

            Authentication newAuth = (currentAuth != null)
                    ? new UsernamePasswordAuthenticationToken(principal, currentAuth.getCredentials(), principal.getAuthorities())
                    : new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

            SecurityContextHolder.getContext().setAuthentication(newAuth);
            log.debug("Updated security principal for user id={}", user.getId());
        } catch (Exception ex) {
            log.error("Failed to update security principal", ex);
        }
    }
}
