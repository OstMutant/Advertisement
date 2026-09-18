package org.ost.marketplace.services.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.core.FailureRateLimiter;
import org.ost.platform.core.TooManyAttemptsException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_LOGIN_ATTEMPTS = 5;

    private final FailureRateLimiter loginLimiter = new FailureRateLimiter(MAX_LOGIN_ATTEMPTS, Duration.ofMinutes(15));

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    public boolean login(@NonNull String email, @NonNull String rawPassword) {
        String key = request.getRemoteAddr() + "|" + email;
        try {
            loginLimiter.checkAllowed(key, "Too many failed login attempts, try again later");
        } catch (TooManyAttemptsException ex) {
            log.warn("Login blocked (rate limit): email={}", email);
            throw ex;
        }

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, rawPassword));

            request.changeSessionId(); // prevent session fixation

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
            loginLimiter.clear(key);
            log.info("Login success: email={}", email);
            return true;

        } catch (BadCredentialsException _) {
            loginLimiter.recordFailure(key);
            log.warn("Login failed (bad credentials): email={}", email);
            return false;
        }
    }

    public void logout() {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        log.info("Logout: user={}", current != null ? current.getName() : "unknown");
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
