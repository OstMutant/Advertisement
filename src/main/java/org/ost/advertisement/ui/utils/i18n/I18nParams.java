package org.ost.advertisement.ui.utils.i18n;

import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;

public interface I18nParams {
    I18nService getI18n();

    default String getValue(I18nKey i18nKey, Object... args) {
        return getI18n().get(i18nKey, args);
    }
}
