package org.ost.attachment.repository;

import org.ost.attachment.entities.AttachmentSnapshot;
import org.ost.attachment.storage.ConditionalOnAttachmentEnabled;
import org.ost.platform.core.model.EntityType;
import org.ost.sqlengine.RepositoryCustom;
import org.ost.sqlengine.exec.SqlCommand;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnAttachmentEnabled
public class AttachmentSnapshotRepository {

    private final RepositoryCustom repo;
    private final AttachmentSnapshotCrudRepository crud;

    public AttachmentSnapshotRepository(JdbcClient jdbcClient, AttachmentSnapshotCrudRepository crud) {
        this.repo = new RepositoryCustom(jdbcClient);
        this.crud = crud;
    }

    public void insert(EntityType entityType, Long entityId, String[] urls, String changesJson, Long actorId) {
        repo.execute(AttachmentSnapshotDescriptor.Write.INSERT,
                AttachmentSnapshotDescriptor.Write.insertParams(entityType.name(), entityId, urls, changesJson, actorId));
    }

    public List<String> getPrevUrls(EntityType entityType, Long entityId) {
        return repo.findOne(SqlCommand.of(AttachmentSnapshotDescriptor.Read.SELECT_PREV_URLS),
                AttachmentSnapshotDescriptor.Read.entityParams(entityType.name(), entityId),
                (rs, row) -> AttachmentSnapshotDescriptor.Read.extractUrls(rs))
                .orElse(List.of());
    }

    public String[] getUrlsAtVersion(EntityType entityType, Long entityId, int version) {
        return repo.findOne(SqlCommand.of(AttachmentSnapshotDescriptor.Read.SELECT_URLS_AT_VERSION),
                AttachmentSnapshotDescriptor.Read.versionParams(entityType.name(), entityId, version),
                (rs, row) -> AttachmentSnapshotDescriptor.Read.extractUrls(rs))
                .map(l -> l.toArray(new String[0]))
                .orElse(new String[0]);
    }

    public Optional<List<String>> getUrlsForSnapshot(EntityType entityType, Long entityId, Long snapshotId) {
        return repo.findOne(SqlCommand.of(AttachmentSnapshotDescriptor.Read.SELECT_URLS_FOR_SNAPSHOT),
                AttachmentSnapshotDescriptor.Read.snapshotParams(entityType.name(), entityId, snapshotId),
                (rs, row) -> AttachmentSnapshotDescriptor.Read.extractUrls(rs));
    }

    public Optional<String> getChangesJsonForSnapshot(EntityType entityType, Long entityId, Long snapshotId) {
        return repo.findOne(SqlCommand.of(AttachmentSnapshotDescriptor.Read.SELECT_CHANGES_JSON_FOR_SNAPSHOT),
                AttachmentSnapshotDescriptor.Read.snapshotParams(entityType.name(), entityId, snapshotId),
                (rs, row) -> rs.getString(1));
    }

    public Optional<String> getChangesJson(EntityType entityType, Long entityId, int version) {
        return repo.findOne(SqlCommand.of(AttachmentSnapshotDescriptor.Read.SELECT_CHANGES_JSON_AT_VERSION),
                AttachmentSnapshotDescriptor.Read.versionParams(entityType.name(), entityId, version),
                (rs, row) -> rs.getString(1));
    }
}
