package org.ost.platform.core.i18n;

public interface I18nService {
    String get(String key, Object... args);
    String get(TranslationKey key, Object... args);
}
