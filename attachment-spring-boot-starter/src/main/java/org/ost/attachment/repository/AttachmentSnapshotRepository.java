package org.ost.attachment.repository;

import lombok.RequiredArgsConstructor;
import org.ost.attachment.storage.ConditionalOnAttachmentEnabled;
import org.ost.platform.core.model.EntityType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnAttachmentEnabled
@RequiredArgsConstructor
public class AttachmentSnapshotRepository {

    private final JdbcClient jdbcClient;

    public void insert(EntityType entityType, Long entityId, String[] urls, String changesJson, Long actorId) {
        AttachmentSnapshotDescriptor.Write.INSERT.execute(jdbcClient,
                AttachmentSnapshotDescriptor.Write.insertParams(entityType.name(), entityId, urls, changesJson, actorId));
    }

    public List<String> getPrevUrls(EntityType entityType, Long entityId) {
        return jdbcClient.sql(AttachmentSnapshotDescriptor.Read.SELECT_PREV_URLS)
                .paramSource(AttachmentSnapshotDescriptor.Read.entityParams(entityType.name(), entityId))
                .query((rs, row) -> AttachmentSnapshotDescriptor.Read.extractUrls(rs))
                .optional().orElse(List.of());
    }

    public String[] getUrlsAtVersion(EntityType entityType, Long entityId, int version) {
        return jdbcClient.sql(AttachmentSnapshotDescriptor.Read.SELECT_URLS_AT_VERSION)
                .paramSource(AttachmentSnapshotDescriptor.Read.versionParams(entityType.name(), entityId, version))
                .query((rs, row) -> AttachmentSnapshotDescriptor.Read.extractUrls(rs))
                .optional()
                .map(l -> l.toArray(new String[0]))
                .orElse(new String[0]);
    }

    public Optional<List<String>> getUrlsForSnapshot(EntityType entityType, Long entityId, Long snapshotId) {
        return jdbcClient.sql(AttachmentSnapshotDescriptor.Read.SELECT_URLS_FOR_SNAPSHOT)
                .paramSource(AttachmentSnapshotDescriptor.Read.snapshotParams(entityType.name(), entityId, snapshotId))
                .query((rs, row) -> AttachmentSnapshotDescriptor.Read.extractUrls(rs))
                .optional();
    }

    public Optional<String> getChangesJsonForSnapshot(EntityType entityType, Long entityId, Long snapshotId) {
        return jdbcClient.sql(AttachmentSnapshotDescriptor.Read.SELECT_CHANGES_JSON_FOR_SNAPSHOT)
                .paramSource(AttachmentSnapshotDescriptor.Read.snapshotParams(entityType.name(), entityId, snapshotId))
                .query((rs, row) -> rs.getString(1))
                .optional();
    }

    public Optional<String> getChangesJson(EntityType entityType, Long entityId, int version) {
        return jdbcClient.sql(AttachmentSnapshotDescriptor.Read.SELECT_CHANGES_JSON_AT_VERSION)
                .paramSource(AttachmentSnapshotDescriptor.Read.versionParams(entityType.name(), entityId, version))
                .query((rs, row) -> rs.getString(1))
                .optional();
    }
}
