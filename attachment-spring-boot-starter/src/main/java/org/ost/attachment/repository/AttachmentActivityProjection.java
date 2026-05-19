package org.ost.attachment.repository;

import org.jetbrains.annotations.NotNull;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.audit.dto.SnapshotPayload;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityType;
import org.ost.attachment.service.AttachmentSnapshotService;
import org.ost.sqlengine.projection.SqlFixedQuery;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.ost.attachment.repository.AttachmentSnapshotDescriptor.*;

public class AttachmentActivityProjection extends SqlFixedQuery<ActivityItemDto> {

    private static final String QUERY =
            "SELECT ps.id AS ps_id, ps.entity_type, ps.entity_id," +
            " ps.changes_summary::text AS changes_summary, ps.created_at, ps.changed_by_actor_id" +
            " FROM " + SOURCE +
            " WHERE ps.changed_by_actor_id = :actorId" +
            " AND ps.changes_summary IS NOT NULL" +
            " ORDER BY ps.created_at DESC LIMIT 20";

    private final AttachmentSnapshotService attachmentSnapshotService;

    public AttachmentActivityProjection(AttachmentSnapshotService attachmentSnapshotService) {
        super(List.of(ID, ENTITY_TYPE, ENTITY_ID, CHANGES_SUMMARY, CREATED_AT, CHANGED_BY_ACTOR_ID));
        this.attachmentSnapshotService = attachmentSnapshotService;
    }

    @Override
    public String querySql() {
        return QUERY;
    }

    @Override
    public ActivityItemDto mapRow(@NotNull ResultSet rs, int rowNum) throws SQLException {
        return new ActivityItemDto(
                ID.extract(rs),
                ENTITY_ID.extract(rs),
                EntityType.valueOf(ENTITY_TYPE.extract(rs)),
                "—",
                ActionType.UPDATED,
                CREATED_AT.extract(rs),
                false,
                attachmentSnapshotService.parseMediaChanges(CHANGES_SUMMARY.extract(rs)),
                CHANGED_BY_ACTOR_ID.extract(rs),
                "—",
                new SnapshotPayload(null)
        );
    }
}
