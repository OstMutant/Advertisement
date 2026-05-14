package org.ost.attachment.repository;

import lombok.RequiredArgsConstructor;
import org.ost.sqlengine.writer.SqlWriteCommand;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PhotoSnapshotRepository {

    private static final SqlWriteCommand INSERT = SqlWriteCommand.of(
            "INSERT INTO " + PhotoSnapshotDescriptor.Write.TABLE +
            " (" + PhotoSnapshotDescriptor.Write.ADVERTISEMENT_ID + "," +
            " "  + PhotoSnapshotDescriptor.Write.ATTACHMENT_URLS + "," +
            " "  + PhotoSnapshotDescriptor.Write.CHANGES_SUMMARY + "," +
            " "  + PhotoSnapshotDescriptor.Write.CHANGED_BY_USER_ID + ", created_at)" +
            " VALUES (:adId," +
            " :urls, CAST(:changes AS JSONB), :userId, NOW())"
    );

    private static final String FROM_TABLE = " FROM " + PhotoSnapshotDescriptor.TABLE;

    private final JdbcClient jdbcClient;

    public void insert(Long adId, String[] urls, String changesJson, Long userId) {
        INSERT.execute(jdbcClient,
                new MapSqlParameterSource()
                        .addValue("adId",    adId)
                        .addValue("urls",    urls)
                        .addValue("changes", changesJson)
                        .addValue("userId",  userId));
    }

    public List<String> getPrevUrls(Long adId) {
        return jdbcClient.sql(
                "SELECT " + PhotoSnapshotDescriptor.Write.ATTACHMENT_URLS +
                FROM_TABLE +
                " WHERE advertisement_id = :adId ORDER BY created_at DESC LIMIT 1")
                .paramSource(new MapSqlParameterSource("adId", adId))
                .query((rs, row) -> toStringList(rs, PhotoSnapshotDescriptor.Write.ATTACHMENT_URLS))
                .optional().orElse(List.of());
    }

    // Subquery: created_at of audit_log entry at position :version+1 (for upper bound).
    // photo_snapshot is created AFTER the audit entry in the same save flow, so we use
    // "created_at < next_audit_entry.created_at" to correctly scope each version's photos.
    private static final String NEXT_AUDIT_TS =
            "SELECT al.created_at FROM (" +
            "    SELECT created_at," +
            "           ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY created_at) AS rn" +
            "    FROM audit_log WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :adId" +
            ") al WHERE al.rn = :version + 1";

    public String[] getUrlsAtVersion(Long adId, int version) {
        return jdbcClient.sql(
                "SELECT attachment_urls" +
                FROM_TABLE +
                " WHERE advertisement_id = :adId" +
                "   AND created_at < COALESCE((" + NEXT_AUDIT_TS + "), 'infinity'::timestamptz)" +
                " ORDER BY created_at DESC LIMIT 1")
                .paramSource(new MapSqlParameterSource().addValue("adId", adId).addValue("version", version))
                .query((rs, row) -> toStringList(rs, PhotoSnapshotDescriptor.Write.ATTACHMENT_URLS))
                .optional()
                .map(l -> l.toArray(new String[0]))
                .orElse(new String[0]);
    }

    public Optional<List<String>> getUrlsForAdvSnapshot(Long adId, Long advSnapshotId) {
        return jdbcClient.sql(
                "SELECT attachment_urls" +
                FROM_TABLE +
                " WHERE advertisement_id = :adId" +
                "   AND created_at < COALESCE((" +
                "       SELECT created_at FROM audit_log" +
                "       WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :adId" +
                "         AND created_at > (SELECT created_at FROM audit_log WHERE id = :snapId)" +
                "       ORDER BY created_at ASC LIMIT 1" +
                "   ), 'infinity'::timestamptz)" +
                " ORDER BY created_at DESC LIMIT 1")
                .paramSource(new MapSqlParameterSource().addValue("adId", adId).addValue("snapId", advSnapshotId))
                .query((rs, row) -> toStringList(rs, PhotoSnapshotDescriptor.Write.ATTACHMENT_URLS))
                .optional();
    }

    public Optional<String> getChangesJson(Long adId, int version) {
        String thisAuditTs =
                "SELECT al.created_at FROM (" +
                "    SELECT created_at," +
                "           ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY created_at) AS rn" +
                "    FROM audit_log WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :adId" +
                ") al WHERE al.rn = :version";
        return jdbcClient.sql(
                "SELECT changes_summary::text" +
                FROM_TABLE +
                " WHERE advertisement_id = :adId" +
                "   AND created_at >= (" + thisAuditTs + ")" +
                "   AND created_at < COALESCE((" + NEXT_AUDIT_TS + "), 'infinity'::timestamptz)" +
                "   AND changes_summary IS NOT NULL" +
                " ORDER BY created_at ASC LIMIT 1")
                .paramSource(new MapSqlParameterSource().addValue("adId", adId).addValue("version", version))
                .query((rs, row) -> rs.getString(1))
                .optional();
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
