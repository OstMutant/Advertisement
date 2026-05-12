package org.ost.advertisement.services.auth;

import lombok.RequiredArgsConstructor;
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
