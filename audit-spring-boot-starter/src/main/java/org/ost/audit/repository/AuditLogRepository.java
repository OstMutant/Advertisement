package org.ost.audit.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.audit.dto.EntityHistoryDto;
import org.ost.platform.audit.dto.SnapshotContentDto;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.spi.EntityDisplayNameResolver;
import org.ost.sqlengine.RepositoryCustom;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AuditLogRepository extends RepositoryCustom {

    private final AuditLogDescriptor.Read.Activity.Projection activityProjection;
    private final AuditLogDescriptor.Read.History.Projection  historyProjection;

    public AuditLogRepository(JdbcClient jdbcClient,
                              @Qualifier("auditObjectMapper") ObjectMapper objectMapper,
                              List<EntityDisplayNameResolver> resolvers) {
        super(jdbcClient);
        this.activityProjection = new AuditLogDescriptor.Read.Activity.Projection(objectMapper, resolvers);
        this.historyProjection  = new AuditLogDescriptor.Read.History.Projection(objectMapper);
    }

    public void insert(EntityType entityType, Long entityId, ActionType actionType,
                       String snapshotData, String changesSummary, Long actorId) {
        executeUpdate(AuditLogDescriptor.Write.INSERT,
                AuditLogDescriptor.Write.insertParams(entityType, entityId, actionType,
                        snapshotData, changesSummary, actorId));
    }

    public Optional<String> getLastSnapshotData(EntityType entityType, Long entityId) {
        return findOne(AuditLogDescriptor.Read.SELECT_LAST_SNAPSHOT_DATA,
                AuditLogDescriptor.Read.entityParams(entityType, entityId),
                (rs, row) -> rs.getString(1));
    }

    public Optional<SnapshotContentDto> getSnapshotContent(Long snapshotId, EntityType entityType) {
        return findOne(AuditLogDescriptor.Read.SELECT_SNAPSHOT_CONTENT_BY_ID,
                AuditLogDescriptor.Read.snapshotByIdParams(snapshotId, entityType),
                (rs, row) -> AuditLogDescriptor.Read.mapSnapshotContent(rs));
    }

    public Optional<SnapshotContentDto> getPreviousSnapshotContent(Long snapshotId, EntityType entityType) {
        return findOne(AuditLogDescriptor.Read.SELECT_PREVIOUS_SNAPSHOT_CONTENT,
                AuditLogDescriptor.Read.previousSnapshotContentParams(snapshotId, entityType),
                (rs, row) -> AuditLogDescriptor.Read.mapSnapshotContent(rs));
    }

    public Optional<Long> findLastSnapshotId(EntityType entityType, Long entityId) {
        return findOne(AuditLogDescriptor.Read.SELECT_LAST_SNAPSHOT_ID,
                AuditLogDescriptor.Read.entityParams(entityType, entityId),
                Long.class);
    }

    public String getChangesSummary(Long snapshotId) {
        return findOne(AuditLogDescriptor.Read.SELECT_CHANGES_SUMMARY,
                AuditLogDescriptor.Read.idParams(snapshotId),
                String.class).orElseThrow();
    }

    public void updateChangesSummary(Long snapshotId, String json) {
        executeUpdate(AuditLogDescriptor.Write.UPDATE_CHANGES_SUMMARY,
                AuditLogDescriptor.Write.updateChangesSummaryParams(snapshotId, json));
    }

    public void deleteOlderThan(int days) {
        executeUpdate(AuditLogDescriptor.Write.DELETE_OLDER_THAN,
                AuditLogDescriptor.Write.deleteOlderThanParams(days));
    }

    public List<EntityHistoryDto> getEntityHistory(EntityType entityType, Long entityId, Long filterUserId) {
        return queryAll(historyProjection,
                AuditLogDescriptor.Read.History.params(entityType, entityId, filterUserId));
    }

    public List<ActivityItemDto> findActivityByActor(Long actorId) {
        return queryAll(activityProjection,
                AuditLogDescriptor.Read.Activity.byActorParams(actorId));
    }
}
