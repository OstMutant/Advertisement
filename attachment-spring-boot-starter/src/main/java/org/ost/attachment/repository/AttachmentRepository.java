package org.ost.attachment.repository;

import org.ost.attachment.entities.Attachment;
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
public class AttachmentRepository {

    public record MediaStats(String mainUrl, String mainContentType, int count) {}

    private final RepositoryCustom repo;
    private final AttachmentCrudRepository crud;

    public AttachmentRepository(JdbcClient jdbcClient, AttachmentCrudRepository crud) {
        this.repo = new RepositoryCustom(jdbcClient);
        this.crud = crud;
    }

    public Attachment save(Attachment attachment) {
        return crud.save(attachment);
    }

    public Iterable<Attachment> saveAll(Iterable<Attachment> attachments) {
        return crud.saveAll(attachments);
    }

    public Optional<Attachment> findById(Long id) {
        return crud.findById(id);
    }

    public void softDelete(Long id, Long actorId) {
        repo.execute(AttachmentDescriptor.Write.SOFT_DELETE,
                AttachmentDescriptor.Write.softDeleteParams(id, actorId));
    }

    public void softDeleteAll(EntityType entityType, Long entityId, Long actorId) {
        repo.execute(AttachmentDescriptor.Write.SOFT_DELETE_ALL,
                AttachmentDescriptor.Write.softDeleteAllParams(entityType, entityId, actorId));
    }

    public void restoreDeleteAll(EntityType entityType, Long entityId, Long actorId) {
        repo.execute(AttachmentDescriptor.Write.RESTORE_DELETE_ALL,
                AttachmentDescriptor.Write.softDeleteAllParams(entityType, entityId, actorId));
    }

    public void restoreUndelete(EntityType entityType, Long entityId, String[] urls) {
        repo.execute(AttachmentDescriptor.Write.RESTORE_UNDELETE,
                AttachmentDescriptor.Write.restoreUndeleteParams(entityType, entityId, urls));
    }

    public void restoreMarkDeleted(EntityType entityType, Long entityId, Long actorId, String[] urls) {
        repo.execute(AttachmentDescriptor.Write.RESTORE_MARK_DELETED,
                AttachmentDescriptor.Write.restoreMarkDeletedParams(entityType, entityId, actorId, urls));
    }

    public List<Attachment> getByEntityId(EntityType entityType, Long entityId) {
        return repo.findAll(SqlCommand.of(AttachmentDescriptor.Read.SELECT_ACTIVE_BY_ENTITY),
                AttachmentDescriptor.Read.entityParams(entityType, entityId),
                AttachmentDescriptor.Read.PROJECTION);
    }

    public List<String> getActiveUrls(EntityType entityType, Long entityId) {
        return repo.findAll(SqlCommand.of(AttachmentDescriptor.Read.SELECT_ACTIVE_URLS),
                AttachmentDescriptor.Read.entityParams(entityType, entityId),
                (rs, n) -> rs.getString(1));
    }

    public List<String> findUrlsDeletedOlderThan(int days) {
        return repo.findAll(SqlCommand.of(AttachmentDescriptor.Read.FIND_URLS_DELETED_OLDER_THAN),
                AttachmentDescriptor.Read.findUrlsDeletedOlderThanParams(days),
                (rs, n) -> rs.getString(1));
    }

    public int deleteByUrls(List<String> urls) {
        return repo.executeUpdate(SqlCommand.of(AttachmentDescriptor.Write.DELETE_BY_URLS),
                AttachmentDescriptor.Write.deleteByUrlsParams(urls));
    }

    public MediaStats loadMediaStats(EntityType entityType, Long entityId) {
        record Row(String url, String contentType) {}
        var params = AttachmentDescriptor.Read.entityParams(entityType, entityId);
        var main = repo.findOne(SqlCommand.of(AttachmentDescriptor.Read.SELECT_MAIN_MEDIA),
                params,
                (rs, n) -> new Row(rs.getString(AttachmentDescriptor.URL.columnName()),
                                    rs.getString(AttachmentDescriptor.CONTENT_TYPE.columnName())));
        int count = repo.findOne(SqlCommand.of(AttachmentDescriptor.Read.COUNT_ACTIVE),
                params, Integer.class).orElse(0);
        return main
                .map(m -> new MediaStats(m.url(), m.contentType(), count))
                .orElse(new MediaStats(null, null, count));
    }
}
