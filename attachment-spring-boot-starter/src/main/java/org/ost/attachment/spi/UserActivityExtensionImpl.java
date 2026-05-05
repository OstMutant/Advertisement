package org.ost.attachment.spi;

import lombok.RequiredArgsConstructor;
import org.ost.advertisement.events.dto.ActivityItemDto;
import org.ost.advertisement.events.model.ActionType;
import org.ost.advertisement.events.model.ChangeEntry;
import org.ost.advertisement.events.spi.UserActivityExtension;
import org.ost.attachment.repository.PhotoSnapshotDescriptor;
import org.ost.attachment.service.PhotoSnapshotService;
import org.ost.advertisement.spi.storage.ConditionalOnStorageEnabled;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnStorageEnabled
@RequiredArgsConstructor
public class UserActivityExtensionImpl implements UserActivityExtension {

    private static final String QUERY =
            "SELECT ps.id, ps.advertisement_id, ps.changes_summary::text," +
            " ps.created_at, ps.changed_by_user_id," +
            " COALESCE(u.name, '—') AS changed_by_name," +
            " COALESCE(a.title, '—') AS display_name," +
            " a.description AS snapshot_description," +
            " EXISTS(SELECT 1 FROM advertisement a2" +
            "        WHERE a2.id = ps.advertisement_id AND a2.deleted_at IS NULL) AS entity_exists" +
            " FROM " + PhotoSnapshotDescriptor.SOURCE +
            " LEFT JOIN advertisement a ON a.id = ps.advertisement_id" +
            " LEFT JOIN user_information u ON u.id = ps.changed_by_user_id" +
            " WHERE ps.changed_by_user_id = :userId" +
            " AND ps.changes_summary IS NOT NULL" +
            " ORDER BY ps.created_at DESC LIMIT 20";

    private final JdbcClient         jdbcClient;
    private final PhotoSnapshotService photoSnapshotService;

    @Override
    public List<ActivityItemDto> getPhotoActivity(Long userId) {
        return jdbcClient.sql(QUERY)
                .paramSource(new MapSqlParameterSource("userId", userId))
                .query((rs, row) -> {
                    List<ChangeEntry> changes = photoSnapshotService.parsePhotoChanges(
                            rs.getString(PhotoSnapshotDescriptor.Write.CHANGES_SUMMARY));
                    return new ActivityItemDto(
                            rs.getLong("id"),
                            rs.getLong(PhotoSnapshotDescriptor.Write.ADVERTISEMENT_ID),
                            "ADVERTISEMENT",
                            rs.getString("display_name"),
                            ActionType.UPDATED,
                            rs.getTimestamp("created_at").toInstant(),
                            rs.getBoolean("entity_exists"),
                            changes,
                            rs.getLong(PhotoSnapshotDescriptor.Write.CHANGED_BY_USER_ID),
                            rs.getString("changed_by_name"),
                            rs.getString("display_name"),
                            rs.getString("snapshot_description"),
                            null,
                            null
                    );
                })
                .list();
    }
}
