package org.ost.marketplace.ui.views.services;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.LocaleProvider;
import org.ost.orchestrator.spi.UiLocaleHook;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Locale;
import java.util.Optional;

/** Answers the UI session's language only inside a UI thread; the UI-scoped provider is resolved lazily so other threads never touch it. */
@SpringComponent
@RequiredArgsConstructor
public class UiLocaleHookImpl implements UiLocaleHook {

    private final ObjectProvider<LocaleProvider> uiLocaleProvider;

    @Override
    public Optional<Locale> currentUiLocale() {
        if (UI.getCurrent() == null) {
            return Optional.empty();
        }
        return Optional.of(uiLocaleProvider.getObject().getCurrentLocale());
    }
}
