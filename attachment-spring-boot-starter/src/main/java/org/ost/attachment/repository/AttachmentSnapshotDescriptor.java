package org.ost.attachment.repository;

import org.ost.sqlengine.projection.SqlSelectField;
import org.ost.sqlengine.writer.SqlWriteCommand;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;

import static org.ost.sqlengine.projection.SqlSelectFieldFactory.*;

public final class AttachmentSnapshotDescriptor {

    public static final String TABLE  = "attachment_snapshot";
    public static final String ALIAS  = "ps";
    public static final String SOURCE = TABLE + " " + ALIAS;

    public static final SqlSelectField<Long>    ID                  = longVal(ALIAS + ".id",                  "ps_id");
    public static final SqlSelectField<String>  ENTITY_TYPE         = str(ALIAS + ".entity_type",             "entity_type");
    public static final SqlSelectField<Long>    ENTITY_ID           = longVal(ALIAS + ".entity_id",           "entity_id");
    public static final SqlSelectField<String>  CHANGES_SUMMARY     = str(ALIAS + ".changes_summary",         "changes_summary");
    public static final SqlSelectField<Long>    CHANGED_BY_ACTOR_ID = longVal(ALIAS + ".changed_by_actor_id", "changed_by_actor_id");
    public static final SqlSelectField<Instant> CREATED_AT          = instant(ALIAS + ".created_at",          "created_at");

    public static final class Write {
        private Write() {}
        public static final String TABLE               = AttachmentSnapshotDescriptor.TABLE;
        public static final String ENTITY_TYPE         = AttachmentSnapshotDescriptor.ENTITY_TYPE.columnName();
        public static final String ENTITY_ID           = AttachmentSnapshotDescriptor.ENTITY_ID.columnName();
        public static final String ATTACHMENT_URLS     = "attachment_urls";
        public static final String CHANGES_SUMMARY     = AttachmentSnapshotDescriptor.CHANGES_SUMMARY.columnName();
        public static final String CHANGED_BY_ACTOR_ID = AttachmentSnapshotDescriptor.CHANGED_BY_ACTOR_ID.columnName();
    }

    // -------- SQL --------

    private static final String FROM_TABLE  = " FROM " + TABLE;

    private static final String WHERE_BY_ENTITY =
            " WHERE " + Write.ENTITY_TYPE + " = :entityType" +
            " AND "   + Write.ENTITY_ID   + " = :entityId";

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

    public static final SqlWriteCommand INSERT = SqlWriteCommand.of(
            "INSERT INTO " + Write.TABLE +
            " (" + Write.ENTITY_TYPE + ", " + Write.ENTITY_ID + ", " +
            Write.ATTACHMENT_URLS + ", " + Write.CHANGES_SUMMARY + ", " +
            Write.CHANGED_BY_ACTOR_ID + ", created_at)" +
            " VALUES (:entityType, :entityId, :urls, CAST(:changes AS JSONB), :actorId, NOW())");

    public static final String SELECT_PREV_URLS_SQL =
            "SELECT " + Write.ATTACHMENT_URLS + FROM_TABLE + WHERE_BY_ENTITY +
            " ORDER BY created_at DESC LIMIT 1";

    public static final String SELECT_URLS_AT_VERSION_SQL =
            "SELECT " + Write.ATTACHMENT_URLS + FROM_TABLE + WHERE_BY_ENTITY +
            "   AND created_at < COALESCE((" + NEXT_AUDIT_TS_BY_VERSION + "), 'infinity'::timestamptz)" +
            " ORDER BY created_at DESC LIMIT 1";

    public static final String SELECT_URLS_FOR_SNAPSHOT_SQL =
            "SELECT " + Write.ATTACHMENT_URLS + FROM_TABLE + WHERE_BY_ENTITY +
            "   AND created_at < COALESCE((" + NEXT_AUDIT_TS_AFTER_SNAPSHOT + "), 'infinity'::timestamptz)" +
            " ORDER BY created_at DESC LIMIT 1";

    public static final String SELECT_CHANGES_JSON_FOR_SNAPSHOT_SQL =
            "SELECT " + Write.CHANGES_SUMMARY + "::text" + FROM_TABLE + WHERE_BY_ENTITY +
            "   AND created_at >= (SELECT created_at FROM audit_log WHERE id = :snapshotId)" +
            "   AND created_at < COALESCE((" + NEXT_AUDIT_TS_AFTER_SNAPSHOT + "), 'infinity'::timestamptz)" +
            "   AND " + Write.CHANGES_SUMMARY + " IS NOT NULL" +
            " ORDER BY created_at ASC LIMIT 1";

    public static final String SELECT_CHANGES_JSON_AT_VERSION_SQL =
            "SELECT " + Write.CHANGES_SUMMARY + "::text" + FROM_TABLE + WHERE_BY_ENTITY +
            "   AND created_at >= (" + THIS_AUDIT_TS_BY_VERSION + ")" +
            "   AND created_at < COALESCE((" + NEXT_AUDIT_TS_BY_VERSION + "), 'infinity'::timestamptz)" +
            "   AND " + Write.CHANGES_SUMMARY + " IS NOT NULL" +
            " ORDER BY created_at ASC LIMIT 1";

    // -------- Param factories --------

    public static MapSqlParameterSource entityParams(String entityTypeName, Long entityId) {
        return new MapSqlParameterSource()
                .addValue("entityType", entityTypeName)
                .addValue("entityId",   entityId);
    }

    public static MapSqlParameterSource insertParams(String entityTypeName, Long entityId,
                                                     String[] urls, String changesJson, Long actorId) {
        return entityParams(entityTypeName, entityId)
                .addValue("urls",    urls)
                .addValue("changes", changesJson)
                .addValue("actorId", actorId);
    }

    public static MapSqlParameterSource versionParams(String entityTypeName, Long entityId, int version) {
        return entityParams(entityTypeName, entityId).addValue("version", version);
    }

    public static MapSqlParameterSource snapshotParams(String entityTypeName, Long entityId, Long snapshotId) {
        return entityParams(entityTypeName, entityId).addValue("snapshotId", snapshotId);
    }

    // -------- Row extraction helpers --------

    public static List<String> extractUrls(ResultSet rs) {
        try {
            java.sql.Array arr = rs.getArray(Write.ATTACHMENT_URLS);
            if (arr == null) return List.of();
            String[] arr2 = (String[]) arr.getArray();
            return arr2 == null ? List.of() : List.of(arr2);
        } catch (Exception _) {
            return List.of();
        }
    }

    private AttachmentSnapshotDescriptor() {}
}
