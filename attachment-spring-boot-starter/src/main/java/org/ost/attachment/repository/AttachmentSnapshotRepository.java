package org.ost.attachment.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.core.model.EntityType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
@SuppressWarnings("java:S1192")
public class AttachmentSnapshotRepository {

    @Qualifier("attachmentObjectMapper") private final ObjectMapper objectMapper;
    private final JdbcClient                                       jdbcClient;

    @SneakyThrows
    public void insert(@NonNull EntityType entityType, @NonNull Long entityId, @NonNull String[] urls, @NonNull List<AttachmentMediaChange> changes, @NonNull Long actorId) {
        jdbcClient.sql("""
                        INSERT INTO attachment_snapshot
                            (entity_type, entity_id, attachment_urls, changes_summary, changed_by_actor_id, created_at, version)
                        VALUES (:entityType, :entityId, :urls, :changes, :actorId, NOW(),
                            COALESCE((SELECT MAX(version) FROM attachment_snapshot
                                      WHERE entity_type = :entityType AND entity_id = :entityId), 0) + 1)
                        """)
                  .paramSource(new MapSqlParameterSource()
                          .addValue("entityType", entityType.name())
                          .addValue("entityId",   entityId)
                          .addValue("urls",        urls)
                          .addValue("changes",     objectMapper.writeValueAsString(changes), Types.OTHER)
                          .addValue("actorId",     actorId))
                  .update();
    }

    public Optional<List<String>> getPrevUrls(@NonNull EntityType entityType, @NonNull Long entityId) {
        return jdbcClient.sql("""
                        SELECT attachment_urls FROM attachment_snapshot
                        WHERE entity_type = :entityType AND entity_id = :entityId
                        ORDER BY id DESC LIMIT 1
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType.name())
                                 .addValue("entityId",   entityId))
                         .query((rs, _) -> extractUrls(rs))
                         .optional();
    }

    public Optional<List<String>> getUrlsById(@NonNull Long id) {
        return jdbcClient.sql("SELECT attachment_urls FROM attachment_snapshot WHERE id = :id")
                         .param("id", id)
                         .query((rs, _) -> extractUrls(rs))
                         .optional();
    }

    @SneakyThrows
    public Optional<List<AttachmentMediaChange>> findChangesById(@NonNull Long id) {
        Optional<String> json = jdbcClient.sql("""
                        SELECT changes_summary::text AS changes_summary FROM attachment_snapshot
                        WHERE id = :id AND changes_summary IS NOT NULL
                        """)
                         .param("id", id)
                         .query(String.class)
                         .optional();
        return json.isEmpty() ? Optional.empty()
                : Optional.of(objectMapper.readValue(json.get(), new TypeReference<>() {}));
    }

    // pure age-based purge -- snapshot rows are just historical bookkeeping (improvement-090 item 3)
    public int deleteOlderThan(int days) {
        return jdbcClient.sql("DELETE FROM attachment_snapshot WHERE created_at < NOW() - MAKE_INTERVAL(days => :days)")
                         .paramSource(new MapSqlParameterSource("days", days))
                         .update();
    }

    public Optional<Long> findLatestId(@NonNull EntityType entityType, @NonNull Long entityId) {
        return jdbcClient.sql("""
                        SELECT id FROM attachment_snapshot
                        WHERE entity_type = :entityType AND entity_id = :entityId
                        ORDER BY id DESC LIMIT 1
                        """)
                         .paramSource(new MapSqlParameterSource()
                                 .addValue("entityType", entityType.name())
                                 .addValue("entityId",   entityId))
                         .query(Long.class)
                         .optional();
    }

    private static List<String> extractUrls(ResultSet rs) {
        try {
            java.sql.Array arr = rs.getArray("attachment_urls");
            if (arr == null) return List.of();
            List<String> urls = new ArrayList<>();
            try (ResultSet arrRs = arr.getResultSet()) {
                while (arrRs.next()) {
                    urls.add(arrRs.getString(2));
                }
            }
            return urls;
        } catch (SQLException e) { //NOSONAR java:S7467 -- e.getMessage() is used
            log.warn("Failed to read attachment_urls array: {}", e.getMessage());
            return List.of();
        }
    }
}
