package org.ost.advertisement.audit.ui;

import org.ost.advertisement.core.i18n.TranslationKey;

public enum AuditMessages implements TranslationKey {
    ACTIVITY_ACTION_CREATED("activity.action.created"),
    ACTIVITY_ACTION_UPDATED("activity.action.updated"),
    ACTIVITY_ACTION_DELETED("activity.action.deleted"),
    ACTIVITY_ENTITY_DELETED("activity.entity.deleted"),
    ACTIVITY_EMPTY("activity.empty"),
    CHANGES_FIELD_TITLE("changes.field.title"),
    CHANGES_FIELD_DESCRIPTION("changes.field.description"),
    CHANGES_FIELD_NAME("changes.field.name"),
    CHANGES_FIELD_EMAIL("changes.field.email"),
    CHANGES_FIELD_ROLE("changes.field.role"),
    CHANGES_PHOTOS("changes.photos"),
    CHANGES_SETTING_ADS_PAGE_SIZE("changes.setting.adsPageSize"),
    CHANGES_SETTING_USERS_PAGE_SIZE("changes.setting.usersPageSize"),
    USER_RESTORE_BUTTON("user.restore.button"),
    USER_ACTIVITY_CURRENT_STATE("user.activity.current.state"),
    SETTINGS_RESTORE_BUTTON("settings.restore.button");

    private final String key;

    AuditMessages(String key) { this.key = key; }

    @Override
    public String key() { return key; }

    public static AuditMessages fieldLabel(String field) {
        return switch (field) {
            case "title"       -> CHANGES_FIELD_TITLE;
            case "description" -> CHANGES_FIELD_DESCRIPTION;
            case "name"        -> CHANGES_FIELD_NAME;
            case "email"       -> CHANGES_FIELD_EMAIL;
            case "role"        -> CHANGES_FIELD_ROLE;
            default            -> throw new IllegalStateException("Unknown field: " + field);
        };
    }

    public static AuditMessages settingLabel(String key) {
        return switch (key) {
            case "adsPageSize"   -> CHANGES_SETTING_ADS_PAGE_SIZE;
            case "usersPageSize" -> CHANGES_SETTING_USERS_PAGE_SIZE;
            default              -> throw new IllegalStateException("Unknown setting: " + key);
        };
    }
}
