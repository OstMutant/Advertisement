package org.ost.attachment.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.model.ChangeEntry;
import org.ost.attachment.repository.AttachmentProjection;
import org.ost.attachment.repository.PhotoSnapshotProjection;
import org.ost.sqlengine.writer.SqlFixedWriter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PhotoSnapshotService {

    record PhotoChange(List<String> before, List<String> after) {}

    private static final SqlFixedWriter INSERT_SNAPSHOT = SqlFixedWriter.of(
            "INSERT INTO " + PhotoSnapshotProjection.Write.TABLE +
            " (" + PhotoSnapshotProjection.Write.ADVERTISEMENT_ID + "," +
            " "  + PhotoSnapshotProjection.Write.VERSION + "," +
            " "  + PhotoSnapshotProjection.Write.ATTACHMENT_URLS + "," +
            " "  + PhotoSnapshotProjection.Write.CHANGES_SUMMARY + "," +
            " "  + PhotoSnapshotProjection.Write.CHANGED_BY_USER_ID + ", created_at)" +
            " VALUES (:adId," +
            " (SELECT COUNT(*)::int FROM audit_log WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :adId)," +
            " :urls, CAST(:changes AS JSONB), :userId, NOW())"
    );

    private final JdbcClient   jdbcClient;
    private final ObjectMapper objectMapper;

    @Transactional
    public void capture(Long adId, Long userId) {
        List<String> currentUrls = getActiveUrls(adId);
        List<String> prevUrls    = getPrevUrls(adId);
        PhotoChange  diff        = buildDiff(prevUrls, currentUrls);
        if (diff == null) return;

        // pgjdbc converts String[] to PostgreSQL text[] array automatically
        INSERT_SNAPSHOT.execute(jdbcClient,
                new MapSqlParameterSource()
                        .addValue("adId",    adId)
                        .addValue("urls",    currentUrls.toArray(new String[0]))
                        .addValue("changes", toJson(diff))
                        .addValue("userId",  userId));
    }

    public String getPhotoStateAtVersion(Long adId, int version) {
        String[] urls = getUrlsAtVersion(adId, version);
        if (urls.length == 0) return "";
        return Arrays.stream(urls).map(PhotoSnapshotService::filename).collect(Collectors.joining(", "));
    }

    public String getPhotoStateForAdvSnapshot(Long adId, Long advSnapshotId) {
        return jdbcClient.sql(
                "SELECT " + PhotoSnapshotProjection.Write.ATTACHMENT_URLS +
                " FROM " + PhotoSnapshotProjection.TABLE +
                " WHERE advertisement_id = :adId" +
                " AND version <= (" +
                "   SELECT COUNT(*)::int FROM audit_log" +
                "   WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :adId" +
                "     AND created_at <= (SELECT created_at FROM audit_log WHERE id = :snapId)" +
                " ) ORDER BY version DESC LIMIT 1")
                .paramSource(new MapSqlParameterSource().addValue("adId", adId).addValue("snapId", advSnapshotId))
                .query((rs, row) -> toStringList(rs, PhotoSnapshotProjection.Write.ATTACHMENT_URLS))
                .optional()
                .map(l -> l.stream().map(PhotoSnapshotService::filename).collect(Collectors.joining(", ")))
                .orElse("");
    }

    public String[] getUrlsAtVersion(Long adId, int version) {
        return jdbcClient.sql(
                "SELECT " + PhotoSnapshotProjection.Write.ATTACHMENT_URLS +
                " FROM " + PhotoSnapshotProjection.TABLE +
                " WHERE advertisement_id = :adId AND version <= :version" +
                " ORDER BY version DESC LIMIT 1")
                .paramSource(new MapSqlParameterSource().addValue("adId", adId).addValue("version", version))
                .query((rs, row) -> toStringList(rs, PhotoSnapshotProjection.Write.ATTACHMENT_URLS))
                .optional()
                .map(l -> l.toArray(new String[0]))
                .orElse(new String[0]);
    }

    public List<String> getActiveUrls(Long adId) {
        return jdbcClient.sql(
                "SELECT " + AttachmentProjection.Write.URL +
                " FROM " + AttachmentProjection.TABLE +
                " WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :adId AND deleted_at IS NULL")
                .paramSource(new MapSqlParameterSource("adId", adId))
                .query(String.class).list();
    }

    public boolean photosMatchCurrent(Long adId, int version) {
        List<String> atVersion = jdbcClient.sql(
                "SELECT " + PhotoSnapshotProjection.Write.ATTACHMENT_URLS +
                " FROM " + PhotoSnapshotProjection.TABLE +
                " WHERE advertisement_id = :adId AND version <= :version" +
                " ORDER BY version DESC LIMIT 1")
                .paramSource(new MapSqlParameterSource().addValue("adId", adId).addValue("version", version))
                .query((rs, row) -> toStringList(rs, PhotoSnapshotProjection.Write.ATTACHMENT_URLS))
                .optional().orElse(List.of());
        List<String> current  = getActiveUrls(adId);
        List<String> atNames  = atVersion.stream().map(PhotoSnapshotService::filename).sorted().toList();
        List<String> curNames = current.stream().map(PhotoSnapshotService::filename).sorted().toList();
        return atNames.equals(curNames);
    }

    public List<ChangeEntry> getChangesForVersion(Long adId, int version) {
        return jdbcClient.sql(
                "SELECT " + PhotoSnapshotProjection.Write.CHANGES_SUMMARY + "::text" +
                " FROM " + PhotoSnapshotProjection.TABLE +
                " WHERE advertisement_id = :adId AND version = :version AND changes_summary IS NOT NULL")
                .paramSource(new MapSqlParameterSource().addValue("adId", adId).addValue("version", version))
                .query((rs, row) -> parsePhotoChanges(rs.getString(1)))
                .optional().orElse(List.of());
    }

    // ── internals ────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<ChangeEntry> parsePhotoChanges(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<Map<String, Object>> raw = objectMapper.readValue(json, new TypeReference<>() {});
            return raw.stream()
                    .map(m -> {
                        List<String> before = (List<String>) m.getOrDefault("before", List.of());
                        List<String> after  = (List<String>) m.getOrDefault("after",  List.of());
                        String beforeStr = before.isEmpty() ? "—" : String.join(", ", before);
                        String afterStr  = after.isEmpty()  ? "—" : String.join(", ", after);
                        return (ChangeEntry) new ChangeEntry.GenericChange("changes.photos", beforeStr, afterStr);
                    })
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> getPrevUrls(Long adId) {
        return jdbcClient.sql(
                "SELECT " + PhotoSnapshotProjection.Write.ATTACHMENT_URLS +
                " FROM " + PhotoSnapshotProjection.TABLE +
                " WHERE advertisement_id = :adId ORDER BY version DESC LIMIT 1")
                .paramSource(new MapSqlParameterSource("adId", adId))
                .query((rs, row) -> toStringList(rs, PhotoSnapshotProjection.Write.ATTACHMENT_URLS))
                .optional().orElse(List.of());
    }

    private static PhotoChange buildDiff(List<String> prev, List<String> curr) {
        List<String> prevNames = prev.stream().map(PhotoSnapshotService::filename).toList();
        List<String> currNames = curr.stream().map(PhotoSnapshotService::filename).toList();
        return Objects.equals(prevNames, currNames) ? null : new PhotoChange(prevNames, currNames);
    }

    private String toJson(PhotoChange diff) {
        if (diff == null) return null;
        try {
            return objectMapper.writeValueAsString(List.of(diff));
        } catch (Exception e) {
            return null;
        }
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

    private static String filename(String url) {
        if (url == null || url.isBlank()) return "";
        int i = url.lastIndexOf('/');
        return i >= 0 ? url.substring(i + 1) : url;
    }
}
