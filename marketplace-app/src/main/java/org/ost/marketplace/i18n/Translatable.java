package org.ost.marketplace.i18n;

public interface Translatable {
    I18nService getI18nService();

    default String getValue(TranslationKey key, Object... args) {
        return getI18nService().get(key, args);
    }
}
