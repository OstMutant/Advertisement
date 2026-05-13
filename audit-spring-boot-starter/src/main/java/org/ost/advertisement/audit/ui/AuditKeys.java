package org.ost.advertisement.audit.ui;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class AuditKeys {
    static final String ACTIVITY_ACTION_CREATED    = "activity.action.created";
    static final String ACTIVITY_ACTION_UPDATED    = "activity.action.updated";
    static final String ACTIVITY_ACTION_DELETED    = "activity.action.deleted";
    static final String ACTIVITY_ENTITY_DELETED    = "activity.entity.deleted";
    static final String ACTIVITY_EMPTY             = "activity.empty";
    static final String CHANGES_FIELD_TITLE        = "changes.field.title";
    static final String CHANGES_FIELD_DESCRIPTION  = "changes.field.description";
    static final String CHANGES_PHOTOS             = "changes.photos";
    static final String USER_RESTORE_BUTTON        = "user.restore.button";
    static final String USER_ACTIVITY_CURRENT_STATE = "user.activity.current.state";
    static final String SETTINGS_RESTORE_BUTTON    = "settings.restore.button";
}
