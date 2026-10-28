package org.ost.orchestrator.services;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.spi.UiLocaleHook;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The language shared code sees: the UI session's own choice, otherwise the saved language, then the request's, then English. */
@ExtendWith(MockitoExtension.class)
class CurrentLocaleServiceTest {

    @Mock private UiLocaleHook uiLocaleHook;
    @Mock private CurrentUserService currentUserService;

    private CurrentLocaleService service;

    @BeforeEach
    void setUp() {
        service = new CurrentLocaleService(uiLocaleHook, currentUserService);
        lenient().when(uiLocaleHook.currentUiLocale()).thenReturn(Optional.empty());
        lenient().when(currentUserService.getCurrentUserLocale()).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void theUiSessionLanguage_winsAndNothingElseIsAsked() {
        when(uiLocaleHook.currentUiLocale()).thenReturn(Optional.of(Locale.forLanguageTag("uk")));

        assertThat(service.getCurrentLocale().getLanguage()).isEqualTo("uk");
        verifyNoInteractions(currentUserService);
    }

    @Test
    void outsideTheUi_savedLanguageBeatsTheRequestHeader() {
        when(currentUserService.getCurrentUserLocale()).thenReturn(Optional.of("uk"));
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        assertThat(service.getCurrentLocale().getLanguage()).isEqualTo("uk");
    }

    @Test
    void outsideTheUi_withoutASavedLanguage_theRequestLanguageIsUsed() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("uk"));

        assertThat(service.getCurrentLocale().getLanguage()).isEqualTo("uk");
    }

    @Test
    void anUnsupportedRequestLanguage_fallsBackToEnglish() {
        LocaleContextHolder.setLocale(Locale.FRENCH);

        assertThat(service.getCurrentLocale()).isEqualTo(Locale.ENGLISH);
    }

    @Test
    void anUnsupportedSavedLanguage_isIgnored() {
        when(currentUserService.getCurrentUserLocale()).thenReturn(Optional.of("fr"));
        LocaleContextHolder.setLocale(Locale.forLanguageTag("uk"));

        assertThat(service.getCurrentLocale().getLanguage()).isEqualTo("uk");
    }

    @Test
    void aBlankSavedLanguage_isIgnored() {
        when(currentUserService.getCurrentUserLocale()).thenReturn(Optional.of(" "));
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        assertThat(service.getCurrentLocale()).isEqualTo(Locale.ENGLISH);
    }
}
