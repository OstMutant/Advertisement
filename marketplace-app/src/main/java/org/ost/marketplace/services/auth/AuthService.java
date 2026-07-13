package org.ost.marketplace.services.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_LOGIN_ATTEMPTS = 5;

    private final Cache<String, AtomicInteger> loginAttempts = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(15))
            .maximumSize(10_000)
            .build();

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final HttpServletRequest request;
    private final HttpServletResponse response;

    public boolean login(@NonNull String email, @NonNull String rawPassword) {
        String key = request.getRemoteAddr() + "|" + email;
        AtomicInteger attempts = loginAttempts.get(key, _ -> new AtomicInteger(0));
        if (attempts.get() >= MAX_LOGIN_ATTEMPTS) {
            log.warn("Login blocked (rate limit): email={}", email);
            throw new IllegalStateException("Too many failed login attempts, try again later");
        }

        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, rawPassword));

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            securityContextRepository.saveContext(context, request, response);
            loginAttempts.invalidate(key);
            log.info("Login success: email={}", email);
            return true;

        } catch (BadCredentialsException _) {
            attempts.incrementAndGet();
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
