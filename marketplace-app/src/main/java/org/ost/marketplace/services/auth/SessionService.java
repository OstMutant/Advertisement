package org.ost.marketplace.services.auth;

import lombok.RequiredArgsConstructor;
import org.ost.platform.core.i18n.LocaleProvider;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final LocaleProvider localeProvider;

    public Locale getCurrentLocale() {
        return localeProvider.getCurrentLocale();
    }

    public void refreshCurrentLocale() {
        localeProvider.refreshCurrentLocale();
    }
}
