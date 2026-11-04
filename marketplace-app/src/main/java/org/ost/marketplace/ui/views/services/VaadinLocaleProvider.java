package org.ost.marketplace.ui.views.services;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.ost.marketplace.services.i18n.LocaleProvider;
import org.ost.orchestrator.services.CurrentUserService;

import java.util.Locale;
import java.util.Optional;

@Slf4j
@SpringComponent
@Scope(value = "vaadin-ui", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class VaadinLocaleProvider implements LocaleProvider {

    private final CurrentUserService currentUserService;

    @Override
    public Locale getCurrentLocale() {
        return getCurrentLocale(UI.getCurrent());
    }

    @Override
    public void refreshCurrentLocale() {
        refreshCurrentLocale(UI.getCurrent());
    }

    public Locale getCurrentLocale(UI ui) {
        if (ui == null) {
            log.warn("UI is null in getCurrentLocale; returning default locale");
            return Locale.getDefault();
        }
        Optional<Locale> userLocale = currentUserService.getCurrentUserLocale()
                .map(locale -> locale != null && !locale.isBlank() ? Locale.forLanguageTag(locale) : null);
        return userLocale.orElseGet(() -> {
            var session = ui.getSession();
            return session != null ? session.getLocale() : Locale.getDefault();
        });
    }

    public void refreshCurrentLocale(UI ui) {
        if (ui == null) {
            log.warn("UI is null in refreshCurrentLocale; skipping");
            return;
        }
        Locale locale = getCurrentLocale(ui);
        var session = ui.getSession();
        if (session != null) {
            session.setLocale(locale);
        } else {
            log.warn("UI session is null; cannot set locale");
        }
    }
}
