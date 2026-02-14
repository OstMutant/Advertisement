package org.ost.advertisement.services;

import com.vaadin.flow.component.UI;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.services.auth.AuthContextService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final AuthContextService authContextService;

    public Locale getCurrentLocale() {
        return getCurrentLocale(UI.getCurrent());
    }

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
