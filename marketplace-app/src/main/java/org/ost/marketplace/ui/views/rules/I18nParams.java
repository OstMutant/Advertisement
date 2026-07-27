package org.ost.marketplace.ui.views.rules;

import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;

public interface I18nParams {

    I18nService getI18nService();

    default String getValue(I18nKey key, Object... args) {
        return getI18nService().get(key, args);
    }
}
