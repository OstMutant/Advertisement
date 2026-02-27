package org.ost.advertisement.ui.utils;

import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;

public interface I18nParams {
    I18nService getI18nService();

    default String getValue(I18nKey i18nKey, Object... args) {
        return getI18nService().get(i18nKey, args);
    }
}
