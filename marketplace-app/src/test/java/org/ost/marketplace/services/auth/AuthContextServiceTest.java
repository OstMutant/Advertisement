package org.ost.marketplace.services.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.UserProfileService;
import org.ost.platform.user.spi.AuthenticatedPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The current user's language: carried by a Vaadin session principal, read from the saved preferences for a REST caller who holds only an id. */
@ExtendWith(MockitoExtension.class)
class AuthContextServiceTest {

    @Mock private UserProfileService userProfileService;

    private AuthContextService service;

    @BeforeEach
    void setUp() {
        service = new AuthContextService(userProfileService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void restCaller_getsTheSavedLanguage() {
        authenticateAs(42L);
        when(userProfileService.findLocale(42L)).thenReturn(Optional.of("uk"));

        assertThat(service.getCurrentUserLocale()).contains("uk");
    }

    @Test
    void restCaller_withoutASavedLanguage_hasNone() {
        authenticateAs(42L);
        when(userProfileService.findLocale(42L)).thenReturn(Optional.empty());

        assertThat(service.getCurrentUserLocale()).isEmpty();
    }

    @Test
    void vaadinSession_usesThePrincipalsLanguage_withoutReadingPreferences() {
        AuthenticatedPrincipal principal = mock(AuthenticatedPrincipal.class);
        when(principal.locale()).thenReturn("en");
        authenticateAs(principal);

        assertThat(service.getCurrentUserLocale()).contains("en");
        verifyNoInteractions(userProfileService);
    }

    @Test
    void noAuthentication_hasNoLanguage() {
        assertThat(service.getCurrentUserLocale()).isEmpty();
        verifyNoInteractions(userProfileService);
    }

    private static void authenticateAs(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}
