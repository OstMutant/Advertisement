package org.ost.audit.ui;

import lombok.RequiredArgsConstructor;
import org.ost.platform.core.i18n.TranslationKey;

@RequiredArgsConstructor
public enum AuditI18n implements TranslationKey {
    HISTORY_EMPTY("audit.history.empty"),
    ACTIVITY_EMPTY("audit.activity.empty"),
    ACTIVITY_CURRENT_STATE("audit.activity.current.state"),
    ACTIVITY_RESTORE("audit.activity.restore"),
    ACTIVITY_ACTION_CREATED("audit.activity.action.created"),
    ACTIVITY_ACTION_UPDATED("audit.activity.action.updated"),
    ACTIVITY_ACTION_DELETED("audit.activity.action.deleted"),
    ACTIVITY_ENTITY_DELETED("audit.activity.entity.deleted"),
    CHANGES_MEDIA("audit.changes.media"),
    CHANGES_SETTING_ADS_PAGE_SIZE("audit.changes.setting.adsPageSize"),
    CHANGES_SETTING_USERS_PAGE_SIZE("audit.changes.setting.usersPageSize");

    private final String key;

    @Override
    public String key() { return key; }

    public static AuditI18n settingLabel(String key) {
        return switch (key) {
            case "adsPageSize"   -> CHANGES_SETTING_ADS_PAGE_SIZE;
            case "usersPageSize" -> CHANGES_SETTING_USERS_PAGE_SIZE;
            default              -> throw new IllegalStateException("Unknown setting: " + key);
        };
    }
}
