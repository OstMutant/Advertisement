package org.ost.advertisement.repository.audit;

import org.ost.sqlengine.projection.SqlFieldDefinition;

import static org.ost.sqlengine.projection.SqlFieldBuilder.*;

public final class AuditLogProjection {

    public static final String TABLE  = "audit_log";
    public static final String ALIAS  = "al";
    public static final String SOURCE = TABLE + " " + ALIAS;

    public static final SqlFieldDefinition<Long>   ID          = id(ALIAS + ".id",         "al_id");
    public static final SqlFieldDefinition<Long>   ENTITY_ID   = longVal(ALIAS + ".entity_id",  "entity_id");
    public static final SqlFieldDefinition<String> ENTITY_TYPE = str(ALIAS + ".entity_type", "entity_type");
    public static final SqlFieldDefinition<String> ACTION_TYPE = str(ALIAS + ".action_type", "action_type");

    public static final class EntityType {
        private EntityType() {}
        public static final String ADVERTISEMENT = "ADVERTISEMENT";
        public static final String USER          = "USER";
        public static final String USER_SETTINGS = "USER_SETTINGS";
    }

    public static final class Write {
        private Write() {}
        public static final String TABLE              = AuditLogProjection.TABLE;
        public static final String ENTITY_TYPE        = AuditLogProjection.ENTITY_TYPE.columnName();
        public static final String ENTITY_ID          = AuditLogProjection.ENTITY_ID.columnName();
        public static final String ACTION_TYPE        = AuditLogProjection.ACTION_TYPE.columnName();
        public static final String SNAPSHOT_DATA      = "snapshot_data";
        public static final String CHANGES_SUMMARY    = "changes_summary";
        public static final String CHANGED_BY_USER_ID = "changed_by_user_id";
    }

    private AuditLogProjection() {}
}
