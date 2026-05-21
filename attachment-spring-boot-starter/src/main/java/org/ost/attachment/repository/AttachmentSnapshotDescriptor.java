package org.ost.attachment.repository;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.platform.core.model.EntityType;
import org.ost.sqlengine.SqlEntityDescriptor;
import static org.ost.sqlengine.SqlEntityDescriptor.Params;
import org.ost.sqlengine.common.SqlDescriptorField;
import org.ost.sqlengine.common.SqlCommand;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;

import static org.ost.sqlengine.common.SqlCommand.sql;
import static org.ost.sqlengine.common.SqlDescriptorFieldFactory.*;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AttachmentSnapshotDescriptor implements SqlEntityDescriptor {

    public static final String TABLE  = "attachment_snapshot";
    public static final String ALIAS  = "ps";
    public static final String SOURCE = TABLE + " " + ALIAS;

    public static final SqlDescriptorField<Long>    ID                  = longVal(ALIAS + ".id", "ps_id");
    public static final SqlDescriptorField<String>  ENTITY_TYPE         = strCol(ALIAS, "entity_type");
    public static final SqlDescriptorField<Long>    ENTITY_ID           = longCol(ALIAS, "entity_id");
    public static final SqlDescriptorField<String>  CHANGES_SUMMARY     = strCol(ALIAS, "changes_summary");
    public static final SqlDescriptorField<Long>    CHANGED_BY_ACTOR_ID = longCol(ALIAS, "changed_by_actor_id");
    public static final SqlDescriptorField<Instant> CREATED_AT          = instantCol(ALIAS, "created_at");

    public static final String ATTACHMENT_URLS = "attachment_urls";

    private static final String FROM_TABLE = " FROM " + TABLE;

    private static final String WHERE_BY_ENTITY = sql(
            " WHERE {entityType} = :entityType AND {entityId} = :entityId",
            "entityType", ENTITY_TYPE.columnName(),
            "entityId",   ENTITY_ID.columnName());

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

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Read {

        public static final SqlCommand SELECT_PREV_URLS = SqlCommand.of(
                "SELECT {urls}{from}{where} ORDER BY created_at DESC LIMIT 1",
                "urls",  ATTACHMENT_URLS,
                "from",  FROM_TABLE,
                "where", WHERE_BY_ENTITY);

        public static final SqlCommand SELECT_URLS_AT_VERSION = SqlCommand.of(
                "SELECT {urls}{from}{where}" +
                "   AND created_at < COALESCE(({nextAuditTs}), 'infinity'::timestamptz) ORDER BY created_at DESC LIMIT 1",
                "urls",        ATTACHMENT_URLS,
                "from",        FROM_TABLE,
                "where",       WHERE_BY_ENTITY,
                "nextAuditTs", NEXT_AUDIT_TS_BY_VERSION);

        public static final SqlCommand SELECT_URLS_FOR_SNAPSHOT = SqlCommand.of(
                "SELECT {urls}{from}{where}" +
                "   AND created_at < COALESCE(({nextAuditTs}), 'infinity'::timestamptz) ORDER BY created_at DESC LIMIT 1",
                "urls",        ATTACHMENT_URLS,
                "from",        FROM_TABLE,
                "where",       WHERE_BY_ENTITY,
                "nextAuditTs", NEXT_AUDIT_TS_AFTER_SNAPSHOT);

        public static final SqlCommand SELECT_CHANGES_JSON_FOR_SNAPSHOT = SqlCommand.of(
                "SELECT {col}::text AS {alias}{from}{where}" +
                "   AND created_at >= (SELECT created_at FROM audit_log WHERE id = :snapshotId)" +
                "   AND created_at < COALESCE(({nextAuditTs}), 'infinity'::timestamptz)" +
                "   AND {col} IS NOT NULL ORDER BY created_at ASC LIMIT 1",
                "col",         CHANGES_SUMMARY.columnName(),
                "alias",       CHANGES_SUMMARY.alias(),
                "from",        FROM_TABLE,
                "where",       WHERE_BY_ENTITY,
                "nextAuditTs", NEXT_AUDIT_TS_AFTER_SNAPSHOT);

        public static final SqlCommand SELECT_CHANGES_JSON_AT_VERSION = SqlCommand.of(
                "SELECT {col}::text AS {alias}{from}{where}" +
                "   AND created_at >= ({thisAuditTs})" +
                "   AND created_at < COALESCE(({nextAuditTs}), 'infinity'::timestamptz)" +
                "   AND {col} IS NOT NULL ORDER BY created_at ASC LIMIT 1",
                "col",         CHANGES_SUMMARY.columnName(),
                "alias",       CHANGES_SUMMARY.alias(),
                "from",        FROM_TABLE,
                "where",       WHERE_BY_ENTITY,
                "thisAuditTs", THIS_AUDIT_TS_BY_VERSION,
                "nextAuditTs", NEXT_AUDIT_TS_BY_VERSION);

        public static MapSqlParameterSource entityParams(EntityType entityType, Long entityId) {
            return Params.with("entityType", entityType.name()).add("entityId", entityId);
        }

        public static MapSqlParameterSource versionParams(EntityType entityType, Long entityId, int version) {
            return entityParams(entityType, entityId).addValue("version", version);
        }

        public static MapSqlParameterSource snapshotParams(EntityType entityType, Long entityId, Long snapshotId) {
            return entityParams(entityType, entityId).addValue("snapshotId", snapshotId);
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

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class Write {

        public static final SqlCommand INSERT = SqlCommand.of(
                "INSERT INTO {table} ({entityType}, {entityId}, {urls}, {changesSummary}, {changedBy}, created_at)" +
                " VALUES (:entityType, :entityId, :urls, CAST(:changes AS JSONB), :actorId, NOW())",
                "table",          TABLE,
                "entityType",     ENTITY_TYPE.columnName(),
                "entityId",       ENTITY_ID.columnName(),
                "urls",           ATTACHMENT_URLS,
                "changesSummary", CHANGES_SUMMARY.columnName(),
                "changedBy",      CHANGED_BY_ACTOR_ID.columnName());

        public static MapSqlParameterSource insertParams(EntityType entityType, Long entityId,
                                                         String[] urls, String changesJson, Long actorId) {
            return Read.entityParams(entityType, entityId)
                    .addValue("urls",    urls)
                    .addValue("changes", changesJson)
                    .addValue("actorId", actorId);
        }
    }

}
