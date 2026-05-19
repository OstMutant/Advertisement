package org.ost.attachment.repository;

import org.ost.sqlengine.SqlEntityDescriptor;
import org.ost.sqlengine.read.SqlSelectField;
import org.ost.sqlengine.write.SqlWriteCommand;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;

import static org.ost.sqlengine.read.SqlSelectFieldFactory.*;

public final class AttachmentSnapshotDescriptor implements SqlEntityDescriptor {

    public static final String TABLE  = "attachment_snapshot";
    public static final String ALIAS  = "ps";
    public static final String SOURCE = TABLE + " " + ALIAS;

    public static final SqlSelectField<Long>    ID                  = longVal(ALIAS + ".id",                  "ps_id");
    public static final SqlSelectField<String>  ENTITY_TYPE         = str(ALIAS + ".entity_type",             "entity_type");
    public static final SqlSelectField<Long>    ENTITY_ID           = longVal(ALIAS + ".entity_id",           "entity_id");
    public static final SqlSelectField<String>  CHANGES_SUMMARY     = str(ALIAS + ".changes_summary",         "changes_summary");
    public static final SqlSelectField<Long>    CHANGED_BY_ACTOR_ID = longVal(ALIAS + ".changed_by_actor_id", "changed_by_actor_id");
    public static final SqlSelectField<Instant> CREATED_AT          = instant(ALIAS + ".created_at",          "created_at");

    public static final String ATTACHMENT_URLS = "attachment_urls";

    private static final String FROM_TABLE = " FROM " + TABLE;

    private static final String WHERE_BY_ENTITY =
            " WHERE " + ENTITY_TYPE.columnName() + " = :entityType" +
            " AND "   + ENTITY_ID.columnName()   + " = :entityId";

    /** created_at of the audit_log entry at position (:version + 1) for the entity — upper bound for that version's attachments. */
    private static final String NEXT_AUDIT_TS_BY_VERSION =
            "SELECT al.created_at FROM (" +
            "    SELECT created_at," +
            "           ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY created_at) AS rn" +
            "    FROM audit_log WHERE entity_type = :entityType AND entity_id = :entityId" +
            ") al WHERE al.rn = :version + 1";

    /** created_at of the audit_log entry at position :version for the entity — lower bound for that version's snapshot. */
    private static final String THIS_AUDIT_TS_BY_VERSION =
            "SELECT al.created_at FROM (" +
            "    SELECT created_at," +
            "           ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY created_at) AS rn" +
            "    FROM audit_log WHERE entity_type = :entityType AND entity_id = :entityId" +
            ") al WHERE al.rn = :version";

    /** created_at of the first audit_log entry strictly after the one identified by :snapshotId. */
    private static final String NEXT_AUDIT_TS_AFTER_SNAPSHOT =
            "SELECT created_at FROM audit_log" +
            " WHERE entity_type = :entityType AND entity_id = :entityId" +
            "   AND created_at > (SELECT created_at FROM audit_log WHERE id = :snapshotId)" +
            " ORDER BY created_at ASC LIMIT 1";

    public static final class Read {
        private Read() {}

        public static final String SELECT_PREV_URLS =
                "SELECT " + ATTACHMENT_URLS + FROM_TABLE + WHERE_BY_ENTITY +
                " ORDER BY created_at DESC LIMIT 1";

        public static final String SELECT_URLS_AT_VERSION =
                "SELECT " + ATTACHMENT_URLS + FROM_TABLE + WHERE_BY_ENTITY +
                "   AND created_at < COALESCE((" + NEXT_AUDIT_TS_BY_VERSION + "), 'infinity'::timestamptz)" +
                " ORDER BY created_at DESC LIMIT 1";

        public static final String SELECT_URLS_FOR_SNAPSHOT =
                "SELECT " + ATTACHMENT_URLS + FROM_TABLE + WHERE_BY_ENTITY +
                "   AND created_at < COALESCE((" + NEXT_AUDIT_TS_AFTER_SNAPSHOT + "), 'infinity'::timestamptz)" +
                " ORDER BY created_at DESC LIMIT 1";

        public static final String SELECT_CHANGES_JSON_FOR_SNAPSHOT =
                "SELECT " + CHANGES_SUMMARY.columnName() + "::text" + FROM_TABLE + WHERE_BY_ENTITY +
                "   AND created_at >= (SELECT created_at FROM audit_log WHERE id = :snapshotId)" +
                "   AND created_at < COALESCE((" + NEXT_AUDIT_TS_AFTER_SNAPSHOT + "), 'infinity'::timestamptz)" +
                "   AND " + CHANGES_SUMMARY.columnName() + " IS NOT NULL" +
                " ORDER BY created_at ASC LIMIT 1";

        public static final String SELECT_CHANGES_JSON_AT_VERSION =
                "SELECT " + CHANGES_SUMMARY.columnName() + "::text" + FROM_TABLE + WHERE_BY_ENTITY +
                "   AND created_at >= (" + THIS_AUDIT_TS_BY_VERSION + ")" +
                "   AND created_at < COALESCE((" + NEXT_AUDIT_TS_BY_VERSION + "), 'infinity'::timestamptz)" +
                "   AND " + CHANGES_SUMMARY.columnName() + " IS NOT NULL" +
                " ORDER BY created_at ASC LIMIT 1";

        public static MapSqlParameterSource entityParams(String entityTypeName, Long entityId) {
            return new MapSqlParameterSource()
                    .addValue("entityType", entityTypeName)
                    .addValue("entityId",   entityId);
        }

        public static MapSqlParameterSource versionParams(String entityTypeName, Long entityId, int version) {
            return entityParams(entityTypeName, entityId).addValue("version", version);
        }

        public static MapSqlParameterSource snapshotParams(String entityTypeName, Long entityId, Long snapshotId) {
            return entityParams(entityTypeName, entityId).addValue("snapshotId", snapshotId);
        }

        public static List<String> extractUrls(ResultSet rs) {
            try {
                java.sql.Array arr = rs.getArray(ATTACHMENT_URLS);
                if (arr == null) return List.of();
                String[] arr2 = (String[]) arr.getArray();
                return arr2 == null ? List.of() : List.of(arr2);
            } catch (Exception _) {
                return List.of();
            }
        }
    }

    public static final class Write {
        private Write() {}

        public static final SqlWriteCommand INSERT = SqlWriteCommand.of(
                "INSERT INTO " + TABLE +
                " (" + ENTITY_TYPE.columnName() + ", " + ENTITY_ID.columnName() + ", " +
                ATTACHMENT_URLS + ", " + CHANGES_SUMMARY.columnName() + ", " +
                CHANGED_BY_ACTOR_ID.columnName() + ", created_at)" +
                " VALUES (:entityType, :entityId, :urls, CAST(:changes AS JSONB), :actorId, NOW())");

        public static MapSqlParameterSource insertParams(String entityTypeName, Long entityId,
                                                         String[] urls, String changesJson, Long actorId) {
            return Read.entityParams(entityTypeName, entityId)
                    .addValue("urls",    urls)
                    .addValue("changes", changesJson)
                    .addValue("actorId", actorId);
        }
    }

    private AttachmentSnapshotDescriptor() {}
}
