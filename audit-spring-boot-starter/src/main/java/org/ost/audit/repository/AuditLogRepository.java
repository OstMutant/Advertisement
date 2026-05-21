package org.ost.audit.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.ost.audit.repository.AuditLogDescriptor.*;

@Repository
public class AuditLogRepository extends RepositoryCustom {

    private final Read.Activity.Projection activityProjection;
    private final Read.History.Projection  historyProjection;

    public AuditLogRepository(JdbcClient jdbcClient,
                              @Qualifier("auditObjectMapper") ObjectMapper objectMapper,
                              List<EntityDisplayNameResolver> resolvers) {
        super(jdbcClient);
        this.activityProjection = new Read.Activity.Projection(objectMapper, resolvers);
        this.historyProjection  = new Read.History.Projection(objectMapper);
    }

    public void insert(EntityType entityType, Long entityId, ActionType actionType,
                       String snapshotData, String changesSummary, Long actorId) {
        executeUpdate(Write.INSERT,
                Write.insertParams(entityType, entityId, actionType, snapshotData, changesSummary, actorId));
    }

    public Optional<String> getLastSnapshotData(EntityType entityType, Long entityId) {
        return findOne(Read.SELECT_LAST_SNAPSHOT_DATA,
                Read.entityParams(entityType, entityId),
                (rs, row) -> SNAPSHOT_DATA.extract(rs));
    }

    public Optional<SnapshotContent> getSnapshotContent(Long snapshotId, EntityType entityType) {
        return findOne(Read.SELECT_SNAPSHOT_CONTENT_BY_ID,
                Read.snapshotByIdParams(snapshotId, entityType),
                (rs, row) -> Read.mapSnapshotContent(rs));
    }

    public Optional<SnapshotContent> getPreviousSnapshotContent(Long snapshotId, EntityType entityType) {
        return findOne(Read.SELECT_PREVIOUS_SNAPSHOT_CONTENT,
                Read.previousSnapshotContentParams(snapshotId, entityType),
                (rs, row) -> Read.mapSnapshotContent(rs));
    }

    public Optional<Long> findLastSnapshotId(EntityType entityType, Long entityId) {
        return findOne(Read.SELECT_LAST_SNAPSHOT_ID,
                Read.entityParams(entityType, entityId),
                Long.class);
    }

    public String getChangesSummary(Long snapshotId) {
        return findOne(Read.SELECT_CHANGES_SUMMARY,
                Read.idParams(snapshotId),
                String.class).orElseThrow();
    }

    public void updateChangesSummary(Long snapshotId, String json) {
        executeUpdate(Write.UPDATE_CHANGES_SUMMARY,
                Write.updateChangesSummaryParams(snapshotId, json));
    }

    public void deleteOlderThan(int days) {
        executeUpdate(Write.DELETE_OLDER_THAN,
                Write.deleteOlderThanParams(days));
    }

    public List<EntityHistoryDto> getEntityHistory(EntityType entityType, Long entityId, Long filterUserId) {
        return queryAll(historyProjection,
                Read.History.params(entityType, entityId, filterUserId));
    }

    public List<ActivityItemDto> findActivityByActor(Long actorId) {
        return queryAll(activityProjection,
                Read.Activity.byActorParams(actorId));
    }
}
