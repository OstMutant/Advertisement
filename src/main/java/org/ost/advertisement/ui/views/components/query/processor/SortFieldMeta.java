package org.ost.advertisement.ui.views.components.query.processor;

import org.ost.advertisement.constants.I18nKey;

public record SortFieldMeta(
        String property,
        I18nKey i18nKey) {

    public static SortFieldMeta of(
            String property,
            I18nKey i18nKey) {
        return new SortFieldMeta(property, i18nKey);
    }
}

