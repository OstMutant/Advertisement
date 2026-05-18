package org.ost.attachment.repository;

import lombok.RequiredArgsConstructor;
import org.ost.platform.core.model.EntityType;
import org.ost.sqlengine.writer.SqlWriteCommand;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AttachmentSnapshotRepository {

    private static final SqlWriteCommand INSERT = SqlWriteCommand.of(
            "INSERT INTO " + AttachmentSnapshotDescriptor.Write.TABLE +
            " (" + AttachmentSnapshotDescriptor.Write.ENTITY_TYPE + "," +
            " "  + AttachmentSnapshotDescriptor.Write.ENTITY_ID + "," +
            " "  + AttachmentSnapshotDescriptor.Write.ATTACHMENT_URLS + "," +
            " "  + AttachmentSnapshotDescriptor.Write.CHANGES_SUMMARY + "," +
            " "  + AttachmentSnapshotDescriptor.Write.CHANGED_BY_USER_ID + ", created_at)" +
            " VALUES (:entityType, :entityId," +
            " :urls, CAST(:changes AS JSONB), :userId, NOW())"
    );

    private static final String FROM_TABLE = " FROM " + AttachmentSnapshotDescriptor.TABLE;

    private static final String WHERE_BY_ENTITY =
            " WHERE entity_type = :entityType AND entity_id = :entityId";

    private final JdbcClient jdbcClient;

    public void insert(EntityType entityType, Long entityId, String[] urls, String changesJson, Long userId) {
        INSERT.execute(jdbcClient,
                new MapSqlParameterSource()
                        .addValue("entityType", entityType.name())
                        .addValue("entityId",   entityId)
                        .addValue("urls",       urls)
                        .addValue("changes",    changesJson)
                        .addValue("userId",     userId));
    }

    public List<String> getPrevUrls(EntityType entityType, Long entityId) {
        return jdbcClient.sql(
                "SELECT " + AttachmentSnapshotDescriptor.Write.ATTACHMENT_URLS +
                FROM_TABLE +
                WHERE_BY_ENTITY +
                " ORDER BY created_at DESC LIMIT 1")
                .paramSource(entityParams(entityType, entityId))
                .query((rs, row) -> toStringList(rs, AttachmentSnapshotDescriptor.Write.ATTACHMENT_URLS))
                .optional().orElse(List.of());
    }

    // Subquery: created_at of audit_log entry at position :version+1 (for upper bound).
    // attachment_snapshot is created AFTER the audit entry in the same save flow, so we use
    // "created_at < next_audit_entry.created_at" to correctly scope each version's attachments.
    private static final String NEXT_AUDIT_TS =
            "SELECT al.created_at FROM (" +
            "    SELECT created_at," +
            "           ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY created_at) AS rn" +
            "    FROM audit_log WHERE entity_type = :entityType AND entity_id = :entityId" +
            ") al WHERE al.rn = :version + 1";

    public String[] getUrlsAtVersion(EntityType entityType, Long entityId, int version) {
        return jdbcClient.sql(
                "SELECT attachment_urls" +
                FROM_TABLE +
                WHERE_BY_ENTITY +
                "   AND created_at < COALESCE((" + NEXT_AUDIT_TS + "), 'infinity'::timestamptz)" +
                " ORDER BY created_at DESC LIMIT 1")
                .paramSource(entityParams(entityType, entityId).addValue("version", version))
                .query((rs, row) -> toStringList(rs, AttachmentSnapshotDescriptor.Write.ATTACHMENT_URLS))
                .optional()
                .map(l -> l.toArray(new String[0]))
                .orElse(new String[0]);
    }

    public Optional<List<String>> getUrlsForAdvSnapshot(EntityType entityType, Long entityId, Long advSnapshotId) {
        return jdbcClient.sql(
                "SELECT attachment_urls" +
                FROM_TABLE +
                WHERE_BY_ENTITY +
                "   AND created_at < COALESCE((" +
                "       SELECT created_at FROM audit_log" +
                "       WHERE entity_type = :entityType AND entity_id = :entityId" +
                "         AND created_at > (SELECT created_at FROM audit_log WHERE id = :snapId)" +
                "       ORDER BY created_at ASC LIMIT 1" +
                "   ), 'infinity'::timestamptz)" +
                " ORDER BY created_at DESC LIMIT 1")
                .paramSource(entityParams(entityType, entityId).addValue("snapId", advSnapshotId))
                .query((rs, row) -> toStringList(rs, AttachmentSnapshotDescriptor.Write.ATTACHMENT_URLS))
                .optional();
    }

    public Optional<String> getChangesJsonForSnapshot(EntityType entityType, Long entityId, Long snapshotId) {
        return jdbcClient.sql(
                "SELECT changes_summary::text" +
                FROM_TABLE +
                WHERE_BY_ENTITY +
                "   AND created_at >= (SELECT created_at FROM audit_log WHERE id = :snapshotId)" +
                "   AND created_at < COALESCE((" +
                "       SELECT created_at FROM audit_log" +
                "       WHERE entity_type = :entityType AND entity_id = :entityId" +
                "         AND created_at > (SELECT created_at FROM audit_log WHERE id = :snapshotId)" +
                "       ORDER BY created_at ASC LIMIT 1" +
                "   ), 'infinity'::timestamptz)" +
                "   AND changes_summary IS NOT NULL" +
                " ORDER BY created_at ASC LIMIT 1")
                .paramSource(entityParams(entityType, entityId).addValue("snapshotId", snapshotId))
                .query((rs, row) -> rs.getString(1))
                .optional();
    }

    public Optional<String> getChangesJson(EntityType entityType, Long entityId, int version) {
        String thisAuditTs =
                "SELECT al.created_at FROM (" +
                "    SELECT created_at," +
                "           ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY created_at) AS rn" +
                "    FROM audit_log WHERE entity_type = :entityType AND entity_id = :entityId" +
                ") al WHERE al.rn = :version";
        return jdbcClient.sql(
                "SELECT changes_summary::text" +
                FROM_TABLE +
                WHERE_BY_ENTITY +
                "   AND created_at >= (" + thisAuditTs + ")" +
                "   AND created_at < COALESCE((" + NEXT_AUDIT_TS + "), 'infinity'::timestamptz)" +
                "   AND changes_summary IS NOT NULL" +
                " ORDER BY created_at ASC LIMIT 1")
                .paramSource(entityParams(entityType, entityId).addValue("version", version))
                .query((rs, row) -> rs.getString(1))
                .optional();
    }

    private static MapSqlParameterSource entityParams(EntityType entityType, Long entityId) {
        return new MapSqlParameterSource()
                .addValue("entityType", entityType.name())
                .addValue("entityId",   entityId);
    }

    private static List<String> toStringList(ResultSet rs, String col) {
        try {
            java.sql.Array arr = rs.getArray(col);
            if (arr == null) return List.of();
            String[] arr2 = (String[]) arr.getArray();
            return arr2 == null ? List.of() : List.of(arr2);
        } catch (Exception _) {
            return List.of();
        }
    }
}
