package org.ost.advertisement.audit.repository;

import org.ost.sqlengine.projection.SqlSelectField;

import static org.ost.sqlengine.projection.SqlSelectFieldFactory.*;

public final class AuditLogDescriptor {

    public static final String TABLE = "audit_log";

    public static final SqlSelectField<Long>   ENTITY_ID   = longVal("al.entity_id",  "entity_id");
    public static final SqlSelectField<String> ENTITY_TYPE = str("al.entity_type", "entity_type");
    public static final SqlSelectField<String> ACTION_TYPE = str("al.action_type", "action_type");

    public static final class Write {
        private Write() {}
        public static final String TABLE              = AuditLogDescriptor.TABLE;
        public static final String ENTITY_TYPE        = AuditLogDescriptor.ENTITY_TYPE.columnName();
        public static final String ENTITY_ID          = AuditLogDescriptor.ENTITY_ID.columnName();
        public static final String ACTION_TYPE        = AuditLogDescriptor.ACTION_TYPE.columnName();
        public static final String SNAPSHOT_DATA      = "snapshot_data";
        public static final String CHANGES_SUMMARY    = "changes_summary";
        public static final String ACTOR_ID           = "actor_id";
    }

    private AuditLogDescriptor() {}
}
