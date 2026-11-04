package org.ost.marketplace.ui.views.services;

import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.marketplace.services.i18n.LocaleProvider;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The UI language is answered only inside a UI thread, and the UI-scoped provider is never touched elsewhere. */
@ExtendWith(MockitoExtension.class)
class UiLocaleHookImplTest {

    @Mock private ObjectProvider<LocaleProvider> uiLocaleProvider;
    @Mock private LocaleProvider localeProvider;

    private UiLocaleHookImpl hook;

    @BeforeEach
    void setUp() {
        hook = new UiLocaleHookImpl(uiLocaleProvider);
        UI.setCurrent(null);
    }

    @AfterEach
    void tearDown() {
        UI.setCurrent(null);
    }

    @Test
    void outsideAUiThread_isEmptyAndTheUiProviderIsNotResolved() {
        assertThat(hook.currentUiLocale()).isEmpty();
        verifyNoInteractions(uiLocaleProvider);
    }

    @Test
    void insideAUiThread_returnsTheUiProvidersLocale() {
        UI.setCurrent(new UI());
        when(uiLocaleProvider.getObject()).thenReturn(localeProvider);
        when(localeProvider.getCurrentLocale()).thenReturn(Locale.forLanguageTag("uk"));

        assertThat(hook.currentUiLocale()).contains(Locale.forLanguageTag("uk"));
    }
}
