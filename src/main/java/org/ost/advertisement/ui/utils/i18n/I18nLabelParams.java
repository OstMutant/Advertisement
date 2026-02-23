package org.ost.advertisement.ui.utils.i18n;

import org.ost.advertisement.constants.I18nKey;

public interface I18nLabelParams extends I18nParams {

    I18nKey getLabelKey();

    default String label() {
        return getI18n().get(getLabelKey());
    }
}
