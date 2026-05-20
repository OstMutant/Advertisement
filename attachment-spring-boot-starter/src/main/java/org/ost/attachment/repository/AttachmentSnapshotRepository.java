package org.ost.attachment.repository;

import org.ost.attachment.storage.ConditionalOnAttachmentEnabled;
import org.ost.platform.core.model.EntityType;
import org.ost.sqlengine.RepositoryCustom;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnAttachmentEnabled
public class AttachmentSnapshotRepository extends RepositoryCustom {

    public AttachmentSnapshotRepository(JdbcClient jdbcClient) {
        super(jdbcClient);
    }

    public void insert(EntityType entityType, Long entityId, String[] urls, String changesJson, Long actorId) {
        executeUpdate(AttachmentSnapshotDescriptor.Write.INSERT,
                AttachmentSnapshotDescriptor.Write.insertParams(entityType, entityId, urls, changesJson, actorId));
    }

    public List<String> getPrevUrls(EntityType entityType, Long entityId) {
        return findOne(AttachmentSnapshotDescriptor.Read.SELECT_PREV_URLS,
                AttachmentSnapshotDescriptor.Read.entityParams(entityType, entityId),
                (rs, row) -> AttachmentSnapshotDescriptor.Read.extractUrls(rs))
                .orElse(List.of());
    }

    public String[] getUrlsAtVersion(EntityType entityType, Long entityId, int version) {
        return findOne(AttachmentSnapshotDescriptor.Read.SELECT_URLS_AT_VERSION,
                AttachmentSnapshotDescriptor.Read.versionParams(entityType, entityId, version),
                (rs, row) -> AttachmentSnapshotDescriptor.Read.extractUrls(rs))
                .map(l -> l.toArray(new String[0]))
                .orElse(new String[0]);
    }

    public Optional<List<String>> getUrlsForSnapshot(EntityType entityType, Long entityId, Long snapshotId) {
        return findOne(AttachmentSnapshotDescriptor.Read.SELECT_URLS_FOR_SNAPSHOT,
                AttachmentSnapshotDescriptor.Read.snapshotParams(entityType, entityId, snapshotId),
                (rs, row) -> AttachmentSnapshotDescriptor.Read.extractUrls(rs));
    }

    public Optional<String> getChangesJsonForSnapshot(EntityType entityType, Long entityId, Long snapshotId) {
        return findOne(AttachmentSnapshotDescriptor.Read.SELECT_CHANGES_JSON_FOR_SNAPSHOT,
                AttachmentSnapshotDescriptor.Read.snapshotParams(entityType, entityId, snapshotId),
                (rs, row) -> AttachmentSnapshotDescriptor.CHANGES_SUMMARY.extract(rs));
    }

    public Optional<String> getChangesJson(EntityType entityType, Long entityId, int version) {
        return findOne(AttachmentSnapshotDescriptor.Read.SELECT_CHANGES_JSON_AT_VERSION,
                AttachmentSnapshotDescriptor.Read.versionParams(entityType, entityId, version),
                (rs, row) -> AttachmentSnapshotDescriptor.CHANGES_SUMMARY.extract(rs));
    }
}
