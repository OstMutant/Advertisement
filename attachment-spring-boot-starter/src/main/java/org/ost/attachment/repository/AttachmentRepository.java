package org.ost.attachment.repository;

import org.ost.attachment.entities.Attachment;
import org.ost.attachment.storage.ConditionalOnAttachmentEnabled;
import org.ost.platform.core.model.EntityType;
import org.ost.sqlengine.RepositoryCustom;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static org.ost.attachment.repository.AttachmentDescriptor.*;

@Repository
@ConditionalOnAttachmentEnabled
public class AttachmentRepository extends RepositoryCustom {

    public record MediaStats(String mainUrl, String mainContentType, int count) {}

    private final AttachmentCrudRepository crud;

    AttachmentRepository(JdbcClient jdbcClient, AttachmentCrudRepository crud) {
        super(jdbcClient);
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
        executeUpdate(Write.SOFT_DELETE, Write.softDeleteParams(id, actorId));
    }

    public void softDeleteAll(EntityType entityType, Long entityId, Long actorId) {
        executeUpdate(Write.SOFT_DELETE_ALL, Write.softDeleteAllParams(entityType, entityId, actorId));
    }

    public void restoreDeleteAll(EntityType entityType, Long entityId, Long actorId) {
        executeUpdate(Write.SOFT_DELETE_ALL, Write.softDeleteAllParams(entityType, entityId, actorId));
    }

    public void restoreUndelete(EntityType entityType, Long entityId, String[] urls) {
        executeUpdate(Write.RESTORE_UNDELETE, Write.restoreUndeleteParams(entityType, entityId, urls));
    }

    public void restoreMarkDeleted(EntityType entityType, Long entityId, Long actorId, String[] urls) {
        executeUpdate(Write.RESTORE_MARK_DELETED,
                Write.restoreMarkDeletedParams(entityType, entityId, actorId, urls));
    }

    public List<Attachment> getByEntityId(EntityType entityType, Long entityId) {
        return findAll(Read.SELECT_ACTIVE_BY_ENTITY, Read.entityParams(entityType, entityId), Read.PROJECTION);
    }

    public List<String> getActiveUrls(EntityType entityType, Long entityId) {
        return findAll(Read.SELECT_ACTIVE_URLS, Read.entityParams(entityType, entityId),
                (rs, n) -> URL.extract(rs));
    }

    public List<String> findUrlsDeletedOlderThan(int days) {
        return findAll(Read.FIND_URLS_DELETED_OLDER_THAN, Read.findUrlsDeletedOlderThanParams(days),
                (rs, n) -> URL.extract(rs));
    }

    public int deleteByUrls(List<String> urls) {
        return executeUpdate(Write.DELETE_BY_URLS, Write.deleteByUrlsParams(urls));
    }

    public MediaStats loadMediaStats(EntityType entityType, Long entityId) {
        record Row(String url, String contentType) {}
        var params = Read.entityParams(entityType, entityId);
        var main = findOne(Read.SELECT_MAIN_MEDIA, params,
                (rs, n) -> new Row(URL.extract(rs), CONTENT_TYPE.extract(rs)));
        int count = findOne(Read.COUNT_ACTIVE, params, Integer.class).orElse(0);
        return main
                .map(m -> new MediaStats(m.url(), m.contentType(), count))
                .orElse(new MediaStats(null, null, count));
    }
}
