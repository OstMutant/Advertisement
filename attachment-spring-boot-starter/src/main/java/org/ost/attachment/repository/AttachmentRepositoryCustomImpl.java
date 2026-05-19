package org.ost.attachment.repository;

import lombok.RequiredArgsConstructor;
import org.ost.attachment.entities.Attachment;
import org.ost.attachment.storage.ConditionalOnAttachmentEnabled;
import org.ost.platform.core.model.EntityType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@ConditionalOnAttachmentEnabled
@RequiredArgsConstructor
public class AttachmentRepositoryCustomImpl implements AttachmentRepositoryCustom {

    private static final AttachmentDescriptor PROJECTION = new AttachmentDescriptor();

    private final JdbcClient jdbcClient;

    @Override
    public void softDelete(Long id, Long actorId) {
        AttachmentDescriptor.SOFT_DELETE.execute(jdbcClient,
                AttachmentDescriptor.softDeleteParams(id, actorId));
    }

    @Override
    public void softDeleteAll(EntityType entityType, Long entityId, Long actorId) {
        AttachmentDescriptor.SOFT_DELETE_ALL.execute(jdbcClient,
                AttachmentDescriptor.softDeleteAllParams(entityType, entityId, actorId));
    }

    @Override
    public void restoreDeleteAll(EntityType entityType, Long entityId, Long actorId) {
        AttachmentDescriptor.RESTORE_DELETE_ALL.execute(jdbcClient,
                AttachmentDescriptor.softDeleteAllParams(entityType, entityId, actorId));
    }

    @Override
    public void restoreUndelete(EntityType entityType, Long entityId, String[] urls) {
        AttachmentDescriptor.RESTORE_UNDELETE.execute(jdbcClient,
                AttachmentDescriptor.restoreUndeleteParams(entityType, entityId, urls));
    }

    @Override
    public void restoreMarkDeleted(EntityType entityType, Long entityId, Long actorId, String[] urls) {
        AttachmentDescriptor.RESTORE_MARK_DELETED.execute(jdbcClient,
                AttachmentDescriptor.restoreMarkDeletedParams(entityType, entityId, actorId, urls));
    }

    @Override
    public List<Attachment> getByEntityId(EntityType entityType, Long entityId) {
        return jdbcClient.sql(AttachmentDescriptor.SELECT_ACTIVE_BY_ENTITY_SQL)
                .paramSource(AttachmentDescriptor.entityParams(entityType, entityId))
                .query(PROJECTION).list();
    }

    @Override
    public List<String> getActiveUrls(EntityType entityType, Long entityId) {
        return jdbcClient.sql(AttachmentDescriptor.SELECT_ACTIVE_URLS_SQL)
                .paramSource(AttachmentDescriptor.entityParams(entityType, entityId))
                .query(String.class).list();
    }

    @Override
    public List<String> findUrlsDeletedOlderThan(int days) {
        return jdbcClient.sql(AttachmentDescriptor.FIND_URLS_DELETED_OLDER_THAN_SQL)
                .paramSource(AttachmentDescriptor.findUrlsDeletedOlderThanParams(days))
                .query(String.class).list();
    }

    @Override
    public int deleteByUrls(List<String> urls) {
        return jdbcClient.sql(AttachmentDescriptor.DELETE_BY_URLS_SQL)
                .paramSource(AttachmentDescriptor.deleteByUrlsParams(urls))
                .update();
    }

    @Override
    public MediaStats loadMediaStats(EntityType entityType, Long entityId) {
        record Row(String url, String contentType) {}
        var params = AttachmentDescriptor.entityParams(entityType, entityId);
        var main = jdbcClient.sql(AttachmentDescriptor.SELECT_MAIN_MEDIA_SQL)
                .paramSource(params)
                .query((rs, n) -> new Row(rs.getString(AttachmentDescriptor.Write.URL),
                                          rs.getString(AttachmentDescriptor.Write.CONTENT_TYPE)))
                .optional();
        Integer countVal = jdbcClient.sql(AttachmentDescriptor.COUNT_ACTIVE_SQL)
                .paramSource(params)
                .query(Integer.class).single();
        int count = countVal != null ? countVal : 0;
        return main
                .map(m -> new MediaStats(m.url(), m.contentType(), count))
                .orElse(new MediaStats(null, null, count));
    }
}
