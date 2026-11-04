package org.ost.marketplace.services.i18n;

import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.orchestrator.services.CurrentLocaleService;
import org.springframework.context.MessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Translation resolves the message in the caller's language and runs without any UI object on the thread. */
@ExtendWith(MockitoExtension.class)
class I18nServiceImplTest {

    @Mock private MessageSource messageSource;
    @Mock private CurrentLocaleService currentLocaleService;

    private I18nServiceImpl service;

    @Test
    void get_resolvesTheMessageInTheCurrentLocale() {
        service = new I18nServiceImpl(messageSource, currentLocaleService);
        Locale uk = Locale.forLanguageTag("uk");
        when(currentLocaleService.getCurrentLocale()).thenReturn(uk);
        when(messageSource.getMessage(eq("greeting"), any(Object[].class), eq(uk))).thenReturn("Привіт");

        assertThat(service.get("greeting")).isEqualTo("Привіт");
        verify(messageSource).getMessage(eq("greeting"), any(Object[].class), eq(uk));
    }

    @Test
    void get_fallsBackToEnglishWhenThatIsTheCurrentLocale() {
        service = new I18nServiceImpl(messageSource, currentLocaleService);
        Locale en = Locale.ENGLISH;
        when(currentLocaleService.getCurrentLocale()).thenReturn(en);
        when(messageSource.getMessage(eq("greeting"), any(Object[].class), eq(en))).thenReturn("Hello");

        assertThat(service.get("greeting")).isEqualTo("Hello");
        verify(messageSource).getMessage(eq("greeting"), any(Object[].class), eq(en));
    }

    @Test
    void get_onAPlainThread_touchesNoUiObject() {
        service = new I18nServiceImpl(messageSource, currentLocaleService);
        when(currentLocaleService.getCurrentLocale()).thenReturn(Locale.ENGLISH);
        when(messageSource.getMessage(eq("greeting"), any(Object[].class), eq(Locale.ENGLISH))).thenReturn("Hello");

        assertThat(UI.getCurrent()).isNull();
        assertThat(service.get("greeting")).isEqualTo("Hello");
    }
}
