package org.ost.marketplace.services.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ost.platform.user.dto.UserDto;
import org.ost.platform.user.spi.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AuthContextService#getCurrentUser} is the single source {@code AccessEvaluator} (and
 * everything downstream of it) relies on for "who is the current user". No mocking of
 * {@code SecurityContextHolder} itself needed -- it's set directly, the standard Spring Security
 * test pattern (improvement-048).
 */
class AuthContextServiceTest {

    private final AuthContextService service = new AuthContextService();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_authenticatedWithAuthenticatedPrincipal_returnsUserDto() {
        UserDto userDto = new UserDto(1L, "Name", "user@example.com", null, null, null, "en", 0L);
        AuthenticatedPrincipal principal = () -> userDto;
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(principal);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<UserDto> result = service.getCurrentUser();

        assertThat(result).contains(userDto);
    }

    @Test
    void getCurrentUser_noAuthenticationInContext_returnsEmpty() {
        Optional<UserDto> result = service.getCurrentUser();

        assertThat(result).isEmpty();
    }

    @Test
    void getCurrentUser_notAuthenticated_returnsEmpty() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<UserDto> result = service.getCurrentUser();

        assertThat(result).isEmpty();
    }

    @Test
    void getCurrentUser_principalIsNotAuthenticatedPrincipal_returnsEmpty() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("anonymousUser");
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<UserDto> result = service.getCurrentUser();

        assertThat(result).isEmpty();
    }

    @Test
    void getCurrentUser_authenticationThrows_returnsEmptyNotException() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenThrow(new RuntimeException("boom"));
        SecurityContextHolder.getContext().setAuthentication(auth);

        Optional<UserDto> result = service.getCurrentUser();

        assertThat(result).isEmpty();
    }
}
