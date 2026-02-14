package org.ost.advertisement.services.auth;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthContextServiceImpl implements AuthContextService {

    private static final Logger log = LoggerFactory.getLogger(AuthContextServiceImpl.class);

    @Override
    public Optional<User> getCurrentUser() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                return Optional.empty();
            }
            Object principal = auth.getPrincipal();
            if (principal instanceof UserPrincipal) {
                return Optional.ofNullable(((UserPrincipal) principal).user());
            }
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Failed to read current user from security context", ex);
            return Optional.empty();
        }
    }

    @Override
    public void updateCurrentUser(User user) {
        if (user == null) {
            log.warn("updateCurrentUser called with null user; skipping");
            return;
        }
        try {
            Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
            UserPrincipal principal = new UserPrincipal(user);

            Authentication newAuth;
            if (currentAuth != null) {
                newAuth = new UsernamePasswordAuthenticationToken(principal, currentAuth.getCredentials(), currentAuth.getAuthorities());
            } else {
                newAuth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
            }
            SecurityContextHolder.getContext().setAuthentication(newAuth);
            log.debug("Updated security principal for user id={}", user.getId());
        } catch (Exception ex) {
            log.error("Failed to update security principal", ex);
        }
    }
}
