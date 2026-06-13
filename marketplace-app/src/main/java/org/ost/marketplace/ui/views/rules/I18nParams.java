package org.ost.marketplace.ui.views.rules;

import org.ost.marketplace.common.I18nKey;
import org.ost.platform.core.model.ActionType;
import org.ost.marketplace.i18n.Translatable;

import static org.ost.marketplace.common.I18nKey.*;

public interface I18nParams extends Translatable {

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
