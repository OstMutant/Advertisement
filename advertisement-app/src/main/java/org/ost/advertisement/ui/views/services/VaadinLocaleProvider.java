package org.ost.advertisement.ui.views.services;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.auth.AuthContextService;
import org.ost.advertisement.services.auth.LocaleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Optional;

@SpringComponent
@UIScope
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
        Optional<User> userOpt = authContextService.getCurrentUser();
        return userOpt.map(User::getLocaleAsObject).orElseGet(() -> {
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
