package org.ost.attachment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PhotoSnapshotService {

    record PhotoChange(List<String> before, List<String> after) {}

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper               objectMapper;

    @Transactional
    public void capture(Long adId, Long userId) {
        List<String> currentUrls = getActiveUrls(adId);
        List<String> prevUrls    = getPrevUrls(adId);
        PhotoChange  diff        = buildDiff(prevUrls, currentUrls);
        if (diff == null) return;

        Array urlArray = toSqlArray(currentUrls);
        jdbc.update("""
                INSERT INTO photo_snapshot
                    (advertisement_id, version, attachment_urls, changes_summary, changed_by_user_id, created_at)
                VALUES (:adId,
                    (SELECT COALESCE(MAX(version), 0) FROM advertisement_snapshot WHERE advertisement_id = :adId),
                    :urls, CAST(:changes AS JSONB), :userId, NOW())
                """,
                new MapSqlParameterSource()
                        .addValue("adId",    adId)
                        .addValue("urls",    urlArray)
                        .addValue("changes", toJson(diff))
                        .addValue("userId",  userId));
    }

    public String[] getUrlsAtVersion(Long adId, int version) {
        return jdbc.query(
                "SELECT attachment_urls FROM photo_snapshot " +
                "WHERE advertisement_id = :adId AND version <= :version " +
                "ORDER BY version DESC LIMIT 1",
                new MapSqlParameterSource().addValue("adId", adId).addValue("version", version),
                (rs, row) -> toStringList(rs, "attachment_urls")
        ).stream().findFirst().map(l -> l.toArray(new String[0])).orElse(new String[0]);
    }

    public List<String> getActiveUrls(Long adId) {
        return jdbc.queryForList(
                "SELECT url FROM attachment WHERE entity_type = 'ADVERTISEMENT' AND entity_id = :adId AND deleted_at IS NULL",
                new MapSqlParameterSource("adId", adId),
                String.class
        );
    }

    // ── internals ────────────────────────────────────────────────────────────

    private List<String> getPrevUrls(Long adId) {
        return jdbc.query(
                "SELECT attachment_urls FROM photo_snapshot WHERE advertisement_id = :adId ORDER BY version DESC LIMIT 1",
                new MapSqlParameterSource("adId", adId),
                (rs, row) -> toStringList(rs, "attachment_urls")
        ).stream().findFirst().orElse(List.of());
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

    private Array toSqlArray(List<String> list) {
        return jdbc.getJdbcOperations().execute(
                (Connection conn) -> conn.createArrayOf("text", list.toArray(new String[0]))
        );
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
