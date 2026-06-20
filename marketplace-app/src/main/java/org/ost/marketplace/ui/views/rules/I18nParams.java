package org.ost.marketplace.ui.views.rules;

import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.platform.core.model.ActionType;

import static org.ost.marketplace.services.i18n.I18nKey.*;

public interface I18nParams {

    I18nService getI18nService();

    default String getValue(I18nKey key, Object... args) {
        return getI18nService().get(key, args);
    }

    default String formatAction(ActionType actionType) {
        return switch (actionType) {
            case CREATED -> getValue(ACTIVITY_ACTION_CREATED);
            case UPDATED -> getValue(ACTIVITY_ACTION_UPDATED);
            case DELETED -> getValue(ACTIVITY_ACTION_DELETED);
        };
    }

    default String truncate(String s) {
        if (s == null) return "";
        return s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }
}
