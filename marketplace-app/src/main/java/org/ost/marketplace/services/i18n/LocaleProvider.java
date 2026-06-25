package org.ost.marketplace.services.i18n;

import java.util.Locale;

public interface LocaleProvider {
    Locale getCurrentLocale();
    void refreshCurrentLocale();
}
