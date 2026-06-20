package org.ost.marketplace.i18n;

import org.ost.marketplace.common.I18nKey;

public interface I18nService {
    String get(String key, Object... args);

    default String get(I18nKey key, Object... args) {
        return get(key.key(), args);
    }
}
