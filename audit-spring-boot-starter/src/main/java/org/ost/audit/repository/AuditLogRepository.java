package org.ost.audit.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.audit.dto.EntityHistoryDto;
import org.ost.platform.audit.dto.SnapshotContent;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.EntityDisplayNameResolver;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

public class AuditLogRepository {

    private final JdbcClient jdbcClient;
    private final AuditLogDescriptor.Read.Activity.Projection activityProjection;
    private final AuditLogDescriptor.Read.History.Projection  historyProjection;

    public AuditLogRepository(JdbcClient jdbcClient,
                              @Qualifier("auditObjectMapper") ObjectMapper objectMapper,
                              List<EntityDisplayNameResolver> resolvers) {
        this.jdbcClient         = jdbcClient;
        this.activityProjection = new AuditLogDescriptor.Read.Activity.Projection(objectMapper, resolvers);
        this.historyProjection  = new AuditLogDescriptor.Read.History.Projection(objectMapper);
    }

    public void insert(EntityType entityType, Long entityId, ActionType actionType,
                       String snapshotData, String changesSummary, Long actorId) {
        AuditLogDescriptor.Write.INSERT.execute(jdbcClient,
                AuditLogDescriptor.Write.insertParams(entityType, entityId, actionType.name(),
                        snapshotData, changesSummary, actorId));
    }

    public Optional<String> getSnapshotData(Long snapshotId, EntityType entityType) {
        return jdbcClient.sql(AuditLogDescriptor.Read.SELECT_SNAPSHOT_DATA_BY_ID)
                .paramSource(AuditLogDescriptor.Read.snapshotByIdParams(snapshotId, entityType))
                .query((rs, row) -> rs.getString(1))
                .optional();
    }

    public Optional<String> getLastSnapshotData(EntityType entityType, Long entityId) {
        return jdbcClient.sql(AuditLogDescriptor.Read.SELECT_LAST_SNAPSHOT_DATA)
                .paramSource(AuditLogDescriptor.Read.entityParams(entityType, entityId))
                .query((rs, row) -> rs.getString(1))
                .optional();
    }

    public Optional<SnapshotContent> getSnapshotContent(Long snapshotId, EntityType entityType) {
        return jdbcClient.sql(AuditLogDescriptor.Read.SELECT_SNAPSHOT_CONTENT_BY_ID)
                .paramSource(AuditLogDescriptor.Read.snapshotByIdParams(snapshotId, entityType))
                .query((rs, row) -> AuditLogDescriptor.Read.mapSnapshotContent(rs))
                .optional();
    }

    public Optional<SnapshotContent> getPreviousSnapshotContent(Long snapshotId, EntityType entityType) {
        return jdbcClient.sql(AuditLogDescriptor.Read.SELECT_PREVIOUS_SNAPSHOT_CONTENT)
                .paramSource(AuditLogDescriptor.Read.previousSnapshotContentParams(snapshotId, entityType))
                .query((rs, row) -> AuditLogDescriptor.Read.mapSnapshotContent(rs))
                .optional();
    }

    public Optional<Long> findLastSnapshotId(EntityType entityType, Long entityId) {
        return jdbcClient.sql(AuditLogDescriptor.Read.SELECT_LAST_SNAPSHOT_ID)
                .paramSource(AuditLogDescriptor.Read.entityParams(entityType, entityId))
                .query(Long.class)
                .optional();
    }

    public String getChangesSummary(Long snapshotId) {
        return jdbcClient.sql(AuditLogDescriptor.Read.SELECT_CHANGES_SUMMARY)
                .paramSource(AuditLogDescriptor.Read.idParams(snapshotId))
                .query(String.class)
                .single();
    }

    public void updateChangesSummary(Long snapshotId, String json) {
        AuditLogDescriptor.Write.UPDATE_CHANGES_SUMMARY.execute(jdbcClient,
                AuditLogDescriptor.Write.updateChangesSummaryParams(snapshotId, json));
    }

    public int deleteOlderThan(int days) {
        return jdbcClient.sql(AuditLogDescriptor.Write.DELETE_OLDER_THAN)
                .paramSource(AuditLogDescriptor.Write.deleteOlderThanParams(days))
                .update();
    }

    public List<EntityHistoryDto> getEntityHistory(EntityType entityType, Long entityId, Long filterUserId) {
        return historyProjection.queryAll(jdbcClient,
                AuditLogDescriptor.Read.History.params(entityType, entityId, filterUserId));
    }

    public List<ActivityItemDto> findActivityByActor(Long actorId) {
        return activityProjection.queryAll(jdbcClient,
                AuditLogDescriptor.Read.Activity.byActorParams(actorId));
    }
}
