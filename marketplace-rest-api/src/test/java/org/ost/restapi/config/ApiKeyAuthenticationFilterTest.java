package org.ost.restapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.ApiKeyManagementService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ApiKeyAuthenticationFilter} must never overwrite an authentication already established
 * by another mechanism (HTTP Basic, on the key-issuance endpoint) — same direct
 * {@code SecurityContextHolder} test pattern as {@code AuthContextServiceTest}, no mocking.
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock private ApiKeyManagementService apiKeyManagementService;
    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private FilterChain filterChain;

    private ApiKeyAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter(apiKeyManagementService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_validBearerKey_setsLongPrincipal() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer raw-key");
        when(apiKeyManagementService.resolveActorId("raw-key")).thenReturn(Optional.of(42L));

        filter.doFilterInternal(request, response, filterChain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isInstanceOf(PreAuthenticatedAuthenticationToken.class);
        assertThat(auth.getPrincipal()).isEqualTo(42L);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_unknownKey_leavesContextUnauthenticated() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer bad-key");
        when(apiKeyManagementService.resolveActorId("bad-key")).thenReturn(Optional.empty());

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_noAuthorizationHeader_neverCallsService() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(apiKeyManagementService, never()).resolveActorId(any());
    }

    @Test
    void doFilterInternal_nonBearerAuthorizationHeader_neverCallsService() throws Exception {
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(apiKeyManagementService, never()).resolveActorId(any());
    }

    @Test
    void doFilterInternal_authenticationAlreadyPresent_skipsBearerResolutionEntirely() throws Exception {
        Authentication existing = mock(Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(existing);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
        verify(request, never()).getHeader(any());
        verify(filterChain).doFilter(request, response);
    }
}
