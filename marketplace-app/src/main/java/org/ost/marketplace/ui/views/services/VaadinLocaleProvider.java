package org.ost.marketplace.ui.views.services;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.ost.marketplace.services.auth.AuthContextService;
import org.ost.marketplace.services.i18n.LocaleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Optional;

@SpringComponent
@Scope(value = "vaadin-ui", proxyMode = ScopedProxyMode.TARGET_CLASS)
@RequiredArgsConstructor
public class VaadinLocaleProvider implements LocaleProvider {

    private static final Logger log = LoggerFactory.getLogger(VaadinLocaleProvider.class);

    private final AuthContextService authContextService;

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
        Optional<Locale> userLocale = authContextService.getCurrentUser()
                .map(u -> u.locale() != null && !u.locale().isBlank()
                        ? Locale.forLanguageTag(u.locale())
                        : null);
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
