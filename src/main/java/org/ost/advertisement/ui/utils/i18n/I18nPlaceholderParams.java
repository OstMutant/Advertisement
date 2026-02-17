package org.ost.advertisement.ui.utils.i18n;

import org.ost.advertisement.constants.I18nKey;

public interface I18nPlaceholderParams extends I18nParams{

    I18nKey getPlaceholderKey();

    default String placeholder() {
        return getI18n().get(getPlaceholderKey());
    }
}
