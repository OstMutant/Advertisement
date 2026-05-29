package org.ost.audit.ui;

import lombok.RequiredArgsConstructor;
import org.ost.platform.core.i18n.TranslationKey;

@RequiredArgsConstructor
public enum AuditMessages implements TranslationKey {
    ACTIVITY_ACTION_CREATED("activity.action.created"),
    ACTIVITY_ACTION_UPDATED("activity.action.updated"),
    ACTIVITY_ACTION_DELETED("activity.action.deleted"),
    ACTIVITY_ENTITY_DELETED("activity.entity.deleted"),
    CHANGES_PHOTOS("changes.photos"),
    CHANGES_SETTING_ADS_PAGE_SIZE("changes.setting.adsPageSize"),
    CHANGES_SETTING_USERS_PAGE_SIZE("changes.setting.usersPageSize");

    private final String key;

    @Override
    public String key() { return key; }

    public static AuditMessages settingLabel(String key) {
        return switch (key) {
            case "adsPageSize"   -> CHANGES_SETTING_ADS_PAGE_SIZE;
            case "usersPageSize" -> CHANGES_SETTING_USERS_PAGE_SIZE;
            default              -> throw new IllegalStateException("Unknown setting: " + key);
        };
    }
}
