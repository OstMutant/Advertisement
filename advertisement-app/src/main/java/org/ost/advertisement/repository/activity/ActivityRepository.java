package org.ost.advertisement.repository.activity;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.ActivityItemDto;
import org.ost.advertisement.entities.ActionType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ActivityRepository {

    private static final String QUERY = """
            WITH adv_act AS (
                SELECT s.id                                                             AS snapshot_id,
                       s.advertisement_id                                               AS entity_id,
                       'ADVERTISEMENT'                                                  AS entity_type,
                       s.title                                                          AS display_name,
                       s.action_type,
                       s.created_at,
                       EXISTS(SELECT 1 FROM advertisement a
                              WHERE a.id = s.advertisement_id AND a.deleted_at IS NULL) AS entity_exists,
                       s.changes_summary,
                       s.changed_by_user_id,
                       COALESCE(u.name, '—')                                            AS changed_by_name
                FROM advertisement_snapshot s
                LEFT JOIN user_information u ON u.id = s.changed_by_user_id
                WHERE s.changed_by_user_id = :userId
                ORDER BY s.created_at DESC LIMIT 20
            ),
            user_act AS (
                SELECT s.id                                     AS snapshot_id,
                       s.user_id                                AS entity_id,
                       'USER'                                   AS entity_type,
                       s.name                                   AS display_name,
                       s.action_type,
                       s.created_at,
                       EXISTS(SELECT 1 FROM user_information u2
                              WHERE u2.id = s.user_id)          AS entity_exists,
                       s.changes_summary,
                       s.changed_by_user_id,
                       COALESCE(u.name, '—')                   AS changed_by_name
                FROM user_snapshot s
                LEFT JOIN user_information u ON u.id = s.changed_by_user_id
                WHERE s.changed_by_user_id = :userId OR s.user_id = :userId
                ORDER BY s.created_at DESC LIMIT 20
            )
            SELECT * FROM adv_act
            UNION ALL
            SELECT * FROM user_act
            ORDER BY created_at DESC
            LIMIT 20
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public List<ActivityItemDto> findByUserId(Long userId) {
        return jdbc.query(QUERY,
                new MapSqlParameterSource("userId", userId),
                (rs, rowNum) -> new ActivityItemDto(
                        rs.getLong("snapshot_id"),
                        rs.getLong("entity_id"),
                        rs.getString("entity_type"),
                        rs.getString("display_name"),
                        ActionType.valueOf(rs.getString("action_type")),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getBoolean("entity_exists"),
                        rs.getString("changes_summary"),
                        rs.getLong("changed_by_user_id"),
                        rs.getString("changed_by_name")
                ));
    }
}
