package org.ost.marketplace.services.i18n;

public interface I18nService {
    String get(String key, Object... args);

    default String get(I18nKey key, Object... args) {
        return get(key.key(), args);
    }
}
