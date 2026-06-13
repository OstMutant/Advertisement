package org.ost.ui.query.sort;

import org.ost.platform.core.i18n.TranslationKey;

public record SortFieldMeta(
        String property,
        TranslationKey i18nKey) {

    public static SortFieldMeta of(
            String property,
            TranslationKey i18nKey) {
        return new SortFieldMeta(property, i18nKey);
    }
}

