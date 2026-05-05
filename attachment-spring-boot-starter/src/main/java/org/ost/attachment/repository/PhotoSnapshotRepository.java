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
            " "  + PhotoSnapshotDescriptor.Write.VERSION + "," +
            " "  + PhotoSnapshotDescriptor.Write.ATTACHMENT_URLS + "," +
            " "  + PhotoSnapshotDescriptor.Write.CHANGES_SUMMARY + "," +
            " "  + PhotoSnapshotDescriptor.Write.CHANGED_BY_USER_ID + ", created_at)" +
            " VALUES (:adId," +
            " (SELECT COUNT(*)::int FROM audit_log WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :adId)," +
            " :urls, CAST(:changes AS JSONB), :userId, NOW())"
    );

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
                " FROM " + PhotoSnapshotDescriptor.TABLE +
                " WHERE advertisement_id = :adId ORDER BY version DESC LIMIT 1")
                .paramSource(new MapSqlParameterSource("adId", adId))
                .query((rs, row) -> toStringList(rs, PhotoSnapshotDescriptor.Write.ATTACHMENT_URLS))
                .optional().orElse(List.of());
    }

    public String[] getUrlsAtVersion(Long adId, int version) {
        return jdbcClient.sql(
                "SELECT " + PhotoSnapshotDescriptor.Write.ATTACHMENT_URLS +
                " FROM " + PhotoSnapshotDescriptor.TABLE +
                " WHERE advertisement_id = :adId AND version <= :version" +
                " ORDER BY version DESC LIMIT 1")
                .paramSource(new MapSqlParameterSource().addValue("adId", adId).addValue("version", version))
                .query((rs, row) -> toStringList(rs, PhotoSnapshotDescriptor.Write.ATTACHMENT_URLS))
                .optional()
                .map(l -> l.toArray(new String[0]))
                .orElse(new String[0]);
    }

    public Optional<List<String>> getUrlsForAdvSnapshot(Long adId, Long advSnapshotId) {
        return jdbcClient.sql(
                "SELECT " + PhotoSnapshotDescriptor.Write.ATTACHMENT_URLS +
                " FROM " + PhotoSnapshotDescriptor.TABLE +
                " WHERE advertisement_id = :adId" +
                " AND version <= (" +
                "   SELECT COUNT(*)::int FROM audit_log" +
                "   WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :adId" +
                "     AND created_at <= (SELECT created_at FROM audit_log WHERE id = :snapId)" +
                " ) ORDER BY version DESC LIMIT 1")
                .paramSource(new MapSqlParameterSource().addValue("adId", adId).addValue("snapId", advSnapshotId))
                .query((rs, row) -> toStringList(rs, PhotoSnapshotDescriptor.Write.ATTACHMENT_URLS))
                .optional();
    }

    public Optional<String> getChangesJson(Long adId, int version) {
        return jdbcClient.sql(
                "SELECT " + PhotoSnapshotDescriptor.Write.CHANGES_SUMMARY + "::text" +
                " FROM " + PhotoSnapshotDescriptor.TABLE +
                " WHERE advertisement_id = :adId AND version = :version AND changes_summary IS NOT NULL")
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
        } catch (Exception e) {
            return List.of();
        }
    }
}
