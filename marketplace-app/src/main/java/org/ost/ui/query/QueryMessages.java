package org.ost.ui.query;

import lombok.RequiredArgsConstructor;
import org.ost.marketplace.i18n.TranslationKey;

@RequiredArgsConstructor
public enum QueryMessages implements TranslationKey {

    SORT_ICON_TOOLTIP("sort.icon.tooltip"),
    SORT_ICON_ASC("sort.icon.asc"),
    SORT_ICON_DESC("sort.icon.desc"),
    SORT_ICON_NEUTRAL("sort.icon.neutral"),

    ACTIONS_APPLY_TOOLTIP("actions.apply.tooltip"),
    ACTIONS_CLEAR_TOOLTIP("actions.clear.tooltip");

    private final String key;

    @Override
    public String key() {
        return key;
    }
}
