package org.ost.marketplace.ui.query.sort;

import org.ost.marketplace.services.i18n.I18nKey;

public record SortFieldMeta(
        String property,
        I18nKey i18nKey) {

    public static SortFieldMeta of(
            String property,
            I18nKey i18nKey) {
        return new SortFieldMeta(property, i18nKey);
    }
}

