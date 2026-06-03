package org.ost.audit.ui;

import lombok.RequiredArgsConstructor;
import org.ost.platform.core.i18n.TranslationKey;
import org.ost.platform.core.model.ActionType;

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
    CHANGES_EDITOR("audit.changes.editor"),
    CHANGES_BULLET("audit.changes.bullet"),
    CHANGES_SET("audit.changes.set"),
    CHANGES_FIELD_CHANGED("audit.changes.field.changed"),
    CHANGES_MEDIA_CHANGED("audit.changes.media.changed"),
    VALUE_TRUNCATED("audit.value.truncated");

    private final String key;

    @Override
    public String key() { return key; }

    public static AuditI18n forAction(ActionType actionType) {
        return switch (actionType) {
            case CREATED -> ACTIVITY_ACTION_CREATED;
            case UPDATED -> ACTIVITY_ACTION_UPDATED;
            case DELETED -> ACTIVITY_ACTION_DELETED;
        };
    }
}
