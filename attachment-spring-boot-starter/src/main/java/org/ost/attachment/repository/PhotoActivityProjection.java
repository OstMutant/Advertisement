package org.ost.attachment.repository;

import org.jetbrains.annotations.NotNull;
import org.ost.advertisement.events.dto.ActivityItemDto;
import org.ost.advertisement.events.model.ActionType;
import org.ost.attachment.service.PhotoSnapshotService;
import org.ost.sqlengine.projection.SqlFixedQuery;
import org.ost.sqlengine.projection.SqlSelectField;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import static org.ost.sqlengine.projection.SqlSelectFieldFactory.*;

public class PhotoActivityProjection extends SqlFixedQuery<ActivityItemDto> {

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

    static final SqlSelectField<Long>    SNAPSHOT_ID        = longVal("ps.id",                         "id");
    static final SqlSelectField<Long>    ADVERTISEMENT_ID   = longVal("ps.advertisement_id",           "advertisement_id");
    static final SqlSelectField<String>  CHANGES_SUMMARY    = str("ps.changes_summary",           "changes_summary");
    static final SqlSelectField<Instant> CREATED_AT         = instant("ps.created_at",            "created_at");
    static final SqlSelectField<Long>    CHANGED_BY_USER_ID = longVal("ps.changed_by_user_id",        "changed_by_user_id");
    static final SqlSelectField<String>  CHANGED_BY_NAME    = str("COALESCE(u.name,'—')",         "changed_by_name");
    static final SqlSelectField<String>  DISPLAY_NAME       = str("COALESCE(a.title,'—')",        "display_name");
    static final SqlSelectField<String>  SNAPSHOT_DESC      = str("a.description",                "snapshot_description");
    static final SqlSelectField<Boolean> ENTITY_EXISTS      = bool("entity_exists",               "entity_exists");

    private final PhotoSnapshotService photoSnapshotService;

    public PhotoActivityProjection(PhotoSnapshotService photoSnapshotService) {
        super(List.of(SNAPSHOT_ID, ADVERTISEMENT_ID, CHANGES_SUMMARY, CREATED_AT,
                      CHANGED_BY_USER_ID, CHANGED_BY_NAME, DISPLAY_NAME, SNAPSHOT_DESC, ENTITY_EXISTS));
        this.photoSnapshotService = photoSnapshotService;
    }

    @Override
    public String querySql() {
        return QUERY;
    }

    @Override
    public ActivityItemDto mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
        return new ActivityItemDto(
                SNAPSHOT_ID.extract(rs),
                ADVERTISEMENT_ID.extract(rs),
                "ADVERTISEMENT",
                DISPLAY_NAME.extract(rs),
                ActionType.UPDATED,
                CREATED_AT.extract(rs),
                ENTITY_EXISTS.extract(rs),
                photoSnapshotService.parsePhotoChanges(CHANGES_SUMMARY.extract(rs)),
                CHANGED_BY_USER_ID.extract(rs),
                CHANGED_BY_NAME.extract(rs),
                DISPLAY_NAME.extract(rs),
                SNAPSHOT_DESC.extract(rs),
                null,
                null
        );
    }
}
