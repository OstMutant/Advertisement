package org.ost.attachment.spi;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.dto.ActivityItemDto;
import org.ost.advertisement.events.model.ActionType;
import org.ost.advertisement.events.model.ChangeEntry;
import org.ost.advertisement.events.spi.UserActivityExtension;
import org.ost.attachment.service.PhotoSnapshotService;
import org.ost.storage.api.ConditionalOnStorageEnabled;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnStorageEnabled
@RequiredArgsConstructor
public class UserActivityExtensionImpl implements UserActivityExtension {

    private final NamedParameterJdbcTemplate jdbc;
    private final PhotoSnapshotService       photoSnapshotService;

    @Override
    public List<ActivityItemDto> getPhotoActivity(Long userId) {
        return jdbc.query("""
                SELECT ps.id, ps.advertisement_id, ps.changes_summary::text,
                       ps.created_at, ps.changed_by_user_id,
                       COALESCE(u.name, '—')    AS changed_by_name,
                       COALESCE(a.title, '—')   AS display_name,
                       EXISTS(SELECT 1 FROM advertisement a2
                              WHERE a2.id = ps.advertisement_id AND a2.deleted_at IS NULL) AS entity_exists
                FROM photo_snapshot ps
                LEFT JOIN advertisement a ON a.id = ps.advertisement_id
                LEFT JOIN user_information u ON u.id = ps.changed_by_user_id
                WHERE ps.changed_by_user_id = :userId
                  AND ps.changes_summary IS NOT NULL
                ORDER BY ps.created_at DESC
                LIMIT 20
                """,
                new MapSqlParameterSource("userId", userId),
                (rs, row) -> {
                    List<ChangeEntry> changes = photoSnapshotService.parsePhotoChanges(
                            rs.getString("changes_summary"));
                    return new ActivityItemDto(
                            rs.getLong("id"),
                            rs.getLong("advertisement_id"),
                            "ADVERTISEMENT",
                            rs.getString("display_name"),
                            ActionType.UPDATED,
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getBoolean("entity_exists"),
                            changes,
                            rs.getLong("changed_by_user_id"),
                            rs.getString("changed_by_name"),
                            rs.getString("display_name"),
                            null
                    );
                }
        );
    }
}
