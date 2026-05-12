package org.ost.advertisement.services.auth;

import java.util.Locale;

public interface LocaleProvider {
    Locale getCurrentLocale();
    void refreshCurrentLocale();
}
