package org.ost.marketplace.services.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers improvement-045 item 3: the login-attempt rate-limiting counter in {@link AuthService}
 * (5 attempts / 15 min, keyed on {@code remoteAddr|email}). Scoped to the threshold/key/reset
 * logic that is actually ours to get wrong — not to Caffeine's own time-based expiry, which would
 * require either a real 15-minute wait or refactoring production code to accept an injectable
 * {@code Ticker} (out of scope for a test-only change). See {@code UserServiceTest} in
 * {@code integration-tests} for the equivalent coverage of {@code UserService.register()}'s
 * asymmetric counter (no key-on-email, no invalidate-on-success).
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String REMOTE_ADDR = "127.0.0.1";

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private SecurityContextRepository securityContextRepository;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authenticationManager, securityContextRepository, request, response);
        when(request.getRemoteAddr()).thenReturn(REMOTE_ADDR);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // doReturn/doThrow, not when(...).thenReturn/thenThrow — these two helpers re-stub the same
    // mock method back and forth within a single test; when(...) would invoke the mock to record
    // the stub, which re-triggers whatever throwing behavior is already configured, before the
    // new stub can be attached.
    private void stubSuccess() {
        Authentication authentication = mock(Authentication.class);
        doReturn(authentication).when(authenticationManager).authenticate(any());
    }

    private void stubBadCredentials() {
        doThrow(new BadCredentialsException("bad credentials")).when(authenticationManager).authenticate(any());
    }

    @Test
    void login_success_returnsTrue() {
        stubSuccess();
        assertThat(authService.login("user@example.com", "password")).isTrue();
    }

    @Test
    void login_badCredentials_returnsFalse() {
        stubBadCredentials();
        assertThat(authService.login("user@example.com", "wrong")).isFalse();
    }

    @Test
    void login_success_rotatesSessionId() {
        stubSuccess();
        authService.login("user@example.com", "password");
        verify(request).changeSessionId();
    }

    @Test
    void login_badCredentials_doesNotRotateSessionId() {
        stubBadCredentials();
        authService.login("user@example.com", "wrong");
        verify(request, never()).changeSessionId();
    }

    @Test
    void login_thresholdReached_throwsIllegalStateException_beforeAttemptingAuthentication() {
        stubBadCredentials();
        String email = "user@example.com";

        for (int i = 0; i < 5; i++) {
            authService.login(email, "wrong");
        }

        assertThatThrownBy(() -> authService.login(email, "wrong"))
                .isInstanceOf(IllegalStateException.class);
        verify(authenticationManager, times(5)).authenticate(any());
    }

    @Test
    void login_successAfterFailures_resetsAttempts() {
        String email = "user@example.com";

        stubBadCredentials();
        for (int i = 0; i < 4; i++) {
            authService.login(email, "wrong");
        }

        stubSuccess();
        assertThat(authService.login(email, "correct")).isTrue();

        // If the earlier success hadn't reset the counter, it would already be at 4 here and this
        // second post-success failure would push it to 5 and the *next* call would throw — instead
        // of asserting that indirectly, drive it past where a non-reset counter would have blocked.
        stubBadCredentials();
        for (int i = 0; i < 4; i++) {
            assertThat(authService.login(email, "wrong")).isFalse();
        }
        assertThat(authService.login(email, "wrong")).isFalse();
    }

    @Test
    void login_differentEmailsSameIp_trackedSeparately() {
        stubBadCredentials();
        String blockedEmail = "blocked@example.com";
        String otherEmail = "other@example.com";

        for (int i = 0; i < 5; i++) {
            authService.login(blockedEmail, "wrong");
        }
        assertThatThrownBy(() -> authService.login(blockedEmail, "wrong"))
                .isInstanceOf(IllegalStateException.class);

        // Same IP, different email — must not be blocked by blockedEmail's attempts.
        assertThat(authService.login(otherEmail, "wrong")).isFalse();
    }
}
