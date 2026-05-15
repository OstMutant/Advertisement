package org.ost.advertisement.i18n;

import java.util.Locale;

public interface LocaleProvider {
    Locale getCurrentLocale();
    void refreshCurrentLocale();
}
