package org.ost.audit.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ost.audit.entities.AuditLog;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.audit.dto.EntityHistoryDto;
import org.ost.platform.audit.dto.SnapshotContent;
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
public class AuditLogRepository {

    private final RepositoryCustom repo;
    private final AuditLogCrudRepository  crud;
    private final AuditLogDescriptor.Read.Activity.Projection activityProjection;
    private final AuditLogDescriptor.Read.History.Projection  historyProjection;

    public AuditLogRepository(JdbcClient jdbcClient,
                              AuditLogCrudRepository crud,
                              @Qualifier("auditObjectMapper") ObjectMapper objectMapper,
                              List<EntityDisplayNameResolver> resolvers) {
        this.repo               = new RepositoryCustom(jdbcClient);
        this.crud               = crud;
        this.activityProjection = new AuditLogDescriptor.Read.Activity.Projection(objectMapper, resolvers);
        this.historyProjection  = new AuditLogDescriptor.Read.History.Projection(objectMapper);
    }

    public void insert(EntityType entityType, Long entityId, ActionType actionType,
                       String snapshotData, String changesSummary, Long actorId) {
        repo.execute(AuditLogDescriptor.Write.INSERT,
                AuditLogDescriptor.Write.insertParams(entityType, entityId, actionType.name(),
                        snapshotData, changesSummary, actorId));
    }

    public Optional<AuditLog> findById(Long id) {
        return crud.findById(id);
    }

    public Optional<String> getSnapshotData(Long snapshotId, EntityType entityType) {
        return repo.findOne(AuditLogDescriptor.Read.SELECT_SNAPSHOT_DATA_BY_ID,
                AuditLogDescriptor.Read.snapshotByIdParams(snapshotId, entityType),
                (rs, row) -> rs.getString(1));
    }

    public Optional<String> getLastSnapshotData(EntityType entityType, Long entityId) {
        return repo.findOne(AuditLogDescriptor.Read.SELECT_LAST_SNAPSHOT_DATA,
                AuditLogDescriptor.Read.entityParams(entityType, entityId),
                (rs, row) -> rs.getString(1));
    }

    public Optional<SnapshotContent> getSnapshotContent(Long snapshotId, EntityType entityType) {
        return repo.findOne(AuditLogDescriptor.Read.SELECT_SNAPSHOT_CONTENT_BY_ID,
                AuditLogDescriptor.Read.snapshotByIdParams(snapshotId, entityType),
                (rs, row) -> AuditLogDescriptor.Read.mapSnapshotContent(rs));
    }

    public Optional<SnapshotContent> getPreviousSnapshotContent(Long snapshotId, EntityType entityType) {
        return repo.findOne(AuditLogDescriptor.Read.SELECT_PREVIOUS_SNAPSHOT_CONTENT,
                AuditLogDescriptor.Read.previousSnapshotContentParams(snapshotId, entityType),
                (rs, row) -> AuditLogDescriptor.Read.mapSnapshotContent(rs));
    }

    public Optional<Long> findLastSnapshotId(EntityType entityType, Long entityId) {
        return repo.findOne(AuditLogDescriptor.Read.SELECT_LAST_SNAPSHOT_ID,
                AuditLogDescriptor.Read.entityParams(entityType, entityId),
                Long.class);
    }

    public String getChangesSummary(Long snapshotId) {
        return repo.findOne(AuditLogDescriptor.Read.SELECT_CHANGES_SUMMARY,
                AuditLogDescriptor.Read.idParams(snapshotId),
                String.class).orElseThrow();
    }

    public void updateChangesSummary(Long snapshotId, String json) {
        repo.execute(AuditLogDescriptor.Write.UPDATE_CHANGES_SUMMARY,
                AuditLogDescriptor.Write.updateChangesSummaryParams(snapshotId, json));
    }

    public void deleteOlderThan(int days) {
        repo.execute(AuditLogDescriptor.Write.DELETE_OLDER_THAN,
                AuditLogDescriptor.Write.deleteOlderThanParams(days));
    }

    public List<EntityHistoryDto> getEntityHistory(EntityType entityType, Long entityId, Long filterUserId) {
        return historyProjection.queryAll(repo.jdbcClient(),
                AuditLogDescriptor.Read.History.params(entityType, entityId, filterUserId));
    }

    public List<ActivityItemDto> findActivityByActor(Long actorId) {
        return activityProjection.queryAll(repo.jdbcClient(),
                AuditLogDescriptor.Read.Activity.byActorParams(actorId));
    }
}
